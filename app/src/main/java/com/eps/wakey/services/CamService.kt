package com.eps.wakey.services

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.AudioManager
import android.media.Image
import android.media.ImageReader
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.*
import androidx.core.app.NotificationCompat
import com.eps.wakey.R
import com.eps.wakey.activities.MainActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions


/**
 * Copyright (c) 2019 by Roman Sisik. All rights reserved.
 */
class CamService: Service() {

    private var wm: WindowManager? = null
    private var rootView: View? = null
    private var textureView: TextureView? = null

    private var cameraManager: CameraManager? = null
    private var previewSize: Size? = null
    private var cameraDevice: CameraDevice? = null
    private var captureRequest: CaptureRequest? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var shouldShowPreview = true

    private var toneGen: ToneGenerator? = null
    private var isPlaying = false

    var detector: FaceDetector? = null

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {}

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {}
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            initCam(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}


    }


    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader?.acquireLatestImage()
        if (image != null) {
            eyesOpen(image)
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(currentCameraDevice: CameraDevice) {
            cameraDevice = currentCameraDevice
            createCaptureSession()
        }

        override fun onDisconnected(currentCameraDevice: CameraDevice) {
            currentCameraDevice.close()
            cameraDevice = null
            toneGen?.stopTone()

        }

        override fun onError(currentCameraDevice: CameraDevice, error: Int) {
            currentCameraDevice.close()
            cameraDevice = null
            toneGen?.stopTone()

        }
    }


    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_START -> start()

            ACTION_START_WITH_PREVIEW -> startWithPreview()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()

        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        // High-accuracy landmark detection and face classification
        val highAccuracyOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        // Real-time contour detection
        val realTimeOpts = FaceDetectorOptions.Builder()
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        detector = FaceDetection.getClient(realTimeOpts)
        startForeground()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        toneGen?.stopTone()
        if (rootView != null)
            wm?.removeView(rootView)

        sendBroadcast(Intent(ACTION_STOPPED))
    }

    private fun start() {

        shouldShowPreview = false

        initCam(320, 200)
    }

    private fun startWithPreview() {

        shouldShowPreview = true

        // Initialize view drawn over other apps
        initOverlay()

        // Initialize camera here if texture view already initialized
        if (textureView!!.isAvailable)
            initCam(textureView!!.width, textureView!!.height)
        else
            textureView!!.surfaceTextureListener = surfaceTextureListener
    }

    private fun initOverlay() {

        val li = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        rootView = li.inflate(R.layout.fragment_overlay, null)
        textureView = rootView?.findViewById(R.id.texPreview)

        val type = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.LEFT

        params.x = 0
        params.y = 0

        // TODO: Navigate to home on click
        // TODO: Update position
        rootView?.setOnClickListener {
            Log.d("OVERLAY", "Clicked")
        }


        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm!!.addView(rootView, params)
    }

    @SuppressLint("MissingPermission")
    private fun initCam(width: Int, height: Int) {

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        var camId: String? = null

        for (id in cameraManager!!.cameraIdList) {
            val characteristics = cameraManager!!.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                camId = id
                break
            }
        }

        previewSize = chooseSupportedSize(camId!!, width, height)

        cameraManager!!.openCamera(camId, stateCallback, null)
    }

    private fun chooseSupportedSize(camId: String, textureViewWidth: Int, textureViewHeight: Int): Size {
        return Size(300, 480)
    }

    private fun startForeground() {

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_NONE
            )
            channel.lightColor = Color.BLUE
            channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getText(R.string.app_name))
            .setContentText(getText(R.string.app_name))
            .setSmallIcon(R.drawable.wakey_logo)
            .setContentIntent(pendingIntent)
            .setTicker(getText(R.string.app_name))
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    private fun createCaptureSession() {
        try {
            val targetSurfaces = ArrayList<Surface>()

            val requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {


                if (shouldShowPreview) {
                    val texture = textureView!!.surfaceTexture!!
                    texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
                    val previewSurface = Surface(texture)

                    targetSurfaces.add(previewSurface)
                    addTarget(previewSurface)
                }

                imageReader = ImageReader.newInstance(
                    previewSize!!.getWidth(), previewSize!!.getHeight(),
                    ImageFormat.YUV_420_888, 20
                )
                imageReader!!.setOnImageAvailableListener(imageListener, null)

                targetSurfaces.add(imageReader!!.surface)
                addTarget(imageReader!!.surface)

                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }

            cameraDevice!!.createCaptureSession(
                targetSurfaces,
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        if (null == cameraDevice) {
                            return
                        }

                        captureSession = cameraCaptureSession
                        try {
                            captureRequest = requestBuilder!!.build()
                            captureSession!!.setRepeatingRequest(
                                captureRequest!!,
                                captureCallback,
                                null
                            )

                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "createCaptureSession", e)
                        }

                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Log.e(TAG, "createCaptureSession()")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "createCaptureSession", e)
        }
    }

    private fun stopCamera() {
        try {
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader?.close()
            imageReader = null

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun eyesOpen(bitmap: Image): Boolean {
        val image = InputImage.fromMediaImage(bitmap, 270)
        var out = false
        val result = detector?.process(image)
            ?.addOnSuccessListener { faces ->
                Log.d("log", "success")
                if (faces.size == 0){
                    toneGen?.stopTone()
                }
                for (face in faces) {
                    Log.d("log", "face")
                    var prob = 1
                    if (face.leftEyeOpenProbability != null) {
                        val leftEyeOpenProb = face.leftEyeOpenProbability
                        Log.d("FACES", "left eye: " + leftEyeOpenProb)
                        if (leftEyeOpenProb < 0.3){
                            prob = 0
                        }
                    }
                    else{
                        Log.d("FACES", "NOPE")
                    }
                    if (face.rightEyeOpenProbability != null) {
                        val rightEyeOpenProb = face.rightEyeOpenProbability
                        Log.d("FACES", "right eye: " + rightEyeOpenProb)
                        if (rightEyeOpenProb < 0.3){
                            prob = 0
                        }
                    }
                    if (prob < 0.1){
                        toneGen?.startTone(ToneGenerator.TONE_DTMF_0)
                    }
                    else{
                        toneGen?.stopTone()
                    }
                }
            }
            ?.addOnFailureListener { e ->
                Log.d("log", "failed" + e)
            }
            ?.addOnCompleteListener {tasks ->
                Log.d("log", "here")
                bitmap.close()
            }


        return out
    }

    companion object {

        val TAG = "CamService"

        val ACTION_START = "com.example.wakey.action.START"
        val ACTION_START_WITH_PREVIEW = "com.example.wakey.action.START_WITH_PREVIEW"
        val ACTION_STOPPED = "com.example.wakey.action.STOPPED"

        val ONGOING_NOTIFICATION_ID = 6660
        val CHANNEL_ID = "cam_service_channel_id"
        val CHANNEL_NAME = "cam_service_channel_name"

    }
}