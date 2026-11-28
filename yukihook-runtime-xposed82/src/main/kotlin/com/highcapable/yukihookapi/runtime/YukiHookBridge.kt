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

import android.util.Log
import androidx.lifecycle.Lifecycle
import com.highcapable.yukihookapi.runtime.bridge.FrameworkHookBridge
import com.highcapable.yukihookapi.runtime.bridge.FrameworkServiceBridge
import com.highcapable.yukihookapi.runtime.bridge.ModuleScopeBridge
import com.highcapable.yukihookapi.runtime.bridge.SharedFilesBridge
import com.highcapable.yukihookapi.runtime.bridge.session.ModuleReloadBridge
import com.highcapable.yukihookapi.runtime.xposed82.Xposed82ExecutableInvoker
import com.highcapable.yukihookapi.runtime.xposed82.Xposed82HookApi
import com.highcapable.yukihookapi.runtime.xposed82.Xposed82ModuleStatus
import com.highcapable.yukihookapi.runtime.xposed82.Xposed82Preferences

/**
 * The core `YukiHook` runtime bridge for `yukihook-runtime-xposed82`.
 */
internal object YukiHookBridge : FrameworkHookBridge {

    private var attached = false

    override val runtime get() = YukiHookRuntime.XPOSED_82

    override val isAvailable get() = attached
    override val isStandaloneAvailable get(): Boolean {
        if (attached) return true
        attached = Xposed82HookApi.isAvailable
        return attached
    }
    override val isModuleActive get() = Xposed82ModuleStatus.isActive
    override val apiVersion get() = if (attached) Xposed82HookApi.apiVersion else Xposed82ModuleStatus.apiVersion
    override val frameworkName get() = if (attached) Xposed82HookApi.frameworkName else Xposed82ModuleStatus.frameworkName
    override val frameworkVersion get() = if (attached) "unsupported" else Xposed82ModuleStatus.frameworkVersion
    override val frameworkVersionCode get() = if (attached) 0L else Xposed82ModuleStatus.frameworkVersionCode

    override val moduleScope: ModuleScopeBridge get() = throw UnsupportedOperationException("Module scope is unavailable in Xposed Framework API.")
    override val moduleReload: ModuleReloadBridge get() = throw UnsupportedOperationException("Module reload is unavailable in Xposed Framework API.")

    override val executableInvoker get() = if (attached) Xposed82ExecutableInvoker else error("Xposed Framework API is not attached to this process.")
    override val hookRegistration get() = Xposed82HookApi

    fun attach() {
        attached = true
    }

    override fun initializeModuleServices() = Unit

    override fun installLegacyModuleHooks(hook: FrameworkHookBridge.LegacyModuleHook, loader: ClassLoader?) = when (hook) {
        FrameworkHookBridge.LegacyModuleHook.STATUS -> Xposed82ModuleStatus.install(loader)
        FrameworkHookBridge.LegacyModuleHook.SHARED_PREFERENCES -> Xposed82Preferences.installHooks()
    }

    override fun observeService(lifecycle: Lifecycle?, listener: FrameworkServiceBridge): FrameworkServiceBridge? =
        throw UnsupportedOperationException("Framework service is unavailable in Xposed Framework API.")

    override fun preferences(hostEnvironment: Boolean) = Xposed82Preferences.open(hostEnvironment)

    override fun sharedFiles(hostEnvironment: Boolean): SharedFilesBridge =
        throw UnsupportedOperationException("Shared files are unavailable in Xposed Framework API.")

    override fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        if (attached)
            Xposed82HookApi.log(priority, tag, message, throwable)
        else Log.println(priority, tag, message + (throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""))
    }
}