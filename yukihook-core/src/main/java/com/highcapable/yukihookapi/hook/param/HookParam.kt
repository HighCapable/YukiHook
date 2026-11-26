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
@file:Suppress("unused", "UNCHECKED_CAST")

package com.highcapable.yukihookapi.hook.param

import android.os.Bundle
import com.highcapable.yukihookapi.annotation.internal.RequiresRuntime
import com.highcapable.yukihookapi.hook.core.YukiHookCreator
import com.highcapable.yukihookapi.hook.core.api.helper.YukiHookHelper
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.runtime.YukiHookRuntime
import com.highcapable.yukihookapi.runtime.bridge.hooker.ClassicHookerBridge
import java.lang.reflect.Member

/**
 * Invocation context for a classic hook callback.
 * @param dataExtraStorage the storage shared by the current callback scope.
 * @param hooker the hooker owning this invocation's registrations.
 * @param param the hook result callback interface.
 */
class HookParam private constructor(
    dataExtraStorage: Bundle,
    hooker: YukiHookCreator.MemberHooker<HookParam>,
    private val param: ClassicHookerBridge.Param
) : HookInvocation(dataExtraStorage, hooker, param) {

    internal companion object {

        /**
         * Creates a new [HookParam] from the given member hooker.
         * @param dataExtraStorage the storage shared by the current callback scope.
         * @param hooker the hooker owning this invocation's registrations.
         * @param param the hook result callback interface.
         * @return [HookParam]
         */
        fun from(
            dataExtraStorage: Bundle,
            hooker: YukiHookCreator.MemberHooker<HookParam>,
            param: ClassicHookerBridge.Param
        ) = HookParam(dataExtraStorage, hooker, param)
    }

    /**
     * Gets the argument array of the currently hooked method or constructor.
     *
     * Each array element has the default type [Any]. Use [arg] to access [ArgumentEditor.get].
     * @see arg
     * @see firstArg
     * @see lastArg
     * @return [Array]
     */
    val args get() = param.args

    /**
     * Gets or sets the result of the currently hooked method or constructor.
     *
     * A value that does not match the method return type is rejected and the current result is kept.
     * @return [Any] or null.
     * @throws IllegalStateException if the assigned value does not match the method return type.
     */
    var result: Any?
        get() = param.result
        set(value) {
            YukiHookCreator.checkReturnType(invocation = this, value)
            param.result = value
        }

    /**
     * Gets whether an exception has been set for the invocation.
     * @return [Boolean]
     */
    val hasThrowable: Boolean get() = param.throwable != null

    /**
     * Gets the exception set for the invocation.
     * @return [Throwable] or null.
     */
    val throwable get() = param.throwable

    /**
     * Throws an exception to the current host.
     *
     * Use [hasThrowable] to check whether an exception is currently set,
     * and [throwable] to get the exception set for the invocation.
     *
     * This API is effective in [YukiHookCreator.ClassicMemberHooker.before], [YukiHookCreator.ClassicMemberHooker.after]
     * and [YukiHookCreator.ClassicMemberHooker.intercept] callbacks, the return value of an [YukiHookCreator.ClassicMemberHooker.intercept]
     * callback is ignored after it is called.
     * Setting the exception also sets [result] to null, the framework throws it to the current host after the callback returns.
     * @receiver the exception to throw to the current host.
     */
    fun Throwable.throwToHost() {
        param.throwable = this
        YLog.error(message ?: "", this)
    }

    /**
     * Gets the result [T] of the currently hooked method or constructor.
     * @return [T] or null when the result is absent or has a different type.
     */
    inline fun <reified T> result() = result as? T

    /**
     * Gets an argument editor for the currently hooked method or constructor.
     * @see firstArg
     * @see lastArg
     * @param index the argument array index.
     * @return [ArgumentEditor]
     */
    fun arg(index: Int) = ArgumentEditor(index)

    /**
     * Gets the argument editor for the first argument of the currently hooked method or constructor.
     * @see arg
     * @return [ArgumentEditor]
     */
    fun firstArg() = arg(0)

    /**
     * Gets the argument editor for the last argument of the currently hooked method or constructor.
     * @see arg
     * @return [ArgumentEditor]
     */
    fun lastArg() = arg(args.lastIndex)

    /**
     * Invokes the original [Member].
     *
     * Invokes the unhooked original [Member] with its original arguments.
     * @see invokeOriginal
     * @return [Any] or null.
     */
    fun callOriginal() = callOriginal<Any>()

    /**
     * Invokes the original [Member].
     *
     * Invokes the unhooked original [Member] with its original arguments.
     * @see invokeOriginal
     * @return [T] or null.
     * @throws ClassCastException if the result cannot be used as [T].
     */
    @JvmName(name = "callOriginalTyped")
    fun <T> callOriginal() = invokeOriginal<T>(*args)

    /**
     * Invokes the original [Member].
     *
     * Invokes the unhooked original [Member] with custom [args].
     * @see callOriginal
     * @param args the argument instances.
     * @return [Any] or null.
     */
    fun invokeOriginal(vararg args: Any?) = invokeOriginal<Any>(*args)

    /**
     * Invokes the original [Member].
     *
     * Invokes the unhooked original [Member] with custom [args].
     * @see callOriginal
     * @param args the argument instances.
     * @return [T] or null.
     * @throws ClassCastException if the result cannot be used as [T].
     */
    @JvmName(name = "invokeOriginalTyped")
    fun <T> invokeOriginal(vararg args: Any?) = YukiHookHelper.invokeOriginal(member, param.instance, args) as T?

    /**
     * Invokes the current [Member] non-virtually.
     *
     * Invokes the current [Member] without hooks or subclass overrides using its current arguments.
     * @see invokeSpecial
     * @return [Any] or null.
     */
    @RequiresRuntime(YukiHookRuntime.LIBXPOSED)
    fun callSpecial() = callSpecial<Any>()

    /**
     * Invokes the current [Member] non-virtually.
     *
     * Invokes the current [Member] without hooks or subclass overrides using its current arguments.
     * @see invokeSpecial
     * @return [T] or null.
     * @throws ClassCastException if the result cannot be used as [T].
     */
    @RequiresRuntime(YukiHookRuntime.LIBXPOSED)
    @JvmName(name = "callSpecialTyped")
    fun <T> callSpecial() = invokeSpecial<T>(*args)

    /**
     * Invokes the current [Member] non-virtually.
     *
     * Invokes the current [Member] without hooks or subclass overrides using custom [args].
     * @see callSpecial
     * @param args the argument instances.
     * @return [Any] or null.
     */
    @RequiresRuntime(YukiHookRuntime.LIBXPOSED)
    fun invokeSpecial(vararg args: Any?) = invokeSpecial<Any>(*args)

    /**
     * Invokes the current [Member] non-virtually.
     *
     * Invokes the current [Member] without hooks or subclass overrides using custom [args].
     * @see callSpecial
     * @param args the argument instances.
     * @return [T] or null.
     * @throws ClassCastException if the result cannot be used as [T].
     */
    @RequiresRuntime(YukiHookRuntime.LIBXPOSED)
    @JvmName(name = "invokeSpecialTyped")
    fun <T> invokeSpecial(vararg args: Any?) = YukiHookHelper.invokeSpecial(member, instance, args) as T?

    /**
     * Argument editor for the currently hooked method or constructor.
     * @param index the argument array index.
     */
    inner class ArgumentEditor internal constructor(private val index: Int) {

        /**
         * Gets the argument instance.
         * @return [Any] or null if the value is null or the index is out of bounds.
         */
        fun get() = args.getOrNull(index)

        /**
         * Gets the argument instance as [T].
         * @return [T] or null if the value is null or the index is out of bounds.
         * @throws ClassCastException if the argument cannot be used as [T].
         */
        @JvmName("getTyped")
        fun <T> get() = get() as T?

        /**
         * Gets the argument instance as [T].
         * @return [T] or null if the value is null, the index is out of bounds, the argument is null, or its type does not match [T].
         */
        inline fun <reified T> getOrNull() = get() as? T?

        /**
         * Sets the argument instance.
         * @param any the source instance.
         * @throws IllegalStateException if the target argument array is empty or [index] does not exist.
         */
        fun <T> set(any: T?) {
            check(args.isNotEmpty()) { "The method has no arguments." }
            check(index >= 0) { "Argument index must be >= 0" }
            check(index <= args.lastIndex) { "Argument index out of bounds, the maximum is ${args.lastIndex}" }

            param.args[index] = any
        }

        // region Deprecated functions for backward compatibility

        /**
         * Gets the argument instance as [T].
         * @return [T] or null.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<T>()"))
        fun <T> cast() = get<T>()

        /**
         * Gets the argument instance as [Byte].
         * @return [Byte] or null.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<Byte>()"))
        fun byte() = get<Byte>()

        /**
         * Gets the argument instance as [Int].
         * @return [Int] 0 when unavailable.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<Int>() ?: 0"))
        fun int() = get<Int>() ?: 0

        /**
         * Gets the argument instance as [Long].
         * @return [Long] 0L when unavailable.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<Long>() ?: 0L"))
        fun long() = get<Long>() ?: 0L

        /**
         * Gets the argument instance as [Short].
         * @return [Short] 0 when unavailable.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<Short>() ?: 0"))
        fun short() = get<Short>() ?: 0

        /**
         * Gets the argument instance as [Double].
         * @return [Double] 0.0 when unavailable.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<Double>() ?: 0.0"))
        fun double() = get<Double>() ?: 0.0

        /**
         * Gets the argument instance as [Float].
         * @return [Float] 0f when unavailable.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<Float>() ?: 0f"))
        fun float() = get<Float>() ?: 0f

        /**
         * Gets the argument instance as [String].
         * @return [String] an empty string when unavailable.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<String>() ?: \"\""))
        fun string() = get<String>() ?: ""

        /**
         * Gets the argument instance as [Char].
         * @return [Char] a space character when unavailable.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<Char>() ?: ' '"))
        fun char() = get<Char>() ?: ' '

        /**
         * Gets the argument instance as [Boolean].
         * @return [Boolean] false when unavailable.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<Boolean>() ?: false"))
        fun boolean() = get<Boolean>() ?: false

        /**
         * Gets the argument instance as [Any].
         * @return [Any] or null.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get()"))
        fun any() = get()

        /**
         * Gets the argument instance as an [Array] whose elements are of type [T].
         * @return [Array] an empty array when unavailable.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<Array<T>>() ?: emptyArray()"))
        inline fun <reified T> array() = get<Array<T>>() ?: emptyArray()

        /**
         * Gets the argument instance as a [List] whose elements are of type [T].
         * @return [List] an empty list when unavailable.
         */
        @Deprecated(message = "Use get instead.", replaceWith = ReplaceWith("get<List<T>>() ?: emptyList()"))
        inline fun <reified T> list() = get<List<T>>() ?: emptyList()

        /** Sets the argument instance to null. */
        @Deprecated(message = "Use set instead.", replaceWith = ReplaceWith("set(null)"))
        fun setNull() = set(null)

        /** Sets the argument instance to true. */
        @Deprecated(message = "Use set instead.", replaceWith = ReplaceWith("set(true)"))
        fun setTrue() = set(true)

        /** Sets the argument instance to false. */
        @Deprecated(message = "Use set instead.", replaceWith = ReplaceWith("set(false)"))
        fun setFalse() = set(false)

        // endregion

        override fun toString() = "ArgumentEditor(index=$index, value=${args.getOrNull(index)}) by ${this@HookParam}"
    }

    // region Deprecated functions and classes for backward compatibility

    /**
     * Gets the class of the current hooked receiver instance.
     *
     * - Use [instanceOrNull] with [javaClass] directly.
     * @return [Class] or null.
     */
    @Deprecated(message = "Use instanceOrNull?.javaClass directly.", replaceWith = ReplaceWith("instanceOrNull?.javaClass"))
    val instanceClass get() = instanceOrNull?.javaClass

    /**
     * Gets the argument-index helper for the currently hooked method or constructor.
     * @return [ArgsIndexCondition]
     */
    @Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
    @Deprecated(message = "Use firstArg or lastArg instead.")
    fun args() = ArgsIndexCondition()

    /**
     * Gets an argument editor for the currently hooked method or constructor.
     * @return [ArgumentEditor]
     */
    @Deprecated(message = "Use arg instead.", replaceWith = ReplaceWith("arg(index)"))
    fun args(index: Int) = ArgumentEditor(index)

    /** Sets the [result] of the currently hooked method to true. */
    @Deprecated(message = "Use result instead.", replaceWith = ReplaceWith("result = true"))
    fun resultTrue() {
        result = true
    }

    /** Sets the [result] of the currently hooked method to false. */
    @Deprecated(message = "Use result instead.", replaceWith = ReplaceWith("result = false"))
    fun resultFalse() {
        result = false
    }

    /** Sets the [result] of the currently hooked method to null. */
    @Deprecated(message = "Use result instead.", replaceWith = ReplaceWith("result = null"))
    fun resultNull() {
        result = null
    }

    /** Throws an exception to the current host. */
    @Deprecated(message = "Use throwToHost instead.", replaceWith = ReplaceWith("throwToHost()"))
    fun Throwable.throwToApp() = throwToHost()

    /**
     * Argument-array index helper.
     */
    @Deprecated(message = "Use firstArg or lastArg instead.")
    inner class ArgsIndexCondition internal constructor() {

        /**
         * Gets the first argument of the currently hooked method or constructor.
         * @return [ArgumentEditor]
         */
        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated(message = "Use firstArg instead.")
        fun first() = firstArg()

        /**
         * Gets the last argument of the currently hooked method or constructor.
         * @return [ArgumentEditor]
         */
        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated(message = "Use lastArg instead.")
        fun last() = lastArg()
    }

    // endregion

    override fun toString() = "HookParam(${super.toString()}) by $param"
}