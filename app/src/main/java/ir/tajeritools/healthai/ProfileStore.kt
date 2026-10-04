package ir.tajeritools.healthai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PersonProfile(
    val id: String,
    val name: String,
    val createdAt: Long
)

object ProfileStore {
    private const val PREFS = "person_profiles"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_ACTIVE = "active_profile"

    fun list(context: Context): List<PersonProfile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROFILES, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                PersonProfile(
                    id = it.optString("id"),
                    name = it.optString("name"),
                    createdAt = it.optLong("createdAt")
                )
            }
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
    }

    fun add(context: Context, name: String): PersonProfile {
        val profile = PersonProfile(UUID.randomUUID().toString(), name.trim(), System.currentTimeMillis())
        val all = list(context).toMutableList().apply { add(profile) }
        saveList(context, all)
        setActive(context, profile.id)
        profileDir(context, profile.id).mkdirs()
        return profile
    }

    fun active(context: Context): PersonProfile? {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, null)
        return list(context).firstOrNull { it.id == id }
    }

    fun setActive(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE, id)
            .apply()
    }

    fun deleteProfile(context: Context, id: String) {
        val remaining = list(context).filterNot { it.id == id }
        saveList(context, remaining)
        profileDir(context, id).deleteRecursively()
        if (active(context)?.id == id) setActive(context, remaining.firstOrNull()?.id)
    }

    fun files(context: Context, id: String): List<File> =
        profileDir(context, id).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun deleteFile(file: File): Boolean = file.delete()

    fun saveBitmap(context: Context, profileId: String, bitmap: Bitmap, prefix: String): File {
        val file = File(profileDir(context, profileId), "${safe(prefix)}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return file
    }

    fun copyUri(context: Context, profileId: String, uri: Uri, prefix: String): File {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = when {
            mime.contains("pdf") -> "pdf"
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val file = File(profileDir(context, profileId), "${safe(prefix)}_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "فایل قابل خواندن نیست" }
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    fun saveResult(context: Context, profileId: String, prefix: String, text: String): File {
        val file = File(profileDir(context, profileId), "${safe(prefix)}_${System.currentTimeMillis()}.txt")
        file.writeText(text, Charsets.UTF_8)
        return file
    }

    private fun profileDir(context: Context, id: String): File =
        File(context.filesDir, "profiles/$id").apply { mkdirs() }

    private fun saveList(context: Context, profiles: List<PersonProfile>) {
        val arr = JSONArray()
        profiles.forEach {
            arr.put(JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("createdAt", it.createdAt))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PROFILES, arr.toString())
            .apply()
    }

    private fun safe(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(32).ifBlank { "item" }
}
