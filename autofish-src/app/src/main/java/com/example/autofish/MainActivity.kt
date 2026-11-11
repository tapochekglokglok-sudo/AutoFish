package com.example.autofish

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = Color.rgb(10, 15, 29)
    private val panel = Color.rgb(20, 28, 48)
    private val panelAlt = Color.rgb(27, 37, 62)
    private val white = Color.rgb(244, 247, 255)
    private val muted = Color.rgb(157, 170, 196)
    private val purple = Color.rgb(139, 92, 246)
    private val blue = Color.rgb(59, 130, 246)
    private val green = Color.rgb(34, 197, 94)
    private val orange = Color.rgb(251, 191, 36)

    private lateinit var overlayValue: TextView
    private lateinit var accessibilityValue: TextView
    private lateinit var automationValue: TextView
    private lateinit var helpContent: LinearLayout
    private val prefs by lazy { getSharedPreferences("fish", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
            setBackgroundColor(bg)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "AF"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(purple, 18)
        }
        top.addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        heading.addView(label("AUTOFISH", 12f, purple, true))
        heading.addView(label("Помощник рыбалки", 22f, white, true))
        heading.addView(label("Для Roblox · Android", 13f, muted, false))
        top.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(top, params(0, 22))

        val hero = card()
        hero.addView(label("УПРАВЛЕНИЕ", 11f, muted, true))
        hero.addView(label("Готов к запуску?", 21f, white, true), params(0, 5))
        hero.addView(label("После настройки разрешений открой Roblox и нажми плавающую кнопку AF.", 14f, muted, false), params(0, 15))
        val startHint = label("КНОПКА AF ПОЯВИТСЯ ПОВЕРХ ИГРЫ", 11f, Color.rgb(221, 214, 254), true)
        startHint.gravity = Gravity.CENTER
        startHint.background = rounded(Color.rgb(47, 37, 82), 12)
        hero.addView(startHint, LinearLayout.LayoutParams(-1, dp(38)))
        page.addView(hero, params(0, 14))

        val statusCard = card()
        statusCard.addView(label("СОСТОЯНИЕ ПРИЛОЖЕНИЯ", 11f, muted, true), params(0, 10))
        overlayValue = statusLine(statusCard, "Отображение поверх приложений")
        accessibilityValue = statusLine(statusCard, "Специальные возможности")
        automationValue = statusLine(statusCard, "Автоматизация")
        page.addView(statusCard, params(0, 18))

        page.addView(label("НАСТРОЙКА", 12f, muted, true), params(2, 9))
        page.addView(actionButton("1   Разрешить отображение поверх приложений", purple) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else updateStatus()
        }, params(0, 9))
        page.addView(actionButton("2   Включить специальные возможности", blue) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, params(0, 9))
        page.addView(actionButton("Обновить состояние", panelAlt) { refreshStatus() }, params(0, 18))
        page.addView(actionButton("Остановить и убрать AF", Color.rgb(127, 29, 29)) {
            sendBroadcast(Intent(FishAccessibilityService.ACTION_HIDE_OVERLAY).setPackage(packageName))
            updateStatus()
        }, params(0, 18))

        page.addView(label("КАК ПОЛЬЗОВАТЬСЯ", 12f, muted, true), params(2, 9))
        val guide = card()
        addStep(guide, "01", "Выдай разрешения", "Разреши показ поверх других приложений и включи службу AutoFish в специальных возможностях.")
        addStep(guide, "02", "Открой Roblox", "Запусти игру и подойди к месту рыбалки. На экране появится круглая кнопка AF.")
        addStep(guide, "03", "Запусти помощник", "Нажми AF. Повторное нажатие остановит работу. Кнопку можно перетаскивать в удобное место.")
        addStep(guide, "04", "Следи за статусом", "AF меняет вид при запуске. Не закрывай службу специальных возможностей во время игры.")
        page.addView(guide, params(0, 18))

        page.addView(label("КАК РАБОТАЕТ АВТОРЫБАЛКА", 12f, muted, true), params(2, 9))
        val logic = card()
        logic.addView(label("Сначала AutoFish ждёт фразу «Удерживайте чтобы забросить», затем удерживает небольшую область в центре экрана. После этого отслеживает жёлтый сектор и движущийся индикатор, ждёт PERFECT и выполняет быстрые нажатия второго этапа.", 14f, white, false))
        logic.addView(label("Распознавание зависит от изображения игры, масштаба интерфейса и качества снимка экрана. Если шкала выглядит иначе, автоматизация может не сработать.", 12f, orange, false), params(0, 0))
        page.addView(logic, params(0, 18))

        page.addView(label("ПОМОЩЬ И ПОДДЕРЖКА", 12f, muted, true), params(2, 9))
        val support = card()
        val helpToggle = actionButton("Открыть подсказки и решение проблем", panelAlt) {
            helpContent.visibility = if (helpContent.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        support.addView(helpToggle)
        helpContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(12), 0, 0)
        }
        addHelp(helpContent, "Не появилась кнопка AF", "Проверь разрешение «Поверх других приложений». Затем вернись в AutoFish и нажми «Обновить состояние».")
        addHelp(helpContent, "AutoFish не запускается", "Открой специальные возможности Android и убедись, что служба AutoFish включена. После этого перезапусти помощник.")
        addHelp(helpContent, "Кнопка есть, но игра не реагирует", "Проверь, что автоматизация включена нажатием AF. Некоторые экраны и режимы Roblox могут ограничивать снимки экрана или жесты.")
        addHelp(helpContent, "Как остановить работу", "Нажми плавающую кнопку AF ещё раз. Если нужно, отключи службу AutoFish в настройках специальных возможностей.")
        support.addView(helpContent)
        page.addView(support, params(0, 18))

        page.addView(label("AutoFish · Версия для тестирования", 11f, muted, false).apply {
            gravity = Gravity.CENTER
        }, params(0, 12))

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(page)
        })
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        sendBroadcast(Intent(FishAccessibilityService.ACTION_HIDE_OVERLAY).setPackage(packageName))
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (::overlayValue.isInitialized) refreshStatus()
    }

    private fun refreshStatus() {
        updateStatus()
        sendBroadcast(Intent(FishAccessibilityService.ACTION_REFRESH_OVERLAY).setPackage(packageName))
    }

    private fun updateStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val component = ComponentName(this, FishAccessibilityService::class.java).flattenToString()
        val accessibility = enabledServices.split(':').any {
            it.equals(component, true) || (it.contains(packageName, true) && it.contains(FishAccessibilityService::class.java.simpleName, true))
        }
        val running = prefs.getBoolean("automationEnabled", false)
        setStatus(overlayValue, overlay, "Разрешено", "Не разрешено")
        setStatus(accessibilityValue, accessibility, "Включены", "Выключены")
        setStatus(automationValue, running, "Включена", "Остановлена")
    }

    private fun setStatus(view: TextView, ok: Boolean, yes: String, no: String) {
        view.text = (if (ok) "●  " else "○  ") + view.tag.toString() + ": " + if (ok) yes else no
        view.setTextColor(if (ok) green else orange)
    }

    private fun statusLine(parent: LinearLayout, title: String): TextView {
        val v = label("", 14f, muted, true)
        v.tag = title
        parent.addView(v, params(0, 9))
        return v
    }

    private fun addStep(parent: LinearLayout, number: String, title: String, detail: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(5), 0, dp(13))
        }
        val badge = TextView(this).apply {
            text = number
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(57, 48, 97), 10)
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(38), dp(34)))
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(label(title, 15f, white, true))
            addView(label(detail, 13f, muted, false), params(3, 0))
        }
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
    }

    private fun addHelp(parent: LinearLayout, title: String, detail: String) {
        parent.addView(label(title, 14f, white, true), params(0, 4))
        parent.addView(label(detail, 13f, muted, false), params(0, 13))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(16), dp(17), dp(16))
        background = rounded(panel, 20)
    }

    private fun actionButton(text: String, color: Int, action: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(white)
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(color, 15)
            minHeight = dp(54)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun params(top: Int, bottom: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}