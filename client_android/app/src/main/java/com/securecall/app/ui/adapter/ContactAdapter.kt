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
    private val onlineClientIds: Set<String> = emptySet(),
    private val hideOnlineStatus: Boolean = false,
    private val onCallClick: ((Contact) -> Unit)? = null,
    private val onLongClick: ((Contact) -> Unit)? = null
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
        holder.txtName.text = if (contact.isVerified) "${contact.name} \u2713" else contact.name
        // Show phone + SecureID if both are available
        val subtitle = if (contact.secureId != null && !contact.phoneOrId.startsWith("android-")) {
            "${contact.phoneOrId} | ${contact.secureId}"
        } else {
            contact.phoneOrId
        }
        holder.txtPhoneOrId.text = subtitle
        holder.txtAvatar.text = contact.name.firstOrNull()?.uppercase() ?: "?"

        holder.itemView.contentDescription = contact.name

        val normalizedPhone = contact.phoneOrId.replace(Regex("[^0-9+]"), "")
        val effectiveClientId = contact.secureId ?: if (contact.phoneOrId.startsWith("android-")) contact.phoneOrId else null
        val isSecureCallMember = contact.phoneOrId.startsWith("android-") ||
            contact.secureId != null ||
            registeredPhones.contains(normalizedPhone)
        val isOnline = onlinePhones.contains(normalizedPhone) ||
            (effectiveClientId != null && onlineClientIds.contains(effectiveClientId))

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
        // Free tier: hide online status dots entirely (Pro/Premium feature)
        if (hideOnlineStatus) {
            holder.statusDot.visibility = View.GONE
        } else {
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
        }

        holder.btnCall.setOnClickListener {
            onCallClick?.invoke(contact)
        }

        // Short press on entire row also triggers call (same as tapping the call icon)
        holder.itemView.setOnClickListener {
            onCallClick?.invoke(contact)
        }

        // Long-press context menu (Verify/Block/Delete)
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(contact)
            true
        }
    }

    override fun getItemCount() = contacts.size
}
