package com.rams.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Your own snippet buttons, saved on the phone.
object SnippetStore {

    private const val KEY = "custom_snippets"

    fun load(context: Context): MutableList<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val raw = context.getSharedPreferences("rams_settings", Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val label = o.optString("label")
                val code = o.optString("code")
                if (label.isNotEmpty() && code.isNotEmpty()) out.add(label to code)
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun save(context: Context, list: List<Pair<String, String>>) {
        val arr = JSONArray()
        list.forEach { (label, code) ->
            arr.put(JSONObject().put("label", label).put("code", code))
        }
        context.getSharedPreferences("rams_settings", Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}