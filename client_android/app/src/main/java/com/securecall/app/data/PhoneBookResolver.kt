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
            // Fix CLIENT-HIGH-002 (2026-04-16): redact phone number — Log.e is not
            // stripped by ProGuard, so raw numbers would end up in logcat / bug reports.
            Log.e(TAG, "PhoneLookup failed (phone redacted)", e)
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

        // 2a. By SecureID (exact match on phoneOrId)
        val contactByClientId = contacts.find { it.phoneOrId == clientId }
        if (contactByClientId != null && contactByClientId.name.isNotBlank()
            && contactByClientId.name != clientId) return contactByClientId.name

        // 2b. By SecureID in secureId field (contact saved by phone, secureId linked later)
        if (clientId.startsWith("android-")) {
            val contactBySecureId = contacts.find { it.secureId == clientId }
            if (contactBySecureId != null && contactBySecureId.name.isNotBlank()) {
                // BUG-013: Also try phone book with this contact's phone number
                val phoneFromContact = contactBySecureId.phoneOrId
                if (!phoneFromContact.startsWith("android-")) {
                    val phoneBookName = resolvePhoneNumber(context, phoneFromContact)
                    if (phoneBookName != null) return phoneBookName
                }
                return contactBySecureId.name
            }
        }

        // 3. By normalized phone number
        if (phoneNumber.isNotEmpty()) {
            val normalizedCaller = PhoneUtils.normalize(phoneNumber, context)
            val contactByPhone = contacts.find {
                !it.phoneOrId.startsWith("android-") &&
                PhoneUtils.matches(it.phoneOrId, phoneNumber, context)
            }
            if (contactByPhone != null && contactByPhone.name.isNotBlank()) return contactByPhone.name
        }

        // 4-5. Fallback to raw phone or clientId
        return if (phoneNumber.isNotEmpty()) phoneNumber else clientId
    }
}
