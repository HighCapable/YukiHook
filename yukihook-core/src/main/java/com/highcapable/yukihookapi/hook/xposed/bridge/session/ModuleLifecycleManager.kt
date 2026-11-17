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
 * This file is created by fankes on 2026/9/27.
 */
package com.highcapable.yukihookapi.hook.xposed.bridge.session

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.xposed.bridge.event.ModuleEventDispatcher
import com.highcapable.yukihookapi.hook.xposed.bridge.session.state.ModuleHandoffState
import com.highcapable.yukihookapi.hook.xposed.reload.lifecycle.ModuleLifecycle
import com.highcapable.yukihookapi.runtime.bridge.session.ModuleSessionBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Manages module lifecycle callbacks and transports component-owned state between module instances.
 */
internal object ModuleLifecycleManager {

    /** How long a reload operation waits for the host main thread, in seconds. */
    private const val MAIN_THREAD_TIMEOUT_SECONDS = 15L

    private var session: ModuleSessionBridge? = null
    private var lifecycle: ModuleLifecycle? = null

    @Volatile
    private var restoring = false

    private val components = mutableListOf<Component>()
    private val failures = mutableListOf<Throwable>()

    private class Component(
        val id: String,
        val instance: ModuleReloadable
    )

    private enum class StateKind { BUNDLE, REFERENCE }

    /** Entry-supplied dispatch for managed work and caught failures. */
    var events = ModuleEventDispatcher()
        private set

    /**
     * Attaches the runtime's module session without initializing host components.
     * @param session the runtime's module session.
     */
    fun attach(session: ModuleSessionBridge) {
        this.session = session
        events = ModuleEventDispatcher(session, ::recordRestoreFailure)
    }

    /**
     * Adds a component after host identity is established.
     * Events follow registration order.
     *
     * Retrying the same instance keeps its existing registration.
     * @param id the unique identifier for the component.
     * @param component the component instance to register.
     */
    fun register(id: String, component: ModuleReloadable) {
        val registered = components.firstOrNull { it.id == id }
        if (registered?.instance === component) return
        check(registered == null) {
            "Reload component '$id' is already registered."
        }

        component.onAttach(events)
        components.add(Component(id, component))
    }

    /**
     * Registers the user's optional lifecycle callbacks.
     * @param lifecycle the user's lifecycle callbacks.
     */
    fun register(lifecycle: ModuleLifecycle) {
        check(this.lifecycle == null) {
            "Module lifecycle callbacks can only be registered once per module instance."
        }

        this.lifecycle = lifecycle
    }

    /**
     * Checks state crossing module classloaders.
     * @param bundle the state to check.
     * @throws IllegalArgumentException if the state contains cycles or unsupported types.
     */
    fun checkBundle(bundle: Bundle?) = checkValue(bundle, StateKind.BUNDLE)

    /**
     * Checks in-process values passed between module instances.
     * @param value the value to check.
     * @throws IllegalStateException if the value retains module-defined types or the module class loader.
     */
    fun checkValue(value: Any?) = checkValue(value, StateKind.REFERENCE)

    /**
     * Checks and saves every component before asking the runtime to accept retirement.
     * @param extras optional extras to pass to the lifecycle callbacks.
     * @param saveState the callback to save the handoff state.
     * @return [Boolean] true if the module can be retired, false if the runtime should continue with the current module instance.
     */
    fun prepare(extras: Bundle?, saveState: (Any) -> Boolean): Boolean {
        val current = session ?: return false
        checkBundle(extras)
        if (lifecycle?.canReloadCallback?.invoke(extras) == false) return false

        components.forEach { it.call { onPrepareReload() } }
        if (!current.freeze()) return false

        var disposing = false
        return runCatching {
            val userState = lifecycle?.saveStateCallback?.invoke()
            checkBundle(userState)
            components.forEach { it.call { onPrepareReload() } }

            val componentStates = components.associate { component ->
                component.id to component.call { onSaveState(recovery = false) }
            }
            val state = ModuleHandoffState(userState, componentStates)
            if (!saveState(ModuleHandoffState.encode(state))) {
                current.resume()
                return false
            }

            disposing = true
            dispose()

            true
        }.onFailure {
            // Once module-owned disposal starts it cannot be rolled back automatically.
            if (!disposing) current.resume()
        }.getOrThrow()
    }

    /**
     * Saves the internal state available after entry initialization failed and retires the instance.
     * @param saveState the callback to save the handoff state.
     * @return [Boolean] true if the failed instance can retire, false while it is still busy.
     */
    fun prepareFailed(saveState: (Any) -> Boolean): Boolean {
        val current = session
        components.forEach { it.call { onPrepareReload() } }
        if (current != null && !current.freeze()) return false

        components.forEach { it.call { onPrepareReload() } }
        val componentStates = components.associate { component ->
            component.id to component.call { onSaveState(recovery = true) }
        }
        val state = ModuleHandoffState(userState = null, components = componentStates)
        if (!saveState(ModuleHandoffState.encode(state))) return false

        // A failed entry stays closed when saving or disposal fails; only a new instance may resume work.
        dispose()
        return true
    }

    /**
     * Decodes the handoff and checks that components requiring saved state have an entry.
     * @param state the handoff state to decode.
     * @return [ModuleHandoffState] the decoded handoff state.
     */
    fun beginRestore(state: Any?): ModuleHandoffState {
        val restored = ModuleHandoffState.decode(state)
        components.forEach {
            check(!it.instance.requiresSavedState || it.id in restored.components) {
                "Missing reload component '${it.id}'"
            }
        }
        synchronized(failures) { failures.clear() }
        restoring = true

        return restored
    }

    /**
     * Restores user state after onInit and before `onHook`.
     * @param state the state to restore.
     */
    fun restoreState(state: ModuleHandoffState) {
        checkBundle(state.userState)
        lifecycle?.restoreStateCallback?.invoke(state.userState)
    }

    /**
     * Lets components prepare their own saved prerequisites before declarations are replayed.
     * @param appInfo the new Xposed module application info.
     * @param state the state to restore.
     */
    fun prepareRestore(appInfo: ApplicationInfo, state: ModuleHandoffState) {
        components.forEach { component ->
            runCatching {
                component.call { onPrepareRestore(appInfo, state.components[component.id]) }
            }.onFailure(::recordRestoreFailure)
        }
    }

    /**
     * Dispatches restoration at an entry lifecycle boundary using each component's registered thread.
     * @param appInfo the new Xposed module application info.
     * @param state the state to restore.
     * @param phase the restore phase.
     */
    fun restoreComponents(appInfo: ApplicationInfo, state: ModuleHandoffState, phase: ModuleReloadable.RestorePhase) {
        components.filter { it.instance.restorePhase == phase }.forEach { component ->
            val result = runCatching { component.call { onRestoreState(appInfo, state.components[component.id]) } }

            // Prerequisites must succeed before module code runs.
            // Rebuild failures are collected so other components can continue.
            if (phase == ModuleReloadable.RestorePhase.PREREQUISITE)
                result.getOrThrow()
            else result.onFailure(::recordRestoreFailure)
        }
    }

    /**
     * Runs the completion callback and reports all failures collected during restoration.
     * @param extras optional extras to pass to the lifecycle callbacks.
     */
    fun finishRestore(extras: Bundle?) {
        runCatching { lifecycle?.reloadedCallback?.invoke(extras) }.onFailure(::recordRestoreFailure)
        restoring = false

        val errors = synchronized(failures) { failures.toList().also { failures.clear() } }
        if (errors.isNotEmpty()) throw IllegalStateException(
            "Module reload completed with ${errors.size} failure(s).",
            errors.first()
        ).apply { errors.drop(1).forEach(::addSuppressed) }
    }

    /** Closes a failed restore instance so it cannot receive new work before its retirement. */
    fun abortRestore() {
        restoring = false
        synchronized(failures) { failures.clear() }
        session?.close()
    }

    /**
     * Retires a failed restore instance once no managed callback is running.
     * @return [Boolean] true when the instance was retired, false while it is still busy.
     */
    fun retire(): Boolean {
        val current = session
        if (current != null && !current.freeze()) return false
        dispose()

        return true
    }

    /**
     * Records a failure without interrupting the remaining registrations.
     * @param failure the failure to record.
     */
    fun recordRestoreFailure(failure: Throwable) {
        if (restoring) synchronized(failures) { failures.add(failure) }
    }

    private fun checkValue(value: Any?, kind: StateKind) {
        val moduleLoader = if (kind == StateKind.REFERENCE) classOf<YukiHook>().classLoader else null
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

        fun checkClass(type: Class<*>) {
            var loader = generateSequence(type) { it.componentType }.last().classLoader
            while (loader != null) {
                check(loader !== moduleLoader) {
                    "Module state must not refer to a module-defined class: ${type.name}"
                }

                loader = loader.parent
            }
        }

        fun checkObject(current: Any?) {
            if (current == null || current is String || current is Boolean || current is Byte || current is Short ||
                current is Int || current is Long || current is Float || current is Double || current is Char ||
                current is BooleanArray || current is ByteArray || current is ShortArray || current is IntArray ||
                current is LongArray || current is FloatArray || current is DoubleArray || current is CharArray
            ) return

            if (kind == StateKind.REFERENCE) checkClass(current.javaClass)
            if (!visited.add(current)) {
                require(kind != StateKind.BUNDLE) { "Reload state must not contain cycles." }
                return
            }
            if (kind == StateKind.BUNDLE && current !is Bundle && current !is Array<*> && current !is ArrayList<*>)
                error("Reload state cannot contain ${current.javaClass.name}. Use primitive values or bundles.")

            when (current) {
                is Bundle -> {
                    // Typed Bundle getters cannot inspect unknown entries without coercion or type warnings.
                    // Resolve the public generic getter only for this classloader-boundary validation.
                    val getter = Bundle::class.resolve().firstMethod {
                        name = "get"
                        parameters(String::class)
                        superclass()
                    }.of(current)
                    current.keySet().forEach { checkObject(getter.invoke<Any>(it)) }
                }
                is Array<*> -> {
                    if (kind == StateKind.BUNDLE) {
                        val component = current.javaClass.componentType
                        require(component == classOf<Any>() || component == classOf<String>() || component == classOf<Bundle>()) {
                            "Reload arrays must contain Object, String or Bundle values."
                        }
                    }
                    current.forEach(::checkObject)
                }
                is Collection<*> -> {
                    if (kind == StateKind.BUNDLE) require(current.javaClass == classOf<ArrayList<*>>()) {
                        "Reload lists must not be module-defined subclasses."
                    }

                    current.forEach(::checkObject)
                }
                is Map<*, *> -> current.forEach { (key, value) ->
                    checkObject(key)
                    checkObject(value)
                }
                is Class<*> -> checkClass(current)
                is Method -> {
                    checkClass(current.declaringClass)
                    checkClass(current.returnType)
                    current.parameterTypes.forEach(::checkClass)
                    current.exceptionTypes.forEach(::checkClass)
                }
                is Constructor<*> -> {
                    checkClass(current.declaringClass)
                    current.parameterTypes.forEach(::checkClass)
                    current.exceptionTypes.forEach(::checkClass)
                }
                is Field -> {
                    checkClass(current.declaringClass)
                    checkClass(current.type)
                }
                is ClassLoader -> {
                    var loader: ClassLoader? = current
                    while (loader != null) {
                        check(loader !== moduleLoader) {
                            "Module state must not contain the module class loader."
                        }

                        loader = loader.parent
                    }
                }
            }

            if (kind == StateKind.BUNDLE) visited.remove(current)
        }

        checkObject(value)
    }

    private fun dispose() {
        var failure: Throwable? = null

        fun release(block: () -> Unit) = runCatching(block).onFailure {
            if (failure == null) failure = it else if (failure !== it) failure.addSuppressed(it)
        }
        release { lifecycle?.disposeCallback?.invoke() }

        components.forEach { component -> release { component.call { onDispose() } } }
        failure?.let { throw it }
    }

    private fun <T> Component.call(block: ModuleReloadable.() -> T): T {
        val value = instance
        return if (value.requiresMainThread) mainThread { value.block() } else value.block()
    }

    private fun <T> mainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()

        val task = FutureTask(block)
        check(Handler(Looper.getMainLooper()).post(task)) {
            "The host main thread is unavailable."
        }

        return try {
            task.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        } catch (failure: TimeoutException) {
            // A busy main thread must not block the reload thread forever, drop the task if it has not started yet.
            task.cancel(false)
            throw IllegalStateException("The host main thread did not respond within $MAIN_THREAD_TIMEOUT_SECONDS seconds.", failure)
        }
    }
}