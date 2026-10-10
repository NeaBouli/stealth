package com.securecall.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.securecall.app.security.IdentityProtocol

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
     * Resolve a caller's display name.
     *
     * For a SecureID caller ([IdentityProtocol.isDirectClientId]) the name comes only from
     * contacts bound to that SecureID (and the phone book entry of the number stored in that
     * contact). The caller-supplied [phoneNumber] is not part of the signed invite and is
     * never used to pick a name, so a caller cannot borrow a phone-book name (#144 review F3).
     *
     * For phone-only records (no SecureID, e.g. older call history) the phone book and
     * phone-number contacts are used as before.
     */
    fun resolveCallerName(
        context: Context,
        clientId: String,
        phoneNumber: String = ""
    ): String = pickCallerName(
        clientId = clientId,
        phoneNumber = phoneNumber,
        contacts = ContactRepository.getAll(context),
        phoneBook = { resolvePhoneNumber(context, it) },
        phoneMatches = { a, b -> PhoneUtils.matches(a, b, context) }
    )

    /** Pure selection logic behind [resolveCallerName]; sources are injected for tests. */
    internal fun pickCallerName(
        clientId: String,
        phoneNumber: String,
        contacts: List<Contact>,
        phoneBook: (String) -> String?,
        phoneMatches: (String, String) -> Boolean
    ): String {
        if (IdentityProtocol.isDirectClientId(clientId)) {
            // By SecureID stored as the contact's primary id
            val byId = contacts.find { it.phoneOrId == clientId }
            if (byId != null && byId.name.isNotBlank() && byId.name != clientId) return byId.name

            // By SecureID linked to a phone contact (BUG-013: prefer that number's phone-book name)
            val bySecureId = contacts.find { it.secureId == clientId }
            if (bySecureId != null && bySecureId.name.isNotBlank()) {
                val storedPhone = bySecureId.phoneOrId
                if (!IdentityProtocol.isDirectClientId(storedPhone)) {
                    phoneBook(storedPhone)?.let { return it }
                }
                return bySecureId.name
            }
            return clientId
        }

        if (phoneNumber.isNotEmpty()) {
            phoneBook(phoneNumber)?.let { return it }
            val byPhone = contacts.find {
                !IdentityProtocol.isDirectClientId(it.phoneOrId) && phoneMatches(it.phoneOrId, phoneNumber)
            }
            if (byPhone != null && byPhone.name.isNotBlank()) return byPhone.name
            return phoneNumber
        }
        return clientId
    }
}
