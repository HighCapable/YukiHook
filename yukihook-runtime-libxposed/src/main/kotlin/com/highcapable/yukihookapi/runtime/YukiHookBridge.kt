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
@file:Suppress("unused")

package com.highcapable.yukihookapi.runtime

import android.util.Log
import androidx.lifecycle.Lifecycle
import com.highcapable.yukihookapi.runtime.bridge.ExecutableInvokerBridge
import com.highcapable.yukihookapi.runtime.bridge.FrameworkHookBridge
import com.highcapable.yukihookapi.runtime.bridge.FrameworkServiceBridge
import com.highcapable.yukihookapi.runtime.bridge.HookRegistrationBridge
import com.highcapable.yukihookapi.runtime.libxposed.LibXposedModuleReload
import com.highcapable.yukihookapi.runtime.libxposed.LibXposedModuleScope
import com.highcapable.yukihookapi.runtime.libxposed.LibXposedPreferences
import com.highcapable.yukihookapi.runtime.libxposed.LibXposedService
import com.highcapable.yukihookapi.runtime.libxposed.LibXposedSharedFiles

/**
 * The core `YukiHook` runtime bridge for `yukihook-runtime-libxposed`.
 */
internal object YukiHookBridge : FrameworkHookBridge {

    private const val NOT_ATTACHED_ERROR = "libxposed API is not attached to this process."

    class ApiSpec(
        val apiVersion: Int = -1,
        val frameworkName: String = "unknown",
        val frameworkVersion: String = "unknown",
        val frameworkVersionCode: Long = -1L,
        val executableInvokerBridge: ExecutableInvokerBridge? = null,
        val hookRegistrationBridge: HookRegistrationBridge? = null,
        val loggerCallback: ((Int, String, String, Throwable?) -> Unit)? = null
    )

    private var apiSpec: ApiSpec? = null
    private val api get() = apiSpec ?: error(NOT_ATTACHED_ERROR)

    override val runtime get() = YukiHookRuntime.LIBXPOSED

    override val isAvailable get() = apiSpec != null
    override val isStandaloneAvailable get() = false
    override val isModuleActive get() = LibXposedService.isAvailable
    override val apiVersion get() = if (isAvailable) api.apiVersion else LibXposedService.apiVersion
    override val frameworkName get() = if (isAvailable) api.frameworkName else LibXposedService.frameworkName
    override val frameworkVersion get() = if (isAvailable) api.frameworkVersion else LibXposedService.frameworkVersion
    override val frameworkVersionCode get() = if (isAvailable) api.frameworkVersionCode else LibXposedService.frameworkVersionCode

    override val moduleScope get() = LibXposedModuleScope
    override val moduleReload get() = LibXposedModuleReload

    override val executableInvoker get() = checkApi(api.executableInvokerBridge)
    override val hookRegistration get() = checkApi(api.hookRegistrationBridge)

    fun attach(spec: ApiSpec) {
        apiSpec = spec
    }

    override fun initializeModuleServices() = LibXposedService.initialize()
    override fun installLegacyModuleHooks(hook: FrameworkHookBridge.LegacyModuleHook, loader: ClassLoader?) = Unit

    override fun observeService(lifecycle: Lifecycle?, listener: FrameworkServiceBridge) = LibXposedService.observe(lifecycle, listener)

    override fun preferences(hostEnvironment: Boolean) = LibXposedPreferences.open(hostEnvironment)
    override fun sharedFiles(hostEnvironment: Boolean) = LibXposedSharedFiles.open(hostEnvironment)

    override fun log(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val callback = api.loggerCallback

        if (callback != null)
            callback(priority, tag, message, throwable)
        else Log.println(priority, tag, message + (throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""))
    }

    private fun <T> checkApi(value: T?) = checkNotNull(value) { NOT_ATTACHED_ERROR }
}