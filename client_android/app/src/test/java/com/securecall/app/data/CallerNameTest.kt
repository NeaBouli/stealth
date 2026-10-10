package com.securecall.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** #144 review F3: the unsigned callerPhone must never select a display name for a SecureID caller. */
class CallerNameTest {
    private val attacker = "sc-" + "A".repeat(43)
    private val friend = "sc-" + "B".repeat(43)
    private val momPhone = "+306900000001"
    private val phoneBook = mapOf(momPhone to "Mom")

    private fun pick(clientId: String, phone: String, contacts: List<Contact> = emptyList()) =
        PhoneBookResolver.pickCallerName(
            clientId = clientId,
            phoneNumber = phone,
            contacts = contacts,
            phoneBook = { phoneBook[it] },
            phoneMatches = { a, b -> a == b }
        )

    @Test
    fun spoofedCallerPhoneDoesNotBorrowPhoneBookName() {
        assertEquals(attacker, pick(attacker, momPhone))
    }

    @Test
    fun spoofedCallerPhoneDoesNotBorrowPhoneContactName() {
        val contacts = listOf(Contact(name = "Mom", phoneOrId = momPhone))
        assertEquals(attacker, pick(attacker, momPhone, contacts))
    }

    @Test
    fun contactBoundToSecureIdWins() {
        val contacts = listOf(Contact(name = "Friend", phoneOrId = friend))
        assertEquals("Friend", pick(friend, momPhone, contacts))
    }

    @Test
    fun linkedPhoneContactUsesItsOwnStoredNumber() {
        val contacts = listOf(Contact(name = "Mom (app)", phoneOrId = momPhone, secureId = friend))
        assertEquals("Mom", pick(friend, "+300000000000", contacts))
    }

    @Test
    fun phoneOnlyHistoryRecordsKeepPhoneBookLookup() {
        assertEquals("Mom", pick("", momPhone))
        assertEquals("+300000000000", pick("", "+300000000000"))
    }
}
