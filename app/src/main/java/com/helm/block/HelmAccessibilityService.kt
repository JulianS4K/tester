package com.helm.block

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app comes to the foreground.
 *
 * Phase 1 (monitor-first): records the current foreground package only, which the
 * app can surface as "what you're using right now". It deliberately does NOT read
 * screen contents (canRetrieveWindowContent=false).
 *
 * Phase 2 will use this signal to enforce limits/focus windows by routing the user
 * to a block screen when [com.helm.data.EnforcementMode.ENFORCE] is active.
 */
class HelmAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            ForegroundAppMonitor.update(pkg)
            // Phase 2: if ENFORCE mode and pkg is over-limit or in a focus window,
            // launch a BlockActivity over it here.
        }
    }

    override fun onInterrupt() { /* no-op */ }
}

/** Lightweight in-memory holder for the current foreground app. */
object ForegroundAppMonitor {
    @Volatile var currentPackage: String? = null
        private set
    @Volatile var lastChangeAt: Long = 0L
        private set

    fun update(pkg: String) {
        currentPackage = pkg
        lastChangeAt = System.currentTimeMillis()
    }
}
