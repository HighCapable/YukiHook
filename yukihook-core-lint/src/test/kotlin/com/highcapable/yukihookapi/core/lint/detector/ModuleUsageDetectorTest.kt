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
            import java.lang.reflect.Constructor
            import java.lang.reflect.Method

            abstract class HookInvocation {
                val method: Method get() = error("stub")
                val constructor: Constructor<*> get() = error("stub")
            }
            class HookParam : HookInvocation() {
                var result: Any? = null
                inline fun <reified T> result(): T? = null
                val args: Array<Any?> = emptyArray()
                fun arg(index: Int) = ArgumentEditor()
                fun firstArg() = ArgumentEditor()
                fun lastArg() = ArgumentEditor()
                class ArgumentEditor {
                    fun get(): Any? = null
                    @JvmName("getTyped")
                    fun <T> get(): T? = null
                    inline fun <reified T> getOrNull(): T? = null
                    fun <T> set(value: T?) = Unit
                }
            }
            class HookChain : HookInvocation() {
                val args: List<Any?> = emptyList()
                fun arg(index: Int): Any? = null
                @JvmName("argTyped") fun <T> arg(index: Int): T? = null
                fun argOrNull(index: Int): Any? = null
                @JvmName("argOrNullTyped") inline fun <reified T> argOrNull(index: Int): T? = null
                fun firstArg(): Any? = null
                @JvmName("firstArgTyped") fun <T> firstArg(): T? = null
                fun lastArg(): Any? = null
                @JvmName("lastArgTyped") fun <T> lastArg(): T? = null
                fun firstArgOrNull(): Any? = null
                @JvmName("firstArgOrNullTyped") inline fun <reified T> firstArgOrNull(): T? = null
                fun lastArgOrNull(): Any? = null
                @JvmName("lastArgOrNullTyped") inline fun <reified T> lastArgOrNull(): T? = null
                fun proceed(): Any? = null
                fun proceed(args: Array<out Any?>): Any? = null
                @JvmName("proceedTyped") fun <T> proceed(): T? = null
                @JvmName("proceedTyped") fun <T> proceed(args: Array<out Any?>): T? = null
                fun proceedWith(instance: Any): Any? = null
                fun proceedWith(instance: Any, args: Array<out Any?>): Any? = null
                @JvmName("proceedWithTyped") fun <T> proceedWith(instance: Any): T? = null
                @JvmName("proceedWithTyped") fun <T> proceedWith(instance: Any, args: Array<out Any?>): T? = null
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

    fun testRegularResultCastsOfferFixesWithTheOriginalNullability() {
        lint().files(hookParam, kotlin("src/test/pkg/Results.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            fun required(param: HookParam): Int = (param.result as String).length
            fun nullable(param: HookParam): Int? = (param.result as String?)?.length
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(2, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/Results.kt line 4: Replace with 'result':
                @@ -4 +4
                - fun required(param: HookParam): Int = (param.result as String).length
                + fun required(param: HookParam): Int = param.result<String>()!!.length
                Fix for src/test/pkg/Results.kt line 5: Replace with 'result':
                @@ -5 +5
                - fun nullable(param: HookParam): Int? = (param.result as String?)?.length
                + fun nullable(param: HookParam): Int? = param.result<String>()?.length
            """.trimIndent())
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

    fun testLocalResultFunctionDoesNotGetASuggestion() {
        lint().files(hookParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            fun HookParam.read() {
                fun <T> result(): T? = null
                val value = result as? String
            }
        """).indented()).testModes(TestMode.DEFAULT).run().expectClean()
    }

    fun testArgumentCastsAndWritesUseTheEditor() {
        lint().files(hookParam, kotlin("src/test/pkg/Arguments.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            fun HookParam.edit(param: HookParam, i: Int) {
                val a = args[i] as String?
                val b = (param.args[0] as? List<String?>?)?.size
                val c = args[i] as? String
                args[i] = a
                param.args[0] = null
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(5, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/Arguments.kt line 5: Replace with 'arg(index).get':
                @@ -5 +5
                -     val a = args[i] as String?
                +     val a = arg(i).get<String>()
                Fix for src/test/pkg/Arguments.kt line 6: Replace with 'firstArg().getOrNull':
                @@ -6 +6
                -     val b = (param.args[0] as? List<String?>?)?.size
                +     val b = param.firstArg().getOrNull<List<String?>>()?.size
                Fix for src/test/pkg/Arguments.kt line 7: Replace with 'arg(index).getOrNull':
                @@ -7 +7
                -     val c = args[i] as? String
                +     val c = arg(i).getOrNull<String>()
                Fix for src/test/pkg/Arguments.kt line 8: Replace with 'arg(index).set':
                @@ -8 +8
                -     args[i] = a
                +     arg(i).set(a)
                Fix for src/test/pkg/Arguments.kt line 9: Replace with 'firstArg().set':
                @@ -9 +9
                -     param.args[0] = null
                +     param.firstArg().set(null)
            """.trimIndent())
    }

    fun testNonNullArgumentCastOffersAnAssertedRead() {
        lint().files(hookParam, kotlin("src/test/pkg/NonNullCasts.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            fun HookParam.read() = (args[0] as String).length
            fun <T> HookParam.readGeneric(): T = args[0] as T
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(2, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/NonNullCasts.kt line 4: Replace with 'firstArg().get':
                @@ -4 +4
                - fun HookParam.read() = (args[0] as String).length
                + fun HookParam.read() = firstArg().get<String>()!!.length
                Fix for src/test/pkg/NonNullCasts.kt line 5: Replace with 'firstArg().get':
                @@ -5 +5
                - fun <T> HookParam.readGeneric(): T = args[0] as T
                + fun <T> HookParam.readGeneric(): T = (firstArg().get<T>() as T)
            """.trimIndent())
    }

    fun testArgumentEditorCastsMoveTheTypeIntoTheRead() {
        lint().files(hookParam, kotlin("src/test/pkg/Editor.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            fun HookParam.read(param: HookParam?, index: Int) {
                val a = firstArg().get() as String?
                val b = (param?.arg(index)?.get() as? List<String?>?)?.size
                val c = lastArg().get<Any>() as? String
                val d = lastArg().getOrNull<CharSequence>() as? String?
                val e = firstArg().getOrNull<Any?>() as String?
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(5, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/Editor.kt line 5: Replace with 'firstArg().get':
                @@ -5 +5
                -     val a = firstArg().get() as String?
                +     val a = firstArg().get<String>()
                Fix for src/test/pkg/Editor.kt line 6: Replace with 'arg(index).getOrNull':
                @@ -6 +6
                -     val b = (param?.arg(index)?.get() as? List<String?>?)?.size
                +     val b = param?.arg(index)?.getOrNull<List<String?>>()?.size
                Fix for src/test/pkg/Editor.kt line 7: Replace with 'lastArg().getOrNull':
                @@ -7 +7
                -     val c = lastArg().get<Any>() as? String
                +     val c = lastArg().getOrNull<String>()
                Fix for src/test/pkg/Editor.kt line 8: Replace with 'lastArg().getOrNull':
                @@ -8 +8
                -     val d = lastArg().getOrNull<CharSequence>() as? String?
                +     val d = lastArg().getOrNull<String>()
                Fix for src/test/pkg/Editor.kt line 9: Replace with 'firstArg().get':
                @@ -9 +9
                -     val e = firstArg().getOrNull<Any?>() as String?
                +     val e = firstArg().get<String>()
            """.trimIndent())
    }

    fun testArgumentEditorCastsOfferFixesRegardlessOfUsage() {
        lint().files(hookParam, kotlin("src/test/pkg/NullableCasts.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            fun HookParam.read() = (firstArg().get() as String).length
            fun HookParam.widen(): Any? = firstArg().get() as String?
            fun HookParam.discard() {
                firstArg().get() as String?
            }
            fun HookParam.widenArrayRead(): Any? = args[1] as String?
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(4, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/NullableCasts.kt line 4: Replace with 'firstArg().get':
                @@ -4 +4
                - fun HookParam.read() = (firstArg().get() as String).length
                + fun HookParam.read() = firstArg().get<String>()!!.length
                Fix for src/test/pkg/NullableCasts.kt line 5: Replace with 'firstArg().get':
                @@ -5 +5
                - fun HookParam.widen(): Any? = firstArg().get() as String?
                + fun HookParam.widen(): Any? = firstArg().get<String>()
                Fix for src/test/pkg/NullableCasts.kt line 7: Replace with 'firstArg().get':
                @@ -7 +7
                -     firstArg().get() as String?
                +     firstArg().get<String>()
                Fix for src/test/pkg/NullableCasts.kt line 9: Replace with 'arg(index).get':
                @@ -9 +9
                - fun HookParam.widenArrayRead(): Any? = args[1] as String?
                + fun HookParam.widenArrayRead(): Any? = arg(1).get<String>()
            """.trimIndent())
    }

    fun testArgumentEditorCastsRetainIndependentTypeChecks() {
        lint().files(hookParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            class Other {
                fun get(): Any? = null
                fun <T> getOrNull(): T? = null
            }
            fun HookParam.read(other: Other) {
                firstArg().getOrNull<String>() as? CharSequence
                firstArg().getOrNull<CharSequence>() as String?
                firstArg().get<CharSequence>() as? String
                other.get() as? String
                other.getOrNull<Any>() as? String
            }
        """).indented()).testModes(TestMode.DEFAULT).run().expectClean()
    }

    fun testFirstAndLastArgUseTheCurrentInvocation() {
        lint().files(hookParam, kotlin("src/test/pkg/Positions.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookParam

            fun HookParam.read(param: HookParam, optional: HookParam?) {
                optional?.arg(index = 0)
                arg(args.size - 1)
                param.arg(param.method.parameterTypes.lastIndex)
                val types = constructor.parameterTypes
                val last = types.size - 1
                arg(last)
                val methodRef = method
                arg(methodRef.parameterTypes.size - 1)
                param.args[param.args.lastIndex] = null
                arg(method.parameterCount - 1)
                val lastFromCount = param.constructor.parameterCount - 1
                param.arg(lastFromCount)
                val count = methodRef.parameterCount
                args[count - 1] = null
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(9, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/Positions.kt line 5: Replace with 'firstArg':
                @@ -5 +5
                -     optional?.arg(index = 0)
                +     optional?.firstArg()
                Fix for src/test/pkg/Positions.kt line 6: Replace with 'lastArg':
                @@ -6 +6
                -     arg(args.size - 1)
                +     lastArg()
                Fix for src/test/pkg/Positions.kt line 7: Replace with 'lastArg':
                @@ -7 +7
                -     param.arg(param.method.parameterTypes.lastIndex)
                +     param.lastArg()
                Fix for src/test/pkg/Positions.kt line 10: Replace with 'lastArg':
                @@ -10 +10
                -     arg(last)
                +     lastArg()
                Fix for src/test/pkg/Positions.kt line 12: Replace with 'lastArg':
                @@ -12 +12
                -     arg(methodRef.parameterTypes.size - 1)
                +     lastArg()
                Fix for src/test/pkg/Positions.kt line 13: Replace with 'lastArg().set':
                @@ -13 +13
                -     param.args[param.args.lastIndex] = null
                +     param.lastArg().set(null)
                Fix for src/test/pkg/Positions.kt line 14: Replace with 'lastArg':
                @@ -14 +14
                -     arg(method.parameterCount - 1)
                +     lastArg()
                Fix for src/test/pkg/Positions.kt line 16: Replace with 'lastArg':
                @@ -16 +16
                -     param.arg(lastFromCount)
                +     param.lastArg()
                Fix for src/test/pkg/Positions.kt line 18: Replace with 'lastArg().set':
                @@ -18 +18
                -     args[count - 1] = null
                +     lastArg().set(null)
            """.trimIndent())
    }

    fun testUnrelatedAndUnsupportedArgumentReadsAreIgnored() {
        lint().files(hookParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookChain
            import com.highcapable.yukihookapi.hook.param.HookParam
            import java.lang.reflect.Method

            fun HookParam.edit(args: Array<Any?>, other: HookParam, unrelated: Method) {
                val value = args[0] as? String
                args[0] = value
                this.args[0]?.toString()
                arg(1)
                arg(this.args.size - 2)
                arg(other.method.parameterTypes.lastIndex)
                arg(unrelated.parameterTypes.size - 1)
                arg(method.parameterCount)
                arg(method.parameterCount - 2)
                arg(other.constructor.parameterCount - 1)
                arg(unrelated.parameterCount - 1)
                var last = method.parameterTypes.lastIndex
                last--
                arg(last)
            }
            fun HookChain.read(other: HookChain, otherArgs: List<Any?>) {
                args[1]
                args[0] as? String
                arg(1) as? String
                argOrNull(1) as String?
                arg(other.args.lastIndex)
                argOrNull(other.constructor.parameterTypes.size - 1)
                args.firstOrNull { it is String }
                otherArgs.getOrNull(1)
                otherArgs.lastOrNull() as? String
                proceed() as? String
                proceedWith("other") as? String
                proceed<CharSequence>() as String?
            }
            fun HookChain.shadowedLookup() {
                fun List<Any?>.getOrNull(index: Int) = first()
                args.getOrNull(1)
            }
            fun HookParam.localArg() {
                fun arg(i: Int) = i
                arg(0)
            }
        """).indented()).testModes(TestMode.DEFAULT).run().expectClean()
    }

    fun testHookChainArgumentShortcutsKeepTypesAndBoundsBehavior() {
        lint().files(hookParam, kotlin("src/test/pkg/ChainPositions.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookChain

            fun HookChain.read(chain: HookChain, optional: HookChain?) {
                optional?.arg(index = 0)
                arg<String>(0)
                arg(args.size - 1)
                val last = chain.method.parameterTypes.lastIndex
                chain.argOrNull<String>(last)
                argOrNull(0)
                chain.arg<Int>(chain.constructor.parameterTypes.size - 1)
                argOrNull(method.parameterCount - 1)
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(7, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/ChainPositions.kt line 5: Replace with 'firstArg':
                @@ -5 +5
                -     optional?.arg(index = 0)
                +     optional?.firstArg()
                Fix for src/test/pkg/ChainPositions.kt line 6: Replace with 'firstArg':
                @@ -6 +6
                -     arg<String>(0)
                +     firstArg<String>()
                Fix for src/test/pkg/ChainPositions.kt line 7: Replace with 'lastArg':
                @@ -7 +7
                -     arg(args.size - 1)
                +     lastArg()
                Fix for src/test/pkg/ChainPositions.kt line 9: Replace with 'lastArgOrNull':
                @@ -9 +9
                -     chain.argOrNull<String>(last)
                +     chain.lastArgOrNull<String>()
                Fix for src/test/pkg/ChainPositions.kt line 10: Replace with 'firstArgOrNull':
                @@ -10 +10
                -     argOrNull(0)
                +     firstArgOrNull()
                Fix for src/test/pkg/ChainPositions.kt line 11: Replace with 'lastArg':
                @@ -11 +11
                -     chain.arg<Int>(chain.constructor.parameterTypes.size - 1)
                +     chain.lastArg<Int>()
                Fix for src/test/pkg/ChainPositions.kt line 12: Replace with 'lastArgOrNull':
                @@ -12 +12
                -     argOrNull(method.parameterCount - 1)
                +     lastArgOrNull()
            """.trimIndent())
    }

    fun testHookChainOptionalReadsFoldSafeCastsIntoOneFix() {
        lint().files(hookParam, kotlin("src/test/pkg/ChainReads.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookChain

            fun HookChain.read(chain: HookChain?, i: Int) {
                args.getOrNull(index = i)
                args.firstOrNull()
                args.lastOrNull()
                val first = (chain?.args?.getOrNull(0) as? String?)?.length
                val indexed = argOrNull(i) as? String
                val last = args.getOrNull(args.lastIndex) as? CharSequence
                val explicitLast = lastArgOrNull() as? String
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(7, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/ChainReads.kt line 5: Replace with 'argOrNull':
                @@ -5 +5
                -     args.getOrNull(index = i)
                +     argOrNull(index = i)
                Fix for src/test/pkg/ChainReads.kt line 6: Replace with 'firstArgOrNull':
                @@ -6 +6
                -     args.firstOrNull()
                +     firstArgOrNull()
                Fix for src/test/pkg/ChainReads.kt line 7: Replace with 'lastArgOrNull':
                @@ -7 +7
                -     args.lastOrNull()
                +     lastArgOrNull()
                Fix for src/test/pkg/ChainReads.kt line 8: Replace with 'firstArgOrNull':
                @@ -8 +8
                -     val first = (chain?.args?.getOrNull(0) as? String?)?.length
                +     val first = chain?.firstArgOrNull<String>()?.length
                Fix for src/test/pkg/ChainReads.kt line 9: Replace with 'argOrNull':
                @@ -9 +9
                -     val indexed = argOrNull(i) as? String
                +     val indexed = argOrNull<String>(i)
                Fix for src/test/pkg/ChainReads.kt line 10: Replace with 'lastArgOrNull':
                @@ -10 +10
                -     val last = args.getOrNull(args.lastIndex) as? CharSequence
                +     val last = lastArgOrNull<CharSequence>()
                Fix for src/test/pkg/ChainReads.kt line 11: Replace with 'lastArgOrNull':
                @@ -11 +11
                -     val explicitLast = lastArgOrNull() as? String
                +     val explicitLast = lastArgOrNull<String>()
            """.trimIndent())
    }

    fun testHookChainHardCastsUseTypedReadsAndProceedCalls() {
        lint().files(hookParam, kotlin("src/test/pkg/ChainCasts.kt", """
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.HookChain

            fun HookChain.read(chain: HookChain, receiver: Any, values: Array<Any?>, i: Int) {
                val indexed = chain.args[i] as String?
                val first = (arg(0) as String).length
                val next = proceed() as String?
                val modified = proceed(args = values) as String
                val other = chain.proceedWith(receiver) as String?
                val both = chain.proceedWith(instance = receiver, args = values) as List<String?>
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(6, Severity.INFORMATIONAL)
            .expectFixDiffs("""
                Fix for src/test/pkg/ChainCasts.kt line 5: Replace with 'arg':
                @@ -5 +5
                -     val indexed = chain.args[i] as String?
                +     val indexed = chain.arg<String>(i)
                Fix for src/test/pkg/ChainCasts.kt line 6: Replace with 'firstArg':
                @@ -6 +6
                -     val first = (arg(0) as String).length
                +     val first = firstArg<String>()!!.length
                Fix for src/test/pkg/ChainCasts.kt line 7: Replace with 'proceed':
                @@ -7 +7
                -     val next = proceed() as String?
                +     val next = proceed<String>()
                Fix for src/test/pkg/ChainCasts.kt line 8: Replace with 'proceed':
                @@ -8 +8
                -     val modified = proceed(args = values) as String
                +     val modified = proceed<String>(args = values)!!
                Fix for src/test/pkg/ChainCasts.kt line 9: Replace with 'proceedWith':
                @@ -9 +9
                -     val other = chain.proceedWith(receiver) as String?
                +     val other = chain.proceedWith<String>(receiver)
                Fix for src/test/pkg/ChainCasts.kt line 10: Replace with 'proceedWith':
                @@ -10 +10
                -     val both = chain.proceedWith(instance = receiver, args = values) as List<String?>
                +     val both = chain.proceedWith<List<String?>>(instance = receiver, args = values)!!
            """.trimIndent())
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