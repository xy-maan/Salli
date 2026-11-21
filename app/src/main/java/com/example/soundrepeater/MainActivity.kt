package com.example.soundrepeater

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val navy = 0xFF0B1F4B.toInt()
    private val card = 0xFF132A5E.toInt()
    private val gold = 0xFFE6B84A.toInt()
    private val soft = 0xFFB8C4E0.toInt()
    private val presets = listOf(5, 10, 15)

    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var dot: View
    private lateinit var toggle: Button
    private lateinit var stats: TextView
    private val chipViews = mutableListOf<TextView>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(fill: Int, stroke: Int? = null, radius: Int = 16) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun text(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
    }

    /** The number currently typed, or null if it is not a valid interval. */
    private fun entered(): Int? = input.text.toString().toIntOrNull()
        ?.takeIf { it in Scheduler.MIN_MINUTES..Scheduler.MAX_MINUTES }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Test hook for automated tests (only works in debug builds):
        //   adb shell am start -n <pkg>/.MainActivity --ei debug_start_minutes 1
        //   adb shell am start -n <pkg>/.MainActivity --ez debug_stop true
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val debugMinutes = intent?.getIntExtra("debug_start_minutes", 0) ?: 0
        if (debuggable && debugMinutes > 0) Scheduler.start(this, debugMinutes)
        if (debuggable && intent?.getBooleanExtra("debug_stop", false) == true) Scheduler.stop(this)

        SalliLog.log(
            this, "APP",
            "opened v${packageManager.getPackageInfo(packageName, 0).versionName} " +
                "android=${android.os.Build.VERSION.RELEASE}(api ${android.os.Build.VERSION.SDK_INT}) " +
                "device=${android.os.Build.MODEL} enabled=${Scheduler.isEnabled(this)} " +
                "interval=${Scheduler.minutes(this)}min ${SalliLog.state(this)}"
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(56), dp(24), dp(32))
        }
        root.addView(text("صَلِّ عَلَى مُحَمَّد", 30f, gold))
        root.addView(text("A gentle reminder, every few minutes", 14f, soft).apply {
            setPadding(0, dp(8), 0, dp(32))
        })

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = shape(card, 0x55E6B84A, 24)
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        box.addView(text("Repeat every", 14f, soft))

        input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(4))
            setText(Scheduler.minutes(this@MainActivity).toString())
            textSize = 56f; setTextColor(gold); gravity = Gravity.CENTER
            background = null
        }
        box.addView(input)
        box.addView(text("minutes (type any number)", 14f, soft).apply { setPadding(0, 0, 0, dp(16)) })

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (m in presets) {
            val chip = text("$m min", 15f, gold).apply {
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener {
                    // Apply immediately if already running, then show it in the box.
                    if (Scheduler.isEnabled(this@MainActivity)) Scheduler.start(this@MainActivity, m)
                    input.setText(m.toString())
                    input.setSelection(input.text.length)
                }
            }
            chipViews.add(chip)
            chips.addView(chip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        box.addView(chips, LinearLayout.LayoutParams(-1, -2))
        root.addView(box, LinearLayout.LayoutParams(-1, -2))

        toggle = Button(this).apply {
            setAllCaps(false); textSize = 18f; stateListAnimator = null
            setOnClickListener { onToggle() }
        }
        root.addView(toggle, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(28) })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        dot = View(this)
        row.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) })
        status = text("", 15f, soft)
        row.addView(status)
        root.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) })

        stats = text("", 12f, soft).apply { setPadding(0, dp(8), 0, 0) }
        root.addView(stats)

        // Debug tools: the log records everything the app does (see README).
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun link(label: String, action: () -> Unit) = text(label, 14f, gold).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { action() }
        }
        tools.addView(link("Copy log") { copyLog() })
        tools.addView(link("Share log") { shareLog() })
        tools.addView(link("Clear log") { clearLog() })
        root.addView(tools, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(24) })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { refresh() }
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun copyLog() {
        val log = SalliLog.read(this)
        if (log.isEmpty()) {
            Toast.makeText(this, "Log is empty", Toast.LENGTH_SHORT).show()
            return
        }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Salli log", log))
        Toast.makeText(this, "Log copied (${log.lines().size} lines)", Toast.LENGTH_SHORT).show()
    }

    private fun shareLog() {
        val log = SalliLog.read(this)
        if (log.isEmpty()) {
            Toast.makeText(this, "Log is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Salli log")
            .putExtra(Intent.EXTRA_TEXT, log)
        startActivity(Intent.createChooser(send, "Share log"))
    }

    private fun clearLog() {
        SalliLog.clear(this)
        Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
    }

    private fun onToggle() {
        val on = Scheduler.isEnabled(this)
        val value = entered()
        if (on && (value == null || value == Scheduler.minutes(this))) {
            Scheduler.stop(this)
        } else {
            if (value == null) {
                Toast.makeText(this, "Enter a number from 1 to 1440", Toast.LENGTH_SHORT).show()
                return
            }
            Scheduler.start(this, value) // start, or update the running interval
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken, 0)
        }
        refresh()
    }

    private fun refresh() {
        val on = Scheduler.isEnabled(this)
        val value = entered()
        val running = Scheduler.minutes(this)

        for ((i, chip) in chipViews.withIndex()) {
            val selected = value == presets[i]
            chip.background = if (selected) shape(gold, null, 20) else shape(Color.TRANSPARENT, gold, 20)
            chip.setTextColor(if (selected) navy else gold)
        }

        if (on && value != null && value != running) {
            toggle.text = "Update to $value min"
            toggle.setTextColor(navy)
            toggle.background = shape(gold, null, 28)
        } else if (on) {
            toggle.text = "Stop"
            toggle.setTextColor(gold)
            toggle.background = shape(Color.TRANSPARENT, gold, 28)
        } else {
            toggle.text = "Start"
            toggle.setTextColor(navy)
            toggle.background = shape(gold, null, 28)
        }

        if (on) {
            val next = Scheduler.nextAt(this)
            status.text = "Running · every $running min" +
                if (next > 0) "\nNext chime ≈ ${SalliLog.clock(next)}" else ""
            dot.background = shape(gold, null, 5)
        } else {
            status.text = "Stopped"
            dot.background = shape(0xFF5C6B8F.toInt(), null, 5)
        }

        val n = Scheduler.chimeCount(this)
        stats.text = if (n > 0)
            "Chimes played: $n · avg awake per chime: " +
                String.format(Locale.US, "%.1f", Scheduler.avgAwakeMs(this) / 1000.0) + " s"
        else ""
    }
}
