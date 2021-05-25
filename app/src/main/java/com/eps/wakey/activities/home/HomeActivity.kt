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
import android.widget.CompoundButton
import com.eps.wakey.services.CamService
import kotlinx.android.synthetic.main.activity_home.*


class HomeActivity : AppCompatActivity() {

    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
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

        val sharedPref = applicationContext.getSharedPreferences("home", MODE_PRIVATE)
        switchPreview.isChecked = sharedPref.getBoolean("WITH_PREVIEW", false)

        switchPreview.setOnCheckedChangeListener { compoundButton: CompoundButton, b: Boolean ->
            val editor = sharedPref.edit()
            editor.putBoolean("WITH_PREVIEW", switchPreview.isChecked)
            editor.apply()
        }

        butStart.setOnClickListener {

            if (switchPreview.isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {

                    // Don't have permission to draw over other apps yet - ask user to give permission
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivityForResult(settingsIntent, CODE_PERM_SYSTEM_ALERT_WINDOW)
                    return@setOnClickListener
                }
                            }

            if (!isServiceRunning(this, CamService::class.java)) {
               if (switchPreview.isChecked)  notifyService(CamService.ACTION_START_WITH_PREVIEW)
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
        Log.d("SERVICE_NOTIFIED", "Should start service")
        startService(intent)
    }

    private fun flipButtonVisibility(running: Boolean) {

        butStart?.visibility =  if (running) View.GONE else View.VISIBLE
        butStop?.visibility =  if (running) View.VISIBLE else View.GONE
    }


    companion object {

        val CODE_PERM_SYSTEM_ALERT_WINDOW = 6111
        val CODE_PERM_CAMERA = 6112
    }


}