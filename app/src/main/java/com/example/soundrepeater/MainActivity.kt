package com.example.soundrepeater

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MainActivity : Activity() {
    private val navy = 0xFF0B1F4B.toInt()
    private val card = 0xFF132A5E.toInt()
    private val gold = 0xFFE6B84A.toInt()
    private val soft = 0xFFB8C4E0.toInt()

    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var dot: View
    private lateinit var toggle: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(fill: Int, stroke: Int? = null, radius: Int = 16) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun text(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(56), dp(24), dp(32))
        }
        root.addView(text("صَلِّ عَلَى مُحَمَّد", 30f, gold))
        root.addView(text("A gentle reminder, every few minutes", 14f, soft).apply {
            setPadding(0, dp(8), 0, dp(32))
        })

        // Card
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
        box.addView(text("minutes", 14f, soft).apply { setPadding(0, 0, 0, dp(16)) })

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (m in listOf(5, 10, 15, 30, 60)) {
            chips.addView(text("$m", 15f, gold).apply {
                background = shape(Color.TRANSPARENT, gold, 20)
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener {
                    input.setText(m.toString())
                    if (Scheduler.isEnabled(this@MainActivity)) Scheduler.start(this@MainActivity, m)
                    refresh()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        box.addView(chips, LinearLayout.LayoutParams(-1, -2))
        root.addView(box, LinearLayout.LayoutParams(-1, -2))

        // Start / Stop
        toggle = Button(this).apply {
            setAllCaps(false); textSize = 18f; stateListAnimator = null
            setOnClickListener { onToggle() }
        }
        root.addView(toggle, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(28) })

        // Status
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        dot = View(this)
        row.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) })
        status = text("", 15f, soft)
        row.addView(status)
        root.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
        refresh()
    }

    private fun onToggle() {
        if (Scheduler.isEnabled(this)) {
            Scheduler.stop(this)
        } else {
            val m = input.text.toString().toIntOrNull()
            if (m == null || m < 1) {
                Toast.makeText(this, "Enter 1 or more minutes", Toast.LENGTH_SHORT).show()
                return
            }
            Scheduler.start(this, m)
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken, 0)
        }
        refresh()
    }

    private fun refresh() {
        val on = Scheduler.isEnabled(this)
        if (on) {
            toggle.text = "Stop"
            toggle.setTextColor(gold)
            toggle.background = shape(Color.TRANSPARENT, gold, 28)
            status.text = "Running · every ${Scheduler.minutes(this)} min"
            dot.background = shape(gold, null, 5)
        } else {
            toggle.text = "Start"
            toggle.setTextColor(navy)
            toggle.background = shape(gold, null, 28)
            status.text = "Stopped"
            dot.background = shape(0xFF5C6B8F.toInt(), null, 5)
        }
    }
}
