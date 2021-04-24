package com.eps.wakey.services

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.*
import androidx.core.app.NotificationCompat
import com.eps.wakey.R
import com.eps.wakey.activities.MainActivity
import com.eps.wakey.utils.YuvToRgbConverter
import kotlin.math.absoluteValue


/**
 * Copyright (c) 2019 by Roman Sisik. All rights reserved.
 */
class CamService: Service() {

    // UI
    private var wm: WindowManager? = null
    private var rootView: View? = null
    private var textureView: TextureView? = null

    // Camera2-related stuff
    private var cameraManager: CameraManager? = null
    private var previewSize: Size? = null
    private var cameraDevice: CameraDevice? = null
    private var captureRequest: CaptureRequest? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    // You can start service in 2 modes - 1.) with preview 2.) without preview (only bg processing)
    private var shouldShowPreview = true


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
            val bmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            val yuvToRgbConverter = YuvToRgbConverter(this)
            yuvToRgbConverter.yuvToRgb(image, bmp)
            val isDark = isDark(bmp)
            Log.d("IMAGE", "Is dark? -> $isDark")
        }
        image?.close()
    }

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(currentCameraDevice: CameraDevice) {
            cameraDevice = currentCameraDevice
            createCaptureSession()
        }

        override fun onDisconnected(currentCameraDevice: CameraDevice) {
            currentCameraDevice.close()
            cameraDevice = null
        }

        override fun onError(currentCameraDevice: CameraDevice, error: Int) {
            currentCameraDevice.close()
            cameraDevice = null
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
        startForeground()
    }

    override fun onDestroy() {
        super.onDestroy()

        stopCamera()

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
        rootView = li.inflate(R.layout.overlay, null)
        textureView = rootView?.findViewById(R.id.texPreview)

        val type = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

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

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Get all supported sizes for TextureView
        val characteristics = manager.getCameraCharacteristics(camId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val supportedSizes = map?.getOutputSizes(SurfaceTexture::class.java)

        // We want to find something near the size of our TextureView
        val texViewArea = textureViewWidth * textureViewHeight
        val texViewAspect = textureViewWidth.toFloat()/textureViewHeight.toFloat()

        val nearestToFurthestSz = supportedSizes?.sortedWith(compareBy(
            // First find something with similar aspect
            {
                val aspect = if (it.width < it.height) it.width.toFloat() / it.height.toFloat()
                else it.height.toFloat() / it.width.toFloat()
                (aspect - texViewAspect).absoluteValue
            },
            // Also try to get similar resolution
            {
                (texViewArea - it.width * it.height).absoluteValue
            }
        ))


        if (nearestToFurthestSz != null) {
            if (nearestToFurthestSz.isNotEmpty())
                return nearestToFurthestSz[0]
        }

        return Size(320, 200)
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
            .setSmallIcon(R.drawable.notification_template_icon_bg)
            .setContentIntent(pendingIntent)
            .setTicker(getText(R.string.app_name))
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    private fun createCaptureSession() {
        try {
            // Prepare surfaces we want to use in capture session
            val targetSurfaces = ArrayList<Surface>()

            // Prepare CaptureRequest that can be used with CameraCaptureSession
            val requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {

                if (shouldShowPreview) {
                    val texture = textureView!!.surfaceTexture!!
                    texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
                    val previewSurface = Surface(texture)

                    targetSurfaces.add(previewSurface)
                    addTarget(previewSurface)
                }

                // Configure target surface for background processing (ImageReader)
                imageReader = ImageReader.newInstance(
                    previewSize!!.getWidth(), previewSize!!.getHeight(),
                    ImageFormat.YUV_420_888, 2
                )
                imageReader!!.setOnImageAvailableListener(imageListener, null)

                targetSurfaces.add(imageReader!!.surface)
                addTarget(imageReader!!.surface)

                // Set some additional parameters for the request
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }

            // Prepare CameraCaptureSession
            cameraDevice!!.createCaptureSession(
                targetSurfaces,
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (null == cameraDevice) {
                            return
                        }

                        captureSession = cameraCaptureSession
                        try {
                            // Now we can start capturing
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

    fun isDark(bitmap: Bitmap): Boolean {
        var dark = false
        val darkThreshold = bitmap.width * bitmap.height * 0.45f
        var darkPixels = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val luminance = 0.299 * r + 0.0f + 0.587 * g + 0.0f + 0.114 * b + 0.0f
            if (luminance < 150) {
                darkPixels++
            }
        }
        if (darkPixels >= darkThreshold) {
            dark = true
        }
        return dark
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