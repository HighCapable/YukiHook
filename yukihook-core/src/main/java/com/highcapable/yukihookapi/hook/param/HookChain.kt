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
 * This file is created by fankes on 2026/9/12.
 */
@file:Suppress("unused", "UNCHECKED_CAST")

package com.highcapable.yukihookapi.hook.param

import android.os.Bundle
import com.highcapable.yukihookapi.hook.core.YukiHookCreator
import com.highcapable.yukihookapi.runtime.bridge.hooker.ChainHookerBridge

/**
 * Invocation context for a chain interceptor.
 *
 * Return a value to complete the invocation, or call [proceed] to continue the remaining chain.
 * Exceptions from [proceed] propagate to the caller, other failures follow the hooker's failure policy.
 *
 * This context must not be shared across threads or
 * used after the interceptor returns or throws.
 * @param dataExtraStorage the storage shared by this interceptor's invocations.
 * @param hooker the hooker owning this invocation's registrations.
 * @param chain the runtime chain.
 */
class HookChain private constructor(
    dataExtraStorage: Bundle,
    hooker: YukiHookCreator.MemberHooker<HookChain>,
    private val chain: ChainHookerBridge.Chain
) : HookInvocation(dataExtraStorage, hooker, chain) {

    internal companion object {

        /**
         * Creates a context for a runtime chain.
         * @param dataExtraStorage the storage shared by this interceptor's invocations.
         * @param hooker the hooker owning the invocation.
         * @param chain the current runtime chain.
         * @return [HookChain]
         */
        fun from(
            dataExtraStorage: Bundle,
            hooker: YukiHookCreator.MemberHooker<HookChain>,
            chain: ChainHookerBridge.Chain
        ) = HookChain(dataExtraStorage, hooker, chain)
    }

    /**
     * Read-only arguments supplied to this interceptor.
     *
     * Pass a new array to [proceed] to change the arguments used downstream.
     * @see arg
     * @see firstArg
     * @see lastArg
     * @see proceed
     * @return [List]
     */
    val args get() = chain.args

    /**
     * Reads an invocation argument.
     * @see args
     * @see firstArg
     * @see lastArg
     * @param index the argument index.
     * @return [Any] the argument, or null.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    fun arg(index: Int) = chain.getArg(index)

    /**
     * Reads an invocation argument as [T].
     * @see args
     * @see firstArg
     * @see lastArg
     * @param index the argument index.
     * @return [T] or null.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     * @throws ClassCastException if the argument cannot be used as [T].
     */
    @JvmName("argTyped")
    fun <T> arg(index: Int) = arg(index) as T?

    /**
     * Reads an invocation argument.
     * @see arg
     * @param index the argument index.
     * @return [Any] or null if the argument is null or the index is out of bounds.
     */
    fun argOrNull(index: Int) = args.getOrNull(index)

    /**
     * Reads an invocation argument as [T].
     * @see arg
     * @param index the argument index.
     * @return [T] or null if the index is out of bounds, the argument is null, or its type does not match [T].
     */
    @JvmName("argOrNullTyped")
    inline fun <reified T> argOrNull(index: Int) = argOrNull(index) as? T

    /**
     * Reads the first invocation argument.
     * @see arg
     * @see args
     * @see lastArg
     * @return [Any] the argument, or null.
     */
    fun firstArg() = arg(0)

    /**
     * Reads the first invocation argument as [T].
     * @see arg
     * @see args
     * @see lastArg
     * @return [T] or null.
     * @throws ClassCastException if the argument cannot be used as [T].
     */
    @JvmName("firstArgTyped")
    fun <T> firstArg() = firstArg() as T?

    /**
     * Reads the first invocation argument.
     * @see firstArg
     * @return [Any] or null if the first argument is null or the invocation has no arguments.
     */
    fun firstArgOrNull() = args.firstOrNull()

    /**
     * Reads the first invocation argument as [T].
     * @see firstArg
     * @return [T] or null if the argument is absent, is null, or has a different type.
     */
    @JvmName("firstArgOrNullTyped")
    inline fun <reified T> firstArgOrNull() = firstArgOrNull() as? T

    /**
     * Reads the last invocation argument.
     * @see arg
     * @see args
     * @see firstArg
     * @return [Any] the argument, or null.
     */
    fun lastArg() = arg(args.lastIndex)

    /**
     * Reads the last invocation argument as [T].
     * @see arg
     * @see args
     * @see firstArg
     * @return [T] or null.
     * @throws ClassCastException if the argument cannot be used as [T].
     */
    @JvmName("lastArgTyped")
    fun <T> lastArg() = lastArg() as T?

    /**
     * Reads the last invocation argument.
     * @see lastArg
     * @return [Any] or null if the last argument is null or the invocation has no arguments.
     */
    fun lastArgOrNull() = args.lastOrNull()

    /**
     * Reads the last invocation argument as [T].
     * @see lastArg
     * @return [T] or null if the argument is absent, is null, or has a different type.
     */
    @JvmName("lastArgOrNullTyped")
    inline fun <reified T> lastArgOrNull() = lastArgOrNull() as? T

    /**
     * Continues the remaining chain with the current arguments and receiver.
     * @see proceedWith
     * @return [Any] the downstream result, or null for void methods and constructors.
     * @throws Throwable if the remaining chain or original member fails.
     */
    fun proceed() = proceed<Any>()

    /**
     * Continues the remaining chain and returns its result as [T].
     * @see proceedWith
     * @return [T] or null for void methods and constructors.
     * @throws Throwable if the remaining chain or original member fails.
     * @throws ClassCastException if the result cannot be used as [T].
     */
    @JvmName("proceedTyped")
    fun <T> proceed() = chain.proceed() as T?

    /**
     * Continues the remaining chain with new arguments and the current receiver.
     * @see proceedWith
     * @param args the arguments to pass downstream.
     * @return [Any] the downstream result, or null for void methods and constructors.
     * @throws Throwable if the remaining chain or original member fails.
     */
    fun proceed(args: Array<out Any?>) = proceed<Any>(args)

    /**
     * Continues the remaining chain with new arguments and returns its result as [T].
     * @see proceedWith
     * @param args the arguments to pass downstream.
     * @return [T] or null for void methods and constructors.
     * @throws Throwable if the remaining chain or original member fails.
     * @throws ClassCastException if the result cannot be used as [T].
     */
    @JvmName("proceedTyped")
    fun <T> proceed(args: Array<out Any?>) = chain.proceed(args) as T?

    /**
     * Continues the remaining chain with a new receiver and the current arguments.
     *
     * Do not use this for static methods.
     * @see proceed
     * @param instance the receiver to pass downstream.
     * @return [Any] the downstream result, or null for void methods and constructors.
     * @throws Throwable if the remaining chain or original member fails.
     */
    fun proceedWith(instance: Any) = proceedWith<Any>(instance)

    /**
     * Continues the remaining chain with a new receiver and returns its result as [T].
     *
     * Do not use this for static methods.
     * @see proceed
     * @param instance the receiver to pass downstream.
     * @return [T] or null for void methods and constructors.
     * @throws Throwable if the remaining chain or original member fails.
     * @throws ClassCastException if the result cannot be used as [T].
     */
    @JvmName("proceedWithTyped")
    fun <T> proceedWith(instance: Any) = chain.proceedWith(instance) as T?

    /**
     * Continues the remaining chain with a new receiver and arguments.
     *
     * Do not use this for static methods.
     * @see proceed
     * @param instance the receiver to pass downstream.
     * @param args the arguments to pass downstream.
     * @return [Any] the downstream result, or null for void methods and constructors.
     * @throws Throwable if the remaining chain or original member fails.
     */
    fun proceedWith(instance: Any, args: Array<out Any?>) = proceedWith<Any>(instance, args)

    /**
     * Continues the remaining chain with a new receiver and arguments, returning its result as [T].
     *
     * Do not use this for static methods.
     * @see proceed
     * @param instance the receiver to pass downstream.
     * @param args the arguments to pass downstream.
     * @return [T] or null for void methods and constructors.
     * @throws Throwable if the remaining chain or original member fails.
     * @throws ClassCastException if the result cannot be used as [T].
     */
    @JvmName("proceedWithTyped")
    fun <T> proceedWith(instance: Any, args: Array<out Any?>) = chain.proceedWith(instance, args) as T?
}