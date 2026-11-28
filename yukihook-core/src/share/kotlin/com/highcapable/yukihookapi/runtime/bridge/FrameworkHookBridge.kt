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
 * This file is created by fankes on 2026/9/10.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.runtime.bridge

import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.lifecycle.Lifecycle
import com.highcapable.yukihookapi.runtime.YukiHookRuntime
import com.highcapable.yukihookapi.runtime.bridge.session.ModuleReloadBridge

/**
 * Binary hook bridge contract shared by core and the selected runtime,
 * providing access to the selected runtime and its services.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface FrameworkHookBridge {

    /**
     * The legacy module hooks to install.
     */
    enum class LegacyModuleHook {
        /** Generated module activation status. */
        STATUS,

        /** Module preference file permissions. */
        SHARED_PREFERENCES
    }

    /** The current runtime type. */
    val runtime: YukiHookRuntime

    /** Whether the runtime is available for hook operations. */
    val isAvailable: Boolean

    /** Whether the standalone hook API is available. */
    val isStandaloneAvailable: Boolean

    /** Whether the module has one available framework service. */
    val isModuleActive: Boolean

    /** Runtime framework API version. */
    val apiVersion: Int

    /** Framework display name. */
    val frameworkName: String

    /** Framework version. */
    val frameworkVersion: String

    /** Framework version code. */
    val frameworkVersionCode: Long

    /** Module-scope bridge. */
    val moduleScope: ModuleScopeBridge

    /** Module-reload bridge. */
    val moduleReload: ModuleReloadBridge

    /** Executable invocation capabilities supplied by the selected runtime. */
    val executableInvoker: ExecutableInvokerBridge

    /** Hook registration, deoptimization and replacement supplied by the selected runtime. */
    val hookRegistration: HookRegistrationBridge

    /** Initializes module runtime services without loading host-only API classes. */
    fun initializeModuleServices()

    /**
     * Installs the selected legacy module hooks when supported by the selected runtime.
     * @param hook the hooks to install.
     * @param loader the module class loader.
     */
    fun installLegacyModuleHooks(hook: LegacyModuleHook, loader: ClassLoader?)

    /**
     * Observes service readiness and returns an independent subscription for the listener.
     * @param lifecycle the optional lifecycle for automatic cancellation, or null for manual cancellation only.
     * @param listener the listener to observe service readiness.
     * @return [FrameworkServiceBridge] or null.
     */
    @MainThread
    fun observeService(lifecycle: Lifecycle?, listener: FrameworkServiceBridge): FrameworkServiceBridge?

    /**
     * Gets preferences storage for the current environment.
     * @param hostEnvironment whether storage is accessed from a host environment.
     * @return [PreferencesBridge]
     */
    fun preferences(hostEnvironment: Boolean): PreferencesBridge

    /**
     * Gets shared-file storage for the current environment.
     * @param hostEnvironment whether storage is accessed from a host environment.
     * @return [SharedFilesBridge]
     */
    fun sharedFiles(hostEnvironment: Boolean): SharedFilesBridge

    /**
     * Logs using the attached framework without discarding its priority or tag.
     * @param priority the priority of the log.
     * @param tag the tag of the log.
     * @param message the message of the log.
     * @param throwable the throwable of the log.
     */
    fun log(priority: Int, tag: String, message: String, throwable: Throwable?)
}