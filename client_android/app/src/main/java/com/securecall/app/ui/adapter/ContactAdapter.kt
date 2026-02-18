package com.securecall.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.securecall.app.R
import com.securecall.app.data.Contact

class ContactAdapter(
    private val contacts: List<Contact>,
    private val onCallClick: ((Contact) -> Unit)? = null
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtAvatar: TextView = view.findViewById(R.id.txtAvatar)
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtPhoneOrId: TextView = view.findViewById(R.id.txtPhoneOrId)
        val btnCall: ImageView = view.findViewById(R.id.btnCallContact)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.txtName.text = contact.name
        holder.txtPhoneOrId.text = contact.phoneOrId
        holder.txtAvatar.text = contact.name.firstOrNull()?.uppercase() ?: "?"

        holder.itemView.contentDescription = contact.name

        holder.btnCall.setOnClickListener {
            onCallClick?.invoke(contact)
        }

        holder.itemView.setOnClickListener {
            onCallClick?.invoke(contact)
        }
    }

    override fun getItemCount() = contacts.size
}
