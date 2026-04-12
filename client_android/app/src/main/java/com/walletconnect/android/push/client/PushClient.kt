package com.walletconnect.android.push.client

import com.walletconnect.android.push.PushInterface

/**
 * Stub singleton for WalletConnect PushClient.
 *
 * CoreProtocol's constructor accesses PushClient.INSTANCE to initialize
 * its Echo and Push fields. Without this stub, the class loader throws
 * NoClassDefFoundError and CoreClient.initialize() fails entirely.
 *
 * This no-op object satisfies the static field reference:
 *   getstatic com/walletconnect/android/push/client/PushClient.INSTANCE
 */
object PushClient : PushInterface
