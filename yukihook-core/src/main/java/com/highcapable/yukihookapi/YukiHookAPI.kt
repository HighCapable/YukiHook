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
 * This file is created by fankes on 2022/2/2.
 */
@file:Suppress("unused", "DEPRECATION", "DeprecatedCallableAddReplaceWith")

package com.highcapable.yukihookapi

import android.content.Context
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam

/**
 * Legacy entry point.
 */
@Deprecated(
    message = "Use YukiHook instead.",
    replaceWith = ReplaceWith("YukiHook", "com.highcapable.yukihookapi.YukiHook")
)
object YukiHookAPI {

    @Deprecated(
        message = "Use YukiHook.TAG instead.",
        replaceWith = ReplaceWith("YukiHook.TAG", "com.highcapable.yukihookapi.YukiHook")
    )
    const val TAG = YukiHook.TAG

    @Deprecated(
        message = "Use YukiHook.VERSION instead.",
        replaceWith = ReplaceWith("YukiHook.VERSION", "com.highcapable.yukihookapi.YukiHook")
    )
    const val VERSION = YukiHook.VERSION

    @Deprecated(
        message = "Use YukiHook.VERSION instead.",
        replaceWith = ReplaceWith("YukiHook.VERSION", "com.highcapable.yukihookapi.YukiHook")
    )
    const val API_VERSION_NAME = VERSION

    @Deprecated(message = "The numeric API version was removed.")
    const val API_VERSION_CODE = -1

    @Deprecated(
        message = "Use YukiHook.Status instead.",
        replaceWith = ReplaceWith("YukiHook.Status", "com.highcapable.yukihookapi.YukiHook")
    )
    object Status {

        @Deprecated(
            message = "Use YukiHook.Status.compiledTimestamp instead.",
            replaceWith = ReplaceWith(
                "YukiHook.Status.compiledTimestamp",
                "com.highcapable.yukihookapi.YukiHook"
            )
        )
        val compiledTimestamp get() = YukiHook.Status.compiledTimestamp

        @Deprecated(
            message = "Use YukiHook.Status.isHostEnvironment instead.",
            replaceWith = ReplaceWith(
                "YukiHook.Status.isHostEnvironment",
                "com.highcapable.yukihookapi.YukiHook"
            )
        )
        val isXposedEnvironment get() = YukiHook.Status.isHostEnvironment

        @Deprecated(
            message = "Use YukiHook.Status.Executor.name instead.",
            replaceWith = ReplaceWith(
                "YukiHook.Status.Executor.name",
                "com.highcapable.yukihookapi.YukiHook"
            )
        )
        val executorName get() = YukiHook.Status.Executor.name

        @Deprecated(
            message = "Use YukiHook.Status.Executor.apiVersion instead.",
            replaceWith = ReplaceWith(
                "YukiHook.Status.Executor.apiVersion",
                "com.highcapable.yukihookapi.YukiHook"
            )
        )
        val executorVersion get() = YukiHook.Status.Executor.apiVersion

        @Deprecated(
            message = "Use YukiHook.Status.isModuleActive instead.",
            replaceWith = ReplaceWith(
                "YukiHook.Status.isModuleActive",
                "com.highcapable.yukihookapi.YukiHook"
            )
        )
        val isModuleActive get() = YukiHook.Status.isModuleActive

        @Deprecated(
            message = "Use YukiHook.Status.isModuleActive instead.",
            replaceWith = ReplaceWith(
                "YukiHook.Status.isModuleActive",
                "com.highcapable.yukihookapi.YukiHook"
            )
        )
        val isXposedModuleActive get() = YukiHook.Status.isModuleActive

        @Deprecated(message = "TaiChi module detection is no longer supported.", level = DeprecationLevel.ERROR)
        val isTaiChiModuleActive get() = false

        @Deprecated(message = "Resource hooks are no longer supported.", level = DeprecationLevel.ERROR)
        val isSupportResourcesHook get() = false

        @Deprecated(
            message = "Use YukiHook.Status.Executor instead.",
            replaceWith = ReplaceWith("YukiHook.Status.Executor", "com.highcapable.yukihookapi.YukiHook")
        )
        object Executor {

            @Deprecated(
                message = "Use YukiHook.Status.Executor.name instead.",
                replaceWith = ReplaceWith(
                    "YukiHook.Status.Executor.name",
                    "com.highcapable.yukihookapi.YukiHook"
                )
            )
            val name get() = YukiHook.Status.Executor.name

            @Deprecated(message = "Hook framework type detection is no longer supported.", level = DeprecationLevel.ERROR)
            val type: Any get() = Unit

            @Deprecated(
                message = "Use YukiHook.Status.Executor.apiVersion instead.",
                replaceWith = ReplaceWith(
                    "YukiHook.Status.Executor.apiVersion",
                    "com.highcapable.yukihookapi.YukiHook"
                )
            )
            val apiLevel get() = YukiHook.Status.Executor.apiVersion

            @Deprecated(
                message = "Use YukiHook.Status.Executor.version instead.",
                replaceWith = ReplaceWith(
                    "YukiHook.Status.Executor.version",
                    "com.highcapable.yukihookapi.YukiHook"
                )
            )
            val versionName get() = YukiHook.Status.Executor.version

            @Deprecated(
                message = "Use YukiHook.Status.Executor.versionCode instead.",
                replaceWith = ReplaceWith(
                    "YukiHook.Status.Executor.versionCode",
                    "com.highcapable.yukihookapi.YukiHook"
                )
            )
            val versionCode get() = YukiHook.Status.Executor.versionCode
        }
    }

    @Deprecated(
        message = "Use YukiHook.Config instead.",
        replaceWith = ReplaceWith("YukiHook.Config", "com.highcapable.yukihookapi.YukiHook")
    )
    object Configs {

        @Deprecated(
            message = "Use YukiHook.Config.logging instead.",
            replaceWith = ReplaceWith(
                "YukiHook.Config.logging(block)",
                "com.highcapable.yukihookapi.YukiHook"
            )
        )
        inline fun debugLog(block: YLog.Config.() -> Unit) = YukiHook.Config.logging(block)

        @Deprecated(
            message = "Use YLog.Config.tag instead.",
            replaceWith = ReplaceWith("YLog.Config.tag", "com.highcapable.yukihookapi.hook.log.YLog")
        )
        var debugTag
            get() = YLog.Config.tag
            set(value) {
                YLog.Config.tag = value
            }

        @Deprecated(message = "Use YLog.Config.level instead.")
        var isDebug
            get() = YLog.Config.level == YLog.Level.DEBUG
            set(value) {
                YLog.Config.level = if (value) YLog.Level.DEBUG else YLog.Level.INFO
            }

        @Deprecated(message = "Use YLog.Config.level instead.")
        var isAllowPrintingLogs
            get() = isDebug
            set(value) {
                isDebug = value
            }

        @Deprecated(message = "This property no longer has any effect.")
        var isEnableModulePrefsCache = false

        @Deprecated(message = "This property no longer has any effect.")
        var isEnablePrefsBridgeCache = false

        @Deprecated(message = "This property no longer has any effect.")
        var isEnableModuleAppResourcesCache = true

        @Deprecated(message = "This property no longer has any effect.")
        var isEnableHookModuleStatus = true

        @Deprecated(
            message = "Use YukiHook.Config.hookSharedPreferences instead.",
            replaceWith = ReplaceWith(
                "YukiHook.Config.hookSharedPreferences",
                "com.highcapable.yukihookapi.YukiHook"
            )
        )
        var isEnableHookSharedPreferences
            get() = YukiHook.Config.hookSharedPreferences
            set(value) {
                YukiHook.Config.hookSharedPreferences = value
            }

        @Deprecated(
            message = "Use YukiHook.Config.dataChannel instead.",
            replaceWith = ReplaceWith("YukiHook.Config.dataChannel", "com.highcapable.yukihookapi.YukiHook")
        )
        var isEnableDataChannel
            get() = YukiHook.Config.dataChannel
            set(value) {
                YukiHook.Config.dataChannel = value
            }

        @Deprecated(message = "This property no longer has any effect.")
        var isEnableMemberCache = false
    }

    @Deprecated(
        message = "Use YukiHook.configure instead.",
        replaceWith = ReplaceWith("YukiHook.configure(block)", "com.highcapable.yukihookapi.YukiHook")
    )
    inline fun configs(block: Configs.() -> Unit) = Configs.apply(block).let {}

    @Deprecated(
        message = "Use YukiHook.encase instead.",
        replaceWith = ReplaceWith("YukiHook.encase(block)", "com.highcapable.yukihookapi.YukiHook")
    )
    fun encase(block: PackageParam.() -> Unit) = YukiHook.encase(block)

    @Deprecated(
        message = "Use YukiHook.encase instead.",
        replaceWith = ReplaceWith("YukiHook.encase(*hooker)", "com.highcapable.yukihookapi.YukiHook")
    )
    fun encase(vararg hooker: YukiBaseHooker) = YukiHook.encase(hookers = hooker)

    @Deprecated(
        message = "Use YukiHook.encase instead.",
        replaceWith = ReplaceWith(
            "YukiHook.encase(baseContext, block)",
            "com.highcapable.yukihookapi.YukiHook"
        )
    )
    fun encase(baseContext: Context?, block: PackageParam.() -> Unit) = YukiHook.encase(baseContext, block)

    @Deprecated(
        message = "Use YukiHook.encase instead.",
        replaceWith = ReplaceWith(
            "YukiHook.encase(baseContext, *hooker)",
            "com.highcapable.yukihookapi.YukiHook"
        )
    )
    fun encase(baseContext: Context?, vararg hooker: YukiBaseHooker) = YukiHook.encase(baseContext, hooker = hooker)
}