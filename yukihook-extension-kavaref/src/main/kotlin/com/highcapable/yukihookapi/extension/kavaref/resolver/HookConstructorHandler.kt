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
 * This file is created by fankes on 2026/10/9.
 */
@file:Suppress("UNCHECKED_CAST")

package com.highcapable.yukihookapi.extension.kavaref.resolver

import com.highcapable.kavaref.resolver.ConstructorResolver
import com.highcapable.yukihookapi.hook.param.PackageParam
import java.lang.reflect.Constructor

/**
 * Provides a [ConstructorResolver.Handler] for `KavaRef` that creates instances through the hook framework.
 * @param param the current [PackageParam].
 */
internal sealed class HookConstructorHandler(protected val param: PackageParam) : ConstructorResolver.Handler() {

    override fun requireAccessible(member: Constructor<*>) = Unit

    /**
     * Creates an instance with the original constructor without hooks.
     * @param param the current [PackageParam].
     */
    class Original(param: PackageParam) : HookConstructorHandler(param) {

        override fun <T> newInstance(constructor: Constructor<T>, args: Array<out Any?>) = with(param) {
            constructor.newInstanceOriginal(*args)
        }
    }

    /**
     * Creates a [subclass] instance initialized by the parent constructor without hooks.
     * @param param the current [PackageParam].
     * @param subclass the subclass to instantiate.
     */
    class Special(param: PackageParam, private val subclass: Class<*>) : HookConstructorHandler(param) {

        override fun <T> newInstance(constructor: Constructor<T>, args: Array<out Any?>) = with(param) {
            constructor.newInstanceSpecial(subclass, *args) as T
        }
    }
}