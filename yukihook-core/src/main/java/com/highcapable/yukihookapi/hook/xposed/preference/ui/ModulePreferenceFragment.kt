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
 * This file is created by fankes on 2022/4/17.
 */
package com.highcapable.yukihookapi.hook.xposed.preference.ui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.core.content.edit
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.xposed.preference.YukiHookPreferences
import com.highcapable.yukihookapi.runtime.YukiHookRuntime

/**
 * Extends preference support for Xposed modules built with [YukiHook].
 *
 * Inherits [PreferenceFragmentCompat] to provide a [PreferenceDataStore] that
 * uses [YukiHookPreferences] to store preferences in the module's app.
 *
 * Inherit this class instead of [PreferenceFragmentCompat].
 */
abstract class ModulePreferenceFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var current: SharedPreferences? = null
    private var isInitializing = false

    private val preferences by lazy {
        YukiHookPreferences.from(requireContext())
            .name(preferenceManager.sharedPreferencesName ?: "${requireContext().packageName}_preferences")
    }

    /**
     * Gets whether the current preferences are writable.
     * @return [Boolean]
     */
    protected val isWritable get() = preferences.isWritable

    final override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = createDataStore()

        if (!YukiHook.Status.isHostEnvironment && YukiHook.Status.runtime == YukiHookRuntime.LIBXPOSED)
            YukiHook.Status.observeFrameworkService(lifecycle) { available ->
                if (available || preferenceScreen == null) initializePreferences(savedInstanceState, rootKey, available)
                else releasePreferences()
            }
        else initializePreferences(savedInstanceState, rootKey)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) = Unit

    @CallSuper
    override fun onDestroy() {
        releasePreferences()
        super.onDestroy()
    }

    /**
     * Creates the preference screen using stored values when available, or defaults otherwise.
     *
     * Called again when the framework service connects or reconnects. Load preferences and register UI callbacks here.
     * Control whether preferences are enabled here; writes require available, writable storage.
     * @param savedInstanceState the saved fragment state.
     * @param rootKey the root preference key.
     */
    abstract fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?)

    private fun initializePreferences(savedInstanceState: Bundle?, rootKey: String?, available: Boolean = true) {
        releasePreferences()

        current = if (available) runCatching { preferences.current }.getOrNull() else null
        current?.registerOnSharedPreferenceChangeListener(this)

        preferenceScreen = null
        isInitializing = true

        try {
            onPreferencesReady(savedInstanceState, rootKey)
        } finally {
            isInitializing = false
        }
    }

    private fun releasePreferences() {
        current?.unregisterOnSharedPreferenceChangeListener(this)
        current = null
    }

    private fun createDataStore() = object : PreferenceDataStore() {

        private val storage get() = current ?: if (isInitializing) null else preferences.current

        private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
            // AndroidX persists defaults during initialization, even without writable storage.
            if (isInitializing && (current == null || YukiHook.Status.isHostEnvironment)) return
            (current ?: preferences.current).edit(action = block)
        }

        override fun getString(key: String, defValue: String?) = storage?.getString(key, defValue) ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?) = storage?.getStringSet(key, defValues) ?: defValues
        override fun getInt(key: String, defValue: Int) = storage?.getInt(key, defValue) ?: defValue
        override fun getLong(key: String, defValue: Long) = storage?.getLong(key, defValue) ?: defValue
        override fun getFloat(key: String, defValue: Float) = storage?.getFloat(key, defValue) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = storage?.getBoolean(key, defValue) ?: defValue
        override fun putString(key: String, value: String?) = edit {
            if (value == null) remove(key) else putString(key, value)
        }
        override fun putStringSet(key: String, values: Set<String>?) = edit {
            if (values == null) remove(key) else putStringSet(key, values)
        }
        override fun putInt(key: String, value: Int) = edit { putInt(key, value) }
        override fun putLong(key: String, value: Long) = edit { putLong(key, value) }
        override fun putFloat(key: String, value: Float) = edit { putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean) = edit { putBoolean(key, value) }
    }
}