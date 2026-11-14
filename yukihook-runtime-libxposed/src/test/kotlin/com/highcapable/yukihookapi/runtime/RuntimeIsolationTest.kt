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
import com.highcapable.yukihookapi.hook.log.YLog
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * Checks module preference storage without an attached framework service.
 */
class RuntimeIsolationTest {

    private val previousEnabled = YLog.Config.enabled

    @Before
    fun setUp() {
        YLog.Config.enabled = false
    }

    @After
    fun tearDown() {
        YLog.Config.enabled = previousEnabled
    }

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
    fun missingServiceUsesMemoryWithoutReportingSharedStorageAvailable() {
        val context = ModuleContext()
        val preferences = context.preferences().name("missing-service")

        preferences.edit { clear() }
        Assert.assertEquals("default", preferences.getString("missing", "default"))
        preferences.edit { putString("key", "temporary") }

        Assert.assertEquals("temporary", context.preferences().name("missing-service").getString("key"))
        Assert.assertFalse(preferences.isAvailable)
        Assert.assertFalse(preferences.isWritable)
        Assert.assertEquals(-1, context.requestedMode)
    }

    @Test
    fun memoryStoresAllValueTypesAndIsolatesNames() {
        val context = ModuleContext()
        val preferences = context.preferences().name("memory-types")
        val other = context.preferences().name("memory-other")
        preferences.edit { clear() }
        other.edit { clear() }

        preferences.edit {
            putString("string", "value")
            putStringSet("set", setOf("a", "b"))
            putInt("int", 7)
            putLong("long", Long.MAX_VALUE)
            putFloat("float", 1.25f)
            putBoolean("boolean", true)
        }

        Assert.assertEquals("value", preferences.getString("string"))
        Assert.assertEquals(setOf("a", "b"), preferences.getStringSet("set"))
        Assert.assertEquals(7, preferences.getInt("int"))
        Assert.assertEquals(Long.MAX_VALUE, preferences.getLong("long"))
        Assert.assertEquals(1.25f, preferences.getFloat("float"), 0f)
        Assert.assertTrue(preferences.getBoolean("boolean"))
        Assert.assertEquals(6, preferences.all().size)
        Assert.assertFalse(other.contains("string"))
        Assert.assertThrows(ClassCastException::class.java) { preferences.getInt("string") }
    }

    @Test
    fun memoryEditsAreBatchedAndClearPrecedesPendingWrites() {
        val preferences = ModuleContext().preferences().name("memory-editor")
        preferences.edit {
            clear()
            putString("existing", "old")
        }
        val editor = preferences.edit().putString("pending", "new").clear()

        Assert.assertEquals("old", preferences.getString("existing"))
        Assert.assertFalse(preferences.contains("pending"))
        val persisted = editor.commit()

        Assert.assertFalse(persisted)
        Assert.assertFalse(preferences.contains("existing"))
        Assert.assertEquals("new", preferences.getString("pending"))
        preferences.edit {
            putString("pending", null)
            putStringSet("set", setOf("value"))
        }
        preferences.edit { putStringSet("set", null) }
        Assert.assertTrue(preferences.all().isEmpty())
    }

    @Test
    fun availabilityDoesNotOpenNativeStorage() {
        val context = ModuleContext()
        val preferences = context.preferences().native()

        Assert.assertTrue(preferences.isAvailable)
        Assert.assertTrue(preferences.isWritable)
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