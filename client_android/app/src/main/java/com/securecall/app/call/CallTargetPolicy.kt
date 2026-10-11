package com.securecall.app.call

import com.securecall.app.data.Contact
import com.securecall.app.security.IdentityProtocol

/**
 * #144 review F4: the server's phone -> identity map is not verified, so a phone target could
 * be answered by whoever last registered that number. When a saved contact for the dialled
 * number is already linked to a SecureID, call that identity instead: the invite then names
 * the SecureID and the caller pins the authenticated acceptor to it.
 */
object CallTargetPolicy {
    fun resolve(
        targetId: String,
        contacts: List<Contact>,
        phoneMatches: (String, String) -> Boolean
    ): String {
        if (IdentityProtocol.isCanonicalIdentityId(targetId)) return targetId
        if (IdentityProtocol.isDirectClientId(targetId)) {
            return contacts.firstNotNullOfOrNull { contact ->
                contact.secureId?.takeIf { contact.phoneOrId == targetId && IdentityProtocol.isCanonicalIdentityId(it) }
            } ?: targetId
        }
        val linked = contacts.mapNotNull { contact ->
            contact.secureId?.takeIf {
                IdentityProtocol.isCanonicalIdentityId(it)
                    && !IdentityProtocol.isDirectClientId(contact.phoneOrId)
                    && phoneMatches(contact.phoneOrId, targetId)
            }
        }.distinct()
        // Ambiguous links are left to the server rather than guessing one identity.
        return linked.singleOrNull() ?: targetId
    }
}
