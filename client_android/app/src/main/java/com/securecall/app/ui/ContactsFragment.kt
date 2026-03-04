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
        private const val STATUS_REFRESH_INTERVAL = 30_000L // 30 seconds

        /** Clear cache (e.g., when a new contact is added). */
        fun invalidateCache() {
            cachedContacts = null
            lastLookupTimestamp = 0L // Force BATCH_PHONE_LOOKUP on next visit
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

        // Show cached contacts immediately (instant tab switch)
        val cached = cachedContacts
        if (cached != null) {
            allContacts = cached
            registeredPhones = cachedRegisteredPhones
            onlinePhones = cachedOnlinePhones
            updateList(allContacts)
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
            updateList(allContacts)
        }
        // Refresh app contacts (cheap — SharedPreferences read) in case a contact was saved
        refreshAppContacts()
        // Fire immediate online status check, then start periodic refresh (every 30s)
        refreshOnlineStatus()
        startStatusRefresh()
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
                if (isAdded) updateList(allContacts)
            }
        }
    }

    /** Lightweight refresh: re-read app contacts and merge with cached phone contacts. */
    private fun refreshAppContacts() {
        val ctx = context ?: return
        val appContacts = ContactRepository.getAll(ctx)
        val cached = cachedContacts ?: return
        val phoneContacts = cached.filter { it.isPhoneContact }

        // Build set of phone numbers covered by app contacts' SecureCall IDs
        val appSecureIds = appContacts.filter { it.phoneOrId.startsWith("android-") }.map { it.phoneOrId }.toSet()
        val phonesCoveredBySecureId = cachedClientIdToPhone
            .filter { it.key in appSecureIds }
            .values.map { it.replace(Regex("[^0-9+]"), "") }.toSet()

        // Dedup app contacts: remove phone-number entries covered by SecureCall ID entries
        val dedupedAppContacts = appContacts.filter { ac ->
            ac.phoneOrId.startsWith("android-") ||
                ac.phoneOrId.replace(Regex("[^0-9+]"), "") !in phonesCoveredBySecureId
        }

        val appPhoneNumbers = dedupedAppContacts
            .filter { !it.phoneOrId.startsWith("android-") }
            .map { it.phoneOrId.replace(Regex("[^0-9+]"), "") }.toSet()
        val excludePhones = appPhoneNumbers + phonesCoveredBySecureId

        val uniquePhoneContacts = phoneContacts.filter { pc ->
            pc.phoneOrId.replace(Regex("[^0-9+]"), "") !in excludePhones
        }
        allContacts = dedupedAppContacts + uniquePhoneContacts
        cachedContacts = allContacts
        updateList(allContacts)
    }

    /** Load contacts on a background thread. Shows cached data immediately, refreshes async. */
    private fun loadContactsAsync(forceRefresh: Boolean) {
        val ctx = context ?: return
        Thread({
            try {
                val appContacts = ContactRepository.getAll(ctx)
                val phoneContacts = loadPhoneContacts()

                // Build set of phone numbers covered by app contacts' SecureCall IDs
                val appSecureIds = appContacts.filter { it.phoneOrId.startsWith("android-") }.map { it.phoneOrId }.toSet()
                val phonesCoveredBySecureId = cachedClientIdToPhone
                    .filter { it.key in appSecureIds }
                    .values.map { it.replace(Regex("[^0-9+]"), "") }.toSet()

                // Dedup app contacts: remove phone-number entries covered by SecureCall ID entries
                val dedupedAppContacts = appContacts.filter { ac ->
                    ac.phoneOrId.startsWith("android-") ||
                        ac.phoneOrId.replace(Regex("[^0-9+]"), "") !in phonesCoveredBySecureId
                }

                val appPhoneNumbers = dedupedAppContacts
                    .filter { !it.phoneOrId.startsWith("android-") }
                    .map { it.phoneOrId.replace(Regex("[^0-9+]"), "") }.toSet()
                val excludePhones = appPhoneNumbers + phonesCoveredBySecureId

                val uniquePhoneContacts = phoneContacts.filter { pc ->
                    pc.phoneOrId.replace(Regex("[^0-9+]"), "") !in excludePhones
                }
                val merged = dedupedAppContacts + uniquePhoneContacts
                cachedContacts = merged
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    allContacts = merged
                    registeredPhones = cachedRegisteredPhones
                    onlinePhones = cachedOnlinePhones
                    updateList(allContacts)
                }
                // Only do BATCH_PHONE_LOOKUP if cache is stale or forced
                val now = System.currentTimeMillis()
                if (forceRefresh || now - lastLookupTimestamp > LOOKUP_CACHE_TTL) {
                    checkSecureCallMembers()
                } else {
                    Log.d(TAG, "BATCH_PHONE_LOOKUP cache still valid (${(now - lastLookupTimestamp) / 1000}s old)")
                    // Still refresh online dots even if registration cache is valid
                    activity?.runOnUiThread { if (isAdded) refreshOnlineStatus() }
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
        Log.d(TAG, "Finalized: ${regPhones.size} registered, ${onPhones.size} online phones, ${onClientIds.size} online clientIds, ${cidToPhone.size} clientId→phone mappings")
        // Re-run dedup merge with updated mappings
        dedupAndRefresh()
    }

    /** Re-merge contacts removing phone duplicates that have a matching app contact (by SecureCall ID). */
    private fun dedupAndRefresh() {
        val ctx = context ?: return
        val appContacts = ContactRepository.getAll(ctx)
        val cached = cachedContacts ?: return
        val phoneContacts = cached.filter { it.isPhoneContact }

        // Build set of phone numbers covered by app contacts' SecureCall IDs
        val appSecureIds = appContacts.filter { it.phoneOrId.startsWith("android-") }.map { it.phoneOrId }.toSet()
        val phonesCoveredBySecureId = cachedClientIdToPhone
            .filter { it.key in appSecureIds }
            .values.map { it.replace(Regex("[^0-9+]"), "") }.toSet()

        // Dedup app contacts: remove phone-number app contacts covered by a SecureCall ID app contact
        val dedupedAppContacts = appContacts.filter { ac ->
            if (ac.phoneOrId.startsWith("android-")) {
                true // Keep SecureCall ID contacts
            } else {
                val norm = ac.phoneOrId.replace(Regex("[^0-9+]"), "")
                val covered = norm in phonesCoveredBySecureId
                if (covered) Log.d(TAG, "Dedup: '${ac.name}' phone covered by SecureCall ID")
                !covered
            }
        }

        val appPhoneNumbers = dedupedAppContacts
            .filter { !it.phoneOrId.startsWith("android-") }
            .map { it.phoneOrId.replace(Regex("[^0-9+]"), "") }.toSet()
        val excludePhones = appPhoneNumbers + phonesCoveredBySecureId

        val uniquePhoneContacts = phoneContacts.filter { pc ->
            pc.phoneOrId.replace(Regex("[^0-9+]"), "") !in excludePhones
        }
        if (appContacts.size != dedupedAppContacts.size || phoneContacts.size != uniquePhoneContacts.size) {
            Log.d(TAG, "dedupAndRefresh: ${appContacts.size} app -> ${dedupedAppContacts.size}, ${phoneContacts.size} phone -> ${uniquePhoneContacts.size}")
        }
        allContacts = dedupedAppContacts + uniquePhoneContacts
        cachedContacts = allContacts
        activity?.runOnUiThread {
            if (isAdded) updateList(allContacts)
        }
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

    private fun filterContacts(query: String) {
        if (query.isEmpty()) {
            updateList(allContacts)
        } else {
            val filtered = allContacts.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.phoneOrId.contains(query, ignoreCase = true)
            }
            updateList(filtered)
        }
    }

    private fun updateList(contacts: List<Contact>) {
        if (contacts.isEmpty()) {
            recycler.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            recycler.adapter = ContactAdapter(contacts, registeredPhones, onlinePhones, cachedOnlineClientIds) { contact ->
                startCall(contact)
            }
        }
    }

    private fun startCall(contact: Contact) {
        if (contact.phoneOrId.startsWith("android-")) {
            // Pre-call health check for direct calls too
            val ws = com.securecall.app.net.WebSocketService.instance
            if (ws == null || !ws.isConnected) {
                ws?.forceReconnect()
                android.widget.Toast.makeText(requireContext(), "Reconnecting to server, please try again", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            // SecureCall ID — call directly
            Log.d(TAG, "Starting call to: ${contact.name}")
            val intent = Intent(requireContext(), CallActivity::class.java).apply {
                putExtra("callerName", contact.name)
                putExtra("phoneNumber", contact.phoneOrId)
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
        val myPhone = prefs.getString("confirmed_phone_number", null) ?: ""
        val base = getString(R.string.dialer_invite_sms, myId)
        val message = if (myPhone.isNotEmpty()) "$base\nMy phone: $myPhone" else base
        val items = arrayOf(
            getString(R.string.dialer_share_link),
            getString(R.string.dialer_send_sms)
        )
        android.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.dialer_invite_message, contact.name))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.dialer_invite_via)))
                    }
                    1 -> {
                        try {
                            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("smsto:${contact.phoneOrId}")
                                putExtra("sms_body", message)
                            }
                            startActivity(smsIntent)
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(ctx, getString(R.string.dialer_no_sms_app), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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
