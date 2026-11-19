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
@file:Suppress("unused", "UnusedReceiverParameter")

package com.highcapable.yukihookapi.hook.xposed.bridge.event

import com.highcapable.yukihookapi.annotation.internal.RequiresRuntime
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.hook.xposed.bridge.caller.YukiHookModuleCaller
import com.highcapable.yukihookapi.runtime.YukiHookRuntime
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

/**
 * Registers framework event listeners from [YukiHookXposedModule.onInit].
 *
 * The configuration block runs immediately, event callbacks receive the attached [XposedInterface]
 * and the original framework parameters when the framework dispatches them.
 * Use native libxposed APIs in these callbacks and keep `YukiHook` hook loading in [YukiHookXposedModule.onHook].
 *
 * Only one registration block is allowed per module instance, even across different entry objects.
 * @receiver [YukiHookXposedModule]
 * @param block the event configuration block.
 * @throws IllegalStateException if called outside [YukiHookXposedModule.onInit] or more than once.
 */
@RequiresRuntime(YukiHookRuntime.LIBXPOSED)
fun YukiHookXposedModule.registerFrameworkEvents(block: YukiHookLibXposedEvent.() -> Unit) =
    YukiHookModuleCaller.callRegisterFrameworkEvents { YukiHookLibXposedEvent.block() }

/**
 * Registers listeners for framework lifecycle events exposed by libxposed API.
 * Configure listeners only inside [registerFrameworkEvents]. Each lifecycle listener can be set once.
 */
@RequiresRuntime(YukiHookRuntime.LIBXPOSED)
object YukiHookLibXposedEvent {
    internal var moduleLoaded: (XposedInterface.(XposedModuleInterface.ModuleLoadedParam) -> Unit)? = null
    internal var packageLoaded: (XposedInterface.(XposedModuleInterface.PackageLoadedParam) -> Unit)? = null
    internal var packageReady: (XposedInterface.(XposedModuleInterface.PackageReadyParam) -> Unit)? = null
    internal var systemServerStarting: (XposedInterface.(XposedModuleInterface.SystemServerStartingParam) -> Unit)? = null
    internal var hotReloading: (XposedInterface.(XposedModuleInterface.HotReloadingParam) -> Boolean)? = null
    internal var hotReloaded: (XposedInterface.(XposedModuleInterface.HotReloadedParam) -> Unit)? = null
}