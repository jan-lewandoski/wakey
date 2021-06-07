package com.eps.wakey.services
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.camera2.*
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.eps.wakey.R
import com.eps.wakey.activities.home.HomeActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.*
import kotlin.collections.ArrayList
import kotlin.system.exitProcess


/**
 * Copyright (c) 2019 by Roman Sisik. All rights reserved.
 */
class CamService: Service() {

    private var wm: WindowManager? = null
    private var rootView: View? = null
    private var textureView: TextureView? = null
    private var overlayPosition: Position? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private var cameraManager: CameraManager? = null
    private var previewSize: Size? = null
    private var cameraDevice: CameraDevice? = null
    private var captureRequest: CaptureRequest? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var shouldShowPreview = true

    private var isPlaying = false
    private var tts:TextToSpeech? = null

    private var previousPeriods: MutableList<EyeBlinkPeriod>? = null


    private var speedY = 0.0f
    private var last: Position = Position(0f,0f)

    var detector: FaceDetector? = null
    private var blinks = 0
    private var framesWithLeftEyeClosed = 0
    private var framesWithLeftEyeOpen = 0


    private var framesWithRightEyeClosed = 0
    private var framesWithRightEyeOpen = 0

    private var totalFramesClosed = 0
    private var totalFramesOpen = 0

    private var sessionTime: Long = 0

    private var sessionInitTime: Long = 0

    private var lastWarningTime: Long = 0

    private var speaking: Boolean = false

    private var mediaPlayer: MediaPlayer? = null

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
        updateTime()
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

        sessionInitTime = System.currentTimeMillis()
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

        mediaPlayer = MediaPlayer.create(applicationContext, R.raw.sound)
        mediaPlayer?.setVolume(1f,1f)
        delay(500){
            mediaPlayer?.start()
        }
        tts = TextToSpeech(this) {
            tts?.language = Locale.forLanguageTag(getString(R.string.used_language))
            delay(2500) {
                if (!speaking) {
                    tts?.speak(
                        getString(R.string.tts_welcome),
                        TextToSpeech.QUEUE_FLUSH,
                        null, R.string.tts_welcome.toString()
                    )
                }
            }
        }

        val speechListener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking = true
            }

            override fun onDone(utteranceId: String?) {
                speaking = false
            }

            override fun onError(utteranceId: String?) {
                speaking = false
            }
        }

        tts!!.setOnUtteranceProgressListener(speechListener)
        previousPeriods = mutableListOf<EyeBlinkPeriod>()
        startForeground()

    }


    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        if (rootView != null)
            wm?.removeView(rootView)
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
        sendBroadcast(Intent(ACTION_STOPPED))
    }

    private fun start() {

        shouldShowPreview = false

        initCam(480, 640)
    }

    private fun startWithPreview() {

        shouldShowPreview = true

        // Initialize view drawn over other apps
        initOverlay()

        // Initialize camera here if texture view already initialized
        if (textureView!!.isAvailable) {
            initCam(textureView!!.width, textureView!!.height)
        }else {
            textureView!!.surfaceTextureListener = surfaceTextureListener
        }


    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initOverlay() {

        val li = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        rootView = li.inflate(R.layout.fragment_overlay, null)
        textureView = rootView?.findViewById(R.id.texPreview)

        val type = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        rootView?.setOnClickListener {
            val pendingIntent: PendingIntent =
                Intent(this, HomeActivity::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(
                        this, 0, notificationIntent, 0
                    )
                }
            pendingIntent.send()
        }

        rootView?.setOnTouchListener {
                view, e ->
            Log.d("OVERLAY", e.toString())

            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    overlayPosition = overlayParams!!.position - e.position
                    last = overlayParams!!.position - e.position
                }
                MotionEvent.ACTION_MOVE -> {
                    overlayParams!!.position = overlayPosition!!.plus(e.position)
                    wm!!.updateViewLayout(rootView, overlayParams)
                    speedY = overlayParams!!.position.fy - last!!.fy
                    last = overlayParams!!.position

                }
                MotionEvent.ACTION_UP -> {

                    Log.d("here", "speed: " + speedY)

                    val path = Path().apply {
                        moveTo(overlayParams!!.position.fx, overlayParams!!.position.fy)
                        arcTo(
                            -overlayParams!!.position.fx,
                            overlayParams!!.position.fy -
                                    VELOCITY_MULTIPLIER* kotlin.math.abs(speedY),
                            overlayParams!!.position.fx,
                            overlayParams!!.position.fy +
                                    VELOCITY_MULTIPLIER*kotlin.math.abs(speedY),
                            if (speedY >= 0) 0f else 359f,
                            if (speedY >= 0) 90f else -90f,
                            true)
                    }

                    Log.d("here", "path: " + path)

                    ValueAnimator.ofPropertyValuesHolder(
                        PropertyValuesHolder.ofMultiFloat("pos",
                            path)).apply {
                        addUpdateListener { updated ->
                            overlayParams!!.position = Position(
                                (updated.animatedValue as FloatArray)[0],
                                (updated.animatedValue as FloatArray)[1]
                            )
                            wm!!.updateViewLayout(rootView, overlayParams)

                        }
                        //interpolator = AccelerateDecelerateInterpolator()
                        duration = 400
                        start()
                    }
                    overlayPosition = null
                }
            }
            false
        }

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm!!.addView(rootView, overlayParams)
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

    private fun chooseSupportedSize(
        camId: String, textureViewWidth: Int, textureViewHeight: Int
    ): Size {
        return Size(300, 480)
    }

    private fun startForeground() {

        val pendingIntent: PendingIntent =
            Intent(this, HomeActivity::class.java).let { notificationIntent ->
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

            val requestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            ).apply {


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
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(24,24))
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

    private fun updateTime(){
        val tv: TextView? = rootView?.findViewById(R.id.session_time_textview)

        sessionTime = System.currentTimeMillis() -  sessionInitTime

        val hours = (sessionTime / (1000 * 60 * 60) % 24)
        val minutes = (sessionTime / (1000 * 60) % 60)
        val seconds = (sessionTime / 1000) % 60

        tv?.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    private fun incrementLeftEyeFrames(prob: Float){
        if (prob < EYE_TRACKING_SENSITIVITY){
            framesWithLeftEyeClosed += 1
        }
        else {
            framesWithLeftEyeOpen += 1
        }
    }
    private fun incrementRightEyeFrames(prob: Float){
        if (prob < EYE_TRACKING_SENSITIVITY){
            framesWithRightEyeClosed += 1
        }
        else {
            framesWithRightEyeOpen += 1
        }
    }
    private fun resetFrameCounters(){
        framesWithLeftEyeClosed = 0
        framesWithLeftEyeOpen = 0
        framesWithRightEyeClosed = 0
        framesWithRightEyeOpen = 0
    }
    private fun eyeJustOpened(eye: Eye): Boolean{
        if(eye.openProb > EYE_TRACKING_SENSITIVITY){
            if(eye.framesClosed >= MINIMUM_FRAMES_PER_BLINK){
                return true
            }
        }
        return false
    }
    private fun longBlinksOccur(): Boolean{
        if (previousPeriods?.size!! > 20){
            Log.d("ERROR", "NOT GOOD")
            exitProcess(0)
        }
        if (previousPeriods?.size  == 20 ){
            var framesExceedingLimit = 0
            for (p in previousPeriods!!){
                if (p.framesClosed >= FRAMES_TO_DETERMINE_DROWSY){
                    framesExceedingLimit++
                }
            }
            if(framesExceedingLimit >= SUS_PERIODS_TO_DETERMINE_DROWSY){
                return true
            }
        }
        return false
    }
    private fun processProbability(leftEyeProb: Float, rightEyeProb: Float){
        var tired = false
        var warning = false
        incrementLeftEyeFrames(leftEyeProb)
        //incrementRightEyeFrames(rightEyeProb)
        var leftEye = Eye(leftEyeProb, framesWithLeftEyeOpen, framesWithLeftEyeClosed)
        //var rightEye = Eye(rightEyeProb, framesWithRightEyeOpen, framesWithRightEyeClosed)
        val leftJustOpened = eyeJustOpened(leftEye)
        //val rightJustOpened = eyeJustOpened(rightEye)

        if (framesWithLeftEyeClosed > FRAMES_TO_TRIGGER_ALARM){

            if (!speaking && System.currentTimeMillis() - lastWarningTime > 5000) {
                mediaPlayer = MediaPlayer.create(applicationContext, R.raw.warning)
                mediaPlayer?.start()
                delay(2500) {
                    tts?.speak(getString(R.string.tts_feeling_tired), TextToSpeech.QUEUE_FLUSH, null, R.string.tts_feeling_tired.toString())
                }
                lastWarningTime = System.currentTimeMillis()
            }

        }
        if (leftJustOpened){
            previousPeriods?.add(EyeBlinkPeriod(leftEye.framesOpen, leftEye.framesClosed))
                if (previousPeriods?.size!! > PERIODS_TO_REMEMBER){
                    previousPeriods?.removeFirst()
                }
            blinks++
            if (longBlinksOccur()){
                previousPeriods?.clear()
                if (!speaking && System.currentTimeMillis() - lastWarningTime > 5000) {
                    mediaPlayer = MediaPlayer.create(applicationContext, R.raw.sound)
                    mediaPlayer?.start()
                    delay(1500) {
                        tts?.speak(getString(R.string.tts_feeling_tired), TextToSpeech.QUEUE_FLUSH, null, R.string.tts_feeling_tired.toString())
                    }
                    lastWarningTime = System.currentTimeMillis()
                }
            }
            resetFrameCounters()
            Log.d("BLINKS", "Blinks: " + previousPeriods)
        }
    }

    private fun eyesOpen(bitmap: Image): Boolean {
        val image = InputImage.fromMediaImage(bitmap, 270)
        var out = false
        val result = detector?.process(image)
            ?.addOnSuccessListener { faces ->
                for (face in faces) {
                    var leftEyeOpenProb = 1.0f
                    var rightEyeOpenProb = 1.0f

                    if (face.leftEyeOpenProbability != null) {
                        leftEyeOpenProb = face.leftEyeOpenProbability
                    }
                    if (face.rightEyeOpenProbability != null) {
                        rightEyeOpenProb = face.rightEyeOpenProbability

                    }
                    processProbability(leftEyeOpenProb, rightEyeOpenProb)
                }
            }
            ?.addOnFailureListener { e ->
                Log.d("log", "failed" + e)
            }
            ?.addOnCompleteListener {tasks ->
                bitmap.close()
            }


        return out
    }
    private fun delay(millis: Long, foo: () -> Unit){
        Handler(Looper.getMainLooper()).postDelayed({
            foo()
        }, millis)
    }
    companion object {

        val TAG = "CamService"

        val ACTION_START = "com.example.wakey.action.START"
        val ACTION_START_WITH_PREVIEW = "com.example.wakey.action.START_WITH_PREVIEW"
        val ACTION_STOPPED = "com.example.wakey.action.STOPPED"

        val ONGOING_NOTIFICATION_ID = 6660
        val CHANNEL_ID = "cam_service_channel_id"
        val CHANNEL_NAME = "cam_service_channel_name"

        val VELOCITY_MULTIPLIER = 10
        var SHOW_CAMERA_PREVIEW = false
        var EYE_TRACKING_SENSITIVITY = 0.3F
        var MINIMUM_FRAMES_PER_BLINK = 3
        val BLINK_TO_SECONDS = 1f/24f
        val PERIODS_TO_REMEMBER = 20
        val FRAMES_TO_TRIGGER_ALARM = 12
        val FRAMES_TO_DETERMINE_DROWSY = 7
        val SUS_PERIODS_TO_DETERMINE_DROWSY = (PERIODS_TO_REMEMBER * 0.33).toInt()

    }
}

private val MotionEvent.position: Position
    get() = Position(rawX, rawY)

private var WindowManager.LayoutParams.position: Position
    get() = Position(x.toFloat(), y.toFloat())
    set(value) {
        x = value.x
        y = value.y
    }

private data class Position(val fx: Float, val fy: Float) {

    val x: Int
        get() = fx.toInt()

    val y: Int
        get() = fy.toInt()

    operator fun plus(p: Position) = Position(fx + p.fx, fy + p.fy)
    operator fun minus(p: Position) = Position(fx - p.fx, fy - p.fy)
}
private data class EyeBlinkPeriod(val framesOpen: Int, val framesClosed: Int) {
    val timeOpen: Float
        get() = framesOpen * CamService.BLINK_TO_SECONDS
    val timeClosed: Float
        get() = framesClosed * CamService.BLINK_TO_SECONDS
    val totalFrames: Int
        get() = framesOpen + framesClosed
    val totalTime: Float
        get() = totalFrames * CamService.BLINK_TO_SECONDS
    operator fun plus(e: EyeBlinkPeriod) = EyeBlinkPeriod(
        framesOpen+ e.framesOpen, framesClosed + e.framesClosed
    )
}
private data class Eye(val openProb: Float, val framesOpen: Int, val framesClosed: Int){

}