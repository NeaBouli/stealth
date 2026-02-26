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
        val records = CallHistoryRepository.getAll(requireContext())
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
