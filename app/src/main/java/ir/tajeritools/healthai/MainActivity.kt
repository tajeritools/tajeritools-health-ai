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
import java.io.File

class MainActivity : AppCompatActivity() {
    private var section = AnalysisEngine.Section.LABS
    private var lastBitmap: Bitmap? = null
    private var lastText: String = ""
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var preview: ImageView
    private lateinit var activeProfileText: TextView

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                val profile = ProfileStore.active(this)
                if (profile == null) {
                    toast("اول یک پروفایل انتخاب کن.")
                    return@registerForActivityResult
                }
                lastBitmap = bitmap
                lastText = ""
                ProfileStore.saveBitmap(this, profile.id, bitmap, sectionPrefix() + "_photo")
                preview.setImageBitmap(bitmap)
                preview.visibility = View.VISIBLE
                status.text = "عکس در پروفایل «${profile.name}» ذخیره شد."
            }
        }

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { handleFile(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshProfileHeader()
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
            setPadding(0, 8, 0, 18)
        }, full())

        activeProfileText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
        }
        root.addView(activeProfileText, full())

        root.addView(Button(this).apply {
            text = "👤 پروفایل‌ها"
            isAllCaps = false
            setOnClickListener { showProfiles() }
        }, fullWithMargins())

        root.addView(Button(this).apply {
            text = "🗂 فایل‌های پروفایل فعال"
            isAllCaps = false
            setOnClickListener { showCurrentFiles() }
        }, fullWithMargins())

        root.addView(Button(this).apply {
            text = "🗑 حذف پروفایل فعال"
            isAllCaps = false
            setOnClickListener { confirmDeleteActiveProfile() }
        }, fullWithMargins())

        val sections = listOf(
            "آزمایش‌ها" to AnalysisEngine.Section.LABS,
            "طب سنتی" to AnalysisEngine.Section.TRADITIONAL,
            "عنبیه" to AnalysisEngine.Section.IRIDOLOGY,
            "زبان" to AnalysisEngine.Section.TONGUE,
            "کف دست" to AnalysisEngine.Section.PALM
        )
        sections.forEach { (label, value) ->
            root.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { selectSection(value, label) }
            }, fullWithMargins())
        }

        status = TextView(this).apply {
            text = "بخش آزمایش‌ها فعال است."
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
                if (!requireActiveProfile()) return@setOnClickListener
                if (section == AnalysisEngine.Section.TRADITIONAL) runQuestionnaire()
                else cameraLauncher.launch(null)
            }
        }, fullWithMargins())

        root.addView(Button(this).apply {
            text = "📁 انتخاب تصویر یا PDF"
            isAllCaps = false
            setOnClickListener {
                if (!requireActiveProfile()) return@setOnClickListener
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
            setOnClickListener {
                if (!requireActiveProfile()) return@setOnClickListener
                analyzeCurrent()
            }
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
            text = "تحلیل‌های آزمایش بر اساس داده و محدوده مرجع گزارش انجام می‌شوند. تفسیرهای سنتی به‌صورت غیرتشخیصی ارائه می‌شوند."
            textSize = 12f
        }, full())

        return ScrollView(this).apply { addView(root) }
    }

    private fun refreshProfileHeader() {
        val active = ProfileStore.active(this)
        activeProfileText.text = if (active == null) {
            "پروفایل فعال: انتخاب نشده"
        } else {
            "پروفایل فعال: ${active.name}"
        }
    }

    private fun requireActiveProfile(): Boolean {
        if (ProfileStore.active(this) != null) return true
        toast("اول برای شخص موردنظر پروفایل بساز یا انتخاب کن.")
        showProfiles()
        return false
    }

    private fun showProfiles() {
        val profiles = ProfileStore.list(this)
        if (profiles.isEmpty()) {
            showNewProfileDialog()
            return
        }
        val activeId = ProfileStore.active(this)?.id
        val names = profiles.map { it.name }.toTypedArray()
        val checked = profiles.indexOfFirst { it.id == activeId }

        AlertDialog.Builder(this)
            .setTitle("پروفایل‌ها")
            .setSingleChoiceItems(names, checked) { dialog, which ->
                ProfileStore.setActive(this, profiles[which].id)
                refreshProfileHeader()
                clearCurrentWork()
                dialog.dismiss()
            }
            .setPositiveButton("پروفایل جدید") { _, _ -> showNewProfileDialog() }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun showNewProfileDialog() {
        val input = EditText(this).apply {
            hint = "نام شخص"
            setPadding(24, 8, 24, 8)
        }
        AlertDialog.Builder(this)
            .setTitle("پروفایل جدید")
            .setView(input)
            .setPositiveButton("ساخت") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    toast("نام شخص را وارد کن.")
                } else {
                    ProfileStore.add(this, name)
                    refreshProfileHeader()
                    clearCurrentWork()
                    toast("پروفایل ساخته شد.")
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun confirmDeleteActiveProfile() {
        val active = ProfileStore.active(this)
        if (active == null) {
            toast("پروفایل فعالی وجود ندارد.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("حذف پروفایل")
            .setMessage("پروفایل «${active.name}» و همه عکس‌ها، فایل‌ها و نتایج آن حذف شوند؟")
            .setPositiveButton("حذف کامل") { _, _ ->
                ProfileStore.deleteProfile(this, active.id)
                refreshProfileHeader()
                clearCurrentWork()
                toast("پروفایل حذف شد.")
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showCurrentFiles() {
        val active = ProfileStore.active(this)
        if (active == null) {
            toast("پروفایل فعالی وجود ندارد.")
            return
        }
        val files = ProfileStore.files(this, active.id)
        if (files.isEmpty()) {
            toast("این پروفایل هنوز فایلی ندارد.")
            return
        }
        val labels = files.map { displayFileName(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("فایل‌های ${active.name}")
            .setItems(labels) { _, which ->
                confirmDeleteFile(files[which])
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun confirmDeleteFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("حذف فایل")
            .setMessage("«${displayFileName(file)}» حذف شود؟")
            .setPositiveButton("حذف") { _, _ ->
                if (ProfileStore.deleteFile(file)) toast("فایل حذف شد.")
                else toast("حذف فایل انجام نشد.")
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun displayFileName(file: File): String =
        file.name.replace("_", " ")

    private fun clearCurrentWork() {
        lastBitmap = null
        lastText = ""
        preview.setImageDrawable(null)
        preview.visibility = View.GONE
        result.text = "نتیجه اینجا نمایش داده می‌شود."
    }

    private fun selectSection(value: AnalysisEngine.Section, label: String) {
        section = value
        clearCurrentWork()
        status.text = when (value) {
            AnalysisEngine.Section.TRADITIONAL -> "بخش ${label} فعال است."
            AnalysisEngine.Section.LABS -> "بخش ${label} فعال است. تصویر یا PDF گزارش آزمایش را وارد کن."
            else -> "بخش ${label} فعال است. عکس واضح بگیر یا تصویر انتخاب کن."
        }
    }

    private fun handleFile(uri: Uri) {
        val active = ProfileStore.active(this) ?: run {
            toast("اول یک پروفایل انتخاب کن.")
            return
        }
        val mime = contentResolver.getType(uri).orEmpty()
        if (section != AnalysisEngine.Section.LABS && mime == "application/pdf") {
            toast("برای عنبیه، زبان و کف دست تصویر انتخاب کن.")
            return
        }
        lifecycleScope.launch {
            try {
                status.text = "در حال خواندن فایل..."
                ProfileStore.copyUri(this@MainActivity, active.id, uri, sectionPrefix() + "_file")
                if (section == AnalysisEngine.Section.LABS) {
                    lastText = OcrEngine.extractFromUri(this@MainActivity, uri)
                    lastBitmap = null
                    preview.visibility = View.GONE
                    status.text = "فایل در پروفایل ذخیره شد؛ OCR تمام شد."
                    result.text = lastText.take(5000)
                } else {
                    val bmp = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                    lastBitmap = requireNotNull(bmp) { "تصویر قابل خواندن نیست" }
                    lastText = ""
                    preview.setImageBitmap(lastBitmap)
                    preview.visibility = View.VISIBLE
                    status.text = "تصویر در پروفایل ذخیره شد و آماده تحلیل است."
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
        val active = ProfileStore.active(this) ?: return
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
                val hasAi = AiClient.isConfigured(cfg)
                val finalText = if (!hasAi) {
                    status.text = "تحلیل محلی تمام شد."
                    local
                } else {
                    val ai = AiClient.analyzeText(
                        this@MainActivity,
                        AnalysisEngine.promptFor(AnalysisEngine.Section.LABS, lastText)
                    )
                    status.text = "تحلیل کامل شد."
                    local + "\n\n=== تحلیل AI ===\n" + ai
                }
                result.text = finalText
                ProfileStore.saveResult(this@MainActivity, active.id, "labs_result", finalText)
            } catch (e: Exception) {
                status.text = "تحلیل با خطا مواجه شد."
                result.text = (result.text.toString() + "\n\n" + (e.message ?: "خطای ناشناخته")).trim()
            }
        }
    }

    private fun analyzeImageSection() {
        val active = ProfileStore.active(this) ?: return
        val bitmap = lastBitmap
        if (bitmap == null) {
            toast("اول یک عکس بگیر یا تصویر انتخاب کن.")
            return
        }
        lifecycleScope.launch {
            try {
                status.text = "در حال تحلیل تصویر..."
                val prompt = AnalysisEngine.promptFor(section)
                val finalText = AiClient.analyzeImage(this@MainActivity, prompt, bitmap)
                result.text = finalText
                ProfileStore.saveResult(this@MainActivity, active.id, sectionPrefix() + "_result", finalText)
                status.text = "تحلیل تصویر کامل شد."
            } catch (e: Exception) {
                status.text = "تحلیل تصویر انجام نشد."
                result.text = e.message ?: "خطای ناشناخته"
            }
        }
    }

    private fun runQuestionnaire() {
        val active = ProfileStore.active(this)
        if (active == null) {
            toast("اول یک پروفایل انتخاب کن.")
            return
        }
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
                ProfileStore.saveResult(this, active.id, "traditional_result", local)
                status.text = "پرسشنامه تکمیل شد."
                val cfg = AiClient.loadConfig(this)
                val hasAi = AiClient.isConfigured(cfg)
                if (hasAi) {
                    lifecycleScope.launch {
                        try {
                            val text = TraditionalQuestionnaire.answersAsText(answers)
                            val ai = AiClient.analyzeText(
                                this@MainActivity,
                                AnalysisEngine.promptFor(AnalysisEngine.Section.TRADITIONAL, text)
                            )
                            val finalText = local + "\n\n=== تحلیل AI ===\n" + ai
                            result.text = finalText
                            ProfileStore.saveResult(this@MainActivity, active.id, "traditional_ai_result", finalText)
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
            AnalysisEngine.Section.LABS -> "صفحه آزمایش را صاف، بدون سایه و بازتاب نور بگیر. متن و محدوده‌های مرجع باید کاملاً خوانا باشند."
            AnalysisEngine.Section.IRIDOLOGY -> "در نور یکنواخت عکس نزدیک و واضح از عنبیه بگیر. فوکوس روی خود عنبیه باشد و هر چشم جداگانه عکس‌برداری شود."
            AnalysisEngine.Section.TONGUE -> "نور طبیعی یا سفید یکنواخت، بدون فیلتر رنگی. زبان را کامل و دوربین را روبه‌رو نگه دار."
            AnalysisEngine.Section.PALM -> "کل کف دست و انگشتان داخل کادر، نور یکنواخت و فوکوس واضح باشد."
            AnalysisEngine.Section.TRADITIONAL -> "این بخش تصویری نیست؛ پرسشنامه را بر اساس وضعیت معمول پاسخ بده."
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
        val gateway = field("Legacy Gateway URL (اختیاری)", old.gatewayUrl)
        val token = field("Gateway token (اختیاری)", old.gatewayToken, true)

        val medUrl = field("MedGemma HTTPS URL کامل /v1/chat/completions", old.medgemmaUrl)
        val medToken = field("MedGemma endpoint token", old.medgemmaToken, true)
        val medModel = field("MedGemma served model", old.medgemmaModel)
        val providers = listOf("auto", "mistral", "medgemma")
        val selector = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("خودکار: Gemini، MedGemma، Gateway، Mistral", "فقط Mistral", "فقط MedGemma"))
            setSelection(providers.indexOf(old.provider).coerceAtLeast(0))
        }
        box.addView(selector, full())
        fun config() = AiConfig(
            gemini.text.toString().trim(),
            geminiModel.text.toString().trim().ifBlank { old.geminiModel },
            mistral.text.toString().trim(),
            mistralModel.text.toString().trim().ifBlank { "mistral-small-latest" },
            gateway.text.toString().trim(), token.text.toString().trim(),
            medUrl.text.toString().trim(), medToken.text.toString().trim(),
            medModel.text.toString().trim().ifBlank { "google/medgemma-4b-it" },
            providers[selector.selectedItemPosition]
        )
        val testStatus = TextView(this)
        listOf("mistral", "medgemma").forEach { provider ->
            box.addView(Button(this).apply {
                text = "آزمایش اتصال $provider"
                setOnClickListener {
                    val cfg = config()
                    isEnabled = false
                    testStatus.text = "در حال آزمایش $provider..."
                    lifecycleScope.launch {
                        try {
                            testStatus.text = "اتصال برقرار شد: " + AiClient.testConnection(cfg, provider)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            testStatus.text = e.message ?: "خطای اتصال"
                        } finally { isEnabled = true }
                    }
                }
            }, full())
        }
        box.addView(testStatus, full())
        AlertDialog.Builder(this)
            .setTitle("تنظیم چند AI")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("ذخیره") { _, _ ->
                AiClient.saveConfig(this, config())
                toast("تنظیمات AI ذخیره شد.")
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun sectionPrefix(): String = when (section) {
        AnalysisEngine.Section.LABS -> "labs"
        AnalysisEngine.Section.TRADITIONAL -> "traditional"
        AnalysisEngine.Section.IRIDOLOGY -> "iridology"
        AnalysisEngine.Section.TONGUE -> "tongue"
        AnalysisEngine.Section.PALM -> "palm"
    }

    private fun full() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun fullWithMargins() = full().apply { setMargins(0, 7, 0, 7) }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
