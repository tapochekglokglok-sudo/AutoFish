package com.autofish.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(22))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        page.addView(TextView(this).apply {
            text = "AutoFish"
            textSize = 28f
        })
        page.addView(TextView(this).apply {
            text = "Ручной запуск рыбалки\n\n1. Разреши отображение поверх других приложений.\n2. Включи службу специальных возможностей AutoFish.\n3. Открой Roblox и нажми кнопку заброса вручную.\n4. Нажми «Я забросил» на плавающей панели — помощник перейдёт в режим ожидания шкалы.\n\nВажно: игра Roblox может не передавать содержимое шкалы службе доступности. В этой версии автоматическое распознавание шкалы ещё не реализовано."
            textSize = 16f
            setPadding(0, dp(18), 0, dp(18))
        })
        page.addView(button("Разрешить окно поверх приложений") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        page.addView(button("Открыть настройки доступности") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        page.addView(button("Показать панель AutoFish") {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, OverlayService::class.java))
            } else {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        page.addView(button("Остановить панель") {
            stopService(Intent(this, OverlayService::class.java))
        })
        setContentView(page)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}