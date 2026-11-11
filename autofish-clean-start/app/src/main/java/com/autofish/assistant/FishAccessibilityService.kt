package com.autofish.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class FishAccessibilityService : AccessibilityService() {
    companion object { var current: FishAccessibilityService? = null }
    @Volatile private var manualCastConfirmed = false

    override fun onServiceConnected() { super.onServiceConnected(); current = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Placeholder for game UI event inspection. Roblox may expose no accessible nodes.
    }
    override fun onInterrupt() {}
    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }
    fun onManualCastConfirmed() {
        manualCastConfirmed = true
        // The manual trigger is registered. Fishing-bar vision/automation is intentionally
        // not faked here; it requires screen capture and a calibrated detector.
    }
}