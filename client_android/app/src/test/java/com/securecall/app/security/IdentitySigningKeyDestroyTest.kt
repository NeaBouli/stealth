package com.securecall.app.security

import java.security.KeyStore
import java.security.KeyStoreException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IdentitySigningKeyDestroyTest {
    @Test
    fun destroyRemovesOnlyTheSignalingAlias() {
        val store = mock<KeyStore>()
        Mockito.mockStatic(KeyStore::class.java).use { factory ->
            factory.`when`<KeyStore> { KeyStore.getInstance("AndroidKeyStore") }.thenReturn(store)

            IdentitySigningKey.destroy()

            verify(store).load(null)
            verify(store).deleteEntry("securecall_signaling_identity_v2")
            Mockito.verifyNoMoreInteractions(store)
        }
    }

    @Test
    fun keystoreFailureIsVisibleToTheWipeContract() {
        val store = mock<KeyStore>()
        doThrow(KeyStoreException("unavailable")).whenever(store).deleteEntry(IdentitySigningKey.ALIAS)
        Mockito.mockStatic(KeyStore::class.java).use { factory ->
            factory.`when`<KeyStore> { KeyStore.getInstance("AndroidKeyStore") }.thenReturn(store)

            assertThrows(KeyStoreException::class.java) { IdentitySigningKey.destroy() }
        }
    }
}
