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
@file:Suppress("unused")

package com.highcapable.yukihookapi.runtime.libxposed.entry

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.xposed.bridge.caller.YukiHookModuleCaller
import com.highcapable.yukihookapi.hook.xposed.bridge.event.YukiHookLibXposedEvent
import com.highcapable.yukihookapi.runtime.bridge.session.ModuleReloadPolicy
import com.highcapable.yukihookapi.runtime.libxposed.LibXposedHookApi
import com.highcapable.yukihookapi.runtime.libxposed.LibXposedModuleSession
import com.highcapable.yukihookapi.runtime.libxposed.entry.base.LibXposedBaseEntry
import com.highcapable.yukihookapi.runtime.state.ModuleStateSpec
import io.github.libxposed.api.XposedModuleInterface

/**
 * Entry for `libxposed:api` version 102.
 */
abstract class LibXposed102Entry : LibXposedBaseEntry() {

    private companion object {
        const val HOT_RELOAD_API_VERSION = API_102
    }

    /**
     * Keeps managed and native reload states separate across module instances.
     */
    private data class HandoffState(
        val managedState: Array<*>,
        val nativeState: Any?
    ) {

        companion object : ModuleStateSpec<HandoffState> {

            private const val SCHEMA_VERSION = YukiHook.SCHEMA_VERSION

            /** Wraps the state produced by the core reload manager. */
            fun fromManagedState(managedState: Any, nativeState: Any?) = HandoffState(
                managedState as? Array<*> ?: error("Invalid YukiHook reload state."), nativeState
            )

            override fun decode(encoded: Any?): HandoffState {
                val values = encoded as? Array<*> ?: error("Missing module reload state.")
                // States saved before the schema version start with the YukiHook version, so their schema is unknown.
                check(values.getOrNull(0) == SCHEMA_VERSION) {
                    "Unsupported YukiHook reload state schema ${values.getOrNull(0) as? Int ?: "unknown"}, the current schema is " +
                        "$SCHEMA_VERSION. Restart the host to apply this module update."
                }
                check(values.size == 3) {
                    "Invalid module reload state."
                }

                val managedState = values[1] as? Array<*> ?: error("Missing YukiHook reload state.")
                return HandoffState(managedState, values[2])
            }

            override fun encode(state: HandoffState) = arrayOf(SCHEMA_VERSION, state.managedState, state.nativeState)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as HandoffState

            if (!managedState.contentEquals(other.managedState)) return false
            if (nativeState != other.nativeState) return false

            return true
        }

        override fun hashCode(): Int {
            var result = managedState.contentHashCode()
            result = 31 * result + nativeState.hashCode()
            return result
        }
    }

    /** Reload policy supplied by the generated entry. */
    protected open val hotReload = ModuleReloadPolicy.NONE

    /** Reload policy in effect, frameworks before API 102 cannot reload modules. */
    private val activeHotReload get() = if (apiVersion >= HOT_RELOAD_API_VERSION) hotReload else ModuleReloadPolicy.NONE

    /** The original handoff kept for a later module instance after a failed restore. */
    private var pendingHandoff: Any? = null

    /** Whether [pendingHandoff] was received by this module instance. */
    private var hasPendingHandoff = false

    override val retainPackageContexts get() = activeHotReload != ModuleReloadPolicy.NONE

    final override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        if (activeHotReload == ModuleReloadPolicy.NONE) return false
        if (hasPendingHandoff) {
            // A failed restore cannot run user save callbacks again; retire this instance and forward the original handoff.
            if (!YukiHookModuleCaller.callRetireFailedRestore()) return false

            param.setSavedInstanceState(pendingHandoff)
            hasPendingHandoff = false
            pendingHandoff = null
            return true
        }
        if (!initialized) return YukiHookModuleCaller.callPrepareFailedModule(moduleApplicationInfo) { managedState ->
            // The first load has no incoming handoff. Keep its available state if retirement needs another attempt.
            val encoded = HandoffState.encode(HandoffState.fromManagedState(managedState, nativeState = null))
            param.setSavedInstanceState(encoded)
            pendingHandoff = encoded
            hasPendingHandoff = true
            true
        }

        return YukiHookModuleCaller.callPrepareReloadModule(param.extras) { managedState ->
            var nativeState: Any? = null
            val nativeParam = object : XposedModuleInterface.HotReloadingParam by param {
                override fun setSavedInstanceState(outState: Any?) {
                    // Let the framework validate native state before combining the two owners' data.
                    param.setSavedInstanceState(outState)
                    nativeState = outState
                }
            }

            if (YukiHookLibXposedEvent.hotReloading?.invoke(this, nativeParam) != false) {
                param.setSavedInstanceState(HandoffState.encode(HandoffState.fromManagedState(managedState, nativeState)))
                true
            } else false
        }
    }

    final override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        // NONE rejects the next request, it must still complete an incoming handoff.
        // Keep the framework-owned handoff so a later module instance can retry a failed restore.
        pendingHandoff = param.savedInstanceState
        hasPendingHandoff = true

        var restoreFailure: Throwable? = null
        runCatching {
            attachRuntime(restoring = true)
            parseProcessName(param)
            val saved = HandoffState.decode(pendingHandoff)
            initialized = YukiHookModuleCaller.callRestoreModule(::createHookEntry, moduleApplicationInfo, processName, saved.managedState) {
                restoreFailure = it
            }
            if (!initialized) throw checkNotNull(restoreFailure) {
                "Module restoration failed without a cause."
            }

            val nativeParam = object : XposedModuleInterface.HotReloadedParam by param {
                override fun getSavedInstanceState() = saved.nativeState
                override fun getOldHookHandles() = param.oldHookHandles.filterNot { it.id?.startsWith("${YukiHook.TAG}:") == true }
            }
            runCatching {
                YukiHookLibXposedEvent.hotReloaded?.invoke(this, nativeParam)
            }.onFailure(YukiHookModuleCaller::callRecordRestoreFailure)

            YukiHookModuleCaller.callFinishRestoreModule(param.extras)
        }.also {
            // Replaced handles are already invalidated. unhook is idempotent and removes only
            // old registrations, including declarations omitted from this module instance.
            param.oldHookHandles.forEach { handle ->
                runCatching {
                    handle.unhook()
                }.onFailure(YukiHookModuleCaller::callRecordRestoreFailure)
            }
        }.onFailure {
            initialized = false
            YukiHookModuleCaller.callAbortRestoreModule()
        }.getOrThrow()

        hasPendingHandoff = false
        pendingHandoff = null
    }

    final override fun attachRuntime() = attachRuntime(restoring = false)

    final override fun onInitializationFailed() {
        if (activeHotReload != ModuleReloadPolicy.NONE) YukiHookModuleCaller.callAbortRestoreModule()
    }

    private fun attachRuntime(restoring: Boolean) {
        val session = if (restoring || activeHotReload != ModuleReloadPolicy.NONE)
            LibXposedModuleSession()
        else null

        val api = LibXposedHookApi(this, session)
        val registrations = session?.let { YukiHookModuleCaller.callAttachModuleSession(it, api) } ?: api
        api.attach(registrations)
    }
}