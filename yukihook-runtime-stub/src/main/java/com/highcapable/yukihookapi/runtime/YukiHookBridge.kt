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
@file:Suppress("unused", "RedundantNullableReturnType")

package com.highcapable.yukihookapi.runtime

import androidx.lifecycle.Lifecycle
import com.highcapable.yukihookapi.runtime.bridge.ExecutableInvokerBridge
import com.highcapable.yukihookapi.runtime.bridge.FrameworkHookBridge
import com.highcapable.yukihookapi.runtime.bridge.FrameworkServiceBridge
import com.highcapable.yukihookapi.runtime.bridge.HookRegistrationBridge
import com.highcapable.yukihookapi.runtime.bridge.ModuleScopeBridge
import com.highcapable.yukihookapi.runtime.bridge.PreferencesBridge
import com.highcapable.yukihookapi.runtime.bridge.SharedFilesBridge
import com.highcapable.yukihookapi.runtime.bridge.session.ModuleReloadBridge

/**
 * The core bridge for the `YukiHook` runtime.
 * Exactly one runtime supplies these symbols in the APK.
 */
object YukiHookBridge : FrameworkHookBridge {

    override val runtime: YukiHookRuntime get() = error("Stub!")

    override val isAvailable: Boolean get() = error("Stub!")
    override val isStandaloneAvailable get(): Boolean = error("Stub!")
    override val isModuleActive: Boolean get() = error("Stub!")
    override val apiVersion: Int get() = error("Stub!")
    override val frameworkName: String get() = error("Stub!")
    override val frameworkVersion: String get() = error("Stub!")
    override val frameworkVersionCode: Long get() = error("Stub!")

    override val moduleScope: ModuleScopeBridge get() = error("Stub!")
    override val moduleReload: ModuleReloadBridge get() = error("Stub!")

    override val executableInvoker: ExecutableInvokerBridge get() = error("Stub!")
    override val hookRegistration: HookRegistrationBridge get() = error("Stub!")

    override fun initializeModuleServices(): Unit = error("Stub!")
    override fun installLegacyModuleHooks(hook: FrameworkHookBridge.LegacyModuleHook, loader: ClassLoader?): Unit = error("Stub!")
    override fun observeService(lifecycle: Lifecycle?, listener: FrameworkServiceBridge): FrameworkServiceBridge? = error("Stub!")
    override fun preferences(hostEnvironment: Boolean): PreferencesBridge = error("Stub!")
    override fun sharedFiles(hostEnvironment: Boolean): SharedFilesBridge = error("Stub!")
    override fun log(priority: Int, tag: String, message: String, throwable: Throwable?): Unit = error("Stub!")
}