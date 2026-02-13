package com.securecall.app.ghostnet.control

/**
 * BACKEND-51:
 * Erzeugt ControlFrames.
 */
object ControlFrameBuilder {

    const val OPCODE_MUTE     = 0x01
    const val OPCODE_UNMUTE   = 0x02
    const val OPCODE_PING     = 0x03
    const val OPCODE_PONG     = 0x04

    fun build(opcode: Int, payload: ByteArray? = null): ByteArray {
        val p = payload ?: ByteArray(0)
        val out = ByteArray(2 + p.size)
        out[0] = 0x02.toByte()
        out[1] = opcode.toByte()
        for (i in p.indices) out[2 + i] = p[i]
        return out
    }

    fun mute(): ByteArray     = build(OPCODE_MUTE)
    fun unmute(): ByteArray   = build(OPCODE_UNMUTE)
    fun ping(): ByteArray     = build(OPCODE_PING)
    fun pong(): ByteArray     = build(OPCODE_PONG)
}
