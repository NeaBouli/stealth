package com.securecall.app

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.securecall.app.data.Contact
import com.securecall.app.data.ContactRepository
import com.securecall.app.security.IdentityProtocol
import com.securecall.app.ui.ContactsFragment
import java.security.MessageDigest
import org.hamcrest.Matchers.anything
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactLookupPreservationInstrumentedTest {
    @get:Rule
    val testName = TestName()

    private lateinit var context: Context
    private lateinit var fragment: ContactsFragment
    private var scenario: ActivityScenario<SettingsActivity>? = null
    private var ownsFixture = false
    private var hadRegisteredPhones = false
    private var previousRegisteredPhones: Set<String>? = null
    private val previousCache = mutableMapOf<String, Any?>()
    private val ownedDialogRoots = mutableSetOf<View>()

    private val direct = Contact(
        id = "lookup-fixture-direct", name = "Lookup direct", phoneOrId = DIRECT_ID,
        createdAt = 1L, isVerified = true,
    )
    private val linked = Contact(
        id = "lookup-fixture-linked", name = "Lookup linked", phoneOrId = LINKED_PHONE,
        createdAt = 2L, secureId = LINKED_ID, isBlocked = true,
    )
    private val positive = Contact(
        id = "lookup-fixture-positive", name = "Lookup positive", phoneOrId = POSITIVE_PHONE,
        createdAt = 3L,
    )
    private val positiveHash = hash(POSITIVE_PHONE)
    private val successfulResult = mapOf(positiveHash to Pair(true, POSITIVE_ID))
    private val positiveQuery = mapOf(positiveHash to POSITIVE_PHONE)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        if (testName.methodName == "syntheticContactWindowMatrix") {
            ContactVisualMatrix.admit(context)
            ContactVisualMatrix.diagnose(ContactVisualMatrix.Phase.FIXTURE_SETUP) { prepareContactFixture() }
        } else {
            prepareContactFixture()
        }
    }

    private fun prepareContactFixture() {
        val contactsPrefs = context.getSharedPreferences("securecall_contacts", Context.MODE_PRIVATE)
        // Refuse to replace existing data; this suite is for a fresh hosted-CI app sandbox.
        assertTrue("Contact fixture requires empty app storage", contactsPrefs.all.isEmpty())
        assertTrue(IdentityProtocol.isDirectClientId(DIRECT_ID))
        assertTrue(IdentityProtocol.isDirectClientId(LINKED_ID))
        assertTrue(IdentityProtocol.isDirectClientId(POSITIVE_ID))
        val prefs = context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)
        hadRegisteredPhones = prefs.contains("cached_registered_phones")
        previousRegisteredPhones = prefs.getStringSet("cached_registered_phones", null)?.toSet()
        ownsFixture = true

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CACHE_FIELDS.forEach { name -> previousCache[name] = cacheField(name).get(null) }
            resetCache(emptyList())
        }
        launchContactHost()
    }

    private fun launchContactHost() {
        scenario = ActivityScenario.launch(Intent(context, SettingsActivity::class.java))
        scenario!!.onActivity { activity ->
            fragment = ContactsFragment()
            activity.supportFragmentManager.executePendingTransactions()
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, fragment)
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED)
                .commitNow()

            // Keep onViewCreated/onResume from starting phonebook loads or permission requests.
            // Bind the real themed layout only for the result path's synchronous list refresh.
            val container = activity.findViewById<ViewGroup>(R.id.settingsContainer)
            val view = fragment.onCreateView(activity.layoutInflater, container, null)
            val recycler = view.findViewById<RecyclerView>(R.id.recyclerContacts)
            recycler.layoutManager = LinearLayoutManager(activity)
            setFragmentField("recycler", recycler)
            setFragmentField("emptyState", view.findViewById<View>(R.id.emptyState))
            setFragmentField("searchInput", view.findViewById<EditText>(R.id.searchInput))
            container.addView(view)
            assertEquals(Lifecycle.State.CREATED, fragment.lifecycle.currentState)
            assertTrue(fragment.isAdded)
        }
    }

    @After
    fun tearDown() {
        if (testName.methodName == "syntheticContactWindowMatrix") {
            ContactVisualMatrix.diagnose(ContactVisualMatrix.Phase.FIXTURE_CLEANUP) { cleanUpContactFixture() }
        } else {
            cleanUpContactFixture()
        }
    }

    private fun cleanUpContactFixture() {
        try {
            try {
                dismissOwnedDialogs()
            } finally {
                scenario?.close()
            }
        } finally {
            try {
                if (ownsFixture) {
                    assertTrue(context.getSharedPreferences("securecall_contacts", Context.MODE_PRIVATE)
                        .edit().clear().commit())
                    val editor = context.getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE).edit()
                    if (hadRegisteredPhones) {
                        editor.putStringSet("cached_registered_phones", previousRegisteredPhones)
                    } else {
                        editor.remove("cached_registered_phones")
                    }
                    assertTrue(editor.commit())
                }
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    previousCache.forEach { (name, value) -> cacheField(name).set(null, value) }
                }
            }
        }
    }

    @Test
    fun nonemptyLookupPreservesUnrelatedDirectIdContact() {
        seed(direct, linked, positive)

        finalizeResults(successfulResult, positiveQuery)

        assertEquals(direct, savedContacts().getValue(direct.id))
        assertEquals(3, savedContacts().size)
    }

    @Test
    fun nonemptyLookupPreservesSecureIdMetadataAbsentFromResults() {
        seed(direct, linked, positive)

        finalizeResults(successfulResult, positiveQuery)

        assertEquals(linked, savedContacts().getValue(linked.id))
        assertEquals(LINKED_ID, savedContacts().getValue(linked.id).secureId)
    }

    @Test
    fun nonemptyPartialCoverageDoesNotRevokeUnreturnedIdentities() {
        seed(direct, linked, positive)
        val partialQuery = positiveQuery + (hash(LINKED_PHONE) to LINKED_PHONE)

        // This is also the accumulator shape when a later batch fails or the service disappears.
        finalizeResults(successfulResult, partialQuery)

        assertSaved(direct, linked, positive.copy(secureId = POSITIVE_ID))
        assertEquals(setOf(POSITIVE_PHONE), context
            .getSharedPreferences("securecall_prefs", Context.MODE_PRIVATE)
            .getStringSet("cached_registered_phones", emptySet()))
    }

    @Test
    fun emptyFailureShapedResultAfterSuccessPreservesSavedAssociations() {
        seed(direct, linked, positive)
        finalizeResults(successfulResult, positiveQuery)

        // The result boundary receives an empty map for failed/denied lookups, not revocations.
        finalizeResults(emptyMap(), positiveQuery + (hash(LINKED_PHONE) to LINKED_PHONE))

        assertSaved(direct, linked, positive.copy(secureId = POSITIVE_ID))
    }

    @Test
    fun positivePhoneAssociationStillPersistsWithoutChangingOtherFields() {
        seed(direct, linked, positive)

        finalizeResults(successfulResult, positiveQuery)

        assertEquals(positive.copy(secureId = POSITIVE_ID), savedContacts().getValue(positive.id))
        assertSaved(direct, linked, positive.copy(secureId = POSITIVE_ID))
    }

    @Test
    fun positiveDuplicateMergeStillRemovesOnlyTheMatchedDirectIdEntry() {
        val duplicate = Contact(
            id = "lookup-fixture-duplicate", name = "Lookup duplicate", phoneOrId = POSITIVE_ID,
            createdAt = 4L,
        )
        seed(direct, linked, positive, duplicate)

        finalizeResults(successfulResult, positiveQuery)

        assertSaved(direct, linked, positive.copy(secureId = POSITIVE_ID))
    }

    @Test
    fun explicitUserDeleteStillRequiresConfirmationAndRemovesOnlyChosenContact() {
        seed(direct, linked, positive)
        finalizeResults(successfulResult, positiveQuery)

        openDeleteConfirmation(linked)
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        assertSaved(direct, linked, positive.copy(secureId = POSITIVE_ID))

        openDeleteConfirmation(linked)
        scenario!!.onActivity { activity ->
            // The confirmation retains its real storage context. Detaching only prevents its
            // subsequent async reload from reading the device phonebook in this storage test.
            activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        }
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

        assertSaved(direct, positive.copy(secureId = POSITIVE_ID))
        assertEquals(setOf(LINKED_PHONE), ContactRepository.getHiddenPhones(context))
    }

    @Test
    fun syntheticContactWindowMatrix() {
        ContactVisualMatrix.run(
            context = context,
            fixtures = listOf(direct, linked, positive),
            positiveId = positive.id,
            positiveSecureId = POSITIVE_ID,
            closeHost = {
                scenario?.close()
                scenario = null
            },
            launchHost = { launchContactHost() },
            withHost = { action -> scenario!!.onActivity { action(it) } },
            seed = { contacts -> seed(*contacts.toTypedArray()) },
            partialSnapshot = {
                finalizeResults(successfulResult, positiveQuery + (hash(LINKED_PHONE) to LINKED_PHONE))
            },
        )
    }

    private fun seed(vararg contacts: Contact) {
        scenario!!.onActivity {
            ContactRepository.replaceAll(context, contacts.toList())
            resetCache(contacts.toList())
        }
        assertSaved(*contacts)
    }

    private fun finalizeResults(
        results: Map<String, Pair<Boolean, String>>,
        queriedPhones: Map<String, String>,
    ) {
        scenario!!.onActivity {
            ContactsFragment::class.java.getDeclaredMethod(
                "finalizeResults", Map::class.java, Map::class.java,
            ).apply {
                isAccessible = true
                invoke(fragment, results, queriedPhones)
            }
        }
    }

    private fun openDeleteConfirmation(contact: Contact) {
        scenario!!.onActivity {
            ContactsFragment::class.java.getDeclaredMethod("showContactMenu", Contact::class.java).apply {
                isAccessible = true
                invoke(fragment, contact)
            }
        }
        rememberDialogRoot(contact.name)
        onData(anything()).inRoot(isDialog()).atPosition(2).perform(click())
        rememberDialogRoot("Delete ${contact.name}?")
    }

    private fun rememberDialogRoot(title: String) {
        onView(withText(title)).inRoot(isDialog()).check { view, error ->
            matches(isDisplayed()).check(view, error)
            ownedDialogRoots.add(checkNotNull(view).rootView)
        }
    }

    private fun dismissOwnedDialogs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            try {
                ownedDialogRoots.toList().asReversed().forEach { root ->
                    if (root.isAttachedToWindow) {
                        // Cleanup only: never select a positive/delete button, even after failure.
                        val cancel = checkNotNull(root.findViewById<View>(android.R.id.button2))
                        assertTrue("Owned dialog must handle cancellation", cancel.performClick())
                    }
                }
            } finally {
                ownedDialogRoots.clear()
            }
        }
        // Negative-button dismissal is queued by AlertDialog; drain it before closing the host.
        instrumentation.waitForIdleSync()
    }

    private fun savedContacts(): Map<String, Contact> =
        ContactRepository.getAll(context).associateBy { it.id }

    private fun assertSaved(vararg contacts: Contact) {
        assertEquals(contacts.associateBy { it.id }, savedContacts())
    }

    private fun setFragmentField(name: String, value: Any) {
        ContactsFragment::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(fragment, value)
        }
    }

    private fun resetCache(contacts: List<Contact>) {
        val values = mapOf(
            "cachedContacts" to contacts,
            "cachedRegisteredPhones" to emptySet<String>(),
            "cachedOnlinePhones" to emptySet<String>(),
            "cachedOnlineClientIds" to emptySet<String>(),
            "cachedClientIdToPhone" to emptyMap<String, String>(),
            "lastLookupTimestamp" to 0L,
        )
        values.forEach { (name, value) -> cacheField(name).set(null, value) }
    }

    private fun cacheField(name: String) = ContactsFragment::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }

    private fun hash(phone: String): String = MessageDigest.getInstance("SHA-256")
        .digest(phone.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        val DIRECT_ID = "sc-" + "A".repeat(43)
        val LINKED_ID = "sc-" + "B".repeat(43)
        val POSITIVE_ID = "sc-" + "C".repeat(43)
        const val LINKED_PHONE = "+15555550102"
        const val POSITIVE_PHONE = "+15555550101"
        val CACHE_FIELDS = listOf(
            "cachedContacts", "cachedRegisteredPhones", "cachedOnlinePhones",
            "cachedOnlineClientIds", "cachedClientIdToPhone", "lastLookupTimestamp",
        )
    }
}
