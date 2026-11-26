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

package com.highcapable.yukihookapi.hook.core

import android.os.Bundle
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.core.api.handle.YukiHookHandle
import com.highcapable.yukihookapi.hook.core.api.helper.YukiHookHelper
import com.highcapable.yukihookapi.hook.core.api.priority.YukiHookPriority
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.HookChain
import com.highcapable.yukihookapi.hook.param.HookInvocation
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.runtime.YukiHookBridge
import com.highcapable.yukihookapi.runtime.bridge.hooker.ChainHookerBridge
import com.highcapable.yukihookapi.runtime.bridge.hooker.ClassicHookerBridge
import com.highcapable.yukihookapi.runtime.bridge.hooker.HookPoint
import com.highcapable.yukihookapi.runtime.bridge.hooker.HookerBridge
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

/**
 * Core [Member] hook implementation for [YukiHook].
 *
 * The core API is implemented through [YukiHookHelper].
 */
class YukiHookCreator private constructor() {

    internal companion object {

        /**
         * Creates a chain member hooker.
         * @param targets the points to hook.
         * @param priority the hook priority.
         * @param invocation the interceptor invoked for each call.
         * @return [ChainMemberHooker]
         */
        fun createChainHooker(targets: Collection<HookPoint>, priority: YukiHookPriority, invocation: HookChain.() -> Any?) =
            ChainMemberHooker(priority, invocation).also { it.targets.addAll(targets) }

        /**
         * Creates a classic member hooker.
         * @param targets the points to hook.
         * @param priority the hook priority.
         * @param isLazyMode whether registration waits until the hook block has been configured.
         * @return [ClassicMemberHooker]
         */
        fun createClassicHooker(targets: Collection<HookPoint>, priority: YukiHookPriority, isLazyMode: Boolean) =
            ClassicMemberHooker(priority, if (isLazyMode) HookMode.LAZY else HookMode.IMMEDIATE)
                .also { it.targets.addAll(targets) }

        /**
         * Checks that [value] can be returned by the method hooked in [invocation].
         *
         * Constructors, void methods and [Any] return types are not checked.
         * Null is accepted unless the method returns a primitive type, which the framework cannot unbox.
         * @param invocation the current invocation.
         * @param value the result to return.
         * @throws IllegalStateException if [value] does not match the method return type.
         */
        fun checkReturnType(invocation: HookInvocation, value: Any?) {
            val returnType = (invocation.member as? Method)?.returnType ?: return
            if (returnType == Void.TYPE || returnType == classOf<Any>()) return

            check(value != null || !returnType.isPrimitive) {
                "Hooked method return type mismatch, expected $returnType but got null"
            }
            if (value == null) return

            val expectedType = returnType.kotlin.javaObjectType
            check(expectedType.isInstance(value)) {
                "Hooked method return type mismatch, expected $returnType but got ${value.javaClass}"
            }
        }
    }

    /**
     * Base member registration and invocation-listener implementation.
     * @param priority the hook priority.
     */
    abstract class MemberHooker<I : HookInvocation> internal constructor(private val priority: YukiHookPriority) {

        private val registrationLock = Any()

        private var isHooked = false
        private var isRegistering = false
        private var isRemovalPending = false
        private var onCallbackFailureCallback: ((I, Throwable) -> Unit)? = null

        private var onInvocationFailureCallback: ((Throwable) -> Unit)? = null
        private val handles = mutableSetOf<YukiHookHandle>()

        /** Points selected for this registration group. */
        internal val targets = mutableSetOf<HookPoint>()

        /** The handle returned after registration. */
        protected abstract val handle: Handle

        /**
         * Removes every registered hook belonging to this hooker.
         * @param callback callback indicating whether any registrations were removed.
         */
        fun removeSelf(callback: (Boolean) -> Unit = {}) = handle.remove(callback)

        /**
         * Registers the configured hooker.
         * @return [Handle] for removal and invocation failure listeners.
         */
        internal open fun build() = handle.apply { hook() }

        /** Registers each point independently so one failure does not interrupt the remaining hooks. */
        internal fun hook() {
            if (!YukiHookBridge.isAvailable) return
            val pendingTargets = synchronized(registrationLock) {
                if (isHooked) return

                isHooked = true
                isRegistering = true
                targets.toList()
            }

            runCatching {
                pendingTargets.forEach { target ->
                    runCatching {
                        YukiHookHelper.hook(target, createHooker(target), priority).let(::storeRegistration)
                    }.onFailure { logHookerError(it, target) }
                }
            }.also {
                synchronized(registrationLock) {
                    isRegistering = false
                    isRemovalPending = false
                }
            }.getOrThrow()
        }

        /**
         * Stores a completed registration or removes it when removal was requested while registering.
         * @param registration the completed registration.
         */
        private fun storeRegistration(registration: YukiHookHandle) {
            val shouldRemove = synchronized(registrationLock) {
                if (!isRemovalPending) {
                    handles.add(registration)
                    false
                } else true
            }

            if (shouldRemove) runCatching {
                removeRegistration(registration)
            }.onFailure {
                synchronized(registrationLock) {
                    handles.add(registration)
                }
            }.getOrThrow()
        }

        /**
         * Removes one native registration outside [registrationLock].
         * @param registration the registration to remove.
         */
        private fun removeRegistration(registration: YukiHookHandle) {
            registration.remove()
            if (YukiHook.Config.debug) YLog.debug("Removed hooker for ${registration.target}")
        }

        /** Replaces each currently registered point with a new callback generation. */
        internal fun replaceRegistrations() {
            synchronized(registrationLock) { handles.toList() }.forEach { registration ->
                runCatching {
                    replaceRegistration(registration)
                }.getOrElse { throwable ->
                    if (throwable is UnsupportedOperationException) throw throwable
                    logHookerError(throwable, registration.target)
                }
            }
        }

        private fun replaceRegistration(registration: YukiHookHandle) {
            synchronized(registrationLock) {
                if (!handles.contains(registration)) return

                val replacement = YukiHookHelper.replace(registration, createHooker(registration.target))
                handles.remove(registration)
                handles.add(replacement)
            }
        }

        /**
         * Creates the runtime hooker for one point.
         * @param target the point being registered.
         * @return [HookerBridge]
         */
        internal abstract fun createHooker(target: HookPoint): HookerBridge

        /**
         * Casts this hooker to the required type.
         * @return [T]
         * @throws IllegalStateException if this hooker is not of the required type.
         */
        internal inline fun <reified T : MemberHooker<*>> asType() = this as? T ?: error("The current hooker is not an instance of ${classOf<T>().name}")

        /**
         * Notifies hooker callback failure listeners without choosing a recovery policy.
         * @param invocation the failed invocation.
         * @param throwable the callback failure.
         */
        protected fun notifyCallbackFailure(invocation: I, throwable: Throwable) {
            // A failing listener must not break the recovery policy of the hooker.
            onCallbackFailureCallback?.let { callback ->
                runCatching {
                    callback(invocation, throwable)
                }.onFailure { logListenerError(it, invocation) }
            }
            onInvocationFailureCallback?.let { callback ->
                runCatching {
                    callback(throwable)
                }.onFailure { logListenerError(it, invocation) }
            }

            if (onCallbackFailureCallback == null && onInvocationFailureCallback == null)
                YLog.error("Hook callback failed for ${invocation.member}", throwable)
        }

        private fun logHookerError(throwable: Throwable, target: Any) = YLog.error("Failed to hook $target", throwable)

        private fun logListenerError(throwable: Throwable, invocation: I) = YLog.error(
            "Hook callback failure listener failed for ${invocation.member}", throwable
        )

        override fun toString() = "${javaClass.simpleName}(members=$targets, priority=$priority, isHooked=$isHooked)"

        /**
         * Registration group handle and invocation failure listeners.
         *
         * Listeners observe subsequent invocation failures only.
         */
        open inner class Handle internal constructor() {

            /** The [MemberHooker] that owns this handle. */
            internal val owner get() = this@MemberHooker

            /**
             * Listens for hooker callback failures with the invocation context.
             *
             * This listener does not change the hooker's exception policy.
             * @param callback the listener receiving the concrete invocation and its failure.
             * @return [Handle]
             */
            open fun onCallbackFailure(callback: (I, Throwable) -> Unit): Handle {
                onCallbackFailureCallback = callback
                return this
            }

            /**
             * Suppresses default callback failure logging without changing exception propagation.
             * @return [Handle]
             */
            open fun ignoreCallbackFailure() = onCallbackFailure { _, _ -> }

            /**
             * Listens for invocation failures without receiving the invocation context.
             *
             * This listener is independent of [onCallbackFailure] and does not observe registration failures.
             * It does not change the hooker's exception policy.
             * @param callback the listener receiving the invocation failure.
             * @return [Handle]
             */
            open fun onInvocationFailure(callback: (Throwable) -> Unit): Handle {
                onInvocationFailureCallback = callback
                return this
            }

            /**
             * Suppresses default invocation failure logging without changing exception propagation.
             *
             * This does not replace the listener configured through [onCallbackFailure].
             * @return [Handle]
             */
            open fun ignoreInvocationFailure() = onInvocationFailure {}

            /**
             * Removes every registration belonging to this hooker.
             * @param callback callback indicating whether any registrations were removed.
             */
            fun remove(callback: (Boolean) -> Unit = {}) {
                val registrations = synchronized(registrationLock) {
                    val registrations = handles.toList()
                    handles.clear()

                    if (isRegistering) isRemovalPending = true
                    registrations
                }

                runCatching {
                    registrations.forEach(::removeRegistration)
                }.onFailure {
                    synchronized(registrationLock) {
                        handles.addAll(registrations)
                    }
                }.getOrThrow()

                callback(registrations.isNotEmpty())
            }

            // region Deprecated functions for backward compatibility

            /**
             * Listens for hooker callback failures through [onCallbackFailure].
             * @param callback the listener receiving the concrete invocation and its failure.
             * @return [Handle]
             */
            @Deprecated(message = "Use onCallbackFailure instead.", replaceWith = ReplaceWith("onCallbackFailure(callback)"))
            fun onConductFailure(callback: (I, Throwable) -> Unit) = onCallbackFailure(callback)

            /** Suppresses default callback failure logging through [ignoreCallbackFailure]. */
            @Deprecated(message = "Use ignoreCallbackFailure instead.", replaceWith = ReplaceWith("ignoreCallbackFailure()"))
            fun ignoredConductFailure() = ignoreCallbackFailure()

            /**
             * Listens for invocation failures through [onInvocationFailure].
             * @param callback the listener receiving the invocation failure.
             * @return [Handle]
             */
            @Deprecated(message = "Use onInvocationFailure instead.", replaceWith = ReplaceWith("onInvocationFailure(callback)"))
            fun onAllFailure(callback: (Throwable) -> Unit) = onInvocationFailure(callback)

            /**
             * Suppresses default invocation failure logging through [ignoreInvocationFailure].
             * @return [Handle]
             */
            @Deprecated(message = "Use ignoreInvocationFailure instead.", replaceWith = ReplaceWith("ignoreInvocationFailure()"))
            fun ignoredAllFailure() = ignoreInvocationFailure()

            /**
             * Configures this handle through [apply].
             * @param block the configuration block.
             * @return [Handle]
             */
            @Deprecated(message = "Use apply instead.", replaceWith = ReplaceWith("apply(block)"))
            inline fun result(block: Handle.() -> Unit) = apply(block)

            /**
             * This function has no effect and does not evaluate [condition].
             *
             * - Check the condition before registering hooks instead.
             * @param condition the unused condition block.
             * @return [Handle]
             */
            @Suppress("DeprecatedCallableAddReplaceWith")
            @Deprecated(message = "This API has no effect. Check the condition before registering hooks instead.")
            inline fun by(condition: () -> Boolean) = this

            /**
             * This function has no effect and does not invoke [callback].
             *
             * - Registration completes before this handle is returned. Remove this call.
             * @param callback the unused registration success listener.
             * @return [Handle]
             */
            @Suppress("DeprecatedCallableAddReplaceWith")
            @Deprecated(message = "This API has no effect. Remove this call.")
            fun onHooked(callback: (Member) -> Unit) = this

            /**
             * This function has no effect and does not invoke [callback].
             *
             * - Registration failures are logged before this handle is returned. Remove this call.
             * @param callback the unused registration failure listener.
             * @return [Handle]
             */
            @Suppress("DeprecatedCallableAddReplaceWith")
            @Deprecated(message = "This API has no effect. Remove this call.")
            fun onHookingFailure(callback: (Throwable) -> Unit) = this

            /**
             * This function has no effect and does not suppress registration failure logs.
             *
             * - Remove this call.
             * @return [Handle]
             */
            @Suppress("DeprecatedCallableAddReplaceWith")
            @Deprecated(message = "This API has no effect. Remove this call.")
            fun ignoredHookingFailure() = this

            // endregion
        }
    }

    /**
     * Chain interceptor implementation.
     * @param priority the hook priority.
     * @param invocation the interceptor invoked for each call.
     */
    class ChainMemberHooker internal constructor(
        priority: YukiHookPriority,
        private var invocation: HookChain.() -> Any?
    ) : MemberHooker<HookChain>(priority) {

        override val handle = Handle()

        private var dataExtra = Bundle()

        @Volatile
        private var throwToHostOnFailure = false

        override fun build() = handle.apply { hook() }

        /**
         * Replaces the current hooker with a new one.
         * @param invocation the new interceptor invoked for each call.
         */
        internal fun replace(invocation: HookChain.() -> Any?) {
            this.invocation = invocation
            dataExtra = Bundle()
            replaceRegistrations()
        }

        override fun createHooker(target: HookPoint): ChainHookerBridge {
            val invocationCount = AtomicLong()
            val callback = invocation
            val dataExtraStorage = dataExtra

            return ChainHookerBridge { chain ->
                val downstream = DownstreamChain(chain)
                val invocation = HookChain.from(dataExtraStorage, this@ChainMemberHooker, downstream)

                runCatching {
                    if (YukiHook.Config.debug) YLog.debug("Invoking intercept callback #${invocationCount.incrementAndGet()} for $target")
                    callback(invocation).also { checkReturnType(invocation, it) }
                }.getOrElse { throwable ->
                    // Failures from the remaining chain or the original member belong to the caller, not to this callback.
                    if (throwable === downstream.failure) throw throwable

                    notifyCallbackFailure(invocation, throwable)
                    if (throwToHostOnFailure) throw throwable

                    // Recover as if this hook did not exist, the remaining chain is never run twice.
                    if (downstream.isProceeded) downstream.outcome() else chain.proceed()
                }
            }
        }

        /**
         * Chain hook registration group handle and invocation failure listeners.
         *
         * Listeners observe subsequent invocation failures only.
         */
        inner class Handle internal constructor() : MemberHooker<HookChain>.Handle() {

            override fun onCallbackFailure(callback: (HookChain, Throwable) -> Unit) = apply { super.onCallbackFailure(callback) }
            override fun ignoreCallbackFailure() = apply { super.ignoreCallbackFailure() }
            override fun onInvocationFailure(callback: (Throwable) -> Unit) = apply { super.onInvocationFailure(callback) }
            override fun ignoreInvocationFailure() = apply { super.ignoreInvocationFailure() }

            /**
             * Throws an exception to the caller when one occurs in the callback body.
             *
             * By default, the invocation continues as if this hook did not exist.
             * Exceptions thrown by [HookChain.proceed] are always thrown to the caller.
             * @return [Handle]
             */
            fun throwToHostOnFailure() = apply { this@ChainMemberHooker.throwToHostOnFailure = true }
        }

        /**
         * Records the outcome of the remaining chain of one invocation.
         * @param chain the runtime chain.
         */
        private class DownstreamChain(private val chain: ChainHookerBridge.Chain) : ChainHookerBridge.Chain by chain {

            /** Whether the remaining chain has been called. */
            var isProceeded = false
                private set

            /** The failure thrown by the last call of the remaining chain, or null. */
            var failure: Throwable? = null
                private set

            private var result: Any? = null

            override fun proceed() = record { chain.proceed() }
            override fun proceed(args: Array<out Any?>) = record { chain.proceed(args) }
            override fun proceedWith(instance: Any) = record { chain.proceedWith(instance) }
            override fun proceedWith(instance: Any, args: Array<out Any?>) = record { chain.proceedWith(instance, args) }

            /**
             * Returns the result of the last call of the remaining chain, or throws its failure.
             * @return [Any] or null.
             */
            fun outcome(): Any? {
                failure?.let { throw it }
                return result
            }

            private inline fun record(block: () -> Any?): Any? {
                isProceeded = true
                return try {
                    block().also {
                        result = it
                        failure = null
                    }
                } catch (throwable: Throwable) {
                    result = null
                    failure = throwable
                    throw throwable
                }
            }
        }
    }

    /**
     * Classic before/after and interception hook implementation.
     * @param priority the hook priority.
     * @param hookMode the registration mode.
     */
    class ClassicMemberHooker internal constructor(
        priority: YukiHookPriority,
        private val hookMode: HookMode
    ) : MemberHooker<HookParam>(priority) {

        private class CallbackState {

            var beforeHookCallback: (HookParam.() -> Unit)? = null
            var afterHookCallback: (HookParam.() -> Unit)? = null
            var interceptHookCallback: (HookParam.() -> Any?)? = null

            var throwBeforeToHostOnFailure = false
            var throwAfterToHostOnFailure = false
            var throwInterceptToHostOnFailure = false

            var isInterceptHookMode = false

            var beforeDataExtra = Bundle()
            var afterDataExtra = Bundle()
            var interceptDataExtra = Bundle()
        }

        override val handle = Handle()

        private var state = CallbackState()

        override fun build() = handle.apply { hook() }

        /**
         * Replaces the current hooker with a new one.
         * @param hooker the new configuration block.
         */
        internal fun replace(hooker: ClassicMemberHooker.() -> Unit) {
            state = CallbackState()
            hooker(this)
            replaceRegistrations()
        }

        /**
         * Hooks before [Member] execution completes.
         *
         * Cannot be used together with [intercept].
         * @param callback the [HookParam] function body.
         * @return [HookCallback]
         */
        fun before(callback: HookParam.() -> Unit): HookCallback {
            val state = state
            state.isInterceptHookMode = false
            state.beforeHookCallback = callback
            immediateHook()

            return HookCallback { state.throwBeforeToHostOnFailure = true }
        }

        /**
         * Hooks after [Member] execution completes.
         *
         * Cannot be used together with [intercept].
         * @param callback the [HookParam] function body.
         * @return [HookCallback]
         */
        fun after(callback: HookParam.() -> Unit): HookCallback {
            val state = state
            state.isInterceptHookMode = false
            state.afterHookCallback = callback
            immediateHook()

            return HookCallback { state.throwAfterToHostOnFailure = true }
        }

        /**
         * Intercepts this [Member] with the callback return value.
         *
         * The callback return value is ignored for void methods and constructors.
         * Cannot be used together with [before] or [after].
         * @param callback the [HookParam] function body.
         * @return [HookCallback]
         */
        fun intercept(callback: HookParam.() -> Any?): HookCallback {
            val state = state
            state.isInterceptHookMode = true
            state.interceptHookCallback = callback
            immediateHook()

            return HookCallback { state.throwInterceptToHostOnFailure = true }
        }

        /**
         * Intercepts this [Member] with a constant return value.
         *
         * The [value] is ignored for void methods and constructors.
         *
         * Setting a [Member] with a constant return value such as [Int], [Long],
         * or [Boolean] to null may cause the host to throw an exception.
         * Cannot be used together with [before] or [after].
         * @param value the replacement constant value, null by default.
         * @return [HookCallback]
         */
        fun intercept(value: Any? = null) = intercept { value }

        // region Deprecated functions for backward compatibility

        /** Intercepts and replaces this [Member] with a return value. */
        @Deprecated(message = "Use intercept instead.", replaceWith = ReplaceWith("intercept(callback)"))
        fun replaceAny(callback: HookParam.() -> Any?) = intercept(callback)

        /** Intercepts and replaces this [Member] without a return value ([Unit]). */
        @Deprecated(message = "Use intercept instead.", replaceWith = ReplaceWith("intercept(callback)"))
        fun replaceUnit(callback: HookParam.() -> Unit) = intercept(callback)

        /** Intercepts and replaces the [Member] return value. */
        @Deprecated(message = "Use intercept instead.", replaceWith = ReplaceWith("intercept(any)"))
        fun replaceTo(any: Any?) = intercept(any)

        /** Intercepts and replaces the [Member] return value with true. */
        @Deprecated(message = "Use intercept instead.", replaceWith = ReplaceWith("intercept(true)"))
        fun replaceToTrue() = intercept(true)

        /** Intercepts and replaces the [Member] return value with false. */
        @Deprecated(message = "Use intercept instead.", replaceWith = ReplaceWith("intercept(false)"))
        fun replaceToFalse() = intercept(false)

        // endregion

        private fun immediateHook() {
            if (hookMode == HookMode.IMMEDIATE) hook()
        }

        override fun createHooker(target: HookPoint): ClassicHookerBridge {
            // The mode is fixed at registration, even if the creator is configured again later.
            val state = state
            val isInterception = state.isInterceptHookMode

            return object : ClassicHookerBridge {

                private val beforeInvocationCount = AtomicLong()
                private val afterInvocationCount = AtomicLong()

                override fun beforeHooked(param: ClassicHookerBridge.Param) {
                    val invocation = HookParam.from(
                        dataExtraStorage = if (isInterception) state.interceptDataExtra else state.beforeDataExtra,
                        this@ClassicMemberHooker, param
                    )

                    runCatching {
                        val callback = if (isInterception) state.interceptHookCallback else state.beforeHookCallback
                        if (callback != null && YukiHook.Config.debug) YLog.debug(
                            "Invoking ${if (isInterception) "intercept" else "before"} callback " +
                                "#${beforeInvocationCount.incrementAndGet()} for $target"
                        )

                        val value = callback?.invoke(invocation)
                        // An exception thrown to the host through `throwToHost` takes the place of the returned value.
                        if (!isInterception || param.throwable != null) return@runCatching

                        val isReturnIgnored = param.member is Constructor<*> || (param.member as? Method)?.returnType == Void.TYPE
                        // Results set through `HookParam.result` are checked on assignment, only the returned value remains.
                        param.result = if (isReturnIgnored) null else value.also { checkReturnType(invocation, it) }
                    }.onFailure {
                        notifyCallbackFailure(invocation, it)

                        when {
                            !isInterception -> if (state.throwBeforeToHostOnFailure) param.throwable = it
                            state.throwInterceptToHostOnFailure -> param.throwable = it
                            // A failed interception invokes the original unhooked `Member` to keep the host running normally.
                            else -> param.result = runCatching { invocation.callOriginal() }.getOrElse { failure ->
                                // The original `Member` has already run, pass its own failure to the caller instead of
                                // letting it escape this callback, which would make the framework run the original again.
                                param.throwable = (failure as? InvocationTargetException)?.targetException ?: failure
                                return
                            }
                        }
                    }
                }

                override fun afterHooked(param: ClassicHookerBridge.Param) {
                    if (isInterception) return

                    HookParam.from(state.afterDataExtra, this@ClassicMemberHooker, param).also { invocation ->
                        runCatching {
                            val callback = state.afterHookCallback
                            if (callback != null) {
                                if (YukiHook.Config.debug) YLog.debug(
                                    "Invoking after callback #${afterInvocationCount.incrementAndGet()} for $target"
                                )
                                callback(invocation)
                            }
                        }.onFailure {
                            notifyCallbackFailure(invocation, it)
                            if (state.throwAfterToHostOnFailure) param.throwable = it
                        }
                    }
                }
            }
        }

        /**
         * Classic hook registration group handle and invocation failure listeners.
         *
         * Listeners observe subsequent invocation failures only.
         */
        inner class Handle internal constructor() : MemberHooker<HookParam>.Handle()

        /**
         * Hook callback body implementation.
         * @param enableThrowToHost enables throwing failures of this callback to the host.
         */
        class HookCallback internal constructor(private val enableThrowToHost: () -> Unit) {

            /**
             * Throws an exception to the current host when one occurs in this callback body.
             *
             * This applies only to the callback that returned this [HookCallback].
             * For [intercept], the original unhooked [Member] is no longer invoked when the callback fails.
             */
            fun throwToHostOnFailure() = enableThrowToHost()

            /** Throws an exception to the current host when one occurs in the callback body. */
            @Deprecated(message = "Use throwToHostOnFailure instead.", replaceWith = ReplaceWith("throwToHostOnFailure()"))
            fun onFailureThrowToApp() = throwToHostOnFailure()
        }
    }

    /**
     * Hook mode type definition.
     */
    internal enum class HookMode {
        /** Lazy mode. */
        LAZY,

        /** Immediate mode. */
        IMMEDIATE
    }
}