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
import com.highcapable.yukihookapi.hook.factory.preferences
import org.junit.Assert
import org.junit.Test

/**
 * Checks module preference storage without an attached framework service.
 */
class RuntimeIsolationTest {

    private class ModuleContext : ContextWrapper(null) {

        var requestedMode = -1

        override fun getPackageName() = "test.module"

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            requestedMode = mode
            throw StorageRequested()
        }
    }

    private class StorageRequested : RuntimeException()

    @Test
    fun missingServiceRejectsReadsAndWritesWithoutOpeningPrivateStorage() {
        val context = ModuleContext()
        val preferences = context.preferences()

        Assert.assertFalse(preferences.isAvailable)
        Assert.assertFalse(preferences.isWritable)
        Assert.assertThrows(IllegalStateException::class.java) { preferences.getString("missing", "default") }
        Assert.assertThrows(IllegalStateException::class.java) { preferences.edit() }
        Assert.assertEquals(-1, context.requestedMode)
    }

    @Test
    fun explicitNativeStorageDoesUseTheLocalContext() {
        var requestedMode = -1
        val context = object : ContextWrapper(null) {
            override fun getPackageName() = "test.module"
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                requestedMode = mode
                error("Local storage requested")
            }
        }
        Assert.assertThrows(IllegalStateException::class.java) { context.preferences().native().getString("test") }
        Assert.assertEquals(Context.MODE_PRIVATE, requestedMode)
    }
}