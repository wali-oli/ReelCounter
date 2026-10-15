package com.oli.reelcounter

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var countView: TextView
    private lateinit var timeView: TextView
    private lateinit var statusView: TextView
    private lateinit var historyView: TextView

    private val refresh = object : Runnable {
        override fun run() {
            countView.text = Store.count(this@MainActivity).toString()
            timeView.text = "Time watched: " + Store.fmt(Store.seconds(this@MainActivity))
            val on = serviceEnabled()
            statusView.text = if (on) "Tracking is ON" else "Tracking is OFF — enable it below"
            statusView.setTextColor(if (on) Color.rgb(30, 130, 60) else Color.rgb(190, 40, 40))
            historyView.text = (1..6).joinToString("\n") { d ->
                "${Store.label(d)}   ${Store.count(this@MainActivity, d)} reels · " +
                    Store.fmt(Store.seconds(this@MainActivity, d))
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/")
    }

    private fun tv(size: Float, color: Int = Color.BLACK) = TextView(this).apply {
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(tv(16f, Color.GRAY).apply { text = "Reels watched today" })
        countView = tv(72f); root.addView(countView)
        timeView = tv(18f); root.addView(timeView)
        statusView = tv(14f).apply { setPadding(0, pad, 0, pad / 2) }; root.addView(statusView)

        root.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        root.addView(tv(14f, Color.GRAY).apply {
            text = "\nPrevious days"
            setPadding(0, pad, 0, 8)
        })
        historyView = tv(13f, Color.DKGRAY); root.addView(historyView)

        setContentView(root)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }
}
