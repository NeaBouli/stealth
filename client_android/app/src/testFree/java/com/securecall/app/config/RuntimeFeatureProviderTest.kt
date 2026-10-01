package com.securecall.app.config

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RuntimeFeatureProviderTest {
    @Test
    fun `certificate pinning remains enabled for the free runtime tier`() {
        val preferences: SharedPreferences = mock()
        val context: Context = mock()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.packageName).thenReturn("com.securecall.app.free")
        whenever(context.getSharedPreferences("securecall_subscription", Context.MODE_PRIVATE))
            .thenReturn(preferences)

        assertTrue(RuntimeFeatureProvider(context).certificatePinning)
    }
}
