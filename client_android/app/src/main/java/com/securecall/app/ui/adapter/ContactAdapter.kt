package com.securecall.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.securecall.app.R
import com.securecall.app.data.Contact

class ContactAdapter(
    private val contacts: List<Contact>,
    private val registeredPhones: Set<String> = emptySet(),
    private val onlinePhones: Set<String> = emptySet(),
    private val onCallClick: ((Contact) -> Unit)? = null
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtAvatar: TextView = view.findViewById(R.id.txtAvatar)
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtPhoneOrId: TextView = view.findViewById(R.id.txtPhoneOrId)
        val btnCall: ImageView = view.findViewById(R.id.btnCallContact)
        val badgeSecureCall: ImageView = view.findViewById(R.id.badgeSecureCall)
        val statusDot: View = view.findViewById(R.id.statusDot)
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

        val normalizedPhone = contact.phoneOrId.replace("\\s".toRegex(), "")
        val isSecureCallMember = contact.phoneOrId.startsWith("android-") ||
            registeredPhones.contains(normalizedPhone)
        val isOnline = onlinePhones.contains(normalizedPhone)

        // Show green badge for SecureCall members
        if (isSecureCallMember) {
            holder.badgeSecureCall.visibility = View.VISIBLE
            holder.badgeSecureCall.setColorFilter(
                ContextCompat.getColor(holder.itemView.context, R.color.call_active_green)
            )
        } else {
            holder.badgeSecureCall.visibility = View.GONE
        }

        // Status dot: green=online, red=offline but registered, gray=not SecureCall
        holder.statusDot.visibility = View.VISIBLE
        when {
            isSecureCallMember && isOnline -> {
                holder.statusDot.setBackgroundResource(R.drawable.status_dot_green)
            }
            isSecureCallMember -> {
                holder.statusDot.setBackgroundResource(R.drawable.status_dot_red)
            }
            else -> {
                holder.statusDot.setBackgroundResource(R.drawable.status_dot_gray)
            }
        }

        holder.btnCall.setOnClickListener {
            onCallClick?.invoke(contact)
        }
    }

    override fun getItemCount() = contacts.size
}
