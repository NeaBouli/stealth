@file:JvmName("PushModuleKt")
package com.walletconnect.android.internal.common.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Stub for WalletConnect PushModule Koin registration.
 *
 * android-core 1.28.0 moved this to the push-client module, but CoreProtocol.initialize()
 * still calls PushModuleKt.pushModule() to register Koin DI bindings. This stub returns
 * an empty Koin module so initialization completes without NoClassDefFoundError.
 */
fun pushModule(): Module = module { }
