package com.securecall.app.ui

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.securecall.app.BuildConfig
import com.securecall.app.R
import com.securecall.app.net.WebSocketService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SettingsFragmentActivationCodeTest {
    private val settings = Mockito.mock(SettingsFragment::class.java, Answers.CALLS_REAL_METHODS)
    private val codePreference: EditTextPreference = mock()
    private val activateButton: Preference = mock()
    private val context: Context = mock()
    private val service: WebSocketService = mock()
    private val toast: Toast = mock()
    private lateinit var toasts: MockedStatic<Toast>
    private var previousService: WebSocketService? = null

    @Before
    fun setUp() {
        previousService = WebSocketService.instance
        WebSocketService.instance = service
        doReturn(codePreference).whenever(settings).findPreference<EditTextPreference>("pref_activation_code")
        doReturn(activateButton).whenever(settings).findPreference<Preference>("pref_activate_button")
        doReturn(context).whenever(settings).requireContext()
        doReturn("Activate Code").whenever(settings).getString(R.string.pref_activate)
        doReturn("Enter code to unlock Pro or Premium").whenever(settings)
            .getString(R.string.pref_activation_code_summary)
        doReturn("Invalid activation code").whenever(settings).getString(R.string.activation_error_invalid)
        doReturn("Connect before activating").whenever(settings).getString(R.string.activation_error_connection)
        whenever(service.isConnected).thenReturn(true)
        toasts = Mockito.mockStatic(Toast::class.java)
        toasts.`when`<Toast> {
            Toast.makeText(eq(context), any<CharSequence>(), any())
        }.thenReturn(toast)
    }

    @After
    fun tearDown() {
        toasts.close()
        WebSocketService.instance = previousService
    }

    @Test
    fun confirmingCodeSubmitsImmediatelyWithoutPersistingRawInput() {
        assumeDirectActivation()
        val listener = activationListener()

        assertFalse(listener.onPreferenceChange(codePreference, "  PRO-1111-2222-3333  "))

        verify(service).activateCode(eq("PRO-1111-2222-3333"), any())
        verify(codePreference).text = ""
        verify(activateButton).isEnabled = false
        verify(activateButton).summary = "Validating\u2026"
        toasts.verify { Toast.makeText(context, "Validating\u2026", Toast.LENGTH_SHORT) }
        verify(toast).show()
    }

    @Test
    fun blankInputShowsPromptAndDoesNotSubmitOrPersist() {
        assumeDirectActivation()
        val listener = activationListener()

        for (input in listOf("", " \t\n ")) {
            assertFalse(listener.onPreferenceChange(codePreference, input))
        }

        verify(service, never()).activateCode(any(), any())
        verify(codePreference, times(2)).text = ""
        toasts.verify(
            { Toast.makeText(context, "Enter an activation code first", Toast.LENGTH_SHORT) },
            times(2)
        )
        verify(toast, times(2)).show()
    }

    @Test
    fun premiumAndTesterCodesStillReachTheExistingSubmitPath() {
        assumeDirectActivation()
        val listener = activationListener()

        for (code in listOf("PREMIUM-1111-2222-3333", "SC-PREM-1111-2222-3333")) {
            assertFalse(listener.onPreferenceChange(codePreference, code))
            verify(service).activateCode(eq(code), any())
        }
    }

    @Test
    fun disconnectedSubmitStillClearsInputAndShowsConnectionError() {
        assumeDirectActivation()
        whenever(service.isConnected).thenReturn(false)

        assertFalse(activationListener().onPreferenceChange(codePreference, "PRO-1111-2222-3333"))

        verify(codePreference).text = ""
        verify(service, never()).activateCode(any(), any())
        toasts.verify { Toast.makeText(context, "Connect before activating", Toast.LENGTH_SHORT) }
        verify(toast).show()
    }

    @Test
    fun failedValidationShowsExistingResultAndEnablesRetry() {
        assumeDirectActivation()
        val activity: FragmentActivity = mock()
        doReturn(activity).whenever(settings).activity
        doReturn(true).whenever(settings).isAdded
        doAnswer { invocation ->
            invocation.getArgument<Runnable>(0).run()
            null
        }.whenever(activity).runOnUiThread(any())
        activationListener().onPreferenceChange(codePreference, "PRO-1111-2222-3333")
        val callback = argumentCaptor<(Boolean, String, String) -> Unit>()
        verify(service).activateCode(eq("PRO-1111-2222-3333"), callback.capture())

        callback.firstValue(false, "", "invalid")

        toasts.verify { Toast.makeText(context, "Invalid activation code", Toast.LENGTH_LONG) }
        verify(activateButton, times(2)).isEnabled = true
        verify(activateButton, times(2)).summary = "Enter code to unlock Pro or Premium"
    }

    @Test
    fun activateEntryOpensTheSameCodeDialog() {
        assumeDirectActivation()
        doNothing().whenever(settings).onDisplayPreferenceDialog(any())
        configureActivationCode("FREE")
        val listener = argumentCaptor<Preference.OnPreferenceClickListener>()
        verify(activateButton).setOnPreferenceClickListener(listener.capture())

        assertTrue(listener.firstValue.onPreferenceClick(activateButton))

        verify(settings).onDisplayPreferenceDialog(codePreference)
        verify(service, never()).activateCode(any(), any())
    }

    @Test
    fun disabledFreeActivationHidesBothEntriesWithoutRegisteringListeners() {
        assumeTrue(BuildConfig.FLAVOR == "free")
        assertFalse(BuildConfig.ACTIVATION_CODE_ENABLED)
        assertFalse(BuildConfig.TESTER_LICENSE_ENABLED)

        for (tier in listOf("FREE", "PRO", "PREMIUM")) {
            configureActivationCode(tier)
        }

        verify(codePreference, times(3)).isVisible = false
        verify(activateButton, times(3)).isVisible = false
        verify(codePreference, never()).setOnPreferenceChangeListener(any())
        verify(activateButton, never()).setOnPreferenceClickListener(any())
        verify(service, never()).activateCode(any(), any())
    }

    private fun assumeDirectActivation() {
        assumeTrue(BuildConfig.FLAVOR in setOf("pro", "premium"))
        assertTrue(BuildConfig.ACTIVATION_CODE_ENABLED)
    }

    private fun activationListener(): Preference.OnPreferenceChangeListener {
        configureActivationCode("FREE")
        val listener = argumentCaptor<Preference.OnPreferenceChangeListener>()
        verify(codePreference).setOnPreferenceChangeListener(listener.capture())
        return listener.firstValue
    }

    private fun configureActivationCode(tier: String) {
        SettingsFragment::class.java.getDeclaredMethod("configureActivationCode", String::class.java).apply {
            isAccessible = true
            invoke(settings, tier)
        }
    }
}
