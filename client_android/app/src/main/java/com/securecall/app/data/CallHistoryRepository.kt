package com.securecall.app.data

import android.content.Context
import org.json.JSONArray

object CallHistoryRepository {
    private const val PREFS = "securecall_call_history"
    private const val KEY = "history_json"

    fun getAll(context: Context): List<CallRecord> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<CallRecord>()
        for (i in 0 until arr.length()) {
            list.add(CallRecord.fromJson(arr.getJSONObject(i)))
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun add(context: Context, record: CallRecord) {
        val all = getAll(context).toMutableList()
        all.add(0, record)
        persist(context, all)
    }

    fun countMissed(context: Context): Int {
        return getAll(context).count { it.type == CallType.MISSED }
    }

    fun delete(context: Context, recordId: String) {
        val all = getAll(context).filter { it.id != recordId }
        persist(context, all)
    }

    private fun persist(context: Context, records: List<CallRecord>) {
        val arr = JSONArray()
        records.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
