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
 * This file is created by fankes on 2022/2/3.
 */
package com.highcapable.yukihookapi.hook.entity

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.annotation.xposed.YukiHookXposed82Entry
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.bridge.XposedModuleRuntime
import com.highcapable.yukihookapi.hook.xposed.bridge.type.HookEntryType

/**
 * Base hooker implementation for [YukiHook].
 * Inherit this class to organize multiple hooker feature modules in a single Xposed module.
 *
 * See [YukiHookLibXposedEntry] or [YukiHookXposed82Entry] for the entry-point configuration.
 * @see YukiHookLibXposedEntry
 * @see YukiHookXposed82Entry
 * @see PackageParam
 */
abstract class YukiBaseHooker : PackageParam() {

    private val contextLock = Any()

    /**
     * Binds a [PackageParam] context and loads this hooker.
     * @param param the [PackageParam] to use.
     */
    internal fun bindTo(param: PackageParam) {
        synchronized(contextLock) {
            val targetContext = param.context ?: return

            val boundContext = context
            if (boundContext?.id == targetContext.id) return
            if (boundContext != null &&
                (boundContext.type != HookEntryType.ZYGOTE || targetContext.type == HookEntryType.ZYGOTE)
            ) {
                YLog.warn(
                    msg = "Hooker \"${this::class.java.name}\" is a singleton or reused instance already bound to hook entry " +
                        "\"${boundContext.id}\", but the current entry is \"${targetContext.id}\". " +
                        "Make sure the hooker supports multiple instances, the current hook entry will be ignored"
                )
                return
            }

            bind(targetContext)
            runCatching { onHook() }.onFailure {
                // Report it as a reload failure as well, the same as an exception from the encase block.
                XposedModuleRuntime.recordRestoreFailure(it)
                YLog.error("An exception occurred in $this", it)
            }
        }
    }

    /** Starts this hooker. */
    abstract fun onHook()
}