package com.securecall.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.securecall.app.CallActivity
import com.securecall.app.R
import com.securecall.app.data.Contact
import com.securecall.app.data.ContactRepository
import com.securecall.app.ui.adapter.ContactAdapter

class DialerFragment : Fragment() {

    private lateinit var phoneDisplay: EditText
    private lateinit var btnBackspace: ImageButton
    private lateinit var contactSuggestions: RecyclerView
    private lateinit var dialPad: View
    private var phoneNumber = StringBuilder()
    private var allContacts: List<Contact> = emptyList()

    // T9 mapping: digit → letters
    private val t9Map = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_dialer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        phoneDisplay = view.findViewById(R.id.phoneNumberDisplay)
        btnBackspace = view.findViewById(R.id.btnBackspace)
        contactSuggestions = view.findViewById(R.id.contactSuggestions)
        dialPad = view.findViewById(R.id.dialPad)

        contactSuggestions.layoutManager = LinearLayoutManager(requireContext())

        // Load contacts
        allContacts = ContactRepository.getAll(requireContext())

        // Wire dial pad buttons
        val dialButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7,
            R.id.btn8, R.id.btn9, R.id.btnStar, R.id.btnHash
        )

        for (id in dialButtons) {
            view.findViewById<Button>(id).setOnClickListener { btn ->
                val digit = (btn as Button).tag.toString()
                phoneNumber.append(digit)
                updateDisplay()
            }
        }

        // Long-press 0 for +
        view.findViewById<Button>(R.id.btn0).setOnLongClickListener {
            phoneNumber.append("+")
            updateDisplay()
            true
        }

        // Backspace
        btnBackspace.setOnClickListener {
            if (phoneNumber.isNotEmpty()) {
                phoneNumber.deleteCharAt(phoneNumber.length - 1)
                updateDisplay()
            }
        }

        // Long-press backspace to clear all
        btnBackspace.setOnLongClickListener {
            phoneNumber.clear()
            updateDisplay()
            true
        }

        // Call button
        view.findViewById<FloatingActionButton>(R.id.fabCall).setOnClickListener {
            handleCall()
        }
    }

    override fun onResume() {
        super.onResume()
        allContacts = ContactRepository.getAll(requireContext())
        if (phoneNumber.isNotEmpty()) filterContacts()
    }

    private fun updateDisplay() {
        phoneDisplay.setText(phoneNumber.toString())
        btnBackspace.visibility = if (phoneNumber.isNotEmpty()) View.VISIBLE else View.GONE
        filterContacts()
    }

    private fun filterContacts() {
        val digits = phoneNumber.toString()
        if (digits.isEmpty()) {
            contactSuggestions.visibility = View.GONE
            return
        }

        val matches = allContacts.filter { contact ->
            // Match by phone number (contains typed digits)
            val normalizedPhone = normalizePhone(contact.phoneOrId)
            normalizedPhone.contains(digits) ||
            // Match by T9 name search
            matchesT9(contact.name, digits)
        }

        if (matches.isNotEmpty()) {
            contactSuggestions.adapter = ContactAdapter(matches) { contact ->
                // Start call with selected contact
                val intent = Intent(requireContext(), CallActivity::class.java).apply {
                    putExtra("callerName", contact.name)
                    putExtra("phoneNumber", contact.phoneOrId)
                }
                startActivity(intent)
            }
            contactSuggestions.visibility = View.VISIBLE
        } else {
            contactSuggestions.visibility = View.GONE
        }
    }

    private fun matchesT9(name: String, digits: String): Boolean {
        val nameLower = name.lowercase()
        // Convert name to T9 digits and check if typed digits are a prefix
        val nameDigits = nameLower.map { ch ->
            t9Map.entries.find { it.value.contains(ch) }?.key ?: ch
        }.joinToString("")
        return nameDigits.startsWith(digits) || nameDigits.contains(digits)
    }

    private fun handleCall() {
        val number = phoneNumber.toString().trim()
        if (number.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.dialer_enter_number), Toast.LENGTH_SHORT).show()
            return
        }

        // Check if a saved contact matches this number
        val contacts = ContactRepository.getAll(requireContext())
        val normalized = normalizePhone(number)
        val match = contacts.find { normalizePhone(it.phoneOrId) == normalized }

        if (match != null) {
            // Known contact — start encrypted call
            val intent = Intent(requireContext(), CallActivity::class.java).apply {
                putExtra("callerName", match.name)
                putExtra("phoneNumber", match.phoneOrId)
            }
            startActivity(intent)
        } else {
            // Unknown number — offer invite
            showInviteDialog(number)
        }
    }

    private fun showInviteDialog(number: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialer_invite_title))
            .setMessage(getString(R.string.dialer_invite_message, number))
            .setPositiveButton(getString(R.string.dialer_send_sms)) { _, _ ->
                sendSmsInvite(number)
            }
            .setNegativeButton(getString(R.string.dialer_share_link)) { _, _ ->
                shareInviteLink()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun sendSmsInvite(number: String) {
        val message = getString(R.string.dialer_invite_sms)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            putExtra("sms_body", message)
        }
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), getString(R.string.dialer_no_sms_app), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareInviteLink() {
        val message = getString(R.string.dialer_invite_share)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.dialer_invite_via)))
    }

    private fun normalizePhone(number: String): String {
        return number.replace(Regex("[^0-9+]"), "")
    }
}
