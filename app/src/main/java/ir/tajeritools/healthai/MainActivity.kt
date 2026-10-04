package ir.tajeritools.healthai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var section = AnalysisEngine.Section.LABS
    private var lastBitmap: Bitmap? = null
    private var lastText: String = ""
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var preview: ImageView

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                lastBitmap = bitmap
                lastText = ""
                preview.setImageBitmap(bitmap)
                preview.visibility = View.VISIBLE
                status.text = "عکس دریافت شد. حالا «تحلیل» را بزن."
            }
        }

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { handleFile(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 38, 28, 48)
        }

        root.addView(TextView(this).apply {
            text = "TajeriTools Health AI"
            textSize = 25f
            gravity = Gravity.CENTER
        }, full())

        root.addView(TextView(this).apply {
            text = "تحلیل آزمایش + طب سنتی + تحلیل تصویری"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 24)
        }, full())

        val sectionRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val sections = listOf(
            "آزمایش‌ها" to AnalysisEngine.Section.LABS,
            "طب سنتی" to AnalysisEngine.Section.TRADITIONAL,
            "عنبیه" to AnalysisEngine.Section.IRIDOLOGY,
            "زبان" to AnalysisEngine.Section.TONGUE,
            "کف دست" to AnalysisEngine.Section.PALM
        )
        sections.forEach { (label, value) ->
            sectionRow.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { selectSection(value, label) }
            }, fullWithMargins())
        }
        root.addView(sectionRow, full())

        status = TextView(this).apply {
            text = "بخش آزمایش‌ها فعال است. عکس بگیر یا تصویر/PDF انتخاب کن."
            textSize = 15f
            setPadding(0, 22, 0, 18)
        }
        root.addView(status, full())

        preview = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = 650
            visibility = View.GONE
        }
        root.addView(preview, full())

        root.addView(Button(this).apply {
            text = "📷 گرفتن عکس"
            isAllCaps = false
            setOnClickListener {
                if (section == AnalysisEngine.Section.TRADITIONAL) runQuestionnaire()
                else cameraLauncher.launch(null)
            }
        }, fullWithMargins())

        root.addView(Button(this).apply {
            text = "📁 انتخاب تصویر یا PDF"
            isAllCaps = false
            setOnClickListener {
                if (section == AnalysisEngine.Section.TRADITIONAL) runQuestionnaire()
                else fileLauncher.launch(arrayOf("image/*", "application/pdf"))
            }
        }, fullWithMargins())

        root.addView(Button(this).apply {
            text = "🖼 راهنمای عکس درست"
            isAllCaps = false
            setOnClickListener { showPhotoGuide() }
        }, fullWithMargins())

        root.addView(Button(this).apply {
            text = "🤖 تحلیل"
            isAllCaps = false
            setOnClickListener { analyzeCurrent() }
        }, fullWithMargins())

        root.addView(Button(this).apply {
            text = "⚙️ تنظیم AI"
            isAllCaps = false
            setOnClickListener { showAiSettings() }
        }, fullWithMargins())

        root.addView(Button(this).apply {
            text = "📋 کپی نتیجه"
            isAllCaps = false
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Health AI result", result.text))
                toast("نتیجه کپی شد.")
            }
        }, fullWithMargins())

        result = TextView(this).apply {
            text = "نتیجه اینجا نمایش داده می‌شود."
            textSize = 15f
            setTextIsSelectable(true)
            setPadding(8, 28, 8, 28)
        }
        root.addView(result, full())

        root.addView(TextView(this).apply {
            text = "تحلیل‌های آزمایش بر اساس داده و محدوده مرجع گزارش انجام می‌شوند. تفسیرهای عنبیه، زبان و کف دست در بخش سنتی به‌صورت غیرتشخیصی ارائه می‌شوند."
            textSize = 12f
        }, full())

        return ScrollView(this).apply { addView(root) }
    }

    private fun selectSection(value: AnalysisEngine.Section, label: String) {
        section = value
        lastBitmap = null
        lastText = ""
        preview.setImageDrawable(null)
        preview.visibility = View.GONE
        result.text = "نتیجه اینجا نمایش داده می‌شود."
        status.text = when (value) {
            AnalysisEngine.Section.TRADITIONAL -> "بخش $label فعال است. «گرفتن عکس» یا «انتخاب فایل» را بزن تا پرسشنامه باز شود."
            AnalysisEngine.Section.LABS -> "بخش $label فعال است. تصویر یا PDF گزارش آزمایش را وارد کن."
            else -> "بخش $label فعال است. عکس واضح بگیر یا تصویر انتخاب کن."
        }
    }

    private fun handleFile(uri: Uri) {
        val mime = contentResolver.getType(uri).orEmpty()
        if (section != AnalysisEngine.Section.LABS && mime == "application/pdf") {
            toast("برای عنبیه، زبان و کف دست تصویر انتخاب کن؛ PDF مخصوص آزمایش‌هاست.")
            return
        }
        lifecycleScope.launch {
            try {
                status.text = "در حال خواندن فایل..."
                if (section == AnalysisEngine.Section.LABS) {
                    lastText = OcrEngine.extractFromUri(this@MainActivity, uri)
                    lastBitmap = null
                    preview.visibility = View.GONE
                    status.text = "OCR تمام شد. «تحلیل» را بزن."
                    result.text = lastText.take(5000)
                } else {
                    val bmp = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                    lastBitmap = requireNotNull(bmp) { "تصویر قابل خواندن نیست" }
                    lastText = ""
                    preview.setImageBitmap(lastBitmap)
                    preview.visibility = View.VISIBLE
                    status.text = "تصویر آماده تحلیل است."
                }
            } catch (e: Exception) {
                status.text = "خطا در خواندن فایل"
                result.text = e.message ?: "خطای ناشناخته"
            }
        }
    }

    private fun analyzeCurrent() {
        when (section) {
            AnalysisEngine.Section.TRADITIONAL -> runQuestionnaire()
            AnalysisEngine.Section.LABS -> analyzeLabs()
            else -> analyzeImageSection()
        }
    }

    private fun analyzeLabs() {
        lifecycleScope.launch {
            try {
                status.text = "در حال تحلیل آزمایش..."
                if (lastText.isBlank() && lastBitmap != null) {
                    lastText = OcrEngine.extractFromBitmap(lastBitmap!!)
                }
                if (lastText.isBlank()) {
                    toast("اول تصویر یا PDF آزمایش را وارد کن.")
                    return@launch
                }

                val local = AnalysisEngine.localLabSummary(lastText)
                val cfg = AiClient.loadConfig(this@MainActivity)
                val hasAi = cfg.gatewayUrl.isNotBlank() || cfg.geminiKey.isNotBlank() || cfg.mistralKey.isNotBlank()
                if (!hasAi) {
                    result.text = local
                    status.text = "تحلیل محلی تمام شد. برای تحلیل AI، تنظیمات AI را وارد کن."
                } else {
                    val ai = AiClient.analyzeText(
                        this@MainActivity,
                        AnalysisEngine.promptFor(AnalysisEngine.Section.LABS, lastText)
                    )
                    result.text = local + "\n\n=== تحلیل AI ===\n" + ai
                    status.text = "تحلیل کامل شد."
                }
            } catch (e: Exception) {
                status.text = "تحلیل با خطا مواجه شد."
                result.text = (result.text.toString() + "\n\n" + (e.message ?: "خطای ناشناخته")).trim()
            }
        }
    }

    private fun analyzeImageSection() {
        val bitmap = lastBitmap
        if (bitmap == null) {
            toast("اول یک عکس بگیر یا تصویر انتخاب کن.")
            return
        }
        lifecycleScope.launch {
            try {
                status.text = "در حال تحلیل تصویر..."
                val prompt = AnalysisEngine.promptFor(section)
                result.text = AiClient.analyzeImage(this@MainActivity, prompt, bitmap)
                status.text = "تحلیل تصویر کامل شد."
            } catch (e: Exception) {
                status.text = "تحلیل تصویر انجام نشد."
                result.text = e.message ?: "خطای ناشناخته"
            }
        }
    }

    private fun runQuestionnaire() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 12, 22, 12)
        }
        val checks = TraditionalQuestionnaire.questions.mapIndexed { index, q ->
            CheckBox(this).apply {
                text = "${index + 1}. ${q.text}"
                setPadding(0, 8, 0, 8)
                box.addView(this)
            }
        }
        AlertDialog.Builder(this)
            .setTitle("پرسشنامه TCM / Ayurveda")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("تحلیل") { _, _ ->
                val answers = checks.map { it.isChecked }
                val local = TraditionalQuestionnaire.summarize(answers)
                result.text = local
                status.text = "پرسشنامه تکمیل شد."
                val cfg = AiClient.loadConfig(this)
                val hasAi = cfg.gatewayUrl.isNotBlank() || cfg.geminiKey.isNotBlank() || cfg.mistralKey.isNotBlank()
                if (hasAi) {
                    lifecycleScope.launch {
                        try {
                            val text = TraditionalQuestionnaire.answersAsText(answers)
                            val ai = AiClient.analyzeText(
                                this@MainActivity,
                                AnalysisEngine.promptFor(AnalysisEngine.Section.TRADITIONAL, text)
                            )
                            result.text = local + "\n\n=== تحلیل AI ===\n" + ai
                        } catch (e: Exception) {
                            result.text = local + "\n\nAI: " + (e.message ?: "خطا")
                        }
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showPhotoGuide() {
        val text = when (section) {
            AnalysisEngine.Section.LABS -> "صفحه آزمایش را صاف، بدون سایه و بازتاب نور بگیر. متن و محدوده‌های مرجع باید کاملاً خوانا باشند. برای چند صفحه، PDF بهتر است."
            AnalysisEngine.Section.IRIDOLOGY -> "در نور یکنواخت عکس نزدیک و واضح از عنبیه بگیر. فوکوس روی خود عنبیه باشد، بازتاب فلش کم باشد و هر چشم جداگانه عکس‌برداری شود."
            AnalysisEngine.Section.TONGUE -> "نور طبیعی یا سفید یکنواخت، بدون فیلتر رنگی. زبان را کامل و بدون فشار بیرون بیاور و دوربین روبه‌رو باشد."
            AnalysisEngine.Section.PALM -> "کل کف دست و انگشتان داخل کادر، نور یکنواخت، فوکوس واضح و بدون فیلتر یا سایه شدید."
            AnalysisEngine.Section.TRADITIONAL -> "این بخش تصویری نیست؛ پرسشنامه را بر اساس وضعیت معمول خودت پاسخ بده."
        }
        AlertDialog.Builder(this)
            .setTitle("راهنمای ورودی صحیح")
            .setMessage(text)
            .setPositiveButton("باشه", null)
            .show()
    }

    private fun showAiSettings() {
        val old = AiClient.loadConfig(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 8)
        }

        fun field(hint: String, value: String, secret: Boolean = false): EditText =
            EditText(this).apply {
                this.hint = hint
                setText(value)
                if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                box.addView(this, full())
            }

        val gemini = field("Gemini API Key", old.geminiKey, true)
        val geminiModel = field("Gemini model", old.geminiModel)
        val mistral = field("Mistral API Key", old.mistralKey, true)
        val mistralModel = field("Mistral model", old.mistralModel)
        val gateway = field("Gateway URL (اختیاری)", old.gatewayUrl)
        val token = field("Gateway token (اختیاری)", old.gatewayToken, true)

        AlertDialog.Builder(this)
            .setTitle("تنظیم چند AI")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("ذخیره") { _, _ ->
                AiClient.saveConfig(
                    this,
                    AiConfig(
                        gemini.text.toString().trim(),
                        geminiModel.text.toString().trim().ifBlank { "gemini-3.8-flash" },
                        mistral.text.toString().trim(),
                        mistralModel.text.toString().trim().ifBlank { "mistral-small-latest" },
                        gateway.text.toString().trim(),
                        token.text.toString().trim()
                    )
                )
                toast("تنظیمات AI ذخیره شد.")
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun full() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun fullWithMargins() = full().apply { setMargins(0, 7, 0, 7) }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
