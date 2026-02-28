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

    companion object {
        private const val TAG = "ContactsFragment"
    }

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d(TAG, "READ_CONTACTS permission granted")
                loadContacts()
            } else {
                Log.w(TAG, "READ_CONTACTS permission denied")
                loadContacts() // Still load app contacts
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

        // Hide bottom nav and main FAB when search is focused so contacts stay visible above keyboard
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            val activity = activity ?: return@setOnFocusChangeListener
            val bottomNav = activity.findViewById<View>(R.id.bottomNav)
            val fab = activity.findViewById<View>(R.id.fabNewCall)
            if (hasFocus) {
                bottomNav?.visibility = View.GONE
                fab?.visibility = View.GONE
            } else {
                bottomNav?.visibility = View.VISIBLE
                fab?.visibility = View.VISIBLE
            }
        }

        // Request contacts permission if not granted
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        } else {
            loadContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
    }

    private fun loadContacts() {
        val appContacts = ContactRepository.getAll(requireContext())
        val phoneContacts = loadPhoneContacts()
        // Merge: app contacts first, then phone contacts not already in app contacts
        val appPhoneNumbers = appContacts.map { it.phoneOrId.replace("\\s".toRegex(), "") }.toSet()
        val uniquePhoneContacts = phoneContacts.filter { pc ->
            pc.phoneOrId.replace("\\s".toRegex(), "") !in appPhoneNumbers
        }
        allContacts = appContacts + uniquePhoneContacts
        updateList(allContacts)
        // Check which contacts are registered SecureCall users
        checkSecureCallMembers()
    }

    private fun checkSecureCallMembers() {
        val ws = com.securecall.app.net.WebSocketService.instance ?: return
        val phoneNumbers = allContacts
            .filter { !it.phoneOrId.startsWith("android-") }
            .map { it.phoneOrId.replace("\\s".toRegex(), "") }
        if (phoneNumbers.isEmpty()) return
        ws.batchPhoneLookup(phoneNumbers) { registered ->
            registeredPhones = registered
            activity?.runOnUiThread {
                if (isAdded) updateList(allContacts)
            }
        }
    }

    private fun loadPhoneContacts(): List<Contact> {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val contacts = mutableListOf<Contact>()
        val seen = mutableSetOf<String>()

        try {
            val cursor = requireContext().contentResolver.query(
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
                    val normalized = number.replace("\\s".toRegex(), "")
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
            recycler.adapter = ContactAdapter(contacts, registeredPhones) { contact ->
                startCall(contact)
            }
        }
    }

    private fun startCall(contact: Contact) {
        if (contact.phoneOrId.startsWith("android-")) {
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
            val normalized = contact.phoneOrId.replace(Regex("[^0-9+]"), "")
            ws.lookupPhone(normalized) { clientId ->
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    if (clientId != null) {
                        Log.d(TAG, "Phone resolved: ${contact.phoneOrId} -> $clientId")
                        val intent = Intent(requireContext(), CallActivity::class.java).apply {
                            putExtra("callerName", contact.name)
                            putExtra("phoneNumber", clientId)
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
        val message = getString(R.string.dialer_invite_sms)
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
                    loadContacts()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
