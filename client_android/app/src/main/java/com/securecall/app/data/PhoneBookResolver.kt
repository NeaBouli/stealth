package com.securecall.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Resolves phone numbers to contact names using the Android system phone book.
 * This is the PRIMARY source for contact names. SecureCall's internal ContactRepository
 * is only for SecureCall-specific data (Secure ID linking).
 */
object PhoneBookResolver {
    private const val TAG = "PhoneBookResolver"

    /**
     * Look up a phone number in the Android system contacts (phone book).
     * Returns the contact display name if found, null otherwise.
     * Uses ContactsContract.PhoneLookup which handles number normalization automatically.
     */
    fun resolvePhoneNumber(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            Log.d(TAG, "Resolved $phoneNumber -> $name (phone book)")
                            return name
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PhoneLookup failed for $phoneNumber", e)
        }
        return null
    }

    /**
     * Resolve a caller's display name using all available sources, in priority order:
     * 1. Android phone book (by phone number)
     * 2. SecureCall internal contacts (by clientId)
     * 3. SecureCall internal contacts (by phone number)
     * 4. Raw phone number (if available)
     * 5. Raw clientId (last resort)
     */
    fun resolveCallerName(
        context: Context,
        clientId: String,
        phoneNumber: String = ""
    ): String {
        // 1. Try Android phone book first (primary source)
        if (phoneNumber.isNotEmpty()) {
            val phoneBookName = resolvePhoneNumber(context, phoneNumber)
            if (phoneBookName != null) return phoneBookName
        }

        // 2-3. Try SecureCall internal contacts
        val contacts = ContactRepository.getAll(context)
        val contactByClientId = contacts.find { it.phoneOrId == clientId }
        if (contactByClientId != null) return contactByClientId.name

        if (phoneNumber.isNotEmpty()) {
            val normalizedCaller = phoneNumber.replace(Regex("[^0-9+]"), "")
            val contactByPhone = contacts.find {
                it.phoneOrId.replace(Regex("[^0-9+]"), "") == normalizedCaller
            }
            if (contactByPhone != null) return contactByPhone.name
        }

        // 4-5. Fallback to raw phone or clientId
        return if (phoneNumber.isNotEmpty()) phoneNumber else clientId
    }
}
