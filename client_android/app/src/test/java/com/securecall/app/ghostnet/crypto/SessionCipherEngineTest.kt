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

    @Test(expected = SecurityException::class)
    fun encrypt_withoutNative_throwsSecurityException() {
        val plain = "hello world".toByteArray()
        SessionCipherEngine.encrypt(makeCtx(), plain)
    }

    @Test(expected = SecurityException::class)
    fun decrypt_withoutNative_throwsSecurityException() {
        val cipher = "encrypted data".toByteArray()
        SessionCipherEngine.decrypt(makeCtx(), cipher)
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

    @Test(expected = SecurityException::class)
    fun encryptFrameV1_withoutNative_throwsSecurityException() {
        val plain = ByteArray(10) { it.toByte() }
        SessionCipherEngine.encryptFrameV1(makeCtx(), plain, 0x01)
    }

    @Test
    fun decryptFrameV1_tooShort_returnsInput() {
        val short = ByteArray(3)
        val result = SessionCipherEngine.decryptFrameV1(makeCtx(), short)
        assertArrayEquals(short, result)
    }

    @Test(expected = SecurityException::class)
    fun encryptAudioFrameV1_withoutNative_throwsSecurityException() {
        SessionCipherEngine.encryptAudioFrameV1(makeCtx(), "audio".toByteArray())
    }

    @Test(expected = SecurityException::class)
    fun encryptControlFrameV1_withoutNative_throwsSecurityException() {
        SessionCipherEngine.encryptControlFrameV1(makeCtx(), "ctrl".toByteArray())
    }

    @Test(expected = SecurityException::class)
    fun encryptKeepAliveFrameV1_withoutNative_throwsSecurityException() {
        SessionCipherEngine.encryptKeepAliveFrameV1(makeCtx(), "ka".toByteArray())
    }
}
