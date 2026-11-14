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
 * This file is created by fankes on 2026/9/28.
 */
package com.highcapable.yukihookapi.runtime.bridge

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.RestrictTo

/**
 * Preferences storage contract implemented by the selected runtime.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface PreferencesBridge {

    /** Whether the selected runtime can provide shared preferences. */
    val isAvailable: Boolean

    /**
     * Opens the preferences selected by the current environment.
     * @param context the module context, if available.
     * @param packageName the module package name.
     * @param name the storage name.
     * @return [SharedPreferences]
     */
    fun open(context: Context?, packageName: String, name: String): SharedPreferences
}