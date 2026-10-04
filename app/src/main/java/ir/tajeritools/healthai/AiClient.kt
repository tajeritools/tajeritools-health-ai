package ir.tajeritools.healthai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AiConfig(
    val geminiKey: String,
    val geminiModel: String,
    val mistralKey: String,
    val mistralModel: String,
    val gatewayUrl: String,
    val gatewayToken: String,
    val medgemmaUrl: String = "",
    val medgemmaToken: String = "",
    val medgemmaModel: String = "google/medgemma-4b-it",
    val provider: String = "auto"
)

object AiClient {
    private const val DEFAULT_GEMINI_MODEL = "gemini-3.7-flash"

    fun loadConfig(context: Context): AiConfig {
        val p = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        return AiConfig(
            geminiKey = p.getString("gemini_key", "") ?: "",
            geminiModel = p.getString("gemini_model", DEFAULT_GEMINI_MODEL) ?: DEFAULT_GEMINI_MODEL,
            mistralKey = p.getString("mistral_key", "") ?: "",
            mistralModel = p.getString("mistral_model", "mistral-small-latest") ?: "mistral-small-latest",
            gatewayUrl = p.getString("gateway_url", "") ?: "",
            gatewayToken = p.getString("gateway_token", "") ?: "",
            medgemmaUrl = p.getString("medgemma_url", "") ?: "",
            medgemmaToken = p.getString("medgemma_token", "") ?: "",
            medgemmaModel = p.getString("medgemma_model", "google/medgemma-4b-it") ?: "google/medgemma-4b-it",
            provider = p.getString("provider", "auto") ?: "auto"
        )
    }

    fun saveConfig(context: Context, cfg: AiConfig) {
        context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE).edit()
            .putString("gemini_key", cfg.geminiKey)
            .putString("gemini_model", cfg.geminiModel.ifBlank { DEFAULT_GEMINI_MODEL })
            .putString("mistral_key", cfg.mistralKey)
            .putString("mistral_model", cfg.mistralModel)
            .putString("gateway_url", cfg.gatewayUrl)
            .putString("gateway_token", cfg.gatewayToken)
            .putString("medgemma_url", cfg.medgemmaUrl)
            .putString("medgemma_token", cfg.medgemmaToken)
            .putString("medgemma_model", cfg.medgemmaModel)
            .putString("provider", cfg.provider)
            .apply()
    }

    fun isConfigured(cfg: AiConfig): Boolean = when (cfg.provider) {
        "mistral" -> cfg.mistralKey.isNotBlank()
        "medgemma" -> cfg.medgemmaUrl.isNotBlank()
        else -> cfg.geminiKey.isNotBlank() || cfg.gatewayUrl.isNotBlank() ||
            cfg.mistralKey.isNotBlank() || cfg.medgemmaUrl.isNotBlank()
    }

    suspend fun analyzeText(context: Context, prompt: String): String =
        analyze(loadConfig(context), medicalPrompt(prompt), null)

    suspend fun analyzeImage(context: Context, prompt: String, bitmap: Bitmap): String =
        analyze(loadConfig(context), medicalPrompt(prompt), withContext(Dispatchers.Default) {
            bitmapToBase64(bitmap)
        })

    suspend fun testConnection(cfg: AiConfig, provider: String): String =
        analyze(cfg.copy(provider = provider), "Reply with OK only.", null)

    private suspend fun analyze(cfg: AiConfig, prompt: String, image: String?): String {
        val providers = if (cfg.provider == "auto") listOf("gemini", "medgemma", "gateway", "mistral")
            else listOf(cfg.provider)
        val errors = mutableListOf<String>()
        for (provider in providers) {
            val configured = when (provider) {
                "gemini" -> cfg.geminiKey.isNotBlank()
                "medgemma" -> cfg.medgemmaUrl.isNotBlank()
                "gateway" -> cfg.gatewayUrl.isNotBlank()
                "mistral" -> cfg.mistralKey.isNotBlank()
                else -> false
            }
            if (!configured) continue
            try {
                val response = when (provider) {
                    "gemini" -> callGemini(cfg, prompt, image)
                    "gateway" -> callGateway(cfg, prompt, image)
                    "medgemma" -> callChat(cfg.medgemmaUrl, cfg.medgemmaToken, cfg.medgemmaModel, prompt, image)
                    else -> callChat("https://api.mistral.ai/v1/chat/completions", cfg.mistralKey, cfg.mistralModel, prompt, image)
                }
                return "[$provider]\n$response"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "$provider: ${e.message}"
            }
        }
        error(errors.joinToString("\n").ifBlank { "تنظیمات سرویس انتخاب‌شده کامل نیست." })
    }

    private fun medicalPrompt(userPrompt: String): String = """
تو یک دستیار هوش مصنوعی سلامت برای تحلیل آموزشی، پیشگیری، غربالگری و پشتیبانی تصمیم‌گیری هستی.
- داده‌های ورودی را دقیق و ساختاریافته بررسی کن.
- بین یافته قطعی، احتمال، ریسک و اطلاعات ناکافی تفاوت روشن بگذار.
- برای علائم خطر، مراجعه فوری یا پیگیری پزشکی را مشخص کن.
- درمان دارویی نسخه‌ای، تغییر دوز، یا قطع دارو را بدون ارزیابی پزشک توصیه نکن.
- در بخش‌های سنتی، ادعاهای سنتی را از شواهد پزشکی جدا کن.
- پاسخ را به فارسی واضح و قابل فهم بنویس.

$userPrompt
""".trimIndent()

    private suspend fun callGemini(cfg: AiConfig, prompt: String, imageBase64: String?): String =
        withContext(Dispatchers.IO) {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${cfg.geminiModel}:generateContent")
            val parts = JSONArray()
            if (imageBase64 != null) {
                parts.put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "image/jpeg")
                    .put("data", imageBase64)))
            }
            parts.put(JSONObject().put("text", prompt))
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            val response = postJson(url, body.toString(), mapOf("x-goog-api-key" to cfg.geminiKey))
            val json = JSONObject(response)
            json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.let { arr ->
                    (0 until arr.length()).mapNotNull { idx ->
                        arr.optJSONObject(idx)?.optString("text")?.takeIf { it.isNotBlank() }
                    }.joinToString("\n")
                }
                ?.takeIf { it.isNotBlank() }
                ?: error("پاسخ قابل استفاده از Gemini دریافت نشد.")
        }

    private suspend fun callChat(endpoint: String, token: String, model: String,
                                 prompt: String, image: String?): String = withContext(Dispatchers.IO) {
        val content: Any = if (image == null) prompt else JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url",
                JSONObject().put("url", "data:image/jpeg;base64,$image")))
        val body = JSONObject().put("model", model).put("max_tokens", 2048)
            .put("stream", false)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val headers = if (token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $token")
        val response = JSONObject(postJson(URL(endpoint), body.toString(), headers))
        val value = response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
        val text = when (value) {
            is String -> value
            is JSONArray -> (0 until value.length()).mapNotNull {
                value.optJSONObject(it)?.optString("text")?.takeIf { it.isNotBlank() }
            }.joinToString("\n")
            else -> ""
        }
        text.takeIf { it.isNotBlank() } ?: error("پاسخ قابل استفاده دریافت نشد.")
    }

    private suspend fun callGateway(cfg: AiConfig, prompt: String, imageBase64: String?): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("prompt", prompt)
                .put("mode", if (imageBase64 == null) "text" else "vision")
            if (imageBase64 != null) body.put("image_base64", imageBase64)
            val headers = if (cfg.gatewayToken.isBlank()) emptyMap()
            else mapOf("Authorization" to "Bearer ${cfg.gatewayToken}")
            val response = postJson(URL(cfg.gatewayUrl), body.toString(), headers)
            val json = JSONObject(response)
            json.optString("result").takeIf { it.isNotBlank() }
                ?: json.optString("text").takeIf { it.isNotBlank() }
                ?: error("Gateway پاسخ قابل استفاده برنگرداند.")
        }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun postJson(url: URL, body: String, headers: Map<String, String>): String {
        require(url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null) { "آدرس سرویس باید HTTPS و بدون اطلاعات ورود باشد." }
        val con = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
        con.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = con.responseCode
        val stream = if (code in 200..299) con.inputStream else con.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("HTTP $code: درخواست ناموفق بود؛ کلید، آدرس، مدل و سهمیه را بررسی کن.")
        return text
        } finally { con.disconnect() }
    }
}
