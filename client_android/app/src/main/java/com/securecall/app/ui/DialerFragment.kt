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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.securecall.app.CallActivity
import com.securecall.app.R
import com.securecall.app.data.ContactRepository

class DialerFragment : Fragment() {

    private lateinit var phoneDisplay: EditText
    private lateinit var btnBackspace: ImageButton
    private var phoneNumber = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_dialer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        phoneDisplay = view.findViewById(R.id.phoneNumberDisplay)
        btnBackspace = view.findViewById(R.id.btnBackspace)

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

    private fun updateDisplay() {
        phoneDisplay.setText(phoneNumber.toString())
        btnBackspace.visibility = if (phoneNumber.isNotEmpty()) View.VISIBLE else View.GONE
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
