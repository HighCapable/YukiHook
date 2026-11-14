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
 * This file is created by fankes on 2022/8/14.
 */
@file:Suppress("QueryPermissionsNeeded")

package com.highcapable.yukihookapi.hook.xposed.parasitic

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.highcapable.betterandroid.system.extension.component.getParcelableExtraCompat
import com.highcapable.betterandroid.system.extension.component.registerReceiver
import com.highcapable.betterandroid.system.extension.utils.AndroidVersion
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.createInstanceAsType
import com.highcapable.kavaref.extension.createInstanceAsTypeOrNull
import com.highcapable.kavaref.extension.hasClass
import com.highcapable.kavaref.extension.isSubclassOf
import com.highcapable.kavaref.extension.lazyClass
import com.highcapable.kavaref.extension.toClass
import com.highcapable.kavaref.extension.toClassOrNull
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.core.api.handle.YukiHookHandle
import com.highcapable.yukihookapi.hook.core.api.helper.YukiHookHelper
import com.highcapable.yukihookapi.hook.core.internal.HiddenApiBypassResolver
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.bridge.XposedModuleRuntime
import com.highcapable.yukihookapi.hook.xposed.bridge.event.ModuleEventDispatcher
import com.highcapable.yukihookapi.hook.xposed.bridge.lifecycle.AppLifecycleBridge
import com.highcapable.yukihookapi.hook.xposed.bridge.resources.ModuleResources
import com.highcapable.yukihookapi.hook.xposed.bridge.session.ModuleReloadable
import com.highcapable.yukihookapi.hook.xposed.bridge.type.HookEntryType
import com.highcapable.yukihookapi.hook.xposed.channel.YukiHookDataChannel
import com.highcapable.yukihookapi.hook.xposed.parasitic.activity.config.ActivityProxyConfig
import com.highcapable.yukihookapi.hook.xposed.parasitic.activity.proxy.ModuleActivity
import com.highcapable.yukihookapi.runtime.YukiHookBridge
import com.highcapable.yukihookapi.runtime.YukiHookRuntime
import com.highcapable.yukihookapi.runtime.bridge.hooker.ClassicHookerBridge
import com.highcapable.yukihookapi.runtime.state.ModuleStateSpec
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Controller for app parasitism features.
 *
 * These features provide [Resources] injection and [Activity] proxies for the host environment.
 *
 * The initial partial solution was provided by [Parasitics](https://github.com/cinit/QAuxiliary/blob/main/app/src/main/java/io/github/qauxv/lifecycle/Parasitics.java)
 */
internal object AppParasitism : ModuleReloadable {

    const val RELOAD_ID = "host"

    /** The Android framework package name. */
    const val FRAMEWORK_PACKAGE_NAME = "android"

    /** The scope name of the system framework used by Xposed frameworks. */
    const val FRAMEWORK_SCOPE_NAME = "system"

    /** The process name of the system server, declared by the framework package. */
    const val SYSTEM_PROCESS_NAME = "system"

    /** The zygote nice name of the system server, also its command line. */
    const val SYSTEM_SERVER_NICE_NAME = "system_server"

    /** The range of UIDs allocated to each user, matching the hidden `UserHandle.PER_USER_RANGE`. */
    private const val PER_USER_RANGE = 100000

    override val requiresMainThread = true

    private val resolver = HiddenApiBypassResolver()

    private var events = ModuleEventDispatcher()

    /**
     * Host values retained across module instances.
     */
    private data class HostBindingsState(
        val packageName: String,
        val proxyClassName: String?,
        val hadApplication: Boolean
    ) {

        companion object : ModuleStateSpec<HostBindingsState> {

            override fun encode(state: HostBindingsState) = arrayOf<Any?>(
                state.packageName, state.proxyClassName, state.hadApplication
            )

            override fun decode(encoded: Any?): HostBindingsState {
                val values = encoded as? Array<*> ?: error("Missing host service reload state.")
                check(values.size == 3 && values[0] is String && (values[1] == null || values[1] is String) && values[2] is Boolean) {
                    "Invalid host service reload state."
                }

                return HostBindingsState(
                    packageName = values[0] as String,
                    proxyClassName = values[1] as String?,
                    hadApplication = values[2] as Boolean
                )
            }
        }
    }

    /** Whether the [Activity] proxy has been registered. */
    private var isActivityProxyRegistered = false

    /** Callbacks waiting for the host [Application]. */
    private val pendingApplicationCallbacks = mutableListOf<(Application) -> Unit>()

    /** Whether host lifecycle methods have been hooked. */
    private var isAppLifecycleInstalled = false

    /** Whether [Instrumentation.callApplicationOnCreate] has been hooked. */
    private var isApplicationOnCreateInstalled = false

    /** Whether system server initialization is waiting for the main looper. */
    private var isSystemServerInitializationPending = false

    /** Saved host bindings waiting for this module instance's lifecycle declarations. */
    private var pendingHostBindings: HostBindingsState? = null

    /** Lifecycle callbacks for the current host. */
    @Volatile
    private var lifecycles = emptyList<AppLifecycleBridge>()

    /** Lifecycle callbacks that have already received the host [Application]. */
    private val initializedLifecycles = mutableSetOf<AppLifecycleBridge>()

    /** Broadcast receivers registered for the current module instance. */
    private val lifecycleReceivers = mutableListOf<Pair<Context, BroadcastReceiver>>()

    /** Module activities tracked until they are destroyed. */
    private val moduleActivities = mutableListOf<WeakReference<Activity>>()

    /** Main-thread handler for delayed system server initialization. */
    private val lifecycleHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Package owning the first [Application] in this process. */
    private var lifecyclePackageName = ""

    private val ActivityThreadClass by lazyClass("android.app.ActivityThread")
    private val ContextImplClass by lazyClass("android.app.ContextImpl")

    /**
     * Global lifecycle [Application] of the current host.
     *
     * This is effective only when [YukiHook.Config.dataChannel] is enabled or [lifecycles] is not empty.
     */
    var hostApplication: Application? = null

    /**
     * The [ClassLoader] used in the current environment.
     *
     * Both the host and module environments use the [ClassLoader] from the current DEX.
     * @return [ClassLoader]
     * @throws IllegalStateException if the [ClassLoader] is null.
     */
    val baseClassLoader get() = classOf<YukiHook>().classLoader ?: error("The operating system is not supported.")

    /**
     * Gets the [Context] of the system framework in the current process.
     * @return [Context] a `ContextImpl` instance or null.
     */
    val systemContext get(): Context? {
        val scope = ActivityThreadClass.resolve()
            .processor(resolver)
            .optional(silent = true)
        val current = scope.firstMethodOrNull {
            name = "currentActivityThread"
            emptyParameters()
        }?.invoke()

        return scope.firstMethodOrNull {
            name = "getSystemContext"
            emptyParameters()
        }?.of(current)?.invokeQuietly<Context>()
    }

    /**
     * Gets the current host [Application].
     * @return [Application] or null.
     */
    val currentApplication get() = hostApplication ?: ActivityThreadClass.resolve()
        .processor(resolver)
        .optional(silent = true)
        .firstMethodOrNull { name = "currentApplication" }
        ?.invoke<Application>()

    /**
     * Gets the current host [ApplicationInfo].
     * @return [ApplicationInfo] or null.
     */
    val currentApplicationInfo get() = currentApplication?.applicationInfo ?: run {
        val scope = ActivityThreadClass.resolve()
            .processor(resolver)
            .optional(silent = true)
        val current = scope.firstMethodOrNull {
            name = "currentActivityThread"
            emptyParameters()
        }?.invoke()

        val currentScope = current?.asResolver()?.optional(silent = true)
        val mBoundApplication = currentScope?.firstFieldOrNull { name = "mBoundApplication" }?.get()
        val appScope = mBoundApplication?.asResolver()?.optional(silent = true)

        appScope?.firstFieldOrNull { name = "appInfo" }?.get<ApplicationInfo>()
    }

    /**
     * Checks whether [name] refers to the system framework.
     * @param name the package or scope name.
     * @return [Boolean]
     */
    fun isSystemFramework(name: String) = name == FRAMEWORK_PACKAGE_NAME || name == FRAMEWORK_SCOPE_NAME

    /**
     * Gets the current host package name.
     * @return [String]
     */
    val currentPackageName get() = currentApplicationInfo?.packageName ?: FRAMEWORK_PACKAGE_NAME

    /**
     * Gets the current host process name.
     * @return [String]
     */
    val currentProcessName get() = ActivityThreadClass.resolve()
        .processor(resolver)
        .optional(silent = true)
        .firstMethodOrNull { name = "currentProcessName" }
        ?.invoke<String>()
        ?.takeIf { it.isNotBlank() }
        // The system server never binds an application, so it has no process name here.
        ?: SYSTEM_PROCESS_NAME

    /**
     * Gets the user ID of the current process.
     *
     * The owner is 0. Cloned apps and work profiles have environment-dependent IDs.
     * @return [Int]
     */
    val currentUserId get() = Process.myUid() / PER_USER_RANGE

    /**
     * Hooks legacy module activation status and [SharedPreferences].
     * @param loader the module [ClassLoader].
     */
    fun hookLegacyModuleRelated(loader: ClassLoader?) {
        if (YukiHookBridge.runtime != YukiHookRuntime.XPOSED_82) return

        if (YukiHook.Config.hookSharedPreferences) ContextImplClass.resolve()
            .processor(resolver)
            .optional(silent = true)
            .firstMethodOrNull { name = "setFilePermissionsFromMode" }?.let {
                YukiHookHelper.hook(
                    it, object : ClassicHookerBridge {
                        override fun beforeHooked(param: ClassicHookerBridge.Param) {
                            if ((param.args[0] as? String?)?.endsWith("preferences.xml") == true) param.args[1] = 1
                        }
                    }
                )
            }
        YukiHookBridge.installLegacyModuleStatusHooks(loader)
    }

    /**
     * Registers lifecycle callbacks for the first [Application] in this process.
     * @param packageName the package owning the [Application].
     * @param entryType the current hook entry type.
     * @param build creates the lifecycle callbacks while this session accepts work.
     */
    fun registerAppLifecycle(packageName: String, entryType: HookEntryType, build: () -> AppLifecycleBridge) = events.use {
        val lifecycle = build()
        if (YukiHook.isStandalone) return@use

        synchronized(this) {
            prepareLifecyclePackage(packageName)
            lifecycles += lifecycle

            installAppLifecycleHooks()
            installApplicationOnCreateHook()
            if (entryType == HookEntryType.SYSTEM_SERVER && pendingHostBindings == null) scheduleSystemServerInitialization()
        }
    }

    /**
     * Records one callback until the host [Application] is available.
     *
     * The callback runs immediately if the [Application] is already available.
     * In the system server, it runs after system services start, like [AppLifecycleBridge.onCreate].
     * While restoring after a module reload, the restored host bindings run it with the existing [Application].
     * @param packageName the package owning the [Application].
     * @param entryType the current hook entry type.
     * @param callback the callback with the host [Application].
     */
    fun withApplicationReady(packageName: String, entryType: HookEntryType, callback: (Application) -> Unit) = events.use {
        if (YukiHook.isStandalone) return@use

        synchronized(this) {
            prepareLifecyclePackage(packageName)
            pendingApplicationCallbacks += callback

            // The system server [Application] already exists here, but its services have not started yet.
            if (entryType == HookEntryType.SYSTEM_SERVER) {
                if (pendingHostBindings == null) scheduleSystemServerInitialization()
                return@use
            }

            val application = currentApplication?.takeIf { it.packageName == lifecyclePackageName }
            if (pendingHostBindings == null && application != null) {
                hostApplication = application
                runPendingApplicationCallbacks(application)
            } else {
                installApplicationOnCreateHook()
                if (!isApplicationOnCreateInstalled) {
                    pendingApplicationCallbacks -= callback
                    YLog.innerE("An Application ready callback is dropped because the Application onCreate hook is unavailable")
                } else if (pendingHostBindings == null) {
                    currentApplication?.takeIf { it.packageName == lifecyclePackageName }?.let {
                        hostApplication = it
                        runPendingApplicationCallbacks(it)
                    }
                }
            }
        }
    }

    /**
     * Prepares services that require the first [Application] in this process.
     * @param packageName the package owning the [Application].
     * @param entryType the current hook entry type.
     */
    @Synchronized
    fun prepareHostApplication(packageName: String, entryType: HookEntryType) {
        prepareLifecyclePackage(packageName)

        if (YukiHook.Config.dataChannel || events.isManaged) installApplicationOnCreateHook()
        if (entryType == HookEntryType.SYSTEM_SERVER && pendingHostBindings == null) scheduleSystemServerInitialization()
    }

    /**
     * Assigns the package owning the first [Application] in this process.
     * @param packageName the package owning the [Application].
     */
    private fun prepareLifecyclePackage(packageName: String) {
        if (lifecyclePackageName.isEmpty()) lifecyclePackageName = packageName
    }

    /** Hooks lifecycle methods used by registered callbacks. */
    private fun installAppLifecycleHooks() {
        if (isAppLifecycleInstalled) return

        val hooked = mutableListOf<YukiHookHandle>()
        runCatching {
            Application::class.resolve().optional(silent = true).apply {
                YukiHookHelper.hook(firstMethod { name = "attach"; parameters(Context::class) }, object : ClassicHookerBridge {
                    override fun beforeHooked(param: ClassicHookerBridge.Param) {
                        val baseContext = param.args[0] as? Context ?: return
                        if (baseContext.packageName != lifecyclePackageName) return

                        param.dispatchAppLifecycle { onAttachBaseContext(baseContext, false) }
                    }

                    override fun afterHooked(param: ClassicHookerBridge.Param) {
                        val baseContext = param.args[0] as? Context ?: return
                        if (baseContext.packageName != lifecyclePackageName) return

                        param.dispatchAppLifecycle { onAttachBaseContext(baseContext, true) }
                    }
                }).let(hooked::add)
                YukiHookHelper.hook(firstMethod { name = "onTerminate" }, object : ClassicHookerBridge {
                    override fun afterHooked(param: ClassicHookerBridge.Param) {
                        val application = param.instance as? Application ?: return
                        // Some hosts create auxiliary Applications without attaching a Context, skip them before reading the package name.
                        if (application.baseContext == null || application.packageName != lifecyclePackageName) return

                        param.dispatchAppLifecycle { onTerminate(application) }
                    }
                }).let(hooked::add)
                YukiHookHelper.hook(firstMethod { name = "onLowMemory" }, object : ClassicHookerBridge {
                    override fun afterHooked(param: ClassicHookerBridge.Param) {
                        val application = param.instance as? Application ?: return
                        if (application.baseContext == null || application.packageName != lifecyclePackageName) return

                        param.dispatchAppLifecycle { onLowMemory(application) }
                    }
                }).let(hooked::add)
                YukiHookHelper.hook(firstMethod { name = "onTrimMemory"; parameters(Int::class) }, object : ClassicHookerBridge {
                    override fun afterHooked(param: ClassicHookerBridge.Param) {
                        val application = param.instance as? Application ?: return
                        if (application.baseContext == null || application.packageName != lifecyclePackageName) return
                        val level = param.args[0] as? Int ?: return

                        param.dispatchAppLifecycle { onTrimMemory(application, level) }
                    }
                }).let(hooked::add)
                YukiHookHelper.hook(
                    firstMethod { name = "onConfigurationChanged"; parameters(Configuration::class) },
                    object : ClassicHookerBridge {
                        override fun afterHooked(param: ClassicHookerBridge.Param) {
                            val application = param.instance as? Application ?: return
                            if (application.baseContext == null || application.packageName != lifecyclePackageName) return
                            val config = param.args[0] as? Configuration ?: return

                            param.dispatchAppLifecycle { onConfigurationChanged(application, config) }
                        }
                    }
                ).let(hooked::add)
            }

            isAppLifecycleInstalled = true
        }.onFailure {
            hooked.forEach { handle -> handle.remove() }
            events.reportFailure(it)
            YLog.innerE("App lifecycle initialization failed", it)
        }
    }

    /** Hooks [Instrumentation.callApplicationOnCreate] for lifecycle callbacks and the data channel. */
    private fun installApplicationOnCreateHook() {
        if (isApplicationOnCreateInstalled) return

        runCatching {
            YukiHookHelper.hook(
                Instrumentation::class.resolve().optional(silent = true).firstMethod {
                    name = "callApplicationOnCreate"
                    parameters(Application::class)
                }, object : ClassicHookerBridge {
                    override fun afterHooked(param: ClassicHookerBridge.Param) {
                        val application = param.args[0] as? Application ?: return
                        if (application.baseContext == null || application.packageName != lifecyclePackageName) return

                        initializeHostApplication(application)?.let { param.throwable = it }
                    }
                }
            )

            isApplicationOnCreateInstalled = true
        }.onFailure {
            events.reportFailure(it)
            YLog.innerE("Application onCreate initialization failed", it)
        }
    }

    /**
     * Initializes host-dependent features when the host [Application] is available.
     * @param application the host [Application].
     */
    private fun initializeHostApplication(application: Application) = bindHostApplication(application, dispatchCreate = true).also {
        YukiHookDataChannel.get().registerToHost(application)
    }

    /**
     * Binds listeners to an existing [Application], only a real creation event dispatches `onCreate`.
     * @param application the host [Application].
     * @param dispatchCreate whether to dispatch the `onCreate` event.
     * @return [Throwable] if an error occurs, or null if successful.
     */
    private fun bindHostApplication(application: Application, dispatchCreate: Boolean): Throwable? {
        hostApplication = application
        runPendingApplicationCallbacks(application)

        val pendingLifecycles = synchronized(this) {
            lifecycles.filter { initializedLifecycles.add(it) }
        }
        return pendingLifecycles.dispatchAppLifecycle {
            if (dispatchCreate) onCreate(application)

            val lifecycle = this
            val actionFilters = receiverActions.map { (actions, exported, callback) ->
                Triple(IntentFilter().apply { actions.forEach { action -> addAction(action) } }, exported, callback)
            }
            (actionFilters + receiverFilters).forEach { (filter, exported, callback) ->
                // Workaround: Android 14+ requires one of the export flags
                // for non-system broadcasts, so RECEIVER_NOT_EXPORTED must be passed explicitly here.
                val flags = if (exported) null else ContextCompat.RECEIVER_NOT_EXPORTED
                application.registerReceiver(filter, flags, exported) { context, intent ->
                    events.dispatch {
                        // Follow the same failure policy as the other lifecycle callbacks of this registration.
                        listOf(lifecycle).dispatchAppLifecycle { callback(context, intent) }?.let { throw it }
                    }
                }.also { synchronized(lifecycleReceivers) { lifecycleReceivers += application to it } }
            }
        }
    }

    /**
     * Runs an operation with the host [Application] already bound to this module instance.
     * @param block the operation to run with the host [Application].
     */
    inline fun withHostApplication(block: (Application) -> Unit) {
        hostApplication?.let(block)
    }

    /** Schedules system server initialization after its services finish starting. */
    @Synchronized
    private fun scheduleSystemServerInitialization() {
        if (isSystemServerInitializationPending) return

        isSystemServerInitializationPending = true
        lifecycleHandler.post {
            events.dispatch {
                synchronized(this) { isSystemServerInitializationPending = false }
                initializeReadyHostApplication()
            }
        }
    }

    /** Initializes the system server [Application] created before the runtime callback. */
    private fun initializeReadyHostApplication() {
        currentApplication
            ?.takeIf { it.packageName == lifecyclePackageName }
            ?.let(::initializeHostApplication)
            ?.let { YLog.innerE("An exception occurred while dispatching an AppLifecycle event", e = it) }
    }

    /** Dispatches one lifecycle event without letting one registration interrupt the others. */
    private inline fun ClassicHookerBridge.Param.dispatchAppLifecycle(
        crossinline callback: AppLifecycleBridge.() -> Unit
    ) {
        lifecycles.dispatchAppLifecycle(callback)?.let { throwable = it }
    }

    /** Dispatches one lifecycle event and returns the first failure intended for the host. */
    private inline fun Iterable<AppLifecycleBridge>.dispatchAppLifecycle(
        crossinline callback: AppLifecycleBridge.() -> Unit
    ): Throwable? {
        var failureToHost: Throwable? = null
        forEach { lifecycle ->
            runCatching { lifecycle.callback() }.onFailure { failure ->
                events.reportFailure(failure)
                if (lifecycle.throwToHostOnFailure) {
                    if (failureToHost == null) failureToHost = failure
                } else YLog.innerE("An exception occurred while dispatching an AppLifecycle event", e = failure)
            }
        }
        return failureToHost
    }

    /**
     * Injects the current Xposed module's resources into the host.
     * @param hostResources the host [Resources] to inject.
     */
    fun injectModuleResources(hostResources: Resources) {
        if (!XposedModuleRuntime.isHostEnvironment)
            return YLog.innerW("Module resources can only be injected in the host environment")
        if (currentPackageName == XposedModuleRuntime.modulePackageName)
            return YLog.innerE("Module resources cannot be injected into the module itself")

        ModuleResources.inject(hostResources, XposedModuleRuntime.moduleSourceDir) { throwable ->
            throwable?.let { YLog.innerE("Failed to inject module resources into $hostResources", it) }
        }
    }

    /**
     * Registers the current Xposed module's [Activity] in the host.
     * @param context the current [Context].
     * @param proxyClass the proxy class of [Activity].
     */
    @RequiresApi(AndroidVersion.N)
    @Synchronized
    fun registerModuleActivities(context: Context, proxyClass: String?) {
        if (isActivityProxyRegistered || !canRegisterModuleActivities(context.packageName)) return
        installModuleActivities(context, proxyClass)
    }

    /**
     * Runs the callbacks waiting for the host [Application].
     * @param application the host [Application].
     */
    @Synchronized
    private fun runPendingApplicationCallbacks(application: Application) {
        val callbacks = pendingApplicationCallbacks.toList().also { pendingApplicationCallbacks.clear() }
        callbacks.forEach { callback ->
            runCatching { callback(application) }.onFailure {
                events.reportFailure(it)
                YLog.innerE("An exception occurred while running an Application ready callback", it)
            }
        }
    }

    /**
     * Checks whether the current Xposed module's [Activity] can be registered in the host.
     * @param packageName the package owning the [Application].
     * @return [Boolean]
     */
    private fun canRegisterModuleActivities(packageName: String): Boolean {
        if (!XposedModuleRuntime.isHostEnvironment) {
            YLog.innerW("The Activity proxy can only be registered in the host environment")
            return false
        }
        if (packageName == XposedModuleRuntime.modulePackageName) {
            YLog.innerE("The Activity proxy cannot be registered in the module itself")
            return false
        }
        if (AndroidVersion.isAtMost(AndroidVersion.M)) {
            YLog.innerE("The Activity proxy requires Android 7.0 (API 24) or higher")
            return false
        }

        return true
    }

    /**
     * Installs the current Xposed module's [Activity] in the host.
     * @param context the current [Context].
     * @param proxyClass the proxy class of [Activity].
     */
    private fun installModuleActivities(context: Context, proxyClass: String?) {
        fun String.toModuleActivityClassOrNull() = toClassOrNull(baseClassLoader)?.takeIf {
            it isSubclassOf Activity::class && it isSubclassOf ModuleActivity::class
        }

        // The declared proxy is fixed for each `Activity` class, resolve it once instead of on every launch.
        val declaredProxyClassNames = ConcurrentHashMap<Class<*>, String>()

        fun Class<*>.resolveDeclaredProxyClassName(): String? {
            val declared = runCatching { createInstanceAsType<ModuleActivity>().proxyClassName }.getOrElse {
                YLog.innerW("Failed to read the proxy Activity declared by \"$name\", the default proxy is used instead", it)
                return null
            }.takeIf { it.isNotBlank() } ?: return null

            // The host [Application] may not be bound yet when activities are registered directly on a [Context].
            if ((hostApplication?.classLoader ?: context.classLoader).hasClass(declared)) return declared

            YLog.innerW("The proxy Activity \"$declared\" declared by \"$name\" was not found in the host, the default proxy is used instead")
            return null
        }

        fun Class<*>.declaredProxyClassName() =
            declaredProxyClassNames.getOrPut(this) { resolveDeclaredProxyClassName().orEmpty() }.ifEmpty { null }

        fun Intent.wrap(): Intent {
            val component = this.component ?: return this
            if (component.packageName != currentPackageName) return this
            val activityClass = component.className.toModuleActivityClassOrNull() ?: return this

            val proxyClassName = activityClass.declaredProxyClassName() ?: ActivityProxyConfig.proxyClassName

            return Intent().apply {
                setClassName(component.packageName, proxyClassName)
                putExtra(ActivityProxyConfig.proxyIntentName, this@wrap)
            }
        }

        val handles = mutableListOf<YukiHookHandle>()
        runCatching {
            ActivityProxyConfig.apply {
                proxyIntentName = "${XposedModuleRuntime.modulePackageName}.ACTIVITY_PROXY_INTENT"
                proxyClassName = proxyClass?.takeIf { it.isNotBlank() } ?: context.packageManager?.runCatching {
                    queryIntentActivities(getLaunchIntentForPackage(context.packageName)!!, 0)
                        .first()
                        .activityInfo.name
                }?.getOrNull() ?: ""

                val checkIsActivity = proxyClassName.toClassOrNull(context.classLoader)
                    ?.resolve()?.optional(silent = true)
                    ?.firstMethodOrNull {
                        name = "setIntent"
                        parameters(Intent::class)
                        superclass()
                    } != null

                if (!checkIsActivity) {
                    if (proxyClassName.isBlank())
                        error("Could not get the launch intent for package \"${context.packageName}\"")
                    else error("Could not find \"$proxyClassName\", or it is not an Activity.")
                }
            }

            val sCurrentActivityThread = ActivityThreadClass.resolve()
                .processor(resolver)
                .optional(silent = true)
                .firstFieldOrNull { name = "sCurrentActivityThread" }
                ?.get()
            val instrumentation = sCurrentActivityThread?.asResolver()
                ?.processor(resolver)
                ?.optional(silent = true)
                ?.firstMethodOrNull { name = "getInstrumentation" }
                ?.invoke<Instrumentation>() ?: error("Could not find Instrumentation in ActivityThread.")
            val instrumentationResolver = instrumentation.asResolver().processor(resolver)

            val performLaunchActivity = ActivityThreadClass.resolve()
                .processor(resolver)
                .firstMethod {
                    name = "performLaunchActivity"
                    parameters($$"android.app.ActivityThread$ActivityClientRecord", Intent::class)
                }
            val newActivityResolver = instrumentationResolver.firstMethod {
                name = "newActivity"
                parameters(ClassLoader::class, String::class, Intent::class)
                superclass()
            }

            fun callActivityOnCreateResolver(types: Array<out KClass<*>>) =
                instrumentationResolver.firstMethod {
                    name = "callActivityOnCreate"
                    parameters(*types)
                    superclass()
                }

            val callActivityOnCreateResolvers = listOf(
                arrayOf(Activity::class, Bundle::class),
                arrayOf(Activity::class, Bundle::class, PersistableBundle::class)
            ).map { types -> callActivityOnCreateResolver(types) }

            val managerClasses = buildList {
                add(AndroidVersion.require(AndroidVersion.O, "android.app.ActivityManagerProxy") {
                    $$"android.app.IActivityManager$Stub$Proxy"
                })
                if (AndroidVersion.isAtLeast(AndroidVersion.Q)) add($$"android.app.IActivityTaskManager$Stub$Proxy")
            }
            val startActivityResolvers = managerClasses.flatMap {
                it.toClass().resolve()
                    .processor(resolver)
                    .optional(silent = true)
                    .method {
                        name = "startActivity"
                        parameters { args -> classOf<Intent>() in args }
                    }
            }
            check(startActivityResolvers.isNotEmpty()) {
                "Could not find ActivityManager startActivity methods."
            }

            // Resolve each overload separately so a custom `Instrumentation` can override only one of them.
            YukiHookHelper.hook(newActivityResolver, object : ClassicHookerBridge {
                override fun afterHooked(param: ClassicHookerBridge.Param) {
                    if (param.instance !== instrumentation) return
                    if (param.throwable != null) {
                        val className = param.args[1] as? String ?: return
                        className.toModuleActivityClassOrNull()
                            ?.createInstanceAsTypeOrNull<Activity>()
                            ?.let { param.result = it }
                    }

                    // Track construction, not just onCreate: reload can arrive between the two.
                    val activity = param.result as? Activity
                    if (events.isManaged && activity is ModuleActivity) moduleActivities += WeakReference(activity)
                }
            }).let(handles::add)
            callActivityOnCreateResolvers.forEach { resolver ->
                YukiHookHelper.hook(resolver, object : ClassicHookerBridge {
                    override fun beforeHooked(param: ClassicHookerBridge.Param) {
                        if (param.instance !== instrumentation) return
                        val activity = param.args[0] as? Activity ?: return
                        if (activity !is ModuleActivity) return

                        (param.args[1] as? Bundle)?.classLoader = baseClassLoader
                        injectModuleResources(activity.resources)
                    }
                }).let(handles::add)
            }
            YukiHookHelper.hook(performLaunchActivity, object : ClassicHookerBridge {
                override fun beforeHooked(param: ClassicHookerBridge.Param) {
                    val record = param.args[0] ?: return

                    val intentResolver = record.asResolver().processor(resolver)
                        .firstField { name = "intent" }
                    val intent = intentResolver.get<Intent>() ?: return
                    intent.setExtrasClassLoader(currentApplication?.classLoader)
                    if (!intent.hasExtra(ActivityProxyConfig.proxyIntentName)) return

                    // Android 12 can cache this record before the launch transaction executes.
                    // Restore the actual record consumed by ActivityThread instead of the transaction item.
                    val original = intent.getParcelableExtraCompat<Intent>(ActivityProxyConfig.proxyIntentName) ?: return
                    intentResolver.set(original)
                }
            }).let(handles::add)
            startActivityResolvers.forEach { resolver ->
                YukiHookHelper.hook(resolver, object : ClassicHookerBridge {
                    override fun beforeHooked(param: ClassicHookerBridge.Param) {
                        val args = param.args

                        val index = args.indexOfFirst { it is Intent }
                        if (index >= 0) args[index] = (args[index] as Intent).wrap()
                    }
                }).let(handles::add)
            }

            isActivityProxyRegistered = true
        }.onFailure {
            handles.forEach { handle -> handle.remove() }
            YLog.innerE("Failed to initialize the Activity proxy", it)
            events.reportFailure(it)
        }
    }

    override fun onAttach(events: ModuleEventDispatcher) {
        this.events = events
    }

    override fun onPrepareReload() {
        moduleActivities.removeAll { it.get()?.isDestroyed != false }
        check(moduleActivities.isEmpty()) {
            "Close module activities in the host before reloading."
        }
    }

    override fun onSaveState(recovery: Boolean): Any {
        val application = if (recovery) currentApplication else hostApplication

        return HostBindingsState.encode(
            HostBindingsState(
                packageName = if (recovery)
                    lifecyclePackageName.ifEmpty { application?.packageName.orEmpty() }
                else lifecyclePackageName,
                proxyClassName = if (isActivityProxyRegistered)
                    ActivityProxyConfig.proxyClassName
                else null,
                hadApplication = application != null
            )
        )
    }

    override fun onDispose() {
        lifecycleHandler.removeCallbacksAndMessages(null)

        val receivers = synchronized(lifecycleReceivers) {
            lifecycleReceivers.toList().also { lifecycleReceivers.clear() }
        }

        var failure: Throwable? = null
        receivers.forEach { (context, receiver) ->
            runCatching { context.unregisterReceiver(receiver) }.onFailure {
                if (failure == null) failure = it else failure.addSuppressed(it)
            }
        }

        lifecycles = emptyList()
        synchronized(this) { initializedLifecycles.clear() }
        hostApplication = null
        pendingApplicationCallbacks.clear()
        moduleActivities.clear()
        failure?.let { throw it }
    }

    override fun onPrepareRestore(appInfo: ApplicationInfo, state: Any?) {
        pendingHostBindings = HostBindingsState.decode(state)
    }

    override fun onRestoreState(appInfo: ApplicationInfo, state: Any?) = runCatching {
        val saved = checkNotNull(pendingHostBindings) {
            "Host service bindings were not prepared."
        }

        prepareLifecyclePackage(saved.packageName)

        val application = currentApplication?.takeIf { it.packageName == lifecyclePackageName }

        // An initial-load failure may have missed Application creation; bind the real instance without replaying onCreate.
        if (application != null)
            bindHostApplication(application, dispatchCreate = false)?.let(events::reportFailure)

        // Register module activities if the host is running on Android N or higher.
        if (AndroidVersion.isAtLeast(AndroidVersion.N) &&
            !isActivityProxyRegistered && saved.proxyClassName != null && application != null
        ) registerModuleActivities(application, saved.proxyClassName)
    }.also { pendingHostBindings = null }.getOrThrow()
}