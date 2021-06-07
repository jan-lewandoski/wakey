package com.eps.wakey.activities.home

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.TextureView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class CameraPreview : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onUserLeaveHint() {
        val rational = Rational(1,1);

        val params = PictureInPictureParams.Builder()
        params.setAspectRatio(rational)
        enterPictureInPictureMode(params.build())
        super.onUserLeaveHint()
    }

    fun setContent(texture: TextureView) {
        setContentView(texture)
    }
}