package com.securecall.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.securecall.app.security.IdentityProtocol
import com.securecall.app.security.IdentitySigningKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IdentitySigningKeyInstrumentedTest {
    @Test
    fun identityKeyIsStableNonExportableAndSignsOnlyCanonicalProtocolTranscript() {
        val first = IdentitySigningKey.ensureIdentity()
        assertNotNull(first)
        assertEquals(first, IdentitySigningKey.existingIdentity())

        val transcript = IdentityProtocol.registrationTranscript(
            UUID.randomUUID().toString(),
            first!!.identityId,
            first.identityId,
            first.keyHash,
            System.currentTimeMillis() / 1000 + 300,
            IdentityProtocol.randomNonce(),
        )
        assertNotNull(transcript)
        assertNotNull(IdentitySigningKey.sign(transcript!!))
        assertNull(IdentitySigningKey.sign("not-a-securecall-transcript"))

        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = store.getKey(IdentitySigningKey.ALIAS, null)
        assertNotNull(privateKey)
        assertNull("AndroidKeyStore private material must not be exportable", privateKey.encoded)
        assertTrue(store.containsAlias(IdentitySigningKey.ALIAS))
    }
}
