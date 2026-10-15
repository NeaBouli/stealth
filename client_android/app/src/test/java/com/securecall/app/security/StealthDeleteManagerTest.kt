package com.securecall.app.security

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.securecall.app.net.WebSocketService
import java.io.File
import java.io.IOException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StealthDeleteManagerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var service: WebSocketService
    private lateinit var notifications: NotificationManager
    private lateinit var prefsDir: File
    private lateinit var storage: List<File>
    private val preferences = mutableMapOf<String, SharedPreferences>()
    private val events = mutableListOf<String>()
    private val databases = linkedSetOf("first.db", "second.db")

    @Before
    fun setUp() {
        context = mock()
        service = mock()
        notifications = mock()
        WebSocketService.instance = service
        prefsDir = temp.newFolder("shared_prefs")
        listOf(
            "securecall_contacts.xml", "securecall_call_history.xml",
            "securecall_prefs.xml", "com.securecall.app.free_preferences.xml",
            "securecall_subscription.xml", "backup_only.xml.bak",
        ).forEach { File(prefsDir, it).writeText("synthetic data") }
        storage = listOf("files", "cache", "externalCache", "secondaryExternalCache").map { name ->
            temp.newFolder(name).also { root ->
                File(root, "nested").mkdir()
                File(root, "nested/data").writeText("synthetic data")
            }
        }
        whenever(context.packageName).thenReturn("com.securecall.app.free")
        val appInfo = mock<ApplicationInfo>().apply { dataDir = temp.root.path }
        whenever(context.applicationInfo).thenReturn(appInfo)
        whenever(context.filesDir).thenReturn(storage[0])
        whenever(context.cacheDir).thenReturn(storage[1])
        whenever(context.externalCacheDirs).thenReturn(arrayOf(storage[2], null, storage[3]))
        whenever(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notifications)
        whenever(context.databaseList()).thenAnswer { databases.toTypedArray() }
        whenever(context.deleteDatabase(any())).thenAnswer { invocation ->
            databases.remove(invocation.getArgument<String>(0))
        }
        whenever(context.getSharedPreferences(any(), any())).thenAnswer { invocation ->
            val name = invocation.getArgument<String>(0)
            preferences.getOrPut(name) {
                val prefs = mock<SharedPreferences>()
                val editor = Mockito.mock(SharedPreferences.Editor::class.java, Mockito.RETURNS_SELF)
                whenever(prefs.getString("client_id", null)).thenReturn("sc-synthetic")
                whenever(prefs.edit()).thenReturn(editor)
                whenever(editor.commit()).thenReturn(true)
                prefs
            }
        }
        whenever(context.deleteSharedPreferences(any())).thenAnswer { invocation ->
            val name = invocation.getArgument<String>(0)
            File(prefsDir, "$name.xml").delete()
            File(prefsDir, "$name.xml.bak").delete()
            true
        }
        doAnswer { invocation ->
            val message = JSONObject(invocation.getArgument<String>(0))
            assertEquals("DEREGISTER", message.getString("type"))
            assertEquals("sc-synthetic", message.getString("clientId"))
            assertTrue(prefsDir.listFiles()!!.isNotEmpty())
            events += "deregister"
            null
        }.whenever(service).sendMessage(any())
        doAnswer { events += "disconnect"; null }.whenever(service).clearSession()
    }

    @After
    fun tearDown() {
        WebSocketService.instance = null
    }

    @Test
    fun sharedManagerWipesEveryStorageLocationAndQueuesDeregistrationFirst() {
        val failures = StealthDeleteManager.wipeData(context) { events += "identity" }

        assertTrue(failures.isEmpty())
        assertEquals(listOf("deregister", "disconnect", "identity"), events)
        assertStorageWiped()
        preferences.values.forEach { prefs ->
            verify(prefs.edit()).clear()
            verify(prefs.edit()).commit()
        }
        verify(service).stopSelf()
        verify(notifications).cancelAll()
    }

    @Test
    fun offlineSendCannotPreventIdentityOrLocalDataDeletion() {
        doThrow(IllegalStateException("offline")).whenever(service).sendMessage(any())

        val failures = StealthDeleteManager.wipeData(context) { events += "identity" }

        assertEquals(listOf(StealthDeleteContract.Step.DEREGISTER), failures)
        assertTrue(events.contains("identity"))
        assertStorageWiped()
    }

    @Test
    fun missingServiceAndExternalStorageDoNotPreventWipe() {
        WebSocketService.instance = null
        whenever(context.externalCacheDirs).thenReturn(emptyArray())
        var identityDestroyed = false

        assertTrue(StealthDeleteManager.wipeData(context) { identityDestroyed = true }.isEmpty())

        assertTrue(identityDestroyed)
        assertTrue(prefsDir.listFiles()!!.isEmpty())
        assertTrue(storage.take(2).all { it.listFiles()!!.isEmpty() })
    }

    @Test
    fun keystoreFailureIsReportedWhileOtherArtifactsAreStillDeleted() {
        val failures = StealthDeleteManager.wipeData(context) { throw IOException("keystore unavailable") }

        assertEquals(listOf(StealthDeleteContract.Step.IDENTITY_SIGNING_KEY), failures)
        assertStorageWiped()
    }

    @Test
    fun failedDatabaseDoesNotPreserveOtherDatabasesOrStorage() {
        doReturn(false).whenever(context).deleteDatabase("first.db")

        val failures = StealthDeleteManager.wipeData(context) {}

        assertEquals(listOf(StealthDeleteContract.Step.DATABASES), failures)
        assertEquals(setOf("first.db"), databases)
        assertTrue(prefsDir.listFiles()!!.isEmpty())
        assertTrue(storage.all { it.listFiles()!!.isEmpty() })
    }

    @Test
    fun failedPreferenceCommitStillDeletesItsFilesAndContinuesWipe() {
        val prefs = context.getSharedPreferences("securecall_contacts", Context.MODE_PRIVATE)
        whenever(prefs.edit().commit()).thenReturn(false)

        val failures = StealthDeleteManager.wipeData(context) {}

        assertEquals(listOf(StealthDeleteContract.Step.SHARED_PREFERENCES), failures)
        assertStorageWiped()
    }

    private fun assertStorageWiped() {
        assertTrue(prefsDir.listFiles()!!.isEmpty())
        assertTrue(databases.isEmpty())
        storage.forEach { root ->
            assertTrue(root.isDirectory)
            assertTrue(root.listFiles()!!.isEmpty())
        }
        assertFalse(preferences.keys.any { it.endsWith(".xml") })
    }
}
