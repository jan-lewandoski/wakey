package com.eps.wakey.activities.home

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.eps.wakey.R
import com.eps.wakey.utils.isServiceRunning
import android.provider.Settings
import android.util.Log
import com.eps.wakey.fragments.ActionBottomFragment
import com.eps.wakey.services.CamService
import kotlinx.android.synthetic.main.activity_home.*


class HomeActivity : AppCompatActivity() {

    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            Log.d("OVERLAY", "Received")
            when (p1?.action) {
                CamService.ACTION_STOPPED -> flipButtonVisibility(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_home)

        initView()

        val permission = Manifest.permission.CAMERA

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), CODE_PERM_CAMERA)
        }

        floatingButSettings.setOnClickListener {
            openSettings()
        }
    }

    override fun onResume() {
        super.onResume()

        registerReceiver(receiver, IntentFilter(CamService.ACTION_STOPPED))

        val running = isServiceRunning(this, CamService::class.java)

        flipButtonVisibility(running)
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        stopService(Intent(this, CamService::class.java))
        super.onDestroy()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CODE_PERM_CAMERA -> {
                if (grantResults?.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getString(R.string.err_no_cam_permission), Toast.LENGTH_LONG).show()
                    moveTaskToBack(true)
                }
            }
        }
    }

    private fun initView() {
        butStart.setOnClickListener {
            val sharedPref = applicationContext.getSharedPreferences("SETTINGS", MODE_PRIVATE)

            SHOW_CAMERA_PREVIEW = sharedPref.getBoolean("WITH_PREVIEW", false)
            EYE_TRACKING_SENSITIVITY = sharedPref.getFloat("EYE_TRACKING_SENSITIVITY", 0.3F)

            if (SHOW_CAMERA_PREVIEW) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {

                    // Don't have permission to draw over other apps yet - ask user to give permission
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivityForResult(settingsIntent, CODE_PERM_SYSTEM_ALERT_WINDOW)
                    return@setOnClickListener
                }
            }

            if (!isServiceRunning(this, CamService::class.java)) {
               if (SHOW_CAMERA_PREVIEW)  notifyService(CamService.ACTION_START_WITH_PREVIEW)
               else notifyService(CamService.ACTION_START)
                moveTaskToBack(true)
            }
            moveTaskToBack(true)
        }

        butStop.setOnClickListener {
            stopService(Intent(this, CamService::class.java))
        }
    }

    private fun notifyService(action: String) {
        val intent = Intent(this, CamService::class.java)
        intent.action = action
        startService(intent)
    }

    private fun flipButtonVisibility(running: Boolean) {
        butStartContainer?.visibility =  if (running) View.GONE else View.VISIBLE
        butStopContainer?.visibility =  if (running) View.VISIBLE else View.GONE
    }

    private fun openSettings() {
        val bottomDialog = ActionBottomFragment.newInstance()
        bottomDialog.show(supportFragmentManager, ActionBottomFragment.TAG)
    }

    companion object {
        val CODE_PERM_SYSTEM_ALERT_WINDOW = 6111
        val CODE_PERM_CAMERA = 6112
        var SHOW_CAMERA_PREVIEW = false
        var EYE_TRACKING_SENSITIVITY = 0.3F
    }
}