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
 * This file is created by fankes on 2022/4/30.
 * This file is modified by fankes on 2022/1/10.
 */
@file:Suppress("unused", "UnusedReceiverParameter")

package com.highcapable.yukihookapi.hook.xposed.bridge.event

import com.highcapable.yukihookapi.annotation.internal.RequiresRuntime
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.hook.xposed.bridge.caller.YukiHookModuleCaller
import com.highcapable.yukihookapi.runtime.YukiHookRuntime
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Registers framework event listeners from [YukiHookXposedModule.onInit].
 *
 * The configuration block runs immediately, event callbacks run when the framework dispatches them.
 * Use native Xposed Framework APIs in these callbacks and keep `YukiHook` hook loading in [YukiHookXposedModule.onHook].
 *
 * Only one registration block is allowed per module process, even across different entry instances.
 * @receiver [YukiHookXposedModule]
 * @param block the event configuration block.
 * @throws IllegalStateException if called outside [YukiHookXposedModule.onInit] or more than once.
 */
@RequiresRuntime(YukiHookRuntime.XPOSED_82)
fun YukiHookXposedModule.registerFrameworkEvents(block: YukiHookXposed82Event.() -> Unit) =
    YukiHookModuleCaller.callRegisterFrameworkEvents { YukiHookXposed82Event.block() }

/**
 * Registers listeners for framework lifecycle events exposed by Xposed Framework API.
 * Configure listeners only inside [registerFrameworkEvents]. Each lifecycle listener can be set once.
 */
@RequiresRuntime(YukiHookRuntime.XPOSED_82)
object YukiHookXposed82Event {

    internal var initZygoteCallback: ((IXposedHookZygoteInit.StartupParam) -> Unit)? = null
        private set
    internal var handleLoadPackageCallback: ((XC_LoadPackage.LoadPackageParam) -> Unit)? = null
        private set

    /**
     * Sets the `initZygote` event listener.
     * @param callback the event callback.
     */
    fun onInitZygote(callback: (IXposedHookZygoteInit.StartupParam) -> Unit) {
        YukiHookModuleCaller.checkFrameworkEventsRegistration()
        check(initZygoteCallback == null) {
            "`onInitZygote` is already registered."
        }

        initZygoteCallback = callback
    }

    /**
     * Sets the `handleLoadPackage` event listener.
     * @param callback the event callback.
     */
    fun onHandleLoadPackage(callback: (XC_LoadPackage.LoadPackageParam) -> Unit) {
        YukiHookModuleCaller.checkFrameworkEventsRegistration()
        check(handleLoadPackageCallback == null) {
            "`onHandleLoadPackage` is already registered."
        }

        handleLoadPackageCallback = callback
    }
}