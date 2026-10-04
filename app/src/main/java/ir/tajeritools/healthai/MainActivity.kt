package ir.tajeritools.healthai

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 48, 32, 48)
        }

        val title = TextView(this).apply {
            text = "TajeriTools Health AI"
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "نسخه اولیه پژوهش و تحلیل سلامت"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }
        root.addView(subtitle)

        val sections = listOf(
            "آزمایش‌ها",
            "پرسشنامه طب سنتی",
            "عنبیه‌شناسی",
            "تحلیل زبان",
            "تحلیل کف دست"
        )

        sections.forEach { name ->
            val button = Button(this).apply {
                text = name
                isAllCaps = false
                setOnClickListener {
                    subtitle.text = "بخش «$name» آماده توسعه و اتصال به دوربین، فایل و AI است."
                }
            }
            root.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 10, 0, 10) }
            )
        }

        val notice = TextView(this).apply {
            text = "نتایج بخش‌های سنتی صرفاً به‌صورت احتمال و تفسیر غیرتشخیصی نمایش داده می‌شوند."
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }
        root.addView(notice)

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
