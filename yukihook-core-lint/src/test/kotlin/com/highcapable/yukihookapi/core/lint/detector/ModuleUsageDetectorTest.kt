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
 * This file is created by fankes on 2026/11/20.
 */
@file:Suppress("UnusedReceiverParameter")

package com.highcapable.yukihookapi.core.lint.detector

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Severity

/**
 * Checks the suggestions for reading hook invocation values.
 */
class ModuleUsageDetectorTest : LintDetectorTest() {

    companion object {

        private val hookParam = kotlin("""
            package com.highcapable.yukihookapi.hook.param
            abstract class HookInvocation
            class HookParam : HookInvocation() {
                var result: Any? = null
                inline fun <reified T> result(): T? = null
            }
        """).indented()
    }

    override fun getDetector() = ModuleUsageDetector()
    override fun getIssues() = listOf(ModuleUsageDetector.USE_HOOK_INVOCATION_API_ISSUE)

    fun testSafeResultCastsKeepTheReceiverAndNormalizeNullableTypes() {
        lint().files(hookParam, kotlin("src/test/pkg/Usage.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            fun HookParam.implicit() = result as? String
            fun qualified(param: HookParam?) = (param?.result as? List<String?>?)?.size
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(2, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/Usage.kt line 4: Replace with 'result':
                @@ -4 +4
                - fun HookParam.implicit() = result as? String
                + fun HookParam.implicit() = result<String>()
                Fix for src/test/pkg/Usage.kt line 5: Replace with 'result':
                @@ -5 +5
                - fun qualified(param: HookParam?) = (param?.result as? List<String?>?)?.size
                + fun qualified(param: HookParam?) = param?.result<List<String?>>()?.size
            """.trimIndent())
    }

    fun testRegularResultCastsSuggestTheNullableApiWithoutOfferingAFix() {
        lint().files(hookParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            fun required(param: HookParam): Int = (param.result as String).length
            fun nullable(param: HookParam): Int? = (param.result as String?)?.length
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(2, Severity.INFORMATIONAL)
            .expectContains("Use param.result<String>() to return null for a missing or mismatched result")
            .expectFixDiffs("")
    }

    fun testUnrelatedPropertiesAndNonReifiedTypesDoNotGetSuggestions() {
        lint().files(hookParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            class Unrelated(val result: Any?)
            fun unrelated(value: Unrelated) = value.result as? String
            fun HookParam.shadowedProperty(result: Any?) = result as? String
            fun <T> HookParam.unchecked() = result as? T
            fun <T> HookParam.uncheckedArray() = result as? Array<T>
        """).indented()).testModes(TestMode.DEFAULT).run().expectClean()
    }

    fun testLocalResultFunctionDoesNotGetAQuickFix() {
        lint().files(hookParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            fun HookParam.read() {
                fun <T> result(): T? = null
                val value = result as? String
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(1, Severity.INFORMATIONAL)
            .expectFixDiffs("")
    }
}