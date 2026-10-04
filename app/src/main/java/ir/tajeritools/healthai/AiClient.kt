package ir.tajeritools.healthai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
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
    val gatewayToken: String
)

object AiClient {
    fun loadConfig(context: Context): AiConfig {
        val p = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        return AiConfig(
            geminiKey = p.getString("gemini_key", "") ?: "",
            geminiModel = p.getString("gemini_model", "gemini-3.8-flash") ?: "gemini-3.8-flash",
            mistralKey = p.getString("mistral_key", "") ?: "",
            mistralModel = p.getString("mistral_model", "mistral-small-latest") ?: "mistral-small-latest",
            gatewayUrl = p.getString("gateway_url", "") ?: "",
            gatewayToken = p.getString("gateway_token", "") ?: ""
        )
    }

    fun saveConfig(context: Context, cfg: AiConfig) {
        context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE).edit()
            .putString("gemini_key", cfg.geminiKey)
            .putString("gemini_model", cfg.geminiModel)
            .putString("mistral_key", cfg.mistralKey)
            .putString("mistral_model", cfg.mistralModel)
            .putString("gateway_url", cfg.gatewayUrl)
            .putString("gateway_token", cfg.gatewayToken)
            .apply()
    }

    suspend fun analyzeText(context: Context, prompt: String): String {
        val cfg = loadConfig(context)
        val errors = mutableListOf<String>()

        if (cfg.gatewayUrl.isNotBlank()) {
            try { return callGateway(cfg, prompt, null) }
            catch (e: Exception) { errors += "Gateway: ${e.message}" }
        }
        if (cfg.geminiKey.isNotBlank()) {
            try { return callGemini(cfg, prompt, null) }
            catch (e: Exception) { errors += "Gemini: ${e.message}" }
        }
        if (cfg.mistralKey.isNotBlank()) {
            try { return callMistral(cfg, prompt) }
            catch (e: Exception) { errors += "Mistral: ${e.message}" }
        }
        if (errors.isNotEmpty()) error(errors.joinToString("\n"))
        error("هیچ کلید یا Gateway هوش مصنوعی تنظیم نشده است.")
    }

    suspend fun analyzeImage(context: Context, prompt: String, bitmap: Bitmap): String {
        val cfg = loadConfig(context)
        val jpeg = bitmapToBase64(bitmap)
        val errors = mutableListOf<String>()

        if (cfg.gatewayUrl.isNotBlank()) {
            try { return callGateway(cfg, prompt, jpeg) }
            catch (e: Exception) { errors += "Gateway: ${e.message}" }
        }
        if (cfg.geminiKey.isNotBlank()) {
            try { return callGemini(cfg, prompt, jpeg) }
            catch (e: Exception) { errors += "Gemini: ${e.message}" }
        }
        if (cfg.mistralKey.isNotBlank()) {
            val ocr = try { OcrEngine.extractFromBitmap(bitmap) } catch (_: Exception) { "" }
            if (ocr.isNotBlank()) {
                try { return callMistral(cfg, prompt + "\n\nمتن استخراج‌شده از تصویر:\n" + ocr) }
                catch (e: Exception) { errors += "Mistral: ${e.message}" }
            }
        }
        if (errors.isNotEmpty()) error(errors.joinToString("\n"))
        error("برای تحلیل تصویر، Gemini یا Gateway را تنظیم کن.")
    }

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

    private suspend fun callMistral(cfg: AiConfig, prompt: String): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", cfg.mistralModel)
                .put("messages", JSONArray().put(JSONObject()
                    .put("role", "user")
                    .put("content", prompt)))
            val response = postJson(
                URL("https://api.mistral.ai/v1/chat/completions"),
                body.toString(),
                mapOf("Authorization" to "Bearer ${cfg.mistralKey}")
            )
            JSONObject(response).optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?: error("پاسخ قابل استفاده از Mistral دریافت نشد.")
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
        val con = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        con.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = con.responseCode
        val stream = if (code in 200..299) con.inputStream else con.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("HTTP $code: ${text.take(500)}")
        return text
    }
}
