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
 * This file is created by fankes on 2026/11/27.
 */
@file:Suppress("UnusedReceiverParameter")

package com.highcapable.yukihookapi.core.lint.detector

import com.android.tools.lint.checks.infrastructure.LintDetectorTest

/**
 * Checks invalid field hook targets and conflicting classic callbacks.
 */
class MemberHookerDetectorTest : LintDetectorTest() {

    companion object {

        private val hooker = kotlin("""
            package com.highcapable.yukihookapi.hook.core
            class YukiHookCreator {
                class ClassicMemberHooker {
                    fun before(block: () -> Unit) = Unit
                    fun after(block: () -> Unit) = Unit
                    fun intercept(block: () -> Unit) = Unit
                    fun replaceAny(block: () -> Unit) = Unit
                }
            }
        """).indented()

        private val packageParam = kotlin("""
            package com.highcapable.yukihookapi.hook.param
            import com.highcapable.yukihookapi.hook.core.YukiHookCreator
            import java.lang.reflect.Member
            class PackageParam {
                fun Member.hook() = YukiHookCreator.ClassicMemberHooker()
                fun Collection<Member>.hookAll() = YukiHookCreator.ClassicMemberHooker()
            }
        """).indented()
    }

    override fun getDetector() = MemberHookerDetector()
    override fun getIssues() = listOf(
        MemberHookerDetector.CONFLICTING_CLASSIC_HOOK_CALLBACKS_ISSUE,
        MemberHookerDetector.INVALID_FIELD_HOOK_TARGET_ISSUE
    )

    fun testFieldCannotBeUsedAsASingleOrMultipleHookTarget() {
        lint().files(hooker, packageParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.PackageParam
            import java.lang.reflect.Field
            class Usage : PackageParam() {
                fun install(field: Field) {
                    field.hook()
                    listOf(field).hookAll()
                }
            }
        """).indented()).run()
            .expectErrorCount(2)
            .expectContains("cannot be used with a Field")
            .expectContains("cannot be used with Field elements")
    }

    fun testBeforeAndAfterCanCoexistButInterceptionConflicts() {
        lint().files(hooker, packageParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.PackageParam
            import java.lang.reflect.Method
            class Usage : PackageParam() {
                fun install(method: Method) {
                    method.hook().apply {
                        before {}
                        after {}
                    }
                    method.hook().apply {
                        before {}
                        intercept {}
                    }
                }
            }
        """).indented()).run()
            .expectErrorCount(1)
            .expectContains("intercept cannot be used with before on the same classic hooker")
    }
}