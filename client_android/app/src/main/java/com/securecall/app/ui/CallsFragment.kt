package com.securecall.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.securecall.app.CallActivity
import com.securecall.app.R
import com.securecall.app.data.CallHistoryRepository
import com.securecall.app.data.CallRecord
import com.securecall.app.ui.adapter.CallHistoryAdapter

class CallsFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View

    companion object {
        private const val TAG = "CallsFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_calls, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(R.id.recyclerCalls)
        emptyState = view.findViewById(R.id.emptyState)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        val ctx = context ?: return
        val records = CallHistoryRepository.getAll(ctx)
        // Show unresolved records immediately, then enrich on background thread
        showRecords(records)
        Thread({
            try {
                var anyUpdated = false
                val enriched = records.map { record ->
                    val name = record.contactName
                    if (name.startsWith("android-") || name.matches(Regex("^[+\\d\\s\\-()]+$"))) {
                        // BUG-013: Use resolveCallerName() which checks BOTH phone book AND SecureCall contacts
                        val clientId = record.contactId ?: ""
                        // Try phoneNumber field first, then contactId, then name as fallback
                        val phoneForLookup = record.phoneNumber
                            ?: (if (clientId.isNotBlank() && !clientId.startsWith("android-")) clientId else "")
                        val phoneFallback = if (phoneForLookup.isNotEmpty()) phoneForLookup else name
                        val resolved = com.securecall.app.data.PhoneBookResolver.resolveCallerName(ctx, clientId, phoneFallback)
                        if (resolved != name && resolved != clientId && !resolved.matches(Regex("^[+\\d\\s\\-()]+$"))) {
                            anyUpdated = true
                            // BUG-013: Persist enriched name so it doesn't flicker on next load
                            val updated = record.copy(contactName = resolved)
                            CallHistoryRepository.update(ctx, updated)
                            updated
                        } else record
                    } else record
                }
                activity?.runOnUiThread {
                    if (isAdded) showRecords(enriched)
                }
                if (anyUpdated) Log.d(TAG, "BUG-013: Enriched and persisted call history names")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enrich call history", e)
            }
        }, "CallHistoryEnrich").start()
    }

    private fun showRecords(records: List<CallRecord>) {
        if (records.isEmpty()) {
            recycler.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            recycler.adapter = CallHistoryAdapter(
                records,
                onItemClick = { record -> callBack(record) },
                onItemLongClick = { record -> showLongPressOptions(record) }
            )
        }
    }

    private fun showLongPressOptions(record: CallRecord) {
        val ctx = context ?: return
        val phoneOrId = record.phoneNumber ?: record.contactId ?: record.contactName
        val displayName = record.contactName

        // Check if contact already exists in SecureCall
        val existingContacts = com.securecall.app.data.ContactRepository.getAll(ctx)
        val existingContact = existingContacts.find { c ->
            c.phoneOrId == phoneOrId ||
            c.secureId == (record.contactId ?: "") ||
            (record.phoneNumber != null && com.securecall.app.data.PhoneUtils.matches(c.phoneOrId, record.phoneNumber!!, ctx))
        }

        val isBlocked = existingContact?.isBlocked == true
        val isSaved = existingContact != null

        val options = mutableListOf<String>()
        if (!isSaved) {
            options.add("Save as Contact")
        }
        options.add(if (isBlocked) "Unblock Number" else "Block Number")
        options.add("Delete from History")

        android.app.AlertDialog.Builder(ctx)
            .setTitle(displayName)
            .setItems(options.toTypedArray()) { _, which ->
                val action = options[which]
                when {
                    action == "Save as Contact" -> saveAsContact(record)
                    action.contains("Block") || action.contains("Unblock") -> toggleBlock(record, existingContact)
                    action == "Delete from History" -> deleteFromHistory(record)
                }
            }
            .show()
    }

    private fun saveAsContact(record: CallRecord) {
        val ctx = context ?: return
        val phone = record.phoneNumber ?: record.contactId ?: ""
        val name = record.contactName
        val secureId = if (record.contactId?.startsWith("android-") == true) record.contactId else null

        // If name is just a number/ID, prompt user for a name
        if (name.startsWith("android-") || name.matches(Regex("^[+\\d\\s\\-()]+$"))) {
            val input = android.widget.EditText(ctx)
            input.hint = "Name, Alias"
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            // EditText stays empty — phone number is shown as subtitle, not in the field
            android.app.AlertDialog.Builder(ctx)
                .setTitle("Save Contact")
                .setMessage(phone)
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val enteredName = input.text.toString().trim().ifEmpty { phone }
                    doSaveContact(enteredName, phone, secureId)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            doSaveContact(name, phone, secureId)
        }
    }

    private fun doSaveContact(name: String, phoneOrId: String, secureId: String?) {
        val ctx = context ?: return
        // Save with phone as primary key, secureId as metadata
        val savePhoneOrId = if (!phoneOrId.startsWith("android-") && phoneOrId.isNotEmpty()) phoneOrId
            else if (secureId != null) secureId else phoneOrId
        val contact = com.securecall.app.data.Contact(
            name = name,
            phoneOrId = savePhoneOrId,
            secureId = if (savePhoneOrId != secureId) secureId else null
        )
        com.securecall.app.data.ContactRepository.save(ctx, contact)
        ContactsFragment.invalidateCache()
        android.widget.Toast.makeText(ctx, "$name saved", android.widget.Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Contact saved from call history: $name -> $savePhoneOrId")
    }

    private fun toggleBlock(record: CallRecord, existingContact: com.securecall.app.data.Contact?) {
        val ctx = context ?: return
        if (existingContact != null) {
            // Toggle block on existing contact
            val updated = existingContact.copy(isBlocked = !existingContact.isBlocked)
            com.securecall.app.data.ContactRepository.update(ctx, updated)
            val msg = if (updated.isBlocked) "${record.contactName} blocked" else "${record.contactName} unblocked"
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Contact ${if (updated.isBlocked) "blocked" else "unblocked"}: ${record.contactName}")
        } else {
            // Create new blocked contact
            val phone = record.phoneNumber ?: record.contactId ?: record.contactName
            val secureId = if (record.contactId?.startsWith("android-") == true) record.contactId else null
            val contact = com.securecall.app.data.Contact(
                name = record.contactName,
                phoneOrId = phone,
                secureId = secureId,
                isBlocked = true
            )
            com.securecall.app.data.ContactRepository.save(ctx, contact)
            android.widget.Toast.makeText(ctx, "${record.contactName} blocked", android.widget.Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Number blocked from call history: ${record.contactName} ($phone)")
        }
        ContactsFragment.invalidateCache()
    }

    private fun deleteFromHistory(record: CallRecord) {
        val ctx = context ?: return
        CallHistoryRepository.delete(ctx, record.id)
        loadHistory()
        android.widget.Toast.makeText(ctx, "Deleted", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun callBack(record: CallRecord) {
        val targetId = record.contactId
        if (targetId.isNullOrBlank()) {
            Log.w(TAG, "No contactId for call history entry: ${record.contactName}")
            return
        }
        if (targetId.startsWith("android-")) {
            // Direct SecureCall ID
            Log.d(TAG, "Calling back: ${record.contactName} ($targetId)")
            val intent = Intent(requireContext(), CallActivity::class.java).apply {
                putExtra("callerName", record.contactName)
                putExtra("phoneNumber", targetId)
            }
            startActivity(intent)
        } else {
            // Phone number — try server lookup first
            val ws = com.securecall.app.net.WebSocketService.instance
            if (ws == null) {
                Log.w(TAG, "WebSocket not connected, can't call back")
                return
            }
            ws.lookupPhone(targetId) { clientId ->
                activity?.runOnUiThread {
                    if (clientId != null) {
                        Log.d(TAG, "Phone resolved: $targetId -> $clientId")
                        val intent = Intent(requireContext(), CallActivity::class.java).apply {
                            putExtra("callerName", record.contactName)
                            putExtra("phoneNumber", clientId)
                        }
                        startActivity(intent)
                    } else {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "${record.contactName} is not on SecureCall",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}
