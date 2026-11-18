# Call protocol 3 rollout note

Calls from the current app use the signed call protocol 3 (authenticated media).
Older apps use protocol 2. The two cannot interoperate; both parties need the
current app for a protected call.

Required order:

1. Deploy a signaling server that accepts protocols 2 and 3 **before** shipping
   the new app. An older server rejects protocol-3 invitations with a generic
   error that the new app cannot explain better.
2. Ship the new app. It advertises its supported call protocol on authenticated
   registration. This is an advisory routing hint, not a security guarantee; the
   signed invite/accept protocol checks remain authoritative.
3. Never roll the server back to a protocol-2-only build once the new app has
   shipped. Roll back only to a build that still accepts protocol 3.

Behaviour in mixed versions:

- New caller to an online old callee: the server answers immediately with
  `peer_media_upgrade_required`; the invite is not forwarded.
- New caller to an offline callee: delivery via push is unchanged. If the callee
  then registers without protocol 3, the server ends the pending call for the
  caller with reason `media_upgrade_required`.
- Old caller to a new callee: the new app rejects the protocol-2 invite and ends
  the call with `media_upgrade_required`. Already released old apps show their
  existing generic call-ended/error behaviour, not a new message.
- Old to old and new to new calls are unchanged.

Runtime interoperability across released builds has not been tested by this note.
