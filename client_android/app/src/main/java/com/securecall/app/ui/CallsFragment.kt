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
            recycler.adapter = CallHistoryAdapter(records) { record ->
                callBack(record)
            }
        }
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
