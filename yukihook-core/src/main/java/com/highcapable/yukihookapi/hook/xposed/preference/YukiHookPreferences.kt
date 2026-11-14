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
 * This file is created by fankes on 2022/2/8.
 */
@file:Suppress("unused", "MemberVisibilityCanBePrivate", "UNCHECKED_CAST")

package com.highcapable.yukihookapi.hook.xposed.preference

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceFragmentCompat
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.bridge.XposedModuleRuntime
import com.highcapable.yukihookapi.hook.xposed.parasitic.AppParasitism
import com.highcapable.yukihookapi.hook.xposed.preference.data.PreferenceData
import com.highcapable.yukihookapi.hook.xposed.preference.ui.ModulePreferenceFragment
import com.highcapable.yukihookapi.runtime.YukiHookBridge
import com.highcapable.yukihookapi.runtime.YukiHookRuntime

/**
 * [YukiHook] storage bridge implementation for [SharedPreferences] with extended storage options.
 *
 * Uses the storage selected by the current [YukiHookRuntime].
 *
 * Uses temporary in-memory storage while the runtime preferences are unavailable.
 * Check [isAvailable] before relying on persistent, shared data.
 *
 * To use [PreferenceFragmentCompat] in the module environment, [YukiHook] provides [ModulePreferenceFragment] with the same functionality.
 * @param context the context instance, null by default.
 */
class YukiHookPreferences private constructor(private var context: Context? = null) {

    internal companion object {

        private val isHostEnvironment = XposedModuleRuntime.isHostEnvironment
        private val inMemoryPreferences = mutableMapOf<Pair<String, String>, InMemoryPreferences>()

        /**
         * Creates a bridge for the module or the current host.
         * @param context the context instance, null by default.
         * @return [YukiHookPreferences]
         */
        fun from(context: Context? = null) = YukiHookPreferences(context)
    }

    /** Storage name. */
    private var preferenceName = ""

    /** Whether native storage is enabled. */
    private var isUsingNativeStorage = false

    private val runtime = YukiHookBridge.preferences(isHostEnvironment)

    /**
     * Gets the current storage name, package name + "_preferences" by default.
     * @return [String]
     */
    private val currentPreferenceName get() = preferenceName.ifBlank {
        if (isUsingNativeStorage) "${context?.packageName ?: "unknown"}_preferences"
        else "${XposedModuleRuntime.modulePackageName.ifBlank { context?.packageName ?: "unknown" }}_preferences"
    }

    /** Checks the API loading state. */
    private fun checkApi() {
        require(!YukiHook.isStandalone) {
            "YukiHookPreferences is not available in the standalone hook API."
        }
        if (isHostEnvironment) require(XposedModuleRuntime.modulePackageName.isNotBlank()) {
            "Failed to load the Xposed modulePackageName, check its configuration and rebuild the project."
        }
    }

    /**
     * Gets the preferences selected by the current runtime.
     * @return [SharedPreferences]
     */
    internal val current: SharedPreferences get() {
        checkApi()
        if (isUsingNativeStorage) return checkNotNull(context) {
            "Native preferences require a Context."
        }.getSharedPreferences(currentPreferenceName, Context.MODE_PRIVATE)

        val packageName = XposedModuleRuntime.modulePackageName.ifBlank { context?.packageName ?: "" }
        if (runtime.isAvailable) return runtime.open(context, packageName, currentPreferenceName)

        YLog.innerW(
            "Shared preferences \"$packageName/$currentPreferenceName\" are unavailable. Using temporary in-memory storage. " +
                "Changes are not saved to disk or shared with other processes, are lost when this process ends, " +
                "and will not be copied to shared storage when it becomes available."
        )
        return synchronized(inMemoryPreferences) {
            inMemoryPreferences.getOrPut(packageName to currentPreferenceName) { InMemoryPreferences() }
        }
    }

    // region Deprecated properties for backward compatibility

    /**
     * Gets the availability state of the current [YukiHookPreferences].
     *
     * - Migrate to [isAvailable] or [isWritable].
     * @return [Boolean]
     */
    @Deprecated(message = "Use isAvailable or isWritable instead.", level = DeprecationLevel.ERROR)
    val isPreferencesAvailable get() = false

    // endregion

    /**
     * Gets whether the current preferences is available.
     * @return [Boolean]
     */
    val isAvailable get() = if (isUsingNativeStorage) context != null else runtime.isAvailable

    /**
     * Gets whether the current preferences are writable.
     * @return [Boolean]
     */
    val isWritable get() = (!isHostEnvironment || isUsingNativeStorage) && isAvailable

    /**
     * Customizes the shared preferences storage name.
     * @param name the custom storage name.
     * @return [YukiHookPreferences]
     */
    fun name(name: String): YukiHookPreferences {
        preferenceName = name
        return this
    }

    /**
     * Ignores the current environment and uses [Context.getSharedPreferences] directly to access data.
     * @return [YukiHookPreferences]
     * @throws IllegalStateException if [context] is null.
     */
    fun native(): YukiHookPreferences {
        if (isHostEnvironment && context == null) context = AppParasitism.currentApplication
            ?: error("The host context has not been initialized yet, native storage cannot be used at this time.")
        isUsingNativeStorage = true

        return this
    }

    /**
     * Gets a [String] value.
     *
     * Detects the corresponding environment intelligently when reading key-value data.
     * Using [PreferenceData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, "" by default.
     * @return [String]
     */
    fun getString(key: String, value: String = "") = current.getString(key, value) ?: value

    /**
     * Gets a [Set] of [String] values.
     *
     * Detects the corresponding environment intelligently when reading key-value data.
     * Using [PreferenceData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param values the default values, an empty [MutableSet] of [String] values by default.
     * @return [Set]<[String]>
     */
    fun getStringSet(key: String, values: Set<String> = mutableSetOf()) = current.getStringSet(key, values) ?: values

    /**
     * Gets a [Boolean] value.
     *
     * Detects the corresponding environment intelligently when reading key-value data.
     * Using [PreferenceData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, false by default.
     * @return [Boolean]
     */
    fun getBoolean(key: String, value: Boolean = false) = current.getBoolean(key, value)

    /**
     * Gets an [Int] value.
     *
     * Detects the corresponding environment intelligently when reading key-value data.
     * Using [PreferenceData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, 0 by default.
     * @return [Int]
     */
    fun getInt(key: String, value: Int = 0) = current.getInt(key, value)

    /**
     * Gets a [Float] value.
     *
     * Detects the corresponding environment intelligently when reading key-value data.
     * Using [PreferenceData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, 0f by default.
     * @return [Float]
     */
    fun getFloat(key: String, value: Float = 0f) = current.getFloat(key, value)

    /**
     * Gets a [Long] value.
     *
     * Detects the corresponding environment intelligently when reading key-value data.
     * Using [PreferenceData] to create a template and [get] to retrieve data is recommended.
     * @param key the key name.
     * @param value the default value, 0L by default.
     * @return [Long]
     */
    fun getLong(key: String, value: Long = 0L) = current.getLong(key, value)

    /**
     * Gets a value of the specified type intelligently.
     * @param preference the key-value instance.
     * @param value the default value. The default is [PreferenceData.value] in [preference].
     * @return [T] which can only be [String], [Set] of [String], [Int], [Float], [Long], or [Boolean].
     */
    fun <T> get(preference: PreferenceData<T>, value: T = preference.value) = getPreferenceData(preference.key, value) as T

    /**
     * Gets a value of the specified type intelligently.
     * @param key the key.
     * @param value the default value.
     * @return [Any]
     */
    private fun getPreferenceData(key: String, value: Any?) = when (value) {
        is String -> getString(key, value)
        is Set<*> -> getStringSet(key, value as? Set<String> ?: error("Key-value type ${value.javaClass.name} is not allowed."))
        is Int -> getInt(key, value)
        is Float -> getFloat(key, value)
        is Long -> getLong(key, value)
        is Boolean -> getBoolean(key, value)
        else -> error("Key-value type ${value?.javaClass?.name} is not allowed.")
    }

    /**
     * Whether data for [key] exists.
     *
     * Detects the corresponding environment intelligently when reading key-value data.
     * @return [Boolean] whether the key exists.
     */
    fun contains(key: String) = current.contains(key)

    /**
     * Gets all stored key-value data.
     *
     * Detects the corresponding environment intelligently when reading key-value data.
     * Each call retrieves real-time data without cache control. Do not use this in highly concurrent scenarios.
     * @return [MutableMap] containing key-value data of all types.
     */
    fun all() = current.all.toMutableMap()

    /**
     * Creates a new [Editor].
     *
     * Module preferences are read-only in a host process, so the underlying storage rejects editing.
     * Call [native] first only when writing the host's private storage is intended.
     * @return [Editor]
     */
    fun edit() = Editor()

    /**
     * Creates a new [Editor].
     *
     * Calls [Editor.apply] automatically.
     *
     * Module preferences are read-only in a host process, so the underlying storage rejects editing.
     * Call [native] first only when writing the host's private storage is intended.
     * @param block the editing block.
     */
    inline fun edit(block: Editor.() -> Unit) = edit().apply(block).apply()

    /**
     * In-memory storage for [YukiHookPreferences] when the runtime preferences are unavailable.
     *
     * Changes are not saved to disk or shared with other processes, are lost when this process ends,
     * and will not be copied to shared storage when it becomes available.
     */
    private class InMemoryPreferences : SharedPreferences {

        private val values = mutableMapOf<String?, Any>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll() = synchronized(values) { values.toMutableMap() }
        override fun contains(key: String?) = synchronized(values) { values.containsKey(key) }
        override fun getString(key: String?, defValue: String?) = (value(key) as String?) ?: defValue
        override fun getStringSet(key: String?, defValues: Set<String>?) = (value(key) as Set<String>?)?.toSet() ?: defValues
        override fun getInt(key: String?, defValue: Int) = (value(key) as Int?) ?: defValue
        override fun getLong(key: String?, defValue: Long) = (value(key) as Long?) ?: defValue
        override fun getFloat(key: String?, defValue: Float) = (value(key) as Float?) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = (value(key) as Boolean?) ?: defValue

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            synchronized(values) { listeners.add(listener) }
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            synchronized(values) { listeners.remove(listener) }
        }

        override fun edit(): SharedPreferences.Editor {
            if (isHostEnvironment) throw UnsupportedOperationException("Module shared preferences are read-only in a host process.")
            return Editor()
        }

        private fun value(key: String?) = synchronized(values) { values[key] }

        private inner class Editor : SharedPreferences.Editor {

            private val changes = mutableMapOf<String?, Any?>()
            private var clear = false

            override fun putString(key: String?, value: String?) = put(key, value)
            override fun putStringSet(key: String?, values: Set<String>?) = put(key, values?.toSet())
            override fun putInt(key: String?, value: Int) = put(key, value)
            override fun putLong(key: String?, value: Long) = put(key, value)
            override fun putFloat(key: String?, value: Float) = put(key, value)
            override fun putBoolean(key: String?, value: Boolean) = put(key, value)
            override fun remove(key: String?) = put(key, null)
            override fun clear() = apply { synchronized(this) { clear = true } }

            override fun commit(): Boolean {
                val keys = mutableSetOf<String?>()
                val callbacks = synchronized(values) {
                    synchronized(this) {
                        if (clear) {
                            if (values.isNotEmpty()) keys.add(null)
                            values.clear()
                            clear = false
                        }
                        changes.forEach { (key, value) ->
                            when {
                                value == null -> {
                                    if (values.containsKey(key)) {
                                        values.remove(key)
                                        keys.add(key)
                                    }
                                }
                                values[key] != value -> {
                                    values[key] = value
                                    keys.add(key)
                                }
                            }
                        }
                        changes.clear()
                    }
                    listeners.toList()
                }

                if (keys.isNotEmpty() && callbacks.isNotEmpty()) {
                    val notify = Runnable {
                        keys.forEach { key ->
                            callbacks.forEach {
                                it.onSharedPreferenceChanged(this@InMemoryPreferences, key)
                            }
                        }
                    }
                    if (Looper.myLooper() == Looper.getMainLooper()) notify.run()
                    else Handler(Looper.getMainLooper()).post(notify)
                }

                // The values are kept in memory, but commit cannot report a successful persistent write.
                return false
            }

            override fun apply() {
                commit()
            }

            private fun put(key: String?, value: Any?) = apply { synchronized(this) { changes[key] = value } }
        }
    }

    /**
     * Storage proxy for [YukiHookPreferences].
     *
     * Use [edit] to obtain [Editor].
     *
     * Module preferences are read-only in a host process, so the underlying storage rejects editing.
     */
    inner class Editor internal constructor() : SharedPreferences.Editor {

        private val editor = current.edit()

        override fun remove(key: String) = apply { editor.remove(key) }

        override fun clear() = apply { editor.clear() }
        override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }

        override fun putStringSet(key: String?, values: Set<String?>?) = apply { editor.putStringSet(key, values) }
        override fun putBoolean(key: String, value: Boolean) = apply { editor.putBoolean(key, value) }
        override fun putInt(key: String, value: Int) = apply { editor.putInt(key, value) }
        override fun putFloat(key: String, value: Float) = apply { editor.putFloat(key, value) }
        override fun putLong(key: String, value: Long) = apply { editor.putLong(key, value) }

        /**
         * Stores a value of the specified type intelligently.
         * @param preference the key-value instance.
         * @param value the value to store. It can only be [String], [Set] of [String], [Int], [Float], [Long], or [Boolean].
         * @return [Editor]
         */
        fun <T> put(preference: PreferenceData<T>, value: T) = putPreferenceData(preference.key, value)

        /**
         * Removes the stored data for [PreferenceData.key].
         * @param preference the key-value instance.
         * @return [Editor]
         */
        fun remove(preference: PreferenceData<*>) = remove(preference.key)

        override fun commit() = editor.commit()
        override fun apply() = editor.apply()

        /**
         * Stores a value of the specified type intelligently.
         * @param key the key.
         * @param value the value to store. It can only be [String], [Set] of [String], [Int], [Float], [Long], or [Boolean].
         * @return [Editor]
         */
        private fun putPreferenceData(key: String, value: Any?) = when (value) {
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value as? Set<String> ?: error("Key-value type ${value.javaClass.name} is not allowed."))
            is Int -> putInt(key, value)
            is Float -> putFloat(key, value)
            is Long -> putLong(key, value)
            is Boolean -> putBoolean(key, value)
            else -> error("Key-value type ${value?.javaClass?.name} is not allowed.")
        }
    }
}