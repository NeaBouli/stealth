package com.walletconnect.android.push

/**
 * Stub interface for WalletConnect PushClient.
 *
 * android-core references this interface for Echo/Push fields in CoreProtocol.
 * The actual push-client module is not bundled (SecureCall doesn't use push
 * notifications via WalletConnect). This stub satisfies the class loader so
 * CoreClient.initialize() completes without NoClassDefFoundError.
 */
interface PushInterface
