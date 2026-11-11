package com.example.autofish

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("fish", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            setBackgroundColor(Color.rgb(244,246,250))
        }
        layout.addView(TextView(this).apply {
            text="AutoFish"; textSize=28f; setTextColor(Color.rgb(23,32,51))
        })
        layout.addView(TextView(this).apply {
            text="Автоматическая рыбалка в Roblox"; textSize=15f
            setTextColor(Color.rgb(101,112,132))
        }, params(4,18))
        status=TextView(this).apply { textSize=15f; setTextColor(Color.rgb(38,50,71)) }
        layout.addView(status,params(0,16))
        layout.addView(action("Разрешить отображение поверх приложений") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else refreshOverlay()
        },params(0,8))
        layout.addView(action("Открыть специальные возможности") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },params(0,16))
        layout.addView(TextView(this).apply {
            text="Как пользоваться"
            textSize=18f; setTextColor(Color.rgb(23,32,51))
        },params(0,8))
        layout.addView(TextView(this).apply {
            text="1. Разрешите отображение поверх других приложений.\n\n"+
                "2. В специальных возможностях включите AutoFish.\n\n"+
                "3. Откройте Roblox. На экране появится плавающая кнопка AF.\n\n"+
                "4. Нажмите AF для запуска. Повторное нажатие остановит автоматизацию. Кнопку можно передвигать.\n\n"+
                "5. AutoFish ждёт надпись «Удерживайте чтобы забросить», удерживает небольшую область чуть правее центра, отслеживает жёлтый сектор, затем ждёт PERFECT и выполняет быстрые нажатия до завершения второго этапа."
            textSize=14f; setTextColor(Color.rgb(65,75,93)); setLineSpacing(dp(3).toFloat(),1f)
        })
        layout.addView(TextView(this).apply {
            text="Автоматизация запускается только после нажатия AF. Если игра ограничивает снимки экрана или оформление шкалы отличается, распознавание может потребовать настройки."
            textSize=12f; setTextColor(Color.rgb(120,70,30))
        },params(18,0))
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onResume() { super.onResume(); updateStatus(); refreshOverlay() }
    private fun updateStatus() {
        if (!::status.isInitialized) return
        val overlay=Settings.canDrawOverlays(this)
        val accessibility=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val enabled=accessibility.split(':').any { it.contains(packageName,true) && it.contains(FishAccessibilityService::class.java.simpleName,true) }
        status.text="Отображение поверх приложений: ${if(overlay) "разрешено" else "не разрешено"}\n"+
            "Специальные возможности: ${if(enabled) "включены" else "выключены"}\n"+
            "Автоматизация: ${if(prefs.getBoolean("automationEnabled",false)) "включена" else "выключена"}"
    }
    private fun refreshOverlay() { sendBroadcast(Intent(FishAccessibilityService.ACTION_REFRESH_OVERLAY).setPackage(packageName)); updateStatus() }
    private fun action(label:String,run:()->Unit)=Button(this).apply { text=label; isAllCaps=false; setOnClickListener { run() } }
    private fun params(top:Int,bottom:Int)=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin=dp(top); bottomMargin=dp(bottom) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}