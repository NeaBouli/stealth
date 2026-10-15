package com.securecall.app.security

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StealthDeleteContractTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun wipeAttemptsEveryRequiredArtifactExactlyOnce() {
        val attempted = mutableListOf<StealthDeleteContract.Step>()

        assertTrue(StealthDeleteContract.run { attempted += it }.isEmpty())

        assertEquals(
            listOf(
                StealthDeleteContract.Step.DEREGISTER,
                StealthDeleteContract.Step.DISCONNECT,
                StealthDeleteContract.Step.IDENTITY_SIGNING_KEY,
                StealthDeleteContract.Step.SHARED_PREFERENCES,
                StealthDeleteContract.Step.DATABASES,
                StealthDeleteContract.Step.FILES,
                StealthDeleteContract.Step.CACHE,
                StealthDeleteContract.Step.EXTERNAL_CACHE,
                StealthDeleteContract.Step.NOTIFICATIONS,
                StealthDeleteContract.Step.CLIPBOARD,
            ),
            attempted,
        )
    }

    @Test
    fun deregistrationCanUseIdentityAndPreferencesBeforeLocalDeletion() {
        var connected = true
        var identityPresent = true
        var preferencesPresent = true
        var attempted = false

        val failures = StealthDeleteContract.run { step ->
            when (step) {
                StealthDeleteContract.Step.DEREGISTER -> {
                    check(connected && identityPresent && preferencesPresent)
                    attempted = true
                }
                StealthDeleteContract.Step.DISCONNECT -> connected = false
                StealthDeleteContract.Step.IDENTITY_SIGNING_KEY -> identityPresent = false
                StealthDeleteContract.Step.SHARED_PREFERENCES -> preferencesPresent = false
                else -> Unit
            }
        }

        assertTrue(failures.isEmpty())
        assertTrue(attempted)
        assertFalse(identityPresent)
        assertFalse(preferencesPresent)
    }

    @Test
    fun offlineDeregistrationStillWipesEveryLocalArtifact() {
        val attempted = mutableListOf<StealthDeleteContract.Step>()

        val failures = StealthDeleteContract.run { step ->
            attempted += step
            if (step == StealthDeleteContract.Step.DEREGISTER) throw IOException("offline")
        }

        assertEquals(listOf(StealthDeleteContract.Step.DEREGISTER), failures)
        assertEquals(StealthDeleteContract.Step.entries, attempted)
    }

    @Test
    fun failureAtAnyStepDoesNotSkipLaterArtifacts() {
        StealthDeleteContract.Step.entries.forEach { failingStep ->
            val attempted = mutableListOf<StealthDeleteContract.Step>()
            val failures = StealthDeleteContract.run { step ->
                attempted += step
                if (step == failingStep) throw IllegalStateException("unavailable")
            }

            assertEquals(listOf(failingStep), failures)
            assertEquals(StealthDeleteContract.Step.entries, attempted)
        }
    }

    @Test
    fun multipleFailuresAreReportedWithoutSkippingRemainingSteps() {
        val failures = StealthDeleteContract.run { throw IOException("unavailable") }

        assertEquals(StealthDeleteContract.Step.entries, failures)
    }

    @Test
    fun requiredPreferencesAreClearedEvenBeforeTheirFirstDiskWrite() {
        assertEquals(
            setOf(
                "securecall_contacts", "securecall_call_history", "securecall_prefs",
                "com.securecall.app.free_preferences",
            ),
            StealthDeleteContract.preferenceNames("com.securecall.app.free", emptyList()),
        )
    }

    @Test
    fun defaultPreferencesFollowTheInstalledPackageForBothDistributions() {
        listOf("com.securecall.app.free", "com.securecall.app.premium").forEach { packageName ->
            assertTrue(
                StealthDeleteContract.preferenceNames(packageName, emptyList())
                    .contains("${packageName}_preferences"),
            )
        }
    }

    @Test
    fun discoversAdditionalPreferencesAndBackupOnlyStoresWithoutDuplicateNames() {
        val discovered = StealthDeleteContract.preferenceNames(
            "com.securecall.app.free",
            listOf(
                "securecall_contacts.xml", "securecall_contacts.xml.bak",
                "securecall_call_history.xml.bak", "securecall_subscription.xml",
                "securecall_direct_entitlement.xml.bak", "third.party.preferences.xml",
                "notes.txt", ".xml", "unrelated.bak",
            ),
        )

        assertEquals(
            setOf(
                "securecall_contacts", "securecall_call_history", "securecall_prefs",
                "com.securecall.app.free_preferences", "securecall_subscription",
                "securecall_direct_entitlement", "third.party.preferences",
            ),
            discovered,
        )
    }

    @Test
    fun aFailingPreferenceOrDatabaseDoesNotPreventOtherArtifactsInThatGroup() {
        val attempted = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            StealthDeleteContract.attemptAll(listOf("first", "second", "third")) { name ->
                attempted += name
                if (name == "first") throw IOException("unavailable")
            }
        }

        assertEquals(listOf("first", "second", "third"), attempted)
    }

    @Test
    fun removesNestedFilesAndCachesWhilePreservingStorageRoots() {
        val roots = listOf("files", "cache", "externalCache").map { temp.newFolder(it) }
        roots.forEach { root ->
            File(root, "nested/deeper").mkdirs()
            File(root, "nested/deeper/payload").writeText("synthetic test data")
            File(root, "top-level").writeText("synthetic test data")
            StealthDeleteContract.deleteContents(root)
        }

        roots.forEach { root ->
            assertTrue(root.isDirectory)
            assertTrue(root.listFiles()!!.isEmpty())
        }
    }

    @Test
    fun absentAndAlreadyEmptyStorageAreSafeToWipeRepeatedly() {
        val empty = temp.newFolder("empty")
        val absent = File(temp.root, "absent")

        repeat(2) {
            StealthDeleteContract.deleteContents(empty)
            StealthDeleteContract.deleteContents(absent)
        }

        assertTrue(empty.isDirectory)
        assertFalse(absent.exists())
    }

    @Test
    fun unreadableStorageIsReportedRatherThanDeclaredWiped() {
        val nonDirectory = temp.newFile("not-a-directory")

        assertThrows(IOException::class.java) {
            StealthDeleteContract.deleteContents(nonDirectory)
        }
    }
}
