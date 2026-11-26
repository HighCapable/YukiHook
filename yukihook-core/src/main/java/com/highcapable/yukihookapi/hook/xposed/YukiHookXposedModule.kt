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
 * This file is created by fankes on 2026/9/15.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.hook.xposed

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.annotation.xposed.YukiHookXposed82Entry
import com.highcapable.yukihookapi.hook.factory.encase

/**
 * Xposed module entry point for [YukiHook].
 *
 * Annotate the implementation with [YukiHookLibXposedEntry] for modern or
 * [YukiHookXposed82Entry] for legacy to match the selected runtime.
 *
 * [onInit] is called automatically while [YukiHook] initializes,
 * and [onHook] is called automatically when hook loading starts.
 *
 * Call [YukiHook.configure] or `configure` from [onInit],
 * and [YukiHook.encase] or [encase] from [onHook].
 */
interface YukiHookXposedModule {

    /**
     * Configures [YukiHook.Config] during initialization.
     *
     * Perform initialization only. Do not run hook operations here.
     * Register framework events once with the selected runtime's `registerFrameworkEvents { ... }` extension.
     * Call native APIs inside the event callbacks, not the registration block.
     *
     * This function is optional when no custom initialization is required.
     */
    fun onInit() = Unit

    /**
     * Starts module hook loading.
     *
     * Call [YukiHook.encase] or [encase] to start hook operations.
     * Framework event registration is not allowed here, register it in [onInit] instead.
     */
    fun onHook()

    // region Deprecated functions for backward compatibility

    /**
     * This callback is no longer invoked and has no effect.
     *
     * - Move framework event registration to the selected runtime's `registerFrameworkEvents { ... }` extension inside [onInit].
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(
        message = "This callback is no longer invoked and has no effect. " +
            "Use the runtime's registerFrameworkEvents { ... } extension in onInit instead."
    )
    fun onXposedEvent() = Unit

    // endregion
}