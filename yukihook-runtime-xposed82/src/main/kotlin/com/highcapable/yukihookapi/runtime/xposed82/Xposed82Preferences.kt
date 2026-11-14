/*
 * YukiHook - A modern hook API and Xposed module solution.
 * Copyright (C) 2019 HighCapable
 * https://github.com/HighCapable/YukiHook
 *
 * Apache License Version 2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
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
package com.highcapable.yukihookapi.runtime.xposed82

import android.content.Context
import android.content.SharedPreferences
import com.highcapable.yukihookapi.runtime.YukiHookBridge
import com.highcapable.yukihookapi.runtime.bridge.PreferencesBridge
import de.robv.android.xposed.XSharedPreferences

/**
 * Framework-readable module storage shared through [XSharedPreferences].
 */
internal object Xposed82Preferences {

    fun open(hostEnvironment: Boolean) = if (hostEnvironment) Host else Module

    private object Module : PreferencesBridge {

        private val modulePreferences = mutableMapOf<String, SharedPreferences>()

        override val isAvailable get() = YukiHookBridge.isModuleActive

        @Synchronized
        override fun open(context: Context?, packageName: String, name: String): SharedPreferences {
            if (context == null) return Host.open(context, packageName, name)
            return modulePreferences.getOrPut(context.toString() + name) {
                @Suppress("DEPRECATION", "WorldReadableFiles")
                context.getSharedPreferences(name, Context.MODE_WORLD_READABLE)
            }
        }
    }

    /**
     * Isolated because [XSharedPreferences] is not present in an ordinary module process.
     */
    private object Host : PreferencesBridge {

        private val hostPreferences = mutableMapOf<String, XSharedPreferences>()

        override val isAvailable get() = YukiHookBridge.isAvailable

        @Synchronized
        override fun open(context: Context?, packageName: String, name: String): SharedPreferences {
            check(YukiHookBridge.isAvailable) {
                "XSharedPreferences requires an attached Xposed host or a module context."
            }
            val prefs = hostPreferences.getOrPut("$packageName/$name") { XSharedPreferences(packageName, name) }

            @Suppress("DEPRECATION")
            prefs.makeWorldReadable()
            prefs.reload()

            return prefs
        }
    }
}