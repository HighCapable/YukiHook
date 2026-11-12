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
 * This file is created by fankes on 2026/9/6.
 */
package com.highcapable.yukihookapi.runtime

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.factory.preferences
import org.junit.Assert
import org.junit.Test

/**
 * Verifies legacy module preference storage when no framework service is available.
 */
class RuntimeIsolationTest {

    private class StorageRequested : RuntimeException()

    @Test
    fun reloadIsUnavailableAndDoesNotPretendToReturnAnEmptyTargetList() {
        val reload = YukiHook.Module.reload
        Assert.assertFalse(reload.isAvailable)
        Assert.assertThrows(IllegalStateException::class.java) { reload.targets }
    }

    @Test
    fun modulePreferencesPropagateReadableAccessFailure() {
        val modes = mutableListOf<Int>()
        val context = object : ContextWrapper(null) {
            override fun getPackageName() = "test.module"
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                modes += mode
                throw StorageRequested()
            }
        }
        val prefs = context.preferences()
        Assert.assertThrows(StorageRequested::class.java) { prefs.getBoolean("key", true) }
        Assert.assertThrows(StorageRequested::class.java) { prefs.edit() }
        @Suppress("DEPRECATION")
        Assert.assertEquals(
            listOf(Context.MODE_WORLD_READABLE, Context.MODE_WORLD_READABLE),
            modes
        )
    }
}