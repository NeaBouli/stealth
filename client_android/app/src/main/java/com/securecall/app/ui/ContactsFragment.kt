package com.securecall.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.securecall.app.R
import com.securecall.app.data.Contact
import com.securecall.app.data.ContactRepository
import com.securecall.app.ui.adapter.ContactAdapter

class ContactsFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var searchInput: EditText
    private var allContacts: List<Contact> = emptyList()

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
            }
        })

        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
    }

    private fun loadContacts() {
        allContacts = ContactRepository.getAll(requireContext())
        updateList(allContacts)
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
            recycler.adapter = ContactAdapter(contacts)
        }
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
