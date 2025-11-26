#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>

// CRYPTO-03 JNI Skeleton
// Später: Bindings auf Rust-CoreCrypto (via cbindgen generiertes Headerfile)

JNIEXPORT jbyteArray JNICALL
Java_com_securecall_crypto_CoreCrypto_encrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray data
) {
    // Placeholder: gibt Input 1:1 zurück
    jsize len = (*env)->GetArrayLength(env, data);
    jbyteArray out = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, out, 0, len,
                               (*env)->GetByteArrayElements(env, data, NULL));
    return out;
}

JNIEXPORT jbyteArray JNICALL
Java_com_securecall_crypto_CoreCrypto_decrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray data
) {
    // Placeholder: gleiche Logik wie encrypt()
    jsize len = (*env)->GetArrayLength(env, data);
    jbyteArray out = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, out, 0, len,
                               (*env)->GetByteArrayElements(env, data, NULL));
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_securecall_crypto_CoreCrypto_selfTest(
    JNIEnv* env, jclass clazz
) {
    // Placeholder: funktioniert immer
    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_securecall_crypto_CoreCrypto_deriveSessionKey(
    JNIEnv* env, jclass clazz, jbyteArray localPriv, jbyteArray remotePub
) {
    // Placeholder: gibt 32 Bytes Null zurück
    jbyteArray out = (*env)->NewByteArray(env, 32);
    jbyte zeros[32] = {0};
    (*env)->SetByteArrayRegion(env, out, 0, 32, zeros);
    return out;
}
