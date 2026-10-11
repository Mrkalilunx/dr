package com.mrkalilunx.dr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class mainactivity : Activity() {

    private val overlayRequest = 1001
    private val notifRequest = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER

        val tv = TextView(this)
        tv.text = "Hello, dr"
        tv.textSize = 24f
        root.addView(tv)

        val startBtn = Button(this)
        startBtn.text = "显示悬浮窗"
        startBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, overlayRequest)
            } else {
                startFloatService()
            }
        }
        root.addView(startBtn)

        val stopBtn = Button(this)
        stopBtn.text = "停止悬浮窗"
        stopBtn.setOnClickListener {
            stopService(Intent(this, floatservice::class.java))
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
        }
        root.addView(stopBtn)

        setContentView(root)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == overlayRequest) {
            if (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) {
                startFloatService()
            } else {
                Toast.makeText(this, "未授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFloatService() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                notifRequest
            )
        }

        val intent = Intent(this, floatservice::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
    }
}