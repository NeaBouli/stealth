package com.securecall.app.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.securecall.app.BuildConfig
import com.securecall.app.CallActivity
import com.securecall.app.R
import com.securecall.app.data.Contact
import com.securecall.app.data.ContactRepository
import com.securecall.app.ui.adapter.ContactAdapter

class ContactsFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var searchInput: EditText
    private var allContacts: List<Contact> = emptyList()
    private var registeredPhones: Set<String> = emptySet()
    private var onlinePhones: Set<String> = emptySet()
    private var statusRefreshHandler: android.os.Handler? = null
    private var contactAdapter: ContactAdapter? = null

    companion object {
        private const val TAG = "ContactsFragment"

        // Cache: shared across fragment instances within the same app session
        @Volatile private var cachedContacts: List<Contact>? = null
        @Volatile private var cachedRegisteredPhones: Set<String> = emptySet()
        @Volatile private var cachedOnlinePhones: Set<String> = emptySet()
        @Volatile private var cachedOnlineClientIds: Set<String> = emptySet()
        @Volatile private var cachedClientIdToPhone: Map<String, String> = emptyMap()
        @Volatile private var lastLookupTimestamp: Long = 0L
        private const val LOOKUP_CACHE_TTL = 300_000L // 5 minutes — registration check is expensive (8 batches)
        private const val STATUS_REFRESH_INTERVAL = 15_000L // 15 seconds

        /** Clear cache (e.g., when a new contact is added). */
        fun invalidateCache() {
            cachedContacts = null
            lastLookupTimestamp = 0L // Force BATCH_PHONE_LOOKUP on next visit
        }

        /** BUG-007 fix: Persist registered phones so presence works immediately after app restart. */
        private fun persistRegisteredPhones(context: android.content.Context, phones: Set<String>) {
            val prefs = context.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putStringSet("cached_registered_phones", phones).apply()
        }

        /** BUG-007 fix: Load persisted registered phones on cold start. */
        private fun loadPersistedRegisteredPhones(context: android.content.Context) {
            if (cachedRegisteredPhones.isNotEmpty()) return // Already populated in-memory
            val prefs = context.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
            val saved = prefs.getStringSet("cached_registered_phones", null)
            if (saved != null && saved.isNotEmpty()) {
                cachedRegisteredPhones = saved
                Log.d(TAG, "Loaded ${saved.size} persisted registered phones for immediate presence")
            }
        }
    }

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d(TAG, "READ_CONTACTS permission granted")
                loadContactsAsync(forceRefresh = true)
            } else {
                Log.w(TAG, "READ_CONTACTS permission denied")
                loadContactsAsync(forceRefresh = true) // Still load app contacts
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_contacts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recyclerContacts)
        emptyState = view.findViewById(R.id.emptyState)
        searchInput = view.findViewById(R.id.searchInput)

        recycler.layoutManager = LinearLayoutManager(requireContext())

        // Show "Online status: PRO feature" banner for Free tier
        if (isFreeTier()) {
            val banner = android.widget.TextView(requireContext()).apply {
                text = "\uD83D\uDD12 Online status is a PRO feature"
                setTextColor(android.graphics.Color.parseColor("#888888"))
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 16, 0, 8)
            }
            val parent = recycler.parent as? android.widget.LinearLayout
            parent?.addView(banner, 1) // After search bar, before recycler
        }

        view.findViewById<FloatingActionButton>(R.id.fabAddContact).setOnClickListener {
            showAddContactDialog()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterContacts(s?.toString() ?: "")
                // When text is cleared (e.g. via X button), dismiss keyboard and restore nav
                if (s.isNullOrEmpty() && searchInput.hasFocus()) {
                    searchInput.clearFocus()
                    val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
                }
            }
        })

        // Back press clears search instead of exiting the app
        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                searchInput.setText("")
                searchInput.clearFocus()
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
                isEnabled = false
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            backCallback.isEnabled = hasFocus
        }

        // BUG-007 fix: restore registered phones from SharedPreferences on cold start
        loadPersistedRegisteredPhones(requireContext())

        // Show cached contacts immediately (instant tab switch)
        val cached = cachedContacts
        if (cached != null) {
            allContacts = cached
            registeredPhones = cachedRegisteredPhones
            onlinePhones = cachedOnlinePhones
            filterContacts(searchInput.text?.toString() ?: "")
        }

        // Request contacts permission if not granted
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        } else {
            loadContactsAsync(forceRefresh = cached == null)
        }
    }

    override fun onResume() {
        super.onResume()
        // Only reload from cache on resume — don't re-query phone book or server
        val cached = cachedContacts
        if (cached != null) {
            allContacts = cached
            registeredPhones = cachedRegisteredPhones
            onlinePhones = cachedOnlinePhones
            filterContacts(searchInput.text?.toString() ?: "")
        }
        // Refresh app contacts (cheap — SharedPreferences read) in case a contact was saved
        refreshAppContacts()
        // Online status is a Pro/Premium feature — skip for Free tier
        if (!isFreeTier()) {
            refreshOnlineStatus()
            startStatusRefresh()
        }
    }

    override fun onPause() {
        super.onPause()
        stopStatusRefresh()
    }

    private fun startStatusRefresh() {
        stopStatusRefresh()
        statusRefreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (!isAdded) return
                Log.d(TAG, "Periodic online status refresh")
                refreshOnlineStatus()
                statusRefreshHandler?.postDelayed(this, STATUS_REFRESH_INTERVAL)
            }
        }
        statusRefreshHandler?.postDelayed(runnable, STATUS_REFRESH_INTERVAL)
    }

    private fun stopStatusRefresh() {
        statusRefreshHandler?.removeCallbacksAndMessages(null)
        statusRefreshHandler = null
    }

    /** Lightweight online status check — only checks phones already known to be registered SecureCall users. */
    private fun refreshOnlineStatus() {
        val ws = com.securecall.app.net.WebSocketService.instance ?: return
        // Only check phones we already know are registered (from BATCH_PHONE_LOOKUP).
        // This is typically 1-5 phones, not 1535. Avoids the 500-phone server cap.
        val phones = cachedRegisteredPhones.toList()
        if (phones.isEmpty()) {
            Log.d(TAG, "refreshOnlineStatus: no registered phones cached, skipping")
            return
        }
        Log.d(TAG, "refreshOnlineStatus: ${phones.size} registered phones")
        ws.requestOnlineStatus(phones) { statuses ->
            if (!isAdded) return@requestOnlineStatus
            val onPhones = mutableSetOf<String>()
            for ((phone, online) in statuses) {
                if (online) onPhones.add(phone)
            }
            onlinePhones = onPhones
            cachedOnlinePhones = onPhones
            // Update onlineClientIds from cachedClientIdToPhone mapping
            val onCids = mutableSetOf<String>()
            for ((cid, phone) in cachedClientIdToPhone) {
                val norm = phone.replace(Regex("[^0-9+]"), "")
                if (norm in onPhones) onCids.add(cid)
            }
            cachedOnlineClientIds = onCids
            Log.d(TAG, "Online status updated: ${onPhones.size} online phones, ${onCids.size} online clientIds")
            activity?.runOnUiThread {
                if (isAdded) filterContacts(searchInput.text?.toString() ?: "")
            }
        }
    }

    /** Lightweight refresh: re-read app contacts and merge with cached phone contacts. */
    private fun refreshAppContacts() {
        val ctx = context ?: return
        val appContacts = ContactRepository.getAll(ctx)
        val cached = cachedContacts ?: return
        val phoneContacts = cached.filter { it.isPhoneContact }

        // Exclude phone book contacts that are already covered by app contacts (by phone or secureId)
        val appPhoneNumbers = appContacts
            .filter { !it.phoneOrId.startsWith("android-") }
            .map { it.phoneOrId.replace(Regex("[^0-9+]"), "") }.toSet()
        val appSecureIds = appContacts.mapNotNull { it.secureId }.toSet() +
            appContacts.filter { it.phoneOrId.startsWith("android-") }.map { it.phoneOrId }.toSet()
        val secureIdPhones = cachedClientIdToPhone
            .filter { it.key in appSecureIds }
            .values.map { it.replace(Regex("[^0-9+]"), "") }.toSet()
        val hiddenPhones = ContactRepository.getHiddenPhones(ctx)
        val excludePhones = appPhoneNumbers + secureIdPhones + hiddenPhones

        val uniquePhoneContacts = phoneContacts.filter { pc ->
            pc.phoneOrId.replace(Regex("[^0-9+]"), "") !in excludePhones
        }
        allContacts = appContacts + uniquePhoneContacts
        cachedContacts = allContacts
        filterContacts(searchInput.text?.toString() ?: "")
    }

    /** Load contacts on a background thread. Shows cached data immediately, refreshes async. */
    private fun loadContactsAsync(forceRefresh: Boolean) {
        val ctx = context ?: return
        Thread({
            try {
                val appContacts = ContactRepository.getAll(ctx)
                val phoneContacts = loadPhoneContacts()

                // Exclude phone book contacts already covered by app contacts
                val appPhoneNumbers = appContacts
                    .filter { !it.phoneOrId.startsWith("android-") }
                    .map { it.phoneOrId.replace(Regex("[^0-9+]"), "") }.toSet()
                val appSecureIds = appContacts.mapNotNull { it.secureId }.toSet() +
                    appContacts.filter { it.phoneOrId.startsWith("android-") }.map { it.phoneOrId }.toSet()
                val secureIdPhones = cachedClientIdToPhone
                    .filter { it.key in appSecureIds }
                    .values.map { it.replace(Regex("[^0-9+]"), "") }.toSet()
                val hiddenPhones = ContactRepository.getHiddenPhones(ctx)
                val excludePhones = appPhoneNumbers + secureIdPhones + hiddenPhones

                val uniquePhoneContacts = phoneContacts.filter { pc ->
                    pc.phoneOrId.replace(Regex("[^0-9+]"), "") !in excludePhones
                }
                val merged = appContacts + uniquePhoneContacts
                cachedContacts = merged
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    allContacts = merged
                    registeredPhones = cachedRegisteredPhones
                    onlinePhones = cachedOnlinePhones
                    filterContacts(searchInput.text?.toString() ?: "")
                }
                // Only do BATCH_PHONE_LOOKUP if cache is stale or forced
                val now = System.currentTimeMillis()
                if (forceRefresh || now - lastLookupTimestamp > LOOKUP_CACHE_TTL) {
                    checkSecureCallMembers()
                } else {
                    Log.d(TAG, "BATCH_PHONE_LOOKUP cache still valid (${(now - lastLookupTimestamp) / 1000}s old)")
                    // Still refresh online dots even if registration cache is valid (Pro/Premium only)
                    if (!isFreeTier()) {
                        activity?.runOnUiThread { if (isAdded) refreshOnlineStatus() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load contacts async", e)
            }
        }, "ContactsLoader").start()
    }

    private fun checkSecureCallMembers() {
        val ws = com.securecall.app.net.WebSocketService.instance ?: return
        // Use cachedContacts (set on background thread) instead of allContacts (set on UI thread)
        // to avoid race condition where allContacts is still empty when this runs
        val contactsSnapshot = cachedContacts ?: allContacts
        val phoneNumbers = contactsSnapshot
            .filter { !it.phoneOrId.startsWith("android-") }
            .map { it.phoneOrId.replace(Regex("[^0-9+]"), "") }
        Log.d(TAG, "checkSecureCallMembers: ${contactsSnapshot.size} contacts, ${phoneNumbers.size} phone numbers to check")
        if (phoneNumbers.isEmpty()) return
        // Hash phone numbers for privacy — server never sees raw numbers
        val hashToPhone = mutableMapOf<String, String>()
        val allHashes = phoneNumbers.map { phone ->
            val normalized = phone.replace(Regex("[^0-9+]"), "")
            val hash = sha256(normalized)
            hashToPhone[hash] = phone
            hash
        }
        // Chunk into batches of 200 (server max per request) and send sequentially
        val batches = allHashes.chunked(200)
        if (batches.isEmpty()) return
        val accumulatedRegistered = mutableMapOf<String, Pair<Boolean, String>>() // hash → (online, clientId)

        fun sendBatch(index: Int) {
            if (!isAdded) return
            val currentWs = com.securecall.app.net.WebSocketService.instance
            if (currentWs == null) {
                finalizeResults(accumulatedRegistered, hashToPhone)
                return
            }
            if (index >= batches.size) {
                Log.d(TAG, "Batch phone lookup complete: ${accumulatedRegistered.size} registered across ${batches.size} batches")
                finalizeResults(accumulatedRegistered, hashToPhone)
                return
            }
            val batch = batches[index]
            Log.d(TAG, "Sending batch ${index + 1}/${batches.size} (${batch.size} hashes)")
            currentWs.batchPhoneLookup(batch) { registeredMap ->
                accumulatedRegistered.putAll(registeredMap)
                sendBatch(index + 1)
            }
        }

        sendBatch(0)
    }

    private fun finalizeResults(
        accumulatedRegistered: Map<String, Pair<Boolean, String>>,
        hashToPhone: Map<String, String>
    ) {
        val regPhones = mutableSetOf<String>()
        val onPhones = mutableSetOf<String>()
        val onClientIds = mutableSetOf<String>()
        val cidToPhone = mutableMapOf<String, String>()
        for ((hash, info) in accumulatedRegistered) {
            val (online, clientId) = info
            val phone = hashToPhone[hash] ?: continue
            regPhones.add(phone)
            if (online) onPhones.add(phone)
            if (clientId.isNotEmpty()) {
                cidToPhone[clientId] = phone
                if (online) onClientIds.add(clientId)
            }
        }
        registeredPhones = regPhones
        onlinePhones = onPhones
        cachedRegisteredPhones = regPhones
        cachedOnlinePhones = onPhones
        cachedOnlineClientIds = onClientIds
        cachedClientIdToPhone = cidToPhone
        lastLookupTimestamp = System.currentTimeMillis()
        // BUG-007 fix: persist registered phones for immediate presence after app restart
        val ctx = context
        if (ctx != null) persistRegisteredPhones(ctx, regPhones)
        Log.d(TAG, "Finalized: ${regPhones.size} registered, ${onPhones.size} online phones, ${onClientIds.size} online clientIds, ${cidToPhone.size} clientId→phone mappings")

        // Clean up stale SecureIDs: app contacts with SecureIDs not found in server response
        cleanupStaleSecureIds(cidToPhone.keys)

        // Re-run dedup merge with updated mappings
        dedupAndRefresh()
        // Immediately refresh online status with dedicated endpoint for freshest data (Pro/Premium only)
        if (!isFreeTier()) {
            activity?.runOnUiThread { if (isAdded) refreshOnlineStatus() }
        }
    }

    /**
     * Deduplicate contacts: merge SecureID-only entries into phone-number entries.
     * After BATCH_PHONE_LOOKUP we know clientId → phone mappings.
     * If the same person appears twice (once by phone, once by SecureID), keep the phone entry
     * with the SecureID attached, and delete the SecureID-only entry from storage.
     */
    private fun dedupAndRefresh() {
        val ctx = context ?: return
        val appContacts = ContactRepository.getAll(ctx)
        val cached = cachedContacts ?: return
        val phoneContacts = cached.filter { it.isPhoneContact }

        // Reverse map: phone → clientId
        val phoneToCid = mutableMapOf<String, String>()
        for ((cid, phone) in cachedClientIdToPhone) {
            phoneToCid[phone.replace(Regex("[^0-9+]"), "")] = cid
        }

        // Separate app contacts into SecureID-based and phone-based
        val secureIdContacts = appContacts.filter { it.phoneOrId.startsWith("android-") }
        val phoneAppContacts = appContacts.filter { !it.phoneOrId.startsWith("android-") }

        // Build lookup: normalized phone → phone app contact
        val phoneAppMap = mutableMapOf<String, Contact>()
        for (c in phoneAppContacts) {
            phoneAppMap[c.phoneOrId.replace(Regex("[^0-9+]"), "")] = c
        }

        val secureIdsToDelete = mutableListOf<String>() // contact IDs to delete from storage
        val phoneContactsToUpdate = mutableListOf<Contact>() // phone contacts that get a secureId attached

        for (secContact in secureIdContacts) {
            val clientId = secContact.phoneOrId
            val phone = cachedClientIdToPhone[clientId]?.replace(Regex("[^0-9+]"), "") ?: continue
            val phoneContact = phoneAppMap[phone]
            if (phoneContact != null) {
                // Duplicate found: same person saved by phone AND by SecureID
                // Keep the phone entry, attach the secureId, delete the SecureID entry
                if (phoneContact.secureId != clientId) {
                    val merged = phoneContact.copy(secureId = clientId)
                    phoneContactsToUpdate.add(merged)
                    phoneAppMap[phone] = merged // update local map too
                }
                secureIdsToDelete.add(secContact.id)
                Log.d(TAG, "Dedup merge: '${secContact.name}' SecureID=$clientId → phone entry '${phoneContact.name}' (${phoneContact.phoneOrId})")
            } else {
                // No phone contact for this SecureID — check if we can create one from phone book contacts
                val phoneBookContact = phoneContacts.find {
                    it.phoneOrId.replace(Regex("[^0-9+]"), "") == phone
                }
                if (phoneBookContact != null) {
                    // Replace SecureID entry with phone entry + secureId
                    val merged = secContact.copy(
                        phoneOrId = phoneBookContact.phoneOrId,
                        name = phoneBookContact.name,
                        secureId = clientId
                    )
                    phoneContactsToUpdate.add(merged)
                    phoneAppMap[phone] = merged
                    Log.d(TAG, "Dedup convert: SecureID=$clientId → phone=${phoneBookContact.phoneOrId} (name=${phoneBookContact.name})")
                }
                // If no phone book match either, keep the SecureID entry as-is
            }
        }

        // Persist changes: delete SecureID duplicates and update phone entries with secureId
        if (secureIdsToDelete.isNotEmpty() || phoneContactsToUpdate.isNotEmpty()) {
            var all = ContactRepository.getAll(ctx).toMutableList()
            // Remove duplicates
            all = all.filter { it.id !in secureIdsToDelete }.toMutableList()
            // Update phone contacts with secureId
            for (updated in phoneContactsToUpdate) {
                val idx = all.indexOfFirst { it.id == updated.id }
                if (idx >= 0) all[idx] = updated
            }
            ContactRepository.replaceAll(ctx, all)
            Log.d(TAG, "dedupAndRefresh: deleted ${secureIdsToDelete.size} SecureID dupes, updated ${phoneContactsToUpdate.size} phone entries")
        }

        // Also attach secureIds to phone app contacts that weren't dupes but have a known mapping
        val finalAppContacts = ContactRepository.getAll(ctx).toMutableList()
        var metadataUpdated = false
        for (i in finalAppContacts.indices) {
            val c = finalAppContacts[i]
            if (!c.phoneOrId.startsWith("android-") && c.secureId == null) {
                val norm = c.phoneOrId.replace(Regex("[^0-9+]"), "")
                val cid = phoneToCid[norm]
                if (cid != null) {
                    finalAppContacts[i] = c.copy(secureId = cid)
                    metadataUpdated = true
                }
            }
        }
        if (metadataUpdated) ContactRepository.replaceAll(ctx, finalAppContacts)

        // Rebuild display list
        val freshAppContacts = ContactRepository.getAll(ctx)
        val appPhoneNumbers = freshAppContacts
            .filter { !it.phoneOrId.startsWith("android-") }
            .map { it.phoneOrId.replace(Regex("[^0-9+]"), "") }.toSet()
        val appSecureIds = freshAppContacts.mapNotNull { it.secureId }.toSet() +
            freshAppContacts.filter { it.phoneOrId.startsWith("android-") }.map { it.phoneOrId }.toSet()
        val secureIdPhones = cachedClientIdToPhone
            .filter { it.key in appSecureIds }
            .values.map { it.replace(Regex("[^0-9+]"), "") }.toSet()
        val hiddenPhones = ContactRepository.getHiddenPhones(ctx)
        val excludePhones = appPhoneNumbers + secureIdPhones + hiddenPhones

        val uniquePhoneContacts = phoneContacts.filter { pc ->
            pc.phoneOrId.replace(Regex("[^0-9+]"), "") !in excludePhones
        }

        allContacts = freshAppContacts + uniquePhoneContacts
        cachedContacts = allContacts
        activity?.runOnUiThread {
            if (isAdded) filterContacts(searchInput.text?.toString() ?: "")
        }
    }

    /**
     * Remove stale SecureIDs from contacts.
     * If an app contact is saved only by SecureID (android-*) and that ID is no longer
     * registered on the server, delete it — the device probably reinstalled and has a new ID.
     */
    private fun cleanupStaleSecureIds(activeClientIds: Set<String>) {
        val ctx = context ?: return
        if (activeClientIds.isEmpty()) return // No server data yet, don't delete anything
        val appContacts = ContactRepository.getAll(ctx)
        val staleContacts = appContacts.filter { c ->
            c.phoneOrId.startsWith("android-") && c.phoneOrId !in activeClientIds
        }
        if (staleContacts.isNotEmpty()) {
            Log.d(TAG, "Cleaning up ${staleContacts.size} stale SecureID contacts: ${staleContacts.map { it.phoneOrId }}")
            val remaining = appContacts.filter { it.id !in staleContacts.map { s -> s.id }.toSet() }
            ContactRepository.replaceAll(ctx, remaining)
        }
        // Also clear stale secureId metadata from phone contacts
        val contactsWithStaleSecureId = appContacts.filter { c ->
            c.secureId != null && c.secureId !in activeClientIds
        }
        if (contactsWithStaleSecureId.isNotEmpty()) {
            val current = ContactRepository.getAll(ctx).toMutableList()
            for (i in current.indices) {
                if (current[i].secureId != null && current[i].secureId !in activeClientIds) {
                    current[i] = current[i].copy(secureId = null)
                }
            }
            ContactRepository.replaceAll(ctx, current)
            Log.d(TAG, "Cleared ${contactsWithStaleSecureId.size} stale secureId metadata entries")
        }
    }

    private fun isFreeTier(): Boolean {
        val ctx = context ?: return BuildConfig.FLAVOR == "free"
        return com.securecall.app.config.TierManager.isFreeTier(ctx)
    }

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun loadPhoneContacts(): List<Contact> {
        val ctx = context ?: return emptyList()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val contacts = mutableListOf<Contact>()
        val seen = mutableSetOf<String>()

        try {
            val cursor = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: continue
                    val number = it.getString(numberIdx) ?: continue
                    val normalized = number.replace(Regex("[^0-9+]"), "")
                    if (normalized !in seen) {
                        seen.add(normalized)
                        contacts.add(Contact(name = name, phoneOrId = number, isPhoneContact = true))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load phone contacts", e)
        }

        return contacts
    }

    private fun isContactOnline(contact: Contact): Boolean {
        val normalized = contact.phoneOrId.replace(Regex("[^0-9+]"), "")
        val clientId = contact.secureId ?: if (contact.phoneOrId.startsWith("android-")) contact.phoneOrId else null
        return onlinePhones.contains(normalized) ||
            (clientId != null && cachedOnlineClientIds.contains(clientId))
    }

    private fun isContactRegistered(contact: Contact): Boolean {
        return contact.phoneOrId.startsWith("android-") ||
            contact.secureId != null ||
            !contact.isPhoneContact || // App-saved contacts always show (not from phone book)
            registeredPhones.contains(contact.phoneOrId.replace(Regex("[^0-9+]"), ""))
    }

    private fun filterContacts(query: String) {
        if (query.isEmpty()) {
            // Default: only registered contacts, sorted online first
            val registered = allContacts.filter { isContactRegistered(it) }
            val online = registered.filter { isContactOnline(it) }.sortedBy { it.name }
            val offline = registered.filter { !isContactOnline(it) }.sortedBy { it.name }
            val sorted = online + offline
            updateList(sorted, showRegistrationBadge = false)
        } else {
            // Search: show ALL contacts, with registration status
            // Normalize query for phone number matching (strip spaces, dashes, parens)
            val normalizedQuery = query.replace(Regex("[^0-9+a-zA-Z]"), "")
            val filtered = allContacts.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.phoneOrId.contains(query, ignoreCase = true) ||
                it.phoneOrId.replace(Regex("[^0-9+]"), "").contains(normalizedQuery)
            }.sortedWith(compareByDescending<Contact> { isContactRegistered(it) }.thenBy { it.name })
            updateList(filtered, showRegistrationBadge = true)
        }
    }

    private fun updateList(contacts: List<Contact>, showRegistrationBadge: Boolean = false) {
        if (contacts.isEmpty()) {
            recycler.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            val existing = contactAdapter
            if (existing != null && recycler.adapter === existing) {
                existing.updateData(contacts, registeredPhones, onlinePhones, cachedOnlineClientIds, isFreeTier())
            } else {
                contactAdapter = ContactAdapter(contacts, registeredPhones, onlinePhones, cachedOnlineClientIds, isFreeTier(),
                    onCallClick = { contact -> startCall(contact) },
                    onLongClick = { contact -> showContactMenu(contact) }
                )
                recycler.adapter = contactAdapter
            }
        }
    }

    private fun showContactMenu(contact: Contact) {
        val ctx = context ?: return
        val verifyLabel = if (contact.isVerified) "Verified \u2713" else "\u2705 Verify Contact"
        val blockLabel = if (contact.isBlocked) "\u2705 Unblock Contact" else "\uD83D\uDEAB Block Contact"
        val options = arrayOf(verifyLabel, blockLabel, "\uD83D\uDDD1 Delete Contact")
        android.app.AlertDialog.Builder(ctx)
            .setTitle(contact.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Verify / Unverify
                        // Ensure contact exists in repository (may be phone-book only)
                        com.securecall.app.data.ContactRepository.save(ctx, contact)
                        val updated = contact.copy(isVerified = !contact.isVerified)
                        com.securecall.app.data.ContactRepository.update(ctx, updated)
                        invalidateCache()
                        loadContactsAsync(forceRefresh = true)
                        val msg = if (updated.isVerified) "${contact.name} verified \u2713" else "${contact.name} unverified"
                        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    1 -> { // Block / Unblock
                        com.securecall.app.data.ContactRepository.save(ctx, contact)
                        val updated = contact.copy(isBlocked = !contact.isBlocked)
                        com.securecall.app.data.ContactRepository.update(ctx, updated)
                        invalidateCache()
                        loadContactsAsync(forceRefresh = true)
                        val msg = if (updated.isBlocked) "${contact.name} blocked" else "${contact.name} unblocked"
                        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                    2 -> { // Delete with confirmation
                        android.app.AlertDialog.Builder(ctx)
                            .setTitle("Delete ${contact.name}?")
                            .setMessage("This will permanently remove this contact.")
                            .setPositiveButton("Delete") { _, _ ->
                                // Delete from repository by ID and by phoneOrId (covers all storage paths)
                                com.securecall.app.data.ContactRepository.delete(ctx, contact.id)
                                com.securecall.app.data.ContactRepository.deleteByPhoneOrId(ctx, contact.phoneOrId)
                                // Hide phone number so phone-book contacts don't reappear
                                if (contact.isPhoneContact || !contact.phoneOrId.startsWith("android-")) {
                                    com.securecall.app.data.ContactRepository.hidePhone(ctx, contact.phoneOrId)
                                }
                                invalidateCache()
                                loadContactsAsync(forceRefresh = true)
                                android.widget.Toast.makeText(ctx, "${contact.name} deleted", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startCall(contact: Contact) {
        if (contact.phoneOrId.startsWith("android-") || contact.secureId != null) {
            // Pre-call health check for direct calls too
            val ws = com.securecall.app.net.WebSocketService.instance
            if (ws == null || !ws.isConnected) {
                ws?.forceReconnect()
                android.widget.Toast.makeText(requireContext(), "Reconnecting to server, please try again", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            // SecureCall ID — call directly (use secureId if contact has phone + secureId)
            val callTarget = contact.secureId ?: contact.phoneOrId
            Log.d(TAG, "Starting call to: ${contact.name} via SecureID=$callTarget")
            val intent = Intent(requireContext(), CallActivity::class.java).apply {
                putExtra("callerName", contact.name)
                putExtra("phoneNumber", callTarget)
                if (!contact.phoneOrId.startsWith("android-")) {
                    putExtra("originalPhone", contact.phoneOrId)
                }
            }
            startActivity(intent)
        } else {
            // Phone number — try server lookup first
            Log.d(TAG, "Looking up phone for: ${contact.name}")
            val ws = com.securecall.app.net.WebSocketService.instance
            if (ws == null) {
                android.widget.Toast.makeText(requireContext(), "Connecting to server, please try again", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            // Pre-call health check: if disconnected, trigger reconnect
            if (!ws.isConnected) {
                Log.w(TAG, "WS not connected — triggering reconnect")
                ws.forceReconnect()
                android.widget.Toast.makeText(requireContext(), "Reconnecting to server, please try again", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val normalized = contact.phoneOrId.replace(Regex("[^0-9+]"), "")
            ws.lookupPhone(normalized) { clientId ->
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    if (clientId != null) {
                        Log.d(TAG, "Phone resolved: ${contact.phoneOrId} -> $clientId")
                        val intent = Intent(requireContext(), CallActivity::class.java).apply {
                            putExtra("callerName", contact.name)
                            putExtra("phoneNumber", clientId)
                            putExtra("originalPhone", contact.phoneOrId)
                        }
                        startActivity(intent)
                    } else {
                        // Check if we're actually connected to server
                        val lastSeen = ws.lastSeen()
                        val now = System.currentTimeMillis()
                        if (now - lastSeen > 15000) {
                            android.widget.Toast.makeText(requireContext(), "Server unavailable, please try again", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            showInviteDialog(contact)
                        }
                    }
                }
            }
        }
    }

    private fun showInviteDialog(contact: Contact) {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("securecall_prefs", android.content.Context.MODE_PRIVATE)
        val myId = prefs.getString("client_id", null)
            ?: com.securecall.app.net.WebSocketService.instance?.getLocalClientId()
            ?: "unknown"
        val myPhone = prefs.getString("confirmed_phone_number", null)
            ?: prefs.getString("manual_phone_number", null) ?: ""

        // Build invite link with optional name param
        val nameParam = java.net.URLEncoder.encode(myId.take(16), "UTF-8")
        val inviteLink = "https://stealthx.tech/invite/?id=$myId&name=$nameParam"

        // Build share text
        val shareText = buildString {
            append("I invited you to SecureCall!\n")
            append("\uD83D\uDD12 Tap here to connect:\n")
            append(inviteLink)
            append("\n\nDon\u2019t have SecureCall yet?\n")
            append("https://play.google.com/apps/testing/com.securecall.app.free")
        }

        // Dialog with options
        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }

        val msgView = android.widget.TextView(ctx).apply {
            text = "Send an invite to ${contact.name} so they can install SecureCall and add you as a contact."
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 14f
        }
        layout.addView(msgView)

        val includeNameCheck = android.widget.CheckBox(ctx).apply {
            text = "Include my SecureID in link"
            isChecked = true
            setTextColor(0xFFDDDDDD.toInt())
        }
        layout.addView(includeNameCheck)

        // QR Code button inside layout
        val qrBtn = android.widget.Button(ctx).apply {
            text = "\uD83D\uDD32 Show QR Code"
            textSize = 14f
            setOnClickListener {
                val intent = Intent(ctx, QrCodeActivity::class.java).apply {
                    putExtra("invite_link", inviteLink)
                    putExtra("contact_name", contact.name)
                }
                startActivity(intent)
            }
        }
        layout.addView(qrBtn)

        android.app.AlertDialog.Builder(ctx)
            .setTitle("\uD83D\uDCE9 Invite ${contact.name}")
            .setView(layout)
            .setPositiveButton("Send Invite") { _, _ ->
                val link = if (includeNameCheck.isChecked) inviteLink
                    else "https://stealthx.tech/invite/?id=$myId"
                val text = if (includeNameCheck.isChecked) shareText
                    else "I invited you to SecureCall!\n\uD83D\uDD12 $link"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(intent, "Send Invite"))
            }
            .setNeutralButton("Copy Link") { _, _ ->
                val link = if (includeNameCheck.isChecked) inviteLink
                    else "https://stealthx.tech/invite/?id=$myId"
                val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Invite Link", link))
                android.widget.Toast.makeText(ctx, "Invite link copied!", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddContactDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = resources.getDimensionPixelSize(R.dimen.spacing_lg)
            setPadding(pad, pad, pad, 0)
        }
        val nameInput = EditText(ctx).apply { hint = getString(R.string.contact_name_hint) }
        val idInput = EditText(ctx).apply { hint = getString(R.string.contact_id_hint) }
        layout.addView(nameInput)
        layout.addView(idInput)

        AlertDialog.Builder(ctx)
            .setTitle(R.string.contacts_add)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                val phoneOrId = idInput.text.toString().trim()
                if (name.isNotEmpty() && phoneOrId.isNotEmpty()) {
                    ContactRepository.save(ctx, Contact(name = name, phoneOrId = phoneOrId))
                    invalidateCache()
                    loadContactsAsync(forceRefresh = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
