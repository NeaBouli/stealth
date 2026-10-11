package com.securecall.app.call

import com.securecall.app.data.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

/** #144 review F4: phone targets of SecureID-linked contacts are called by identity. */
class CallTargetPolicyTest {
    private val mom = "sc-" + "M".repeat(43)
    private val other = "sc-" + "O".repeat(43)
    private val phone = "+306900000001"
    private fun resolve(target: String, contacts: List<Contact>) =
        CallTargetPolicy.resolve(target, contacts) { a, b -> a == b }

    @Test
    fun linkedPhoneContactIsCalledByIdentity() {
        assertEquals(mom, resolve(phone, listOf(Contact(name = "Mom", phoneOrId = phone, secureId = mom))))
    }

    @Test
    fun unlinkedPhoneStaysPhone() {
        assertEquals(phone, resolve(phone, listOf(Contact(name = "Mom", phoneOrId = phone))))
        assertEquals(phone, resolve(phone, emptyList()))
    }

    @Test
    fun ambiguousLinksAreNotGuessed() {
        val contacts = listOf(
            Contact(name = "A", phoneOrId = phone, secureId = mom),
            Contact(name = "B", phoneOrId = phone, secureId = other),
        )
        assertEquals(phone, resolve(phone, contacts))
    }

    @Test
    fun identityTargetIsUnchanged() {
        assertEquals(mom, resolve(mom, listOf(Contact(name = "X", phoneOrId = phone, secureId = other))))
    }

    @Test
    fun invalidLinkedIdIsIgnored() {
        assertEquals(phone, resolve(phone, listOf(Contact(name = "Mom", phoneOrId = phone, secureId = "android-old01"))))
    }
}
