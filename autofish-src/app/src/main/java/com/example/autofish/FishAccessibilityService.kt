package com.example.autofish

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.GestureDescription
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FishAccessibilityService : AccessibilityService() {
    companion object { const val ACTION_REFRESH_OVERLAY = "com.example.autofish.REFRESH_OVERLAY"; const val ACTION_HIDE_OVERLAY = "com.example.autofish.HIDE_OVERLAY"; const val ACTION_CAPTURE = "com.example.autofish.CAPTURE"; const val ACTION_RECORD_START = "com.example.autofish.RECORD_START"; const val ACTION_RECORD_STOP = "com.example.autofish.RECORD_STOP"; const val ACTION_CAPTURED = "com.example.autofish.CAPTURED" }
    private enum class Stage { WAIT_CAST, HOLDING, WAIT_PERFECT, WAIT_FAST, FAST_TAP }

    private val handler = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private var stage = Stage.WAIT_CAST
    private var lastTap = 0L
    private var lastMarkerY = -1
    private var lastMarkerAt = 0L
    private var stableTargetTop = -1
    private var stableTargetBottom = -1
    private var targetStableFrames = 0
    private var fastMissFrames = 0
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var receiverRegistered = false
    private var tess: TessBaseAPI? = null
    private var recording = false
    private var recordingIndex = 0
    private val captureDir by lazy { File(filesDir, "autofish_capture").apply { mkdirs() } }
    private val prefs by lazy { getSharedPreferences("fish", MODE_PRIVATE) }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CAPTURE) { captureFrame(false); return }
            if (intent?.action == ACTION_RECORD_START) { startRecording(); return }
            if (intent?.action == ACTION_RECORD_STOP) { stopRecording(); return }
            if (intent?.action == ACTION_HIDE_OVERLAY) {
                stopAutomation()
                removeBubble()
            } else {
                refreshOverlay()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(ACTION_REFRESH_OVERLAY).apply { addAction(ACTION_HIDE_OVERLAY); addAction(ACTION_CAPTURE); addAction(ACTION_RECORD_START); addAction(ACTION_RECORD_STOP) }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(refreshReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg.contains("roblox", true) && prefs.getBoolean("autoStartRoblox", false) && !prefs.getBoolean("automationEnabled", false)) {
            logEvent("Обнаружен Roblox — автоматический запуск")
            startAutomation()
        }
    }
    override fun onInterrupt() = stopAutomation()
    override fun onServiceConnected() { super.onServiceConnected(); initOcr(); refreshOverlay() }

    private fun initOcr() {
        if (tess != null) return
        try {
            val root = File(filesDir, "autofish_ocr")
            val data = File(root, "tessdata")
            data.mkdirs()
            val trained = File(data, "rus.traineddata")
            val english = File(data, "eng.traineddata")
            if (!trained.exists()) assets.open("tessdata/rus.traineddata").use { input -> FileOutputStream(trained).use { input.copyTo(it) } }
            if (!english.exists()) assets.open("tessdata/eng.traineddata").use { input -> FileOutputStream(english).use { input.copyTo(it) } }
            tess = TessBaseAPI().also { if (!it.init(root.absolutePath, "rus+eng")) { it.recycle(); tess = null } }
        } catch (_: Exception) { tess = null }
    }

    override fun onDestroy() {
        stopAutomation(); stopRecording(); removeBubble(); tess?.recycle(); tess = null
        if (receiverRegistered) unregisterReceiver(refreshReceiver)
        super.onDestroy()
    }

    private fun refreshOverlay() {
        if (!Settings.canDrawOverlays(this)) { removeBubble(); stopAutomation(); return }
        if (bubble == null) createBubble()
        updateBubble()
        if (prefs.getBoolean("automationEnabled", false)) startAutomation() else stopAutomation()
    }

    private fun createBubble() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val size = (58 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = (resources.displayMetrics.widthPixels - size - 10).coerceAtLeast(0)
        params.y = (resources.displayMetrics.heightPixels / 2 - size / 2).coerceAtLeast(0)
        val view = TextView(this).apply {
            text = "AF"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = bubbleBackground(false)
            contentDescription = "AutoFish: нажмите для запуска или остановки"
        }
        view.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var originX = 0; var originY = 0; var moved = false; var downAt = 0L
            val threshold = 8 * resources.displayMetrics.density
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { downX=e.rawX; downY=e.rawY; originX=params.x; originY=params.y; downAt=System.currentTimeMillis(); moved=false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=e.rawX-downX; val dy=e.rawY-downY
                        if (abs(dx)>threshold || abs(dy)>threshold) moved=true
                        if (moved) { params.x=(originX+dx.toInt()).coerceAtLeast(0); params.y=(originY+dy.toInt()).coerceAtLeast(0); try { wm.updateViewLayout(view,params) } catch (_:Exception) {} }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) {
                        if (System.currentTimeMillis() - downAt >= 650L) {
                            val active = recording
                            if (active) stopRecording() else startRecording()
                            Toast.makeText(this@FishAccessibilityService, if (active) "Запись остановлена" else "Запись кадров началась", Toast.LENGTH_SHORT).show()
                        } else {
                            if (prefs.getBoolean("automationEnabled",false)) stopAutomation() else startAutomation()
                        }
                        updateBubble()
                    }
                }
                return true
            }
        })
        try { wm.addView(view,params); bubble=view } catch (_:Exception) { bubble=null }
    }

    private fun bubbleBackground(enabled: Boolean) = GradientDrawable().apply {
        shape=GradientDrawable.OVAL
        setColor(if (enabled) Color.rgb(22,163,74) else Color.rgb(55,65,81))
        setStroke((2*resources.displayMetrics.density).toInt(),Color.WHITE)
    }
    private fun updateBubble() {
        bubble?.let {
            val enabled=prefs.getBoolean("automationEnabled",false)
            it.text=if(enabled) "ON" else "AF"; it.background=bubbleBackground(enabled)
            it.contentDescription=if(enabled) "AutoFish включён. Нажмите, чтобы остановить" else "AutoFish выключен. Нажмите, чтобы запустить"
        }
    }
    private fun removeBubble() { bubble?.let { try { windowManager?.removeView(it) } catch (_:Exception) {} }; bubble=null }

    private fun startAutomation() {
        if (!Settings.canDrawOverlays(this)) return
        initOcr()
        prefs.edit().putBoolean("automationEnabled",true).apply()
        logEvent("Автоматизация включена")
        resetTracking(); stage=Stage.WAIT_CAST; updateBubble(); handler.removeCallbacks(loop); handler.post(loop)
    }
    private fun stopAutomation() {
        val wasHolding = stage == Stage.HOLDING
        prefs.edit().putBoolean("automationEnabled",false).apply()
        handler.removeCallbacks(loop)
        if (wasHolding) tapAt(55,55,40)
        logEvent("Автоматизация остановлена")
        resetTracking(); stage=Stage.WAIT_CAST; updateBubble()
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!prefs.getBoolean("automationEnabled",false)) return
            if (Build.VERSION.SDK_INT < 30 || !busy.compareAndSet(false,true)) { schedule(100); return }
            takeScreenshot(Display.DEFAULT_DISPLAY,mainExecutor,object:TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer=result.hardwareBuffer
                    val wrapped=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
                    buffer.close()
                    if (wrapped==null) { busy.set(false); schedule(150); return }
                    val bmp=wrapped.copy(Bitmap.Config.ARGB_8888,false); wrapped.recycle()
                    handler.post {
                        try { processFrame(bmp) } finally { bmp.recycle(); busy.set(false); schedule(if(stage==Stage.FAST_TAP) 70 else 180) }
                    }
                }
                override fun onFailure(errorCode: Int) { busy.set(false); schedule(250) }
            })
        }
    }


    private fun startRecording() {
        if (Build.VERSION.SDK_INT < 30) return
        recording = true
        recordingIndex = captureDir.listFiles()?.count { it.name.startsWith("frame_") } ?: 0
        handler.removeCallbacks(recordLoop)
        handler.post(recordLoop)
    }
    private fun stopRecording() { recording = false; handler.removeCallbacks(recordLoop) }
    private val recordLoop = object : Runnable {
        override fun run() {
            if (!recording) return
            captureFrame(true)
            if (recording) handler.postDelayed(this, 850)
        }
    }
    private fun captureFrame(saveAsRecording: Boolean) {
        if (Build.VERSION.SDK_INT < 30 || !busy.compareAndSet(false, true)) return
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                try {
                    val buffer = result.hardwareBuffer
                    val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    buffer.close()
                    if (wrapped == null) { busy.set(false); return }
                    val bitmap = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                    wrapped.recycle()
                    try {
                        FileOutputStream(File(captureDir, "latest.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        if (saveAsRecording && recording) {
                            FileOutputStream(File(captureDir, "frame_%05d.jpg".format(recordingIndex++))).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                            val frames = captureDir.listFiles()?.filter { it.name.startsWith("frame_") }?.sortedBy { it.name } ?: emptyList()
                            if (frames.size > 120) frames.take(frames.size - 120).forEach { it.delete() }
                        }
                    } finally { bitmap.recycle(); busy.set(false) }
                    sendBroadcast(Intent(ACTION_CAPTURED).setPackage(packageName))
                } catch (_: Exception) { busy.set(false) }
            }
            override fun onFailure(errorCode: Int) {
                busy.set(false)
                prefs.edit().putInt("lastScreenshotError", errorCode).apply()
                sendBroadcast(Intent(ACTION_CAPTURED).setPackage(packageName))
            }
        })
    }

    private fun processFrame(bitmap: Bitmap) {
        if (!prefs.getBoolean("automationEnabled",false)) return
        val text = try { tess?.setImage(bitmap); tess?.getUTF8Text()?.lowercase()?.replace('ё','е') ?: "" } catch (_:Exception) { "" }
        val normalized = text.replace(Regex("[^\\p{L}]"), "")
        val castPrompt = (normalized.contains("удерж") && normalized.contains("заброс")) ||
            (normalized.contains("hold") && normalized.contains("cast"))
        val perfectPrompt = normalized.contains("perfect")
        val fastPrompt = (normalized.contains("нажим") && normalized.contains("быстро")) ||
            (normalized.contains("tap") && normalized.contains("fast"))
        when(stage) {
            Stage.WAIT_CAST -> if (castPrompt) {
                logEvent("Найдена подсказка заброса")
                resetTracking(); stage=Stage.HOLDING
                longHold(55,55)
            }
            Stage.HOLDING -> if (yellowIndicatorOverlap(bitmap)) {
                logEvent("Маркер достиг сектора")
                cancelHoldWithRelease(55,55)
                stage=Stage.WAIT_PERFECT
            }
            Stage.WAIT_PERFECT -> if (perfectPrompt) { logEvent("Распознано PERFECT"); stage=Stage.WAIT_FAST }
            Stage.WAIT_FAST -> if (fastPrompt) { logEvent("Начат этап быстрых нажатий"); stage=Stage.FAST_TAP; lastTap=0L; fastMissFrames=0 }
            Stage.FAST_TAP -> {
                if (fastPrompt) {
                    fastMissFrames=0
                    val now=System.currentTimeMillis()
                    if (now-lastTap>=55) { tapAt(55,55,35); lastTap=now }
                } else {
                    fastMissFrames++
                    if (fastMissFrames>=5) { logEvent("Этап завершён или подсказка исчезла"); stage=Stage.WAIT_CAST; resetTracking() }
                }
            }
        }
    }

    // Yellow target is stationary; the moving marker is tracked separately across frames.
    private fun yellowIndicatorOverlap(b: Bitmap): Boolean {
        val h=b.height
        val xCenter=(b.width*0.55f).toInt().coerceIn(0,b.width-1)
        val halfWidth=max(4,(b.width*0.018f).toInt())
        val x0=max(0,xCenter-halfWidth); val x1=min(b.width-1,xCenter+halfWidth)
        val y0=(h*0.12f).toInt(); val y1=(h*0.90f).toInt()
        val width=x1-x0+1
        val yellowRows=BooleanArray(h)
        for(y in y0 until y1) {
            var count=0
            for(x in x0..x1) if(isYellow(b.getPixel(x,y))) count++
            yellowRows[y]=count>=max(3,(width*0.22f).toInt())
        }
        var bestTop=-1; var bestBottom=-1; var runTop=-1
        for(y in y0 until y1) {
            if(yellowRows[y]) {
                if(runTop<0) runTop=y
            } else if(runTop>=0) {
                val bottom=y-1
                if(bottom-runTop>bestBottom-bestTop) { bestTop=runTop; bestBottom=bottom }
                runTop=-1
            }
        }
        if(runTop>=0 && y1-1-runTop>bestBottom-bestTop) { bestTop=runTop; bestBottom=y1-1 }
        if(bestTop<0 || bestBottom-bestTop !in 2..(h*0.12f).toInt()) {
            targetStableFrames=0; stableTargetTop=-1; stableTargetBottom=-1; lastMarkerY=-1
            return false
        }
        if(stableTargetTop>=0 && abs(bestTop-stableTargetTop)<=3 && abs(bestBottom-stableTargetBottom)<=3) {
            targetStableFrames++
        } else {
            targetStableFrames=1
            stableTargetTop=bestTop; stableTargetBottom=bestBottom
            lastMarkerY=-1
        }
        if(targetStableFrames<2) return false

        val markerRows=BooleanArray(h)
        for(y in y0 until y1) {
            var count=0
            for(x in x0..x1) {
                val c=b.getPixel(x,y)
                if(!isYellow(c) && isMarkerPixel(c)) count++
            }
            markerRows[y]=count>=max(3,(width*0.18f).toInt())
        }
        var candidateY=-1; var candidateStrength=0; var y=y0
        while(y<y1) {
            if(!markerRows[y]) { y++; continue }
            val top=y
            var strength=0
            while(y<y1 && markerRows[y]) {
                for(x in x0..x1) { val c=b.getPixel(x,y); if(!isYellow(c) && isMarkerPixel(c)) strength++ }
                y++
            }
            val center=(top+y-1)/2
            val nearPrevious=lastMarkerY<0 || abs(center-lastMarkerY)<=max(24,(h*0.10f).toInt())
            if(nearPrevious && y-top<=max(12,(h*0.025f).toInt()) && strength>candidateStrength) {
                candidateY=center; candidateStrength=strength
            }
        }
        if(candidateY<0) return false
        val now=System.currentTimeMillis()
        val wasMoving=lastMarkerY>=0 && now-lastMarkerAt<900 && abs(candidateY-lastMarkerY)>=1
        lastMarkerY=candidateY; lastMarkerAt=now
        val padding=max(3,(bestBottom-bestTop)/2)
        return wasMoving && candidateY in (bestTop-padding)..(bestBottom+padding)
    }

    private fun isYellow(c:Int):Boolean {
        val r=Color.red(c); val g=Color.green(c); val b=Color.blue(c)
        return r>185 && g>145 && b<125 && r>g*1.03
    }
    private fun isMarkerPixel(c:Int):Boolean {
        val r=Color.red(c); val g=Color.green(c); val b=Color.blue(c)
        val hi=max(r,max(g,b)); val lo=min(r,min(g,b))
        val saturation=hi-lo
        return (lo>185 && saturation<75) || (saturation>75 && hi>125)
    }
    private fun logEvent(message: String) {
        val old = prefs.getString("eventLog", "") ?: ""
        val line = System.currentTimeMillis().toString() + " | " + message
        val entries = (old.lines().filter { it.isNotBlank() } + line).takeLast(80)
        prefs.edit().putString("eventLog", entries.joinToString("\n")).putString("lastEvent", message).apply()
    }

    private fun resetTracking() {
        lastMarkerY=-1; lastMarkerAt=0L
        stableTargetTop=-1; stableTargetBottom=-1; targetStableFrames=0
        fastMissFrames=0
    }

    private fun longHold(px:Int,py:Int) {
        val p=point(px,py)
        val g=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,60000)).build()
        dispatchGesture(g,null,null)
    }
    private fun cancelHoldWithRelease(px:Int,py:Int) { tapAt(px,py,40) }
    private fun point(px:Int,py:Int)=Path().apply { moveTo(resources.displayMetrics.widthPixels*px/100f,resources.displayMetrics.heightPixels*py/100f) }
    private fun tapAt(px:Int,py:Int,duration:Long) {
        val p=point(px,py)
        val g=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,duration)).build()
        dispatchGesture(g,null,null)
    }
    private fun schedule(delay:Long) { if(prefs.getBoolean("automationEnabled",false)) handler.postDelayed(loop,delay) }
}