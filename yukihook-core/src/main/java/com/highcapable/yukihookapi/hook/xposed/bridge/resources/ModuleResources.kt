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
 * This file is created by fankes on 2022/4/29.
 */
@file:Suppress("DEPRECATION", "ClassName")

package com.highcapable.yukihookapi.hook.xposed.bridge.resources

import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.highcapable.betterandroid.system.extension.utils.AndroidVersion
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.core.internal.HiddenApiBypassResolver
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.bridge.session.ModuleReloadable
import java.io.File

/**
 * The module APK resources.
 */
class ModuleResources private constructor(private val baseInstance: Resources) : Resources(
    baseInstance.assets,
    baseInstance.displayMetrics,
    baseInstance.configuration
) {

    internal companion object : ModuleReloadable {

        const val RELOAD_ID = "module-resources"

        override val restorePhase = ModuleReloadable.RestorePhase.PREREQUISITE
        override val requiresMainThread = true

        private var hasLegacyInjection = false

        private val resolver = HiddenApiBypassResolver()

        /**
         * Loads the module APK into a separate [AssetManager] without modifying host resources.
         * @param path the APK path.
         * @return [ModuleResources] or null when the resources cannot be created.
         */
        fun from(path: String) = runCatching {
            val assets = AssetManager::class.resolve()
                .optional(silent = true)
                .firstConstructorOrNull { emptyParameters() }
                ?.create() ?: error("Unable to create AssetManager.")
            val system = getSystem()
            val resources = Resources(assets, system.displayMetrics, system.configuration)

            assets.addAssetPath(path).getOrThrow()
            ModuleResources(resources)
        }.onFailure {
            YLog.error("Failed to create module resources from $path", it)
        }.getOrNull()

        /**
         * Injects the APK into host [Resources] synchronously.
         *
         * On Android 11 (API 30) or later, call this function on the main thread.
         * Resource loaders participate in system resource rebuilding, older Android versions use asset paths.
         * @param host the host resources.
         * @param path the APK path.
         * @param callback the callback to receive the result of the injection.
         */
        fun inject(host: Resources, path: String, callback: (Throwable?) -> Unit = {}) {
            fun Result<*>.notify() = onSuccess { callback(null) }.onFailure { callback(it) }

            if (AndroidVersion.isAtLeast(AndroidVersion.R)) runCatching {
                require(path.isNotBlank()) { "The APK path is unavailable." }
                Api30_Impl.inject(host, path)
            }.notify() else host.assets.addAssetPath(path).onSuccess { hasLegacyInjection = true }.notify()
        }

        override fun onPrepareReload() {
            check(!hasLegacyInjection) {
                "Reload cannot replace resources injected through addAssetPath. Restart the host to try again."
            }
        }

        override fun onSaveState(recovery: Boolean) = if (AndroidVersion.isAtLeast(AndroidVersion.R)) Api30_Impl.loader else null

        override fun onRestoreState(appInfo: ApplicationInfo, state: Any?) {
            if (AndroidVersion.isAtLeast(AndroidVersion.R) && state != null)
                Api30_Impl.restore(state, appInfo.sourceDir)
        }

        /**
         * Adds the APK path to an existing [Resources] instance.
         * @receiver the target assets.
         * @param path the APK path.
         * @return [Result]
         */
        private fun AssetManager.addAssetPath(path: String) = runCatching {
            require(path.isNotBlank()) { "The APK path is unavailable." }

            // `addAssetPath` is hidden on Android. Use the existing hidden-API resolver rather
            // than `XModuleResources`, which is unavailable to modern modules.
            val cookie = this.asResolver()
                .processor(resolver)
                .optional(silent = true)
                .firstMethodOrNull {
                    name = "addAssetPath"
                    parameters(String::class)
                }?.invoke<Int>(path)
            check(cookie != null && cookie != 0) { "Unable to load resources from $path" }
        }

        /**
         * Keep API 30 types out of the initialization path used by older Android versions.
         */
        @RequiresApi(AndroidVersion.R)
        private object Api30_Impl {

            var loader: ResourcesLoader? = null
                private set

            fun inject(host: Resources, path: String) {
                val moduleLoader = loader ?: ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    ResourcesLoader().apply {
                        // The provider duplicates the descriptor, so closing it does not unload the APK.
                        addProvider(ResourcesProvider.loadFromApk(descriptor))
                    }.also { loader = it }
                }

                try {
                    // Unlike `addAssetPath`, loaders are recorded in `ResourcesKey` and survive `ResourcesImpl` rebuilding.
                    // Reusing the same loader also lets `Resources.addLoaders` handle duplicate injection.
                    host.addLoaders(moduleLoader)
                } catch (cause: IllegalArgumentException) {
                    if (cause.message != "Cannot modify resource loaders of ResourcesImpl not registered with ResourcesManager") throw cause

                    // A framework-replaced `ResourcesImpl` may no longer have a `ResourcesManager` record.
                    // Only this registration mismatch falls back to mutating its current `AssetManager`.
                    host.assets.addAssetPath(path).getOrThrow()
                    hasLegacyInjection = true
                }
            }

            fun restore(state: Any, path: String) {
                val previous = state as? ResourcesLoader ?: error("Invalid resource loader handoff.")
                val provider = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use {
                    ResourcesProvider.loadFromApk(it)
                }

                val obsolete = previous.providers.toList()
                runCatching {
                    previous.providers = listOf(provider)
                }.onFailure { failure ->
                    if (provider !in previous.providers) runCatching {
                        provider.close()
                    }.onFailure(failure::addSuppressed)
                }.getOrThrow()

                loader = previous
                obsolete.forEach { it.close() }
            }
        }
    }

    /**
     * Synchronizes the configuration copied from the current host resources.
     *
     * A separately constructed [Resources] is not managed by the host's resource manager,
     * so configuration changes are not delivered to it automatically.
     * @param resources the current host resources.
     * @return [ModuleResources]
     */
    internal fun syncConfiguration(resources: Resources) = apply {
        if (configuration != resources.configuration || displayMetrics != resources.displayMetrics)
            updateConfiguration(resources.configuration, resources.displayMetrics)
    }

    override fun toString() = "ModuleResources($baseInstance)"
}