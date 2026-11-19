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
 * This file is created by fankes on 2026/9/27.
 */
@file:Suppress("unused", "UnusedReceiverParameter")

package com.highcapable.yukihookapi.hook.xposed.reload.lifecycle

import android.os.Bundle
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.hook.xposed.bridge.XposedModuleRuntime
import com.highcapable.yukihookapi.hook.xposed.bridge.session.ModuleLifecycleManager

/**
 * Configures module-owned state and resource handoff from [onInit].
 *
 * Configuration runs immediately. Handoff callbacks use the thread chosen by the framework. Marshal UI work to the main thread.
 *
 * Stop module-owned threads, native hooks and external listeners in [ModuleLifecycle.onDispose].
 * Register once per module instance. A module without external resources does not need this block.
 * @param block the lifecycle configuration.
 */
fun YukiHookXposedModule.registerModuleLifecycle(block: ModuleLifecycle.() -> Unit) {
    XposedModuleRuntime.checkInitializingEntry("module lifecycle callbacks")

    ModuleLifecycleManager.register(ModuleLifecycle().apply(block))
}

/**
 * Optional state and resource callbacks for a module reload.
 *
 * These callbacks do not replay completed host lifecycle events.
 *
 * - Note: Do not transfer module-defined objects.
 */
class ModuleLifecycle internal constructor() {

    internal var canReloadCallback: (Bundle?) -> Boolean = { true }
        private set
    internal var saveStateCallback: () -> Bundle? = { null }
        private set
    internal var disposeCallback: () -> Unit = {}
        private set
    internal var restoreStateCallback: (Bundle?) -> Unit = {}
        private set
    internal var reloadedCallback: (Bundle?) -> Unit = {}
        private set

    /**
     * Checks whether the old module instance can retire.
     *
     * Return false before changing resources to reject the request.
     * @param callback the callback that returns whether the old module instance can retire.
     */
    fun canReload(callback: (extras: Bundle?) -> Boolean) {
        canReloadCallback = callback
    }

    /**
     * Saves classloader-neutral state before disposal.
     *
     * The new module instance receives it before its `onHook` runs.
     * @param callback the callback that returns a [Bundle] of state to save.
     */
    fun onSaveState(callback: () -> Bundle?) {
        saveStateCallback = callback
    }

    /**
     * Stops and releases module-owned work.
     *
     * Throwing reports a failure. Completed disposal is not rolled back.
     * @param callback releases resources owned by the old module instance before it retires.
     */
    fun onDispose(callback: () -> Unit) {
        disposeCallback = callback
    }

    /**
     * Restores saved state after the new entry's `onInit` and before its `onHook`.
     *
     * @param callback the callback that runs to restore the saved state.
     */
    fun onRestoreState(callback: (state: Bundle?) -> Unit) {
        restoreStateCallback = callback
    }

    /**
     * Runs after hook replay and managed listener restoration.
     *
     * Use this to restart other module-owned work.
     * @param callback the callback that runs after the new module instance is fully loaded.
     */
    fun onReloaded(callback: (extras: Bundle?) -> Unit) {
        reloadedCallback = callback
    }
}