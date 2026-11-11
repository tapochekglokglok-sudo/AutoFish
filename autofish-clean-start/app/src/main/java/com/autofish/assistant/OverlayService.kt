package com.autofish.assistant

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private var status: TextView? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val pad = (10 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xEE20242B.toInt())
        }
        status = TextView(this).apply {
            text = "AutoFish: ожидание"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Я забросил"
            isAllCaps = false
            setOnClickListener {
                status?.text = "AutoFish: заброс отмечен · ожидание поклёвки"
                FishAccessibilityService.current?.onManualCastConfirmed()
            }
        })
        root.addView(Button(this).apply {
            text = "Сбросить"
            isAllCaps = false
            setOnClickListener { status?.text = "AutoFish: ожидание" }
        })
        root.addView(Button(this).apply {
            text = "Закрыть панель"
            isAllCaps = false
            setOnClickListener { stopSelf() }
        })
        panel = root
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            (190 * density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = pad; y = (120 * density).toInt() }
        wm.addView(root, params)
    }

    override fun onDestroy() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}