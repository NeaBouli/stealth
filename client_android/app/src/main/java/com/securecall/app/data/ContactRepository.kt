package com.securecall.app.data

import android.content.Context
import org.json.JSONArray

object ContactRepository {
    private const val PREFS = "securecall_contacts"
    private const val KEY = "contacts_json"

    fun getAll(context: Context): List<Contact> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Contact>()
        for (i in 0 until arr.length()) {
            list.add(Contact.fromJson(arr.getJSONObject(i)))
        }
        return list.sortedBy { it.name.lowercase() }
    }

    fun save(context: Context, contact: Contact) {
        val all = getAll(context).toMutableList()
        all.add(contact)
        persist(context, all)
    }

    fun delete(context: Context, contactId: String) {
        val all = getAll(context).filter { it.id != contactId }
        persist(context, all)
    }

    private fun persist(context: Context, contacts: List<Contact>) {
        val arr = JSONArray()
        contacts.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
