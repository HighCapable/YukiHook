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
package com.highcapable.yukihookapi.hook.xposed.bridge.session

import android.content.pm.ApplicationInfo
import com.highcapable.yukihookapi.hook.xposed.bridge.event.ModuleEventDispatcher

/**
 * Reloadable component of a module that can save and restore its state across module instances.
 *
 * [ModuleLifecycleManager] dispatches events in registration order, respecting each implementation's phase and thread requirements.
 * Implementations keep their state private and interpret only their own saved value.
 * Objects defined by the module must not cross module instances.
 *
 * Rebuild listener registrations without repeating completed host or framework events.
 */
internal interface ModuleReloadable {

    /**
     * The role of a component during state restoration.
     */
    enum class RestorePhase {
        /** Restores resources required before new module code runs. */
        PREREQUISITE,

        /** Rebuilds registrations after the new module has declared them. */
        REBUILD
    }

    /** The phase in which this component restores its state. */
    val restorePhase get() = RestorePhase.REBUILD

    /** Whether restoration requires an entry saved by the previous module instance, even if its value is null. */
    val requiresSavedState get() = true

    /** Whether lifecycle events, except entry-thread attachment, run on the host main thread. */
    val requiresMainThread get() = false

    /**
     * Receives dispatch configuration on the entry thread after host identity is established, before user code runs.
     * @param events the dispatch configuration for this module instance.
     */
    fun onAttach(events: ModuleEventDispatcher) = Unit

    /**
     * Checks whether retirement is safe without releasing resources.
     *
     * - Note: May run again after callbacks are frozen.
     */
    fun onPrepareReload() = Unit

    /**
     * Saves only values that can cross module class loaders.
     *
     * The manager transports this value without inspecting it.
     * Recovery saves the state available after entry initialization failed, without requesting user lifecycle state.
     * @param recovery whether the state is saved for recovery after entry initialization failed.
     * @return [Any] the value to transport to the new module instance.
     */
    fun onSaveState(recovery: Boolean): Any? = null

    /** Releases this module instance's resources after the framework has accepted the saved state. */
    fun onDispose() = Unit

    /**
     * Makes saved component prerequisites available before the entry and its declarations run.
     * @param appInfo the new Xposed module application info.
     * @param state the value returned by the old module instance's [onSaveState], or null if its optional entry is absent.
     */
    fun onPrepareRestore(appInfo: ApplicationInfo, state: Any?) = Unit

    /**
     * Rebuilds this component in the new module instance at its registered phase.
     * @param appInfo the new Xposed module application info.
     * @param state the value returned by the old module instance's [onSaveState], or null if its optional entry is absent.
     */
    fun onRestoreState(appInfo: ApplicationInfo, state: Any?) = Unit
}