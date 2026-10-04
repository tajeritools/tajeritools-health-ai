package ir.tajeritools.healthai.data

import android.content.Context
import org.json.JSONObject

class ResearchKnowledgeRepository(private val context: Context) {
    fun load(module: Module): JSONObject {
        val file = when (module) {
            Module.IRIDOLOGY -> "knowledge/iridology_traditional_map.json"
            Module.TONGUE -> "knowledge/tongue_extended.json"
            Module.PALM_HAND -> "knowledge/palm_hand_extended.json"
        }
        val json = context.assets.open(file).bufferedReader().use { it.readText() }
        return JSONObject(json)
    }

    enum class Module { IRIDOLOGY, TONGUE, PALM_HAND }
}
