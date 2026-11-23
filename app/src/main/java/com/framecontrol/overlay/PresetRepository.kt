package com.framecontrol.overlay

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class OverlayPreset(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val gridOrdinal: Int,
    val color: Int,
    val alpha: Int,
    val strokeDp: Float,
    val flip: Boolean
)

object PresetRepository {
    private const val PREF_NAME = "framecontrol_presets_v2"
    private const val KEY_PRESETS = "saved_presets_json"
    private const val KEY_ACTIVE = "active_state_json"

    fun getDefaultPresets(screenWidth: Int, screenHeight: Int): List<OverlayPreset> {
        val w = screenWidth.coerceAtLeast(720)
        val h43 = (w * 4) / 3
        val y43 = ((screenHeight - h43) / 2).coerceAtLeast(80)

        val h169 = (w * 16) / 9
        val y169 = ((screenHeight - h169) / 2).coerceAtLeast(40)

        val h11 = w
        val y11 = ((screenHeight - h11) / 2).coerceAtLeast(120)

        return listOf(
            OverlayPreset("4:3 Camera Viewfinder", 0, y43, w, h43, 0, Color.parseColor("#00E5FF"), 200, 1.8f, false),
            OverlayPreset("16:9 Video Frame", 0, y169, w, h169, 1, Color.parseColor("#FFD600"), 200, 1.8f, false),
            OverlayPreset("1:1 Square Frame", 0, y11, w, h11, 2, Color.parseColor("#00E676"), 200, 1.8f, false),
            OverlayPreset("Full Screen", 0, 0, w, screenHeight.coerceAtLeast(1280), 0, Color.parseColor("#00E5FF"), 180, 1.8f, false)
        )
    }

    fun getPresets(context: Context): List<OverlayPreset> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val dm = context.resources.displayMetrics
        val raw = prefs.getString(KEY_PRESETS, null)
        if (raw.isNullOrEmpty()) {
            val defaults = getDefaultPresets(dm.widthPixels, dm.heightPixels)
            saveAllPresets(context, defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<OverlayPreset>()
            for (i in 0 until arr.length()) {
                list.add(fromJson(arr.getJSONObject(i)))
            }
            if (list.isEmpty()) getDefaultPresets(dm.widthPixels, dm.heightPixels) else list
        } catch (e: Exception) {
            getDefaultPresets(dm.widthPixels, dm.heightPixels)
        }
    }

    fun savePreset(context: Context, preset: OverlayPreset) {
        val current = getPresets(context).toMutableList()
        val idx = current.indexOfFirst { it.name.equals(preset.name, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = preset
        } else {
            current.add(preset)
        }
        saveAllPresets(context, current)
        saveActiveState(context, preset)
    }

    fun deletePreset(context: Context, name: String) {
        val current = getPresets(context).filterNot { it.name.equals(name, ignoreCase = true) }
        saveAllPresets(context, current)
    }

    private fun saveAllPresets(context: Context, list: List<OverlayPreset>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESETS, arr.toString())
            .apply()
    }

    fun saveActiveState(context: Context, preset: OverlayPreset) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE, toJson(preset).toString())
            .apply()
    }

    fun loadActiveState(context: Context): OverlayPreset {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ACTIVE, null)
        if (!raw.isNullOrEmpty()) {
            try {
                return fromJson(JSONObject(raw))
            } catch (_: Exception) {}
        }
        return getPresets(context).first()
    }

    private fun toJson(p: OverlayPreset): JSONObject = JSONObject().apply {
        put("name", p.name)
        put("x", p.x)
        put("y", p.y)
        put("width", p.width)
        put("height", p.height)
        put("gridOrdinal", p.gridOrdinal)
        put("color", p.color)
        put("alpha", p.alpha)
        put("strokeDp", p.strokeDp.toDouble())
        put("flip", p.flip)
    }

    private fun fromJson(o: JSONObject): OverlayPreset = OverlayPreset(
        name = o.optString("name", "Custom Preset"),
        x = o.optInt("x", 0),
        y = o.optInt("y", 140),
        width = o.optInt("width", 1080),
        height = o.optInt("height", 1440),
        gridOrdinal = o.optInt("gridOrdinal", 0),
        color = o.optInt("color", Color.parseColor("#00E5FF")),
        alpha = o.optInt("alpha", 200),
        strokeDp = o.optDouble("strokeDp", 1.8).toFloat(),
        flip = o.optBoolean("flip", false)
    )
}
