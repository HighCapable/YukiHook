/*
 * YukiHook - A modern hook API and Xposed module solution.
 * Copyright (C) 2019 HighCapable
 * https://github.com/HighCapable/YukiHook
 *
 * Apache License Version 2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is created by fankes on 2026/9/6.
 */
@file:Suppress("SameParameterValue")

package com.highcapable.yukihookapi.runtime.libxposed

import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.highcapable.yukihookapi.runtime.bridge.FrameworkServiceBridge
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.libxposed.service.YukiHookServiceBridge
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArraySet
import io.github.libxposed.service.HookedTarget as NativeHookedTarget

/**
 * Owns the asynchronous module service connection independently of the host hook API.
 */
internal object LibXposedService {

    private const val RUNTIME_API_VERSION = XposedService.API_101
    private const val HOT_RELOAD_API_VERSION = XposedService.API_102

    private val mainHandler = Handler(Looper.getMainLooper())

    private val services = IdentityHashMap<IBinder, ServiceConnection>()
    private val listeners = CopyOnWriteArraySet<ServiceSubscription>()

    private val service get() = synchronized(services) { services.values.singleOrNull()?.service }

    private var initialized = false

    val isAvailable get() = runCatching { service?.apiVersion?.let { it >= RUNTIME_API_VERSION } ?: false }.getOrDefault(false)
    val apiVersion get() = runCatching { service?.apiVersion }.getOrNull() ?: -1
    val frameworkName get() = runCatching { service?.frameworkName }.getOrNull() ?: "unknown"
    val frameworkVersion get() = runCatching { service?.frameworkVersion }.getOrNull() ?: "unknown"
    val frameworkVersionCode get() = runCatching { service?.frameworkVersionCode }.getOrNull() ?: -1L
    val isRemoteFileAccessAvailable get() = useRemoteFiles(false) { true }

    class HookedTarget(private val service: XposedService, val target: NativeHookedTarget) {

        fun request(current: XposedService, extras: Bundle?, callback: (HotReloadResult) -> Unit) {
            check(service === current) {
                "The target's service connection has changed. Query targets again."
            }

            current.hotReloadModule(target, extras) { _, result -> callback(result) }
        }
    }

    @Synchronized
    fun initialize() {
        if (initialized) return
        initialized = true

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {

            override fun onServiceBind(service: XposedService) {
                val isAdded = synchronized(services) {
                    val binder = YukiHookServiceBridge.asBinder(service)
                    if (binder !in services) {
                        services[binder] = ServiceConnection(service)
                        true
                    } else false
                }
                if (isAdded) notifyListeners()
            }

            override fun onServiceDied(service: XposedService) {
                val isRemoved = synchronized(services) {
                    services.remove(YukiHookServiceBridge.asBinder(service)) != null
                }
                if (isRemoved) notifyListeners()
            }
        })
    }

    @MainThread
    fun observe(lifecycle: Lifecycle?, listener: FrameworkServiceBridge): FrameworkServiceBridge? {
        if (lifecycle?.currentState == Lifecycle.State.DESTROYED) return null

        return ServiceSubscription(lifecycle, listener).also { subscription ->
            runCatching {
                lifecycle?.addObserver(subscription)

                initialize()
                subscription.register()
            }.onFailure {
                subscription.unregister()
            }.getOrThrow()
        }
    }

    fun getReloadTargets() = useModuleReload { service -> service.runningTargets.map { HookedTarget(service, it) } }
    fun requestReload(target: HookedTarget, extras: Bundle?, callback: (HotReloadResult) -> Unit) =
        useModuleReload { target.request(it, extras, callback) }

    fun getScope() = useModuleScope { it.scope }
    fun requestScope(packages: List<String>, callback: XposedService.OnScopeEventListener) = useModuleScope { it.requestScope(packages, callback) }
    fun removeScope(packages: List<String>) = useModuleScope { it.removeScope(packages) }

    fun preferencesOrNull(name: String) = useService("remote preferences", XposedService.PROP_CAP_REMOTE) {
        it.getRemotePreferences(name)
    }

    fun listRemoteFiles() = useRemoteFiles { it.listRemoteFiles().toList() }
    fun openRemoteFile(name: String) = useRemoteFiles { it.openRemoteFile(name) }
    fun deleteRemoteFile(name: String) = useRemoteFiles { it.deleteRemoteFile(name) }

    private inline fun <T> useModuleReload(block: (XposedService) -> T) = checkNotNull(
        useService("module reload") {
            check(it.apiVersion >= HOT_RELOAD_API_VERSION) {
                "Module reload requires libxposed service API $HOT_RELOAD_API_VERSION"
            }
            block(it)
        }
    ) { "libxposed services are unavailable." }

    private inline fun <T> useModuleScope(block: (XposedService) -> T) = checkNotNull(
        useService("module scope") {
            check(it.apiVersion >= RUNTIME_API_VERSION) {
                "Module scope requires libxposed service API $RUNTIME_API_VERSION"
            }
            block(it)
        }
    ) { "libxposed services are unavailable." }

    private inline fun <T> useRemoteFiles(defaultValue: T? = null, block: (XposedService) -> T) = checkNotNull(
        useService("remote files", XposedService.PROP_CAP_REMOTE, block) ?: defaultValue
    ) { "libxposed services are unavailable." }

    private inline fun <T> useService(
        capabilityName: String,
        capability: Long? = null,
        block: (XposedService) -> T
    ): T? {
        initialize()
        val current = synchronized(services) {
            check(services.size <= 1) {
                "Multiple Xposed frameworks are connected, so the owner of $capabilityName is ambiguous."
            }
            services.values.singleOrNull()
        } ?: return null

        capability?.let {
            check(current.frameworkProperties and it != 0L) {
                "This framework does not support $capabilityName"
            }
        }
        return block(current.service)
    }

    private fun notifyListeners() = listeners.forEach { it.dispatch() }

    /**
     * Keeps cached properties within one service connection.
     */
    private class ServiceConnection(val service: XposedService) {
        val frameworkProperties by lazy { service.frameworkProperties }
    }

    /**
     * Independent lifecycle-aware subscription with callbacks delivered on the main thread.
     *
     * Every callback reads the service state when it runs on the main thread,
     * so the last callback always matches the current state.
     */
    private class ServiceSubscription(
        private val lifecycle: Lifecycle?,
        private val listener: FrameworkServiceBridge
    ) : FrameworkServiceBridge, DefaultLifecycleObserver {

        @Volatile
        private var isUnregistered = false

        override fun onServiceChanged(available: Boolean) = notifyListener(available)
        override fun onDestroy(owner: LifecycleOwner) = unregister()

        override fun unregister() {
            if (isUnregistered) return
            isUnregistered = true

            listeners.remove(this)
            lifecycle?.removeObserver(this)
        }

        fun register() {
            if (isUnregistered) return

            listeners.add(this)
            // Run the first callback directly on the main thread so a failing listener is thrown to the caller.
            if (Looper.myLooper() == Looper.getMainLooper())
                notifyListener(isAvailable)
            else dispatch()
        }

        fun dispatch() {
            mainHandler.post { notifyListener(isAvailable) }
        }

        private fun notifyListener(available: Boolean) {
            if (!isUnregistered) listener.onServiceChanged(available)
        }
    }
}