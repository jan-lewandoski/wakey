package com.eps.wakey.activities.home

import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.TextureView
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.eps.wakey.R

class CameraPreview : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        instance = this
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUserLeaveHint() {
        val rational = Rational(1,1);

        val params = PictureInPictureParams.Builder()
        params.setAspectRatio(rational)
        enterPictureInPictureMode(params.build())
        super.onUserLeaveHint()
    }
    companion object{
        var instance: CameraPreview? = null
    }
}