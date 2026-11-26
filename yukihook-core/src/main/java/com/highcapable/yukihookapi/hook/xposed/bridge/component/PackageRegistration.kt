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
package com.highcapable.yukihookapi.hook.xposed.bridge.component

import android.content.pm.ApplicationInfo
import com.highcapable.yukihookapi.hook.param.context.PackageContext
import com.highcapable.yukihookapi.hook.xposed.bridge.event.ModuleEventDispatcher
import com.highcapable.yukihookapi.hook.xposed.bridge.session.ModuleReloadable
import com.highcapable.yukihookapi.hook.xposed.bridge.type.HookEntryType

/**
 * Records loaded package contexts and replays them in a new module instance.
 * @param callback the callback to run for each package context in a new module instance.
 */
internal class PackageRegistration(private val callback: (PackageContext) -> Unit) : ModuleReloadable {

    companion object {
        const val RELOAD_ID = "packages"
    }

    private var events = ModuleEventDispatcher()

    private val loadedEntries = mutableSetOf<String>()
    private val reloadPackages = linkedMapOf<String, PackageContext>()

    override fun onAttach(events: ModuleEventDispatcher) {
        this.events = events
    }

    override fun onSaveState(recovery: Boolean) = synchronized(loadedEntries) {
        reloadPackages.values.map(PackageContext::encode).toTypedArray()
    }

    override fun onRestoreState(appInfo: ApplicationInfo, state: Any?) {
        val packages = state as? Array<*> ?: error("Missing package reload state.")
        packages.forEach { callback(PackageContext.decode(it)) }
    }

    /**
     * Records a package and returns whether its callback should run.
     * @param context the package context to record.
     * @param retain whether to keep the context for a later module instance, even before runtime binding succeeds.
     * @return [Boolean] whether the package context callback should run.
     */
    fun record(context: PackageContext, retain: Boolean = events.isManaged): Boolean {
        // Legacy zygote events always run their callback.
        return context.type == HookEntryType.ZYGOTE || synchronized(loadedEntries) {
            val added = loadedEntries.add(context.id)
            if (retain) reloadPackages.getOrPut(context.id) { context }
            added
        }
    }
}