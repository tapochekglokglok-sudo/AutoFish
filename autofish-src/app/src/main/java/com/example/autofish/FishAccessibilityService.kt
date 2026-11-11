package com.example.autofish

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

class FishAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private enum class Phase { WAIT_CAST, HOLDING, WAIT_PERFECT, TAPPING }
    private var phase = Phase.WAIT_CAST
    private var holdStarted = false
    private var lastTap = 0L
    private var lastPromptSeen = 0L
    private var lastCast = 0L
    private var targetY = -1
    private val rapidTap = object : Runnable {
        override fun run() {
            if (phase != Phase.TAPPING) return
            val prefs = getSharedPreferences("fish", MODE_PRIVATE)
            tap(prefs.getInt("tapX", 50), prefs.getInt("tapY", 80), 22L)
            handler.postDelayed(this, 45L)
        }
    }
    private var screenW = 0
    private var screenH = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Some game UI labels are exposed through the accessibility tree.
        if (phase == Phase.WAIT_CAST && event?.text?.any { isCastPrompt(it.toString()) } == true) {
            startHold()
        }
    }

    private fun isCastPrompt(value: String): Boolean {
        val normalized = value.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^а-я\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized.contains("удерживайте") && normalized.contains("забросить")
    }

    private fun hasCastPrompt(): Boolean {
        fun scan(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (isCastPrompt(node.text?.toString().orEmpty()) ||
                isCastPrompt(node.contentDescription?.toString().orEmpty())) return true
            for (i in 0 until node.childCount) {
                if (scan(node.getChild(i))) return true
            }
            return false
        }
        return scan(rootInActiveWindow)
    }
    override fun onInterrupt() { stopLoop() }
    override fun onServiceConnected() { handler.post(loop) }
    override fun onDestroy() { stopLoop(); super.onDestroy() }

    private fun stopLoop() {
        handler.removeCallbacks(loop)
        busy.set(false)
    }

    private val loop = object : Runnable {
        override fun run() {
            if (Build.VERSION.SDK_INT >= 30 && busy.compareAndSet(false, true)) {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        buffer.close()
                        if (wrapped == null) { busy.set(false); return }
                        val bitmap = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                        wrapped.recycle()
                        screenW = bitmap.width
                        screenH = bitmap.height
                        inspect(bitmap)
                    }
                    override fun onFailure(errorCode: Int) { busy.set(false) }
                })
            }
            handler.postDelayed(this, 85L)
        }
    }

    private fun inspect(bitmap: Bitmap) {
        val prefs = getSharedPreferences("fish", MODE_PRIVATE)
        val cue = prefs.getString("prompt", "Нажимайте быстро!") ?: "Нажимайте быстро!"
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                val now = System.currentTimeMillis()

                when (phase) {
                    Phase.WAIT_CAST -> {
                        // Do not cast on a timer: wait for the game's Russian cast prompt.
                        if (hasCastPrompt()) startHold()
                    }
                    Phase.HOLDING -> {
                        val geometry = detectVial(bitmap)
                        if (geometry != null) {
                            targetY = geometry.first
                            val edgeY = geometry.second
                            if (abs(edgeY - targetY) <= max(5, screenH / 100)) {
                                releaseHold()
                                phase = Phase.WAIT_PERFECT
                            }
                        }
                    }
                    Phase.WAIT_PERFECT -> {
                        if (text.contains("Perfect", ignoreCase = true)) {
                            phase = Phase.TAPPING
                            lastPromptSeen = now
                            handler.removeCallbacks(rapidTap)
                            handler.post(rapidTap)
                        } else if (now - lastCast > 18000L) {
                            // Safety timeout: do not leave a long press running indefinitely.
                            releaseHold()
                            phase = Phase.WAIT_CAST
                            handler.removeCallbacks(rapidTap)
                            lastCast = now
                        }
                    }
                    Phase.TAPPING -> {
                        if (text.contains(cue, ignoreCase = true)) {
                            lastPromptSeen = now
                        } else if (now - lastPromptSeen > 700L) {
                            phase = Phase.WAIT_CAST
                            lastCast = now
                        }
                    }
                }
            }
            .addOnCompleteListener {
                bitmap.recycle()
                busy.set(false)
            }
    }

    // Returns the row of the stationary yellow target and the strongest vertical fill edge.
    private fun detectVial(bitmap: Bitmap): Pair<Int, Int>? {
        val w = bitmap.width
        val h = bitmap.height
        val xCenter = (w * 0.55f).toInt().coerceIn(2, w - 3)
        val halfWidth = max(2, w / 180)
        val yStart = (h * 0.12f).toInt()
        val yEnd = (h * 0.88f).toInt()

        var bestYellowY = -1
        var bestYellowScore = 0
        for (y in yStart until yEnd) {
            var score = 0
            for (x in (xCenter - halfWidth)..(xCenter + halfWidth)) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255
                if (r > 150 && g > 125 && b < 150 && r > b * 1.20f) score++
            }
            if (score > bestYellowScore) {
                bestYellowScore = score
                bestYellowY = y
            }
        }
        if (bestYellowY < 0 || bestYellowScore < max(2, halfWidth)) return null

        var edgeY = -1
        var bestEdge = 0
        for (y in (yStart + 1) until yEnd) {
            // Ignore the yellow target itself when looking for the liquid boundary.
            if (abs(y - bestYellowY) < h / 35) continue
            var delta = 0
            for (x in (xCenter - halfWidth)..(xCenter + halfWidth)) {
                val a = bitmap.getPixel(x, y - 1)
                val b = bitmap.getPixel(x, y)
                delta += abs(((a shr 16) and 255) - ((b shr 16) and 255))
                delta += abs(((a shr 8) and 255) - ((b shr 8) and 255))
                delta += abs((a and 255) - (b and 255))
            }
            if (delta > bestEdge) {
                bestEdge = delta
                edgeY = y
            }
        }
        if (edgeY < 0 || bestEdge < halfWidth * 45) return null
        return Pair(bestYellowY, edgeY)
    }

    private fun startHold() {
        if (holdStarted) return
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.50f
        val y = dm.heightPixels * 0.50f
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.1f, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60000L))
            .build()
        holdStarted = dispatchGesture(gesture, null, null)
        if (holdStarted) {
            phase = Phase.HOLDING
            lastCast = System.currentTimeMillis()
        }
    }

    private fun releaseHold() {
        if (!holdStarted) return
        // A new gesture cancels the long stroke and releases the held pointer.
        val dm = resources.displayMetrics
        val path = Path().apply { moveTo(dm.widthPixels * 0.50f, dm.heightPixels * 0.50f) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 25L))
            .build()
        dispatchGesture(gesture, null, null)
        holdStarted = false
    }

    private fun tap(px: Int, py: Int, duration: Long) {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * px.coerceIn(0, 100) / 100f
        val y = dm.heightPixels * py.coerceIn(0, 100) / 100f
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }
}
