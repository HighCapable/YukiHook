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
@file:Suppress("UnusedReceiverParameter", "RedundantSuspendModifier")

package com.highcapable.yukihookapi.core.lint.detector

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Severity

/**
 * Checks the suggestions for reading hook invocation values and configuring hooks.
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

        private val packageParam = kotlin("""
            package com.highcapable.yukihookapi.hook.param
            import java.lang.reflect.Member

            class Hooker {
                fun before(callback: () -> Unit) = Unit
            }
            class Handle
            class PackageParam {
                fun Member.hook(priority: Int = 0) = Hooker()
                fun Member.hook(priority: Int = 0, hooker: Hooker.() -> Unit) = Handle()
                @JvmName("hookAllByMember")
                fun Collection<Member>.hookAll(priority: Int = 0) = Hooker()
                @JvmName("hookAllByMember")
                fun Collection<Member>.hookAll(priority: Int = 0, hooker: Hooker.() -> Unit) = Handle()
            }
        """).indented()

        private val memberResolver = kotlin("""
            package com.highcapable.yukihookapi.extension.kavaref
            import com.highcapable.yukihookapi.hook.param.Handle
            import com.highcapable.yukihookapi.hook.param.Hooker
            import com.highcapable.yukihookapi.hook.param.PackageParam

            class Resolver
            context(param: PackageParam)
            fun Resolver.hook(priority: Int = 0) = Hooker()
            context(param: PackageParam)
            fun Resolver.hook(priority: Int = 0, hooker: Hooker.() -> Unit) = Handle()
            context(param: PackageParam)
            fun Collection<Resolver>.hookAll(priority: Int = 0) = Hooker()
            context(param: PackageParam)
            fun Collection<Resolver>.hookAll(priority: Int = 0, hooker: Hooker.() -> Unit) = Handle()
        """).indented()
    }

    override fun getDetector() = ModuleUsageDetector()
    override fun getIssues() = listOf(ModuleUsageDetector.USE_HOOK_INVOCATION_API_ISSUE, ModuleUsageDetector.USE_HOOK_BLOCK_ISSUE)

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

    fun testHookBlocksKeepPriorityAndSafeReceivers() {
        lint().files(packageParam, kotlin("src/test/pkg/Hooks.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.PackageParam
            import java.lang.reflect.Member

            fun PackageParam.install(member: Member?, members: List<Member>) {
                member?.hook()?.apply { before { return@before } }
                members.hookAll(priority = 10).apply { before {} }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(2, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/Hooks.kt line 6: Use 'hook' block:
                @@ -6 +6
                -     member?.hook()?.apply { before { return@before } }
                +     member?.hook { before { return@before } }
                Fix for src/test/pkg/Hooks.kt line 7: Use 'hookAll' block:
                @@ -7 +7
                -     members.hookAll(priority = 10).apply { before {} }
                +     members.hookAll(priority = 10) { before {} }
            """.trimIndent())
    }

    fun testKavaRefExtensionsOfferHookBlocks() {
        lint().files(packageParam, memberResolver, kotlin("src/test/pkg/Hooks.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.extension.kavaref.Resolver
            import com.highcapable.yukihookapi.extension.kavaref.hook
            import com.highcapable.yukihookapi.extension.kavaref.hookAll
            import com.highcapable.yukihookapi.hook.param.PackageParam

            fun PackageParam.install(resolver: Resolver, resolvers: List<Resolver>) {
                resolver.hook().apply { before {} }
                resolvers.hookAll().apply { before {} }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(2, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/Hooks.kt line 8: Use 'hook' block:
                @@ -8 +8
                -     resolver.hook().apply { before {} }
                +     resolver.hook { before {} }
                Fix for src/test/pkg/Hooks.kt line 9: Use 'hookAll' block:
                @@ -9 +9
                -     resolvers.hookAll().apply { before {} }
                +     resolvers.hookAll { before {} }
            """.trimIndent())
    }

    fun testAlsoLetAndUnrelatedCallsAreIgnored() {
        lint().files(packageParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.Hooker
            import com.highcapable.yukihookapi.hook.param.PackageParam
            import java.lang.reflect.Member

            fun unrelated() = "value"
            fun String.hook() = Hooker()
            fun PackageParam.install(member: Member) {
                member.hook().also { it.before {} }
                member.hook().let { it.before {} }
                member.hook {}.apply {}
                "value".hook().apply { before {} }
                unrelated().apply {}
            }
            fun PackageParam.customApply(member: Member) {
                fun Hooker.apply(block: Hooker.() -> Unit) = block()
                member.hook().apply { before {} }
            }
        """).indented()).testModes(TestMode.DEFAULT).run().expectClean()
    }

    fun testHookApplyOnlySuggestsWhenTheRewriteNeedsManualChanges() {
        lint().files(packageParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.PackageParam
            import java.lang.reflect.Member

            fun PackageParam.save(member: Member) = member.hook().apply { before {} }
            fun PackageParam.install(member: Member?) {
                member?.hook().apply { this?.before {} }
                member?.hook()?.apply { return@apply }
                member?.hook()?.apply { return }
                run outer@ {
                    member?.hook()?.apply { return@outer }
                    Unit
                }
            }
            suspend fun prepare() = Unit
            suspend fun PackageParam.installAfterPreparation(member: Member) {
                member.hook().apply { prepare(); before {} }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(6, Severity.INFORMATIONAL)
            .expectFixDiffs("")
    }
}