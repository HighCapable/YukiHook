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
@file:Suppress("unused")

package com.highcapable.yukihookapi

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import com.highcapable.yukihookapi.annotation.internal.RequiresRuntime
import com.highcapable.yukihookapi.annotation.xposed.YukiHookXposed82Entry
import com.highcapable.yukihookapi.generated.YukiHookProperties
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.param.context.PackageContext
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.highcapable.yukihookapi.hook.xposed.bridge.XposedModuleRuntime
import com.highcapable.yukihookapi.hook.xposed.channel.YukiHookDataChannel
import com.highcapable.yukihookapi.hook.xposed.file.YukiHookSharedFiles
import com.highcapable.yukihookapi.hook.xposed.preference.YukiHookPreferences
import com.highcapable.yukihookapi.hook.xposed.reload.YukiHookModuleReload
import com.highcapable.yukihookapi.hook.xposed.scope.YukiHookModuleScope
import com.highcapable.yukihookapi.runtime.YukiHookBridge
import com.highcapable.yukihookapi.runtime.YukiHookRuntime
import com.highcapable.yukihookapi.runtime.bridge.FrameworkServiceBridge

/**
 * [YukiHook] entry point.
 *
 * Xposed module loading automatically adapts the relevant APIs. Call [encase] directly to complete the operation.
 */
object YukiHook {

    /** Whether the welcome message has not yet been logged. */
    private var isSayHelloOnceTime = true

    /** Whether loading originated from a standalone hook API. */
    internal var isStandalone = false

    /** The tag name. */
    const val TAG = YukiHookProperties.PROJECT_NAME

    /** The current version. */
    const val VERSION = YukiHookProperties.PROJECT_YUKIHOOK_BOM_VERSION

    /** The current schema version. */
    const val SCHEMA_VERSION = YukiHookProperties.PROJECT_YUKIHOOK_SCHEMA_VERSION

    /**
     * Configuration for [YukiHook].
     */
    object Config {

        /**
         * Configures [YLog.Config].
         * @param block the configuration block.
         */
        inline fun logging(block: YLog.Config.() -> Unit) = YLog.Config.apply(block).let {}

        /**
         * Whether debug mode is enabled, false by default.
         *
         * Once enabled, the log manager prints detailed hook logs to the console.
         *
         * Disabling [YLog.Config.enabled] also disables [debug].
         */
        @set:JvmName("setDebugEnabled")
        var debug = false

        /**
         * Whether hook support for [SharedPreferences] is enabled.
         *
         * Once enabled, module startup forces [SharedPreferences] file permissions to [Context.MODE_WORLD_READABLE] (0664).
         *
         * This optional experimental feature is disabled by default.
         * Use this only to fix file-permission errors that may remain on some systems after enabling the new `XSharedPreferences` in the legacy API.
         *
         * Do not enable it when [YukiHookPreferences] already works correctly.
         *
         * Requires framework injection into the module's own process.
         */
        @RequiresRuntime(YukiHookRuntime.XPOSED_82)
        var hookSharedPreferences = false

        /**
         * Whether [YukiHookDataChannel] between the current Xposed module and host is enabled.
         *
         * The Xposed module's [Application] must inherit [ModuleApplication].
         *
         * This feature is enabled by default. When disabled, initialization does not load [YukiHookDataChannel].
         */
        var dataChannel = true

        // region Deprecated properties for backward compatibility

        /** Whether debug mode is enabled. */
        @Deprecated(message = "Use debug instead.", replaceWith = ReplaceWith("debug"))
        var isDebug
            get() = debug
            set(value) {
                debug = value
            }

        /** Whether [YukiHookDataChannel] between the current Xposed module and host is enabled. */
        @Deprecated(message = "Use dataChannel instead.", replaceWith = ReplaceWith("dataChannel"))
        var isEnableDataChannel
            get() = dataChannel
            set(value) {
                dataChannel = value
            }

        /** Whether caching the current Xposed module resources is enabled. */
        @Deprecated(message = "This property is deprecated and has no effect. Remove it.")
        var isEnableModuleAppResourcesCache = true

        // endregion
    }

    /**
     * Current status for [YukiHook].
     */
    object Status {

        /**
         * The selected runtime, independently of module activation or hook availability.
         * @return [YukiHookRuntime]
         */
        val runtime get() = YukiHookBridge.runtime

        /**
         * Observes framework service availability. The listener is invoked on registration and on bind/death.
         *
         * Call it on the main thread. Callbacks run on the main thread, the first one runs immediately on registration.
         * Call [FrameworkServiceBridge.unregister] on the returned subscription to cancel it.
         *
         * An exception thrown by the listener is not caught, later callbacks crash the main thread just like the first one.
         *
         * Optionally pass a [Lifecycle] to automatically cancel the subscription when it is destroyed.
         * When provided, the subscription remains active while the lifecycle is stopped,
         * an already destroyed lifecycle does not register or invoke the listener.
         * @param lifecycle the optional lifecycle for automatic cancellation, null by default for manual cancellation only.
         * @param listener the listener notified when the framework service is available or unavailable.
         * @return [FrameworkServiceBridge] or null.
         */
        @RequiresRuntime(YukiHookRuntime.LIBXPOSED)
        @MainThread
        fun observeFrameworkService(lifecycle: Lifecycle? = null, listener: FrameworkServiceBridge) =
            YukiHookBridge.observeService(lifecycle, listener)

        /**
         * Gets the project compilation timestamp in milliseconds since the Unix epoch.
         * @return [Long]
         */
        val compiledTimestamp get() = runCatching { YukiHook_Impl.compiledTimestamp }.getOrNull() ?: 0L

        /**
         * Gets whether the current environment is a host environment.
         * @return [Boolean]
         */
        val isHostEnvironment get() = XposedModuleRuntime.isHostEnvironment

        /**
         * Checks whether module activation is detected through the selected Xposed runtime.
         *
         * Returns true when [isHostEnvironment] is true, independently of module services.
         *
         * With [YukiHookRuntime.XPOSED_82], module detection uses self-hook status. It requires
         *   [YukiHookXposed82Entry.generateModuleStatus] and framework injection into the module's own process.
         *
         * With [YukiHookRuntime.LIBXPOSED], module detection requires a single compatible framework service connection.
         *   [ModuleApplication] initializes it automatically. [observeFrameworkService] can also initialize it and observe changes.
         *   Binding is asynchronous: false before connection or after disconnection does not necessarily mean the module is disabled.
         *   The framework may keep the service connected after the module is disabled, so true does not guarantee
         *   that the module is still enabled in the manager. This is not a real-time query of the manager's enable/disable state.
         *
         * This does not verify the scope or whether hooks are installed in a particular target app.
         * @return [Boolean] whether activation is currently detected.
         */
        val isModuleActive get() = isHostEnvironment || YukiHookBridge.isModuleActive

        // region Deprecated properties for backward compatibility

        /** Gets whether module activation is detected through the selected Xposed runtime. */
        @Deprecated(message = "Use isModuleActive instead.", replaceWith = ReplaceWith("isModuleActive"))
        val isXposedModuleActive get() = isModuleActive

        /** Gets whether the current environment is a host environment. */
        @Deprecated(message = "Use isHostEnvironment instead.", replaceWith = ReplaceWith("isHostEnvironment"))
        val isXposedEnvironment get() = isHostEnvironment

        // endregion

        /**
         * Information about the hook framework used by the current [YukiHook].
         *
         * In a legacy module, [YukiHookXposed82Entry.generateModuleStatus] must be enabled.
         * In a modern module, these values follow framework-service availability.
         */
        object Executor {

            /**
             * Gets the current hook framework name.
             * @return [String] `unknown` when unavailable or `invalid` when resolution fails.
             */
            val name get() = YukiHookBridge.frameworkName

            /**
             * Gets the current hook framework API version.
             * @return [Int] -1 when unavailable.
             */
            val apiVersion get() = YukiHookBridge.apiVersion

            /**
             * Gets the current hook framework version.
             * @return [String] `unknown` when unavailable or `unsupported` when unsupported.
             */
            val version get() = YukiHookBridge.frameworkVersion

            /**
             * Gets the current hook framework version code.
             * @return [Long] -1 when unavailable or 0 when unsupported.
             */
            val versionCode get() = YukiHookBridge.frameworkVersionCode

            // region Deprecated properties for backward compatibility

            /** Gets the current hook framework API version. */
            @Deprecated(message = "Use apiVersion instead.", replaceWith = ReplaceWith("apiVersion"))
            val apiLevel get() = apiVersion

            /** Gets the current hook framework version. */
            @Deprecated(message = "Use version instead.", replaceWith = ReplaceWith("version"))
            val versionName get() = version

            // endregion
        }
    }

    /**
     * Module scope for [YukiHook].
     */
    object Module {

        /**
         * Gets the module-wide [YukiHookModuleReload] object.
         *
         * - Requires [YukiHookModuleReload.isAvailable].
         * @return [YukiHookModuleReload]
         */
        @get:RequiresRuntime(YukiHookRuntime.LIBXPOSED)
        val reload get() = YukiHookModuleReload.get()

        /**
         * Gets the module-wide [YukiHookModuleScope] object.
         *
         * - Requires [YukiHookModuleScope.isAvailable].
         * @return [YukiHookModuleScope]
         */
        @get:RequiresRuntime(YukiHookRuntime.LIBXPOSED)
        val scope get() = YukiHookModuleScope.get()

        /**
         * Gets the module-wide [YukiHookSharedFiles] object.
         *
         * - Requires [YukiHook.Status.isModuleActive].
         *
         * Files are writable from the module environment and read-only from a host environment.
         * This storage is independent of the module application's private file directory.
         * @return [YukiHookSharedFiles]
         */
        @get:RequiresRuntime(YukiHookRuntime.LIBXPOSED)
        val sharedFiles get() = YukiHookSharedFiles.get()
    }

    /**
     * Configures [YukiHook].
     * @param block the configuration block.
     */
    inline fun configure(block: Config.() -> Unit) = Config.apply(block).let {}

    /**
     * Loads the entry point for an Xposed module.
     * @param block the [PackageParam] hook block.
     * @throws IllegalStateException if called outside [YukiHookXposedModule.onHook].
     */
    fun encase(block: PackageParam.() -> Unit) {
        XposedModuleRuntime.checkHookLoading()
        isStandalone = false

        if (XposedModuleRuntime.isHostEnvironment)
            XposedModuleRuntime.packageParamCallback = block
        else logNotFoundHookApiError()
    }

    /**
     * Loads the entry point for an Xposed module.
     *
     * An empty [hookers] logs an error and loads nothing.
     * @param hookers the hookers.
     * @throws IllegalStateException if called outside [YukiHookXposedModule.onHook].
     */
    fun encase(vararg hookers: YukiBaseHooker) {
        XposedModuleRuntime.checkHookLoading()
        isStandalone = false

        if (XposedModuleRuntime.isHostEnvironment)
            XposedModuleRuntime.packageParamCallback = {
                if (hookers.isNotEmpty())
                    hookers.forEach { loadHooker(it) }
                else YLog.innerE("Failed to call \"encase\" because the hookers parameter is empty", isImplicit = true)
            }
        else logNotFoundHookApiError()
    }

    /**
     * Loads the legacy standalone hook API from an [Application].
     * @param baseContext the attachment base context.
     * @param block the [PackageParam] hook block.
     */
    @RequiresRuntime(YukiHookRuntime.XPOSED_82)
    @Deprecated(message = "Not recommended, use the hook framework's native API instead.")
    fun encase(baseContext: Context?, block: PackageParam.() -> Unit) {
        if (YukiHookBridge.runtime != YukiHookRuntime.XPOSED_82) return

        isStandalone = true
        when {
            YukiHookBridge.isStandaloneAvailable && baseContext != null -> PackageContext.from(baseContext).let { context ->
                context.declare { block(PackageParam(context).apply { sayHello() }) }
            }
            else -> logNotFoundHookApiError()
        }
    }

    /**
     * Loads the legacy standalone hook API from an [Application].
     * @param baseContext the attachment base context.
     * @param hooker the hooker.
     */
    @RequiresRuntime(YukiHookRuntime.XPOSED_82)
    @Deprecated(message = "Not recommended, use the hook framework's native API instead.")
    fun encase(baseContext: Context?, vararg hooker: YukiBaseHooker) {
        if (YukiHookBridge.runtime != YukiHookRuntime.XPOSED_82) return

        isStandalone = true
        if (YukiHookBridge.isStandaloneAvailable) {
            if (baseContext != null)
                if (hooker.isNotEmpty()) {
                    sayHello()

                    val context = PackageContext.from(baseContext)
                    val param = PackageParam(context)
                    context.declare { hooker.forEach { param.loadHooker(it) } }
                } else YLog.innerE("Failed to call \"encase\" because the hookers parameter is empty", isImplicit = true)
        } else logNotFoundHookApiError()
    }

    // region Deprecated functions for backward compatibility

    /** Configures [YukiHook]. */
    @Deprecated(message = "Use configure instead.", replaceWith = ReplaceWith("configure(block)"))
    inline fun configs(block: Config.() -> Unit) = configure(block)

    // endregion

    /** Shows a welcome message when [Config.debug] is enabled. */
    internal fun sayHello() {
        if (!Config.debug || !isSayHelloOnceTime) return

        isSayHelloOnceTime = false
        YLog.innerD("Welcome to YukiHook $VERSION! Running on ${Status.Executor.name} API ${Status.Executor.apiVersion}", isImplicit = true)
    }

    /** Logs an error when no hook API can be found. */
    private fun logNotFoundHookApiError() = YLog.innerE(
        "No available hook API was found in the current environment, aborting",
        isImplicit = true
    )
}