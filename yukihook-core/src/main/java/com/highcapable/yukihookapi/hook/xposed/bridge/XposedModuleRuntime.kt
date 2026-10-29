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
 * This file is created by fankes on 2022/4/3.
 * This file is modified by fankes on 2023/1/9.
 */
@file:Suppress("SameParameterValue")

package com.highcapable.yukihookapi.hook.xposed.bridge

import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.os.Bundle
import android.os.Process
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.param.context.PackageContext
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.hook.xposed.bridge.component.HookRegistration
import com.highcapable.yukihookapi.hook.xposed.bridge.component.PackageRegistration
import com.highcapable.yukihookapi.hook.xposed.bridge.component.ParcelCreatorCache
import com.highcapable.yukihookapi.hook.xposed.bridge.component.RetainedValuesStore
import com.highcapable.yukihookapi.hook.xposed.bridge.event.ModuleEventDispatcher
import com.highcapable.yukihookapi.hook.xposed.bridge.lifecycle.XposedModuleLifecycle
import com.highcapable.yukihookapi.hook.xposed.bridge.resources.ModuleResources
import com.highcapable.yukihookapi.hook.xposed.bridge.session.ModuleLifecycleManager
import com.highcapable.yukihookapi.hook.xposed.bridge.session.ModuleReloadable
import com.highcapable.yukihookapi.hook.xposed.bridge.session.state.ModuleHandoffState
import com.highcapable.yukihookapi.hook.xposed.bridge.type.HookEntryType
import com.highcapable.yukihookapi.hook.xposed.channel.YukiHookDataChannel
import com.highcapable.yukihookapi.hook.xposed.parasitic.AppParasitism
import com.highcapable.yukihookapi.runtime.YukiHookBridge
import com.highcapable.yukihookapi.runtime.bridge.HookRegistrationBridge
import com.highcapable.yukihookapi.runtime.bridge.session.ModuleSessionBridge
import kotlin.reflect.KType

/**
 * Core Xposed module runtime for [YukiHook].
 */
internal object XposedModuleRuntime : XposedModuleLifecycle {

    private var events = ModuleEventDispatcher()
    private val packageRegistration = PackageRegistration(::dispatchPackage)
    private val retainedValues = RetainedValuesStore()
    private var hookRegistration: HookRegistration? = null

    private val hookEntry = ThreadLocal<YukiHookXposedModule>()
    private val registeringFrameworkEvents = ThreadLocal<Boolean>()
    private var isFrameworkEventsRegistered = false

    /** Whether the Xposed module has been loaded. */
    private var isModuleLoaded = false

    /** The process ID and name reported by the runtime. */
    private var reportedProcess: Pair<Int, String>? = null

    /** The current Xposed module resources. */
    private var currentModuleResources: ModuleResources? = null

    /** The current [PackageParam] block callback. */
    var packageParamCallback: (PackageParam.() -> Unit)? = null

    /** The predefined Xposed module package name. */
    var modulePackageName = ""
        private set

    /** The current Xposed module source directory. */
    var moduleSourceDir = ""
        private set

    /** The current Xposed module application info. */
    var moduleAppInfo: ApplicationInfo? = null
        private set

    /**
     * Gets the current Xposed module resources synchronized with the host configuration.
     * @return [ModuleResources] or null.
     */
    val moduleResources get() = currentModuleResources
        ?.syncConfiguration(AppParasitism.currentApplication?.resources ?: Resources.getSystem())

    /**
     * Gets the name of the hook process currently running in the host.
     *
     * A process forked from zygote inherits the name reported in zygote, so the name only applies to the process that reported it.
     * @return [String]
     */
    val hostProcessName get() = reportedProcess?.takeIf { it.first == Process.myPid() }?.second ?: AppParasitism.currentProcessName

    /**
     * Gets whether the current environment is a host environment.
     * @return [Boolean]
     */
    val isHostEnvironment get() = YukiHookBridge.isAvailable && isModuleLoaded

    /**
     * Checks that registration runs inside this entry's `onInit` on the current thread.
     * @param entry the current [YukiHookXposedModule] entry.
     * @param name the name of the feature being registered.
     * @throws IllegalStateException if this entry is not being initialized on the current thread.
     */
    fun checkInitializingEntry(entry: YukiHookXposedModule, name: String) = check(hookEntry.get() === entry) {
        "$name must be registered in onInit of the current process."
    }

    /**
     * Checks that package hooks have not been declared before `onHook` runs.
     * @throws IllegalStateException if a package callback was already registered.
     */
    fun checkHookLoading() = check(packageParamCallback == null) {
        "Do not load hookers in onInit."
    }

    /**
     * Runs the entry's initialization with its registration context bound to this thread.
     * @param entry the current [YukiHookXposedModule] entry.
     * @param block the initialization block.
     */
    fun createHookEntry(entry: YukiHookXposedModule, block: () -> Unit) {
        hookEntry.set(entry)

        try {
            block()
        } finally {
            hookEntry.remove()
        }
    }

    /**
     * Checks that a framework event listener is being configured inside the registration block.
     * @throws IllegalStateException if no registration block is running on the current thread.
     */
    fun checkFrameworkEventsRegistration() = check(registeringFrameworkEvents.get() == true) {
        "Framework event listeners must be configured inside `registerFrameworkEvents`."
    }

    /**
     * Registers one framework event configuration block during entry initialization.
     * @param entry the current [YukiHookXposedModule] entry.
     * @param block the framework event configuration block.
     * @throws IllegalStateException if called outside this entry's initialization or more than once.
     */
    fun registerFrameworkEvents(entry: YukiHookXposedModule, block: () -> Unit) {
        checkInitializingEntry(entry, "framework events")
        synchronized(this) {
            check(!isFrameworkEventsRegistered) {
                "Framework events can only be registered once per module process."
            }
            isFrameworkEventsRegistered = true
        }
        registeringFrameworkEvents.set(true)

        try {
            block()
        } finally {
            registeringFrameworkEvents.remove()
        }
    }

    /** Reloads the current Xposed module's [Resources]. */
    fun reloadModuleResources() {
        currentModuleResources = ModuleResources.from(moduleSourceDir)
    }

    /**
     * Retains a value across module instances, computing it if no saved value has the same declared type.
     * @param context the hook entry that owns [key].
     * @param type the declared result type, including type arguments and nullability.
     * @param key the versioned key of the result value.
     * @param block computes the value when no saved result has the same type.
     * @return [T]
     * @throws IllegalArgumentException if [key] is blank or already used in the same package scope.
     * @throws IllegalStateException if the result cannot be retained or the module instance is retiring.
     */
    fun <T> retainValue(context: PackageContext, type: KType, key: String, block: () -> T) = retainedValues.retain(context, type, key, block)

    /**
     * Attaches the runtime session and prepares hook registration before module loading.
     * @param session the current [ModuleSessionBridge] session.
     * @param registrations the current [HookRegistrationBridge] registrations.
     * @return [HookRegistrationBridge] for the current session.
     */
    fun attachModuleSession(session: ModuleSessionBridge, registrations: HookRegistrationBridge): HookRegistrationBridge {
        ModuleLifecycleManager.attach(session)

        events = ModuleLifecycleManager.events
        val component = HookRegistration(registrations, events)
        hookRegistration = component

        return component
    }

    /**
     * Dispatches a framework callback while this module instance accepts work.
     * @param block the framework callback block.
     */
    fun dispatchFrameworkEvent(block: () -> Unit) = ModuleLifecycleManager.events.dispatch(block)

    /**
     * Records a caught runtime callback failure.
     * @param failure the failure to record.
     */
    fun recordRestoreFailure(failure: Throwable) = ModuleLifecycleManager.recordRestoreFailure(failure)

    override fun onStartLoadModule(packageName: String, sourceDir: String, appInfo: ApplicationInfo?, processName: String) {
        reportProcessName(processName)
        attachModuleInfo(packageName, sourceDir, appInfo)
        reloadModuleResources()
        if (hookRegistration != null) registerLifecycleComponents()
    }

    override fun onPackageLoaded(
        type: HookEntryType,
        packageName: String,
        processName: String,
        mainProcessName: String,
        isFirstApplication: Boolean,
        packageNameForMatching: String?,
        appClassLoader: ClassLoader,
        appInfo: ApplicationInfo?,
        dispatch: Boolean
    ) {
        // Legacy zygote events run in zygote, whose name was reported when the module started loading.
        if (type != HookEntryType.ZYGOTE) reportProcessName(processName)

        val context = PackageContext(
            type = type,
            packageName = packageName,
            processName = processName,
            mainProcessName = mainProcessName,
            isFirstApplication = isFirstApplication,
            classLoader = appClassLoader,
            appInfo = appInfo,
            packageNameForMatching = packageNameForMatching
        )
        if (dispatch) events.dispatch { dispatchPackage(context) }
        else packageRegistration.record(context, retain = true)
    }

    override fun onPrepareReloadModule(extras: Bundle?, saveState: (Any) -> Boolean) = ModuleLifecycleManager.prepare(extras, saveState)

    override fun onPrepareFailedModule(appInfo: ApplicationInfo, saveState: (Any) -> Boolean): Boolean {
        // Binding may have failed before the entry established module identity or registered its components.
        attachModuleInfo(appInfo.packageName, appInfo.sourceDir, appInfo)
        registerLifecycleComponents()

        return ModuleLifecycleManager.prepareFailed(saveState)
    }

    override fun onBeginRestoreModule(appInfo: ApplicationInfo, state: Any?) = ModuleLifecycleManager.beginRestore(state).also {
        ModuleLifecycleManager.prepareRestore(appInfo, it)
        ModuleLifecycleManager.restoreComponents(appInfo, it, ModuleReloadable.RestorePhase.PREREQUISITE)
    }

    override fun onRestoreUserState(state: ModuleHandoffState) = ModuleLifecycleManager.restoreState(state)

    override fun onRestoreComponents(appInfo: ApplicationInfo, state: ModuleHandoffState) =
        ModuleLifecycleManager.restoreComponents(appInfo, state, ModuleReloadable.RestorePhase.REBUILD)

    override fun onFinishRestoreModule(extras: Bundle?) = ModuleLifecycleManager.finishRestore(extras)

    override fun onAbortRestoreModule() = ModuleLifecycleManager.abortRestore()

    override fun onRetireFailedRestore() = ModuleLifecycleManager.retire()

    private fun reportProcessName(name: String) {
        reportedProcess = Process.myPid() to name
    }

    private fun attachModuleInfo(packageName: String, sourceDir: String, appInfo: ApplicationInfo?) {
        isModuleLoaded = true
        modulePackageName = packageName
        moduleSourceDir = sourceDir
        moduleAppInfo = appInfo
    }

    private fun registerLifecycleComponents() {
        // The data channel caches host identity, so registration follows attachModuleInfo.
        ModuleLifecycleManager.register(ModuleResources.RELOAD_ID, ModuleResources)
        ModuleLifecycleManager.register(PackageRegistration.RELOAD_ID, packageRegistration)
        ModuleLifecycleManager.register(RetainedValuesStore.RELOAD_ID, retainedValues)
        ModuleLifecycleManager.register(AppParasitism.RELOAD_ID, AppParasitism)
        ModuleLifecycleManager.register(YukiHookDataChannel.RELOAD_ID, YukiHookDataChannel)
        // Disposed after the data channel stops receiving, so no later read adds the class loaders back.
        ModuleLifecycleManager.register(ParcelCreatorCache.RELOAD_ID, ParcelCreatorCache)
    }

    private fun dispatchPackage(context: PackageContext) {
        if (!packageRegistration.record(context)) return

        runCatching {
            val callback = {
                context.declare { packageParamCallback?.invoke(PackageParam(context).apply { YukiHook.sayHello() }) }
            }
            val component = hookRegistration
            if (component == null) callback() else component.withEntry(context.id, callback)

            if (context.type != HookEntryType.ZYGOTE && context.packageName == modulePackageName)
                AppParasitism.hookLegacyModuleRelated(context.classLoader)
            if (context.isFirstApplication) AppParasitism.prepareHostApplication(context.packageName, context.type)
        }.onFailure {
            events.reportFailure(it)
            YLog.innerE("An exception occurred in the hooking process of YukiHook", it)
        }
    }
}