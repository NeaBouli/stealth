package com.securecall.app.ghostnet.crypto

import com.securecall.app.ghostnet.frame.header.FrameFlags
import com.securecall.app.ghostnet.frame.header.FrameHeaderV1
import org.junit.Assert.*
import org.junit.Test

class SessionCipherEngineTest {

    private fun makeCtx() = SessionCipherContext(
        sessionId = "test-session",
        keyId = 1,
        rxKey = ByteArray(32) { 0xAA.toByte() },
        txKey = ByteArray(32) { 0xBB.toByte() }
    )

    @Test
    fun encrypt_withoutNative_returnsPlaintext() {
        val plain = "hello world".toByteArray()
        val result = SessionCipherEngine.encrypt(makeCtx(), plain)
        assertArrayEquals(plain, result)
    }

    @Test
    fun decrypt_withoutNative_returnsCiphertext() {
        val cipher = "encrypted data".toByteArray()
        val result = SessionCipherEngine.decrypt(makeCtx(), cipher)
        assertArrayEquals(cipher, result)
    }

    @Test
    fun buildFrameHeaderV1_produces4Bytes() {
        val header = SessionCipherEngine.buildFrameHeaderV1(makeCtx(), 0x01, 256L)
        assertEquals(4, header.size)
    }

    @Test
    fun buildFrameHeaderV1_hasCorrectVersion() {
        val header = SessionCipherEngine.buildFrameHeaderV1(makeCtx(), 0x01, 1L)
        assertEquals(FrameHeaderV1.VERSION.toByte(), header[0])
    }

    @Test
    fun buildFrameHeaderV1_hasCorrectFlags() {
        val header = SessionCipherEngine.buildFrameHeaderV1(makeCtx(), 0x02, 1L)
        assertEquals(0x02.toByte(), header[1])
    }

    @Test
    fun buildFrameHeaderV1_hasCorrectKeyId() {
        val ctx = makeCtx() // keyId = 1
        val header = SessionCipherEngine.buildFrameHeaderV1(ctx, 0x01, 1L)
        assertEquals(1.toByte(), header[2])
    }

    @Test
    fun encryptFrameV1_withoutNative_returnsHeaderPlusPlain() {
        val plain = ByteArray(10) { it.toByte() }
        val result = SessionCipherEngine.encryptFrameV1(makeCtx(), plain, 0x01)
        assertEquals(4 + plain.size, result.size)
    }

    @Test
    fun decryptFrameV1_tooShort_returnsInput() {
        val short = ByteArray(3)
        val result = SessionCipherEngine.decryptFrameV1(makeCtx(), short)
        assertArrayEquals(short, result)
    }

    @Test
    fun encryptAudioFrameV1_usesAudioFlag() {
        val result = SessionCipherEngine.encryptAudioFrameV1(makeCtx(), "audio".toByteArray())
        assertEquals(FrameFlags.AUDIO.toByte(), result[1])
    }

    @Test
    fun encryptControlFrameV1_usesControlFlag() {
        val result = SessionCipherEngine.encryptControlFrameV1(makeCtx(), "ctrl".toByteArray())
        assertEquals(FrameFlags.CONTROL.toByte(), result[1])
    }

    @Test
    fun encryptKeepAliveFrameV1_usesKeepAliveFlag() {
        val result = SessionCipherEngine.encryptKeepAliveFrameV1(makeCtx(), "ka".toByteArray())
        assertEquals(FrameFlags.KEEPALIVE.toByte(), result[1])
    }
}
