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
 * This file is created by fankes on 2025/6/15.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.extension.kavaref.resolver

import com.highcapable.betterandroid.system.extension.utils.AndroidVersion
import com.highcapable.kavaref.resolver.processor.MemberProcessor
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Provides [HiddenApiBypass] integration for `KavaRef`.
 *
 * Implemented with [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass).
 *
 * Create an instance and pass it to `processor` wherever access to Android hidden APIs is required.
 */
class AndroidHiddenApiBypassResolver : MemberProcessor.Resolver() {

    override fun <T : Any> getDeclaredFields(declaringClass: Class<T>): List<Field> =
        AndroidVersion.require(AndroidVersion.P, super.getDeclaredFields(declaringClass)) {
            HiddenApiBypass.getStaticFields(declaringClass) + HiddenApiBypass.getInstanceFields(declaringClass)
        }

    override fun <T : Any> getDeclaredConstructors(declaringClass: Class<T>): List<Constructor<T>> =
        AndroidVersion.require(AndroidVersion.P, super.getDeclaredConstructors(declaringClass)) {
            HiddenApiBypass.getDeclaredMethods(declaringClass).filterIsInstance<Constructor<T>>().toList()
        }

    override fun <T : Any> getDeclaredMethods(declaringClass: Class<T>): List<Method> =
        AndroidVersion.require(AndroidVersion.P, super.getDeclaredMethods(declaringClass)) {
            HiddenApiBypass.getDeclaredMethods(declaringClass).filterIsInstance<Method>().toList()
        }
}