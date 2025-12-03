
    // PATCH 224: Create / Destroy native decoder (stub)
    private var nativeHandle: Int = 0

    fun prepareNativeDecoder(sampleRate: Int, channels: Int) {
        if (nativeHandle != 0) return
        android.util.Log.d("DECODER_CTX", "prepareNativeDecoder(): stub-init")

        // später via JNI: nativeHandle = OpusDecoderInit(...)
        nativeHandle = 1
    }

    fun getNativeHandle(): Int = nativeHandle

    fun freeNativeDecoder() {
        if (nativeHandle == 0) return

        android.util.Log.d("DECODER_CTX", "freeNativeDecoder(): stub-destroy")

        // später via JNI: OpusDecoderDestroy(nativeHandle)
        nativeHandle = 0
    }

    // PATCH 225: placeholder for decoder API
    fun decodeStub(enc: ByteArray): ShortArray {
        android.util.Log.d("DECODER_CTX", "decodeStub(): got size=${enc.size}")
        return ShortArray(480) { 0 }  // silence
    }

    // PATCH 227: consistent PCM frame generation (480 samples)
    fun decodeStub(enc: ByteArray): ShortArray {
        android.util.Log.d("DECODER_CTX", "decodeStub(): size=${enc.size}")

        // später: nativeOpusDecode()
        val pcm = ShortArray(480) { 0 }

        return pcm
    }
