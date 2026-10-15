package com.oli.reelcounter

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class ReelService : AccessibilityService() {

    companion object {
        // Package -> view-id keywords that only exist on the reels/shorts screen.
        // Empty list = count the whole app (TikTok's main screen is the feed).
        private val APPS: Map<String, List<String>> = mapOf(
            "com.instagram.android" to listOf("clips_viewer", "reel_viewer", "clips_swipe"),
            "com.google.android.youtube" to listOf("reel_recycler", "reel_player", "shorts"),
            "com.facebook.katana" to listOf("reel", "fb_reels"),
            "com.facebook.lite" to listOf("reel"),
            "com.zhiliaoapp.musically" to emptyList(),
            "com.ss.android.ugc.trill" to emptyList()
        )
        private const val SCROLL_DEBOUNCE_MS = 700L
        private const val CHECK_INTERVAL_MS = 800L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var overlay: TextView? = null
    private var lp: WindowManager.LayoutParams? = null

    private var inReel = false
    private var lastCountAt = 0L
    private var lastCheckAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val active = inReel && pm.isInteractive
            if (active) Store.addSeconds(this@ReelService, 1)
            if (active) showOverlay() else hideOverlay()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        handler.post(ticker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val keywords = APPS[pkg]
        if (keywords == null) {
            // Ignore system UI and keyboards; any other app window means the user left.
            val ignore = pkg == "com.android.systemui" ||
                pkg.contains("inputmethod") || pkg.contains("keyboard")
            if (!ignore && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                inReel = false
            }
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckAt > CHECK_INTERVAL_MS ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            lastCheckAt = now
            inReel = isReelScreen(keywords)
        }

        if (inReel &&
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            now - lastCountAt > SCROLL_DEBOUNCE_MS
        ) {
            lastCountAt = now
            Store.addCount(this)
            updateText()
        }
    }

    private fun isReelScreen(keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return true
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 500) {
            val node = queue.removeFirst()
            visited++
            val id = node.viewIdResourceName
            if (id != null && keywords.any { id.contains(it, ignoreCase = true) }) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun updateText() {
        overlay?.text = "${Store.count(this)} · ${Store.fmtShort(Store.seconds(this))}"
    }

    private fun showOverlay() {
        if (overlay != null) {
            updateText()
            return
        }
        val tv = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(150, 0, 0, 0))
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(120)
        }

        // Drag to reposition
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    wm?.updateViewLayout(tv, params)
                }
            }
            true
        }

        overlay = tv
        lp = params
        updateText()
        wm?.addView(tv, params)
    }

    private fun hideOverlay() {
        overlay?.let { runCatching { wm?.removeView(it) } }
        overlay = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        super.onDestroy()
    }
}
