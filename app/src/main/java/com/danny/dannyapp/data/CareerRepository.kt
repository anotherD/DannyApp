package com.danny.dannyapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class CareerRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCareers(): List<Career> {
        val jsonString = prefs.getString(KEY_CAREERS, null) ?: return emptyList()
        val list = mutableListOf<Career>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val level = obj.getInt("level")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                list.add(Career(id, name, level, createdAt))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.createdAt }
    }

    fun saveCareer(career: Career) {
        val careers = getCareers().toMutableList()
        val index = careers.indexOfFirst { it.id == career.id }
        if (index >= 0) {
            careers[index] = career
        } else {
            careers.add(0, career)
        }
        saveAll(careers)
    }

    private fun saveAll(careers: List<Career>) {
        val jsonArray = JSONArray()
        for ((id, name, level, createdAt) in careers) {
            val obj = JSONObject().apply {
                put("id", id)
                put("name", name)
                put("level", level)
                put("createdAt", createdAt)
            }
            jsonArray.put(obj)
        }
        prefs.edit {
            putString(KEY_CAREERS, jsonArray.toString())
        }
    }

    companion object {
        private const val PREFS_NAME = "career_prefs"
        private const val KEY_CAREERS = "key_careers"
    }
}
