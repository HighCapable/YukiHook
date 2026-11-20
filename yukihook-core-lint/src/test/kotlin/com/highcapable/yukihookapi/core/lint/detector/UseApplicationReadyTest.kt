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
@file:Suppress(
    "UnusedReceiverParameter", "LocalVariableName", "RedundantUnitExpression",
    "UnnecessaryVariable", "CanConvertToMultiDollarString"
)

package com.highcapable.yukihookapi.core.lint.detector

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Severity

/**
 * Checks migration advice for hooks used as an application-ready callback.
 */
class UseApplicationReadyTest : LintDetectorTest() {

    companion object {

        private val invocation = kotlin("""
            package com.highcapable.yukihookapi.hook.param

            abstract class HookInvocation {
                val instance: Any get() = error("stub")
                inline fun <reified T> instance(): T = error("stub")
            }
            class HookParam : HookInvocation() {
                val args: Array<Any?> = emptyArray()
                var result: Any? = null
                val hasThrowable: Boolean = false
                fun firstArg() = ArgumentEditor()
                fun lastArg() = ArgumentEditor()
                fun arg(index: Int) = ArgumentEditor()
                class ArgumentEditor {
                    fun <T> get(): T? = null
                    fun <T> set(value: T?) = Unit
                }
            }
            class HookChain : HookInvocation() {
                val args: List<Any?> = emptyList()
                fun proceed(): Any? = null
                fun proceed(args: Array<out Any?>): Any? = null
                fun <T> firstArg(): T? = null
            }
        """).indented()

        private val hooker = kotlin("""
            package com.highcapable.yukihookapi.hook.core
            import com.highcapable.yukihookapi.hook.param.HookParam

            object YukiHookCreator {
                class ClassicMemberHooker {
                    fun before(callback: HookParam.() -> Unit) = Unit
                    fun after(callback: HookParam.() -> Unit) = Unit
                    fun intercept(callback: HookParam.() -> Any?) = Unit
                }
            }
        """).indented()

        private val packageParam = kotlin("""
            package com.highcapable.yukihookapi.hook.param
            import android.app.Application
            import com.highcapable.yukihookapi.hook.core.YukiHookCreator
            import java.lang.reflect.Member

            class PackageParam {
                val hostClassLoader: ClassLoader? = null
                fun Member.hook(priority: Int = 0) = YukiHookCreator.ClassicMemberHooker()
                fun Member.hook(priority: Int = 0, hooker: YukiHookCreator.ClassicMemberHooker.() -> Unit) = Unit
                fun Collection<Member>.hookAll(priority: Int = 0, hooker: YukiHookCreator.ClassicMemberHooker.() -> Unit) = Unit
                fun Class<*>.hook(priority: Int = 0, hooker: YukiHookCreator.ClassicMemberHooker.() -> Unit) = Unit
                fun Member.intercept(priority: Int = 0, invocation: HookChain.() -> Any?) = Unit
                fun registerAppLifecycle(throwToHostOnFailure: Boolean = true, block: AppLifecycle.() -> Unit) = Unit
                fun onAppLifecycle(block: AppLifecycle.() -> Unit) = Unit
                fun withApplicationReady(block: (Application) -> Unit) = Unit
                class AppLifecycle {
                    fun onCreate(callback: Application.() -> Unit) = Unit
                }
            }
        """).indented()

        private val kava = kotlin("""
            package com.highcapable.kavaref
            import com.highcapable.kavaref.condition.MethodCondition
            import com.highcapable.kavaref.resolver.MethodResolver
            import kotlin.reflect.KClass

            class KavaRef {
                companion object {
                    fun <T : Any> KClass<T>.resolve(): MemberScope<T> = MemberScope()
                    fun <T : Any> Class<T>.resolve(): MemberScope<T> = MemberScope()
                    fun <T : Any> T.asResolver(): MemberScope<T> = MemberScope()
                }
                class MemberScope<T : Any> {
                    fun optional(silent: Boolean = false): MemberScope<T> = this
                    fun method(): MethodCondition<T> = MethodCondition()
                    fun method(condition: MethodCondition<T>): List<MethodResolver<T>> = emptyList()
                    fun method(condition: MethodCondition<T>.() -> Unit): List<MethodResolver<T>> = emptyList()
                    fun firstMethod(condition: MethodCondition<T>.() -> Unit): MethodResolver<T> = error("stub")
                    fun firstMethod(condition: MethodCondition<T>): MethodResolver<T> = error("stub")
                    fun firstMethodOrNull(condition: MethodCondition<T>.() -> Unit): MethodResolver<T>? = null
                    fun lastMethod(condition: MethodCondition<T>.() -> Unit): MethodResolver<T> = error("stub")
                    fun lastMethodOrNull(condition: MethodCondition<T>.() -> Unit): MethodResolver<T>? = null
                }
            }
        """).indented()

        private val conditionBase = kotlin("""
            package com.highcapable.kavaref.condition.base

            open class MemberCondition {
                class Configuration<T : Any>(val declaringClass: Class<T>)
                var name: String = ""
                open fun name(name: String): MemberCondition = this
                open fun name(predicate: (String) -> Boolean): MemberCondition = this
            }
            open class ExecutableCondition : MemberCondition() {
                val parameters = mutableListOf<Any>()
                var parameterCount: Int? = null
                open fun parameters(vararg types: Any): ExecutableCondition = this
                open fun emptyParameters(): ExecutableCondition = this
                open fun parameterCount(count: Int): ExecutableCondition = this
            }
        """).indented()

        private val condition = kotlin("""
            package com.highcapable.kavaref.condition
            import com.highcapable.kavaref.condition.base.ExecutableCondition
            import com.highcapable.kavaref.condition.base.MemberCondition
            import com.highcapable.kavaref.resolver.MethodResolver

            class MethodCondition<T : Any> : ExecutableCondition() {
                override fun name(name: String): MethodCondition<T> = this
                override fun parameters(vararg types: Any): MethodCondition<T> = this
                override fun emptyParameters(): MethodCondition<T> = this
                override fun parameterCount(count: Int): MethodCondition<T> = this
                var returnType: Any? = null
                fun returnType(type: Any): MethodCondition<T> = this
                fun copy(): MethodCondition<T> = this
                fun build(configuration: MemberCondition.Configuration<T>? = null): List<MethodResolver<T>> = emptyList()
            }
        """).indented()

        private val methodResolver = kotlin("""
            package com.highcapable.kavaref.resolver
            import java.lang.reflect.Method

            class MethodResolver<T : Any> {
                val self: Method get() = error("stub")
            }
        """).indented()

        private val kavaHook = kotlin("""
            package com.highcapable.yukihookapi.extension.kavaref
            import com.highcapable.kavaref.resolver.MethodResolver
            import com.highcapable.yukihookapi.hook.core.YukiHookCreator
            import com.highcapable.yukihookapi.hook.param.PackageParam

            context(param: PackageParam)
            fun MethodResolver<*>.hook(priority: Int = 0, hooker: YukiHookCreator.ClassicMemberHooker.() -> Unit) = Unit

            context(param: PackageParam)
            fun Collection<MethodResolver<*>>.hookAll(priority: Int = 0, hooker: YukiHookCreator.ClassicMemberHooker.() -> Unit) = Unit
        """).indented()
    }

    override fun getDetector() = ModuleUsageDetector()
    override fun getIssues() = listOf(ModuleUsageDetector.USE_APPLICATION_READY_ISSUE)

    fun testReflectionFollowsAliasesAndReportsAtRegistrationWithoutFix() {
        lint().files(invocation, hooker, packageParam, kotlin("src/test/pkg/Usage.kt", """
            package test.pkg
            import android.app.Application
            import com.highcapable.yukihookapi.hook.param.PackageParam

            private const val CREATE = "onCreate"
            fun initialize(app: Application) = Unit
            fun PackageParam.install() {
                val owner = Application::class.java
                val create = owner.getDeclaredMethod(CREATE)
                create.hook { after { initialize(instance<Application>()) } }
                create.hook().after { initialize(instance as Application) }
                create.hook().apply { after { initialize(instance<Application>()) } }
                listOf(create).hookAll { after { initialize(instance<Application>()) } }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(4, Severity.INFORMATIONAL)
            .expectContains("src/test/pkg/Usage.kt:10: Hint: Use withApplicationReady for host application initialization")
            .expectFixDiffs("")
    }

    fun testKavaRefBeforeAndImplicitScope() {
        lint().files(invocation, hooker, packageParam, kava, conditionBase, condition, methodResolver, kavaHook, kotlin("src/test/pkg/Usage.kt", """
            package test.pkg
            import android.app.Application
            import android.app.Instrumentation
            import com.highcapable.kavaref.KavaRef.Companion.resolve
            import com.highcapable.yukihookapi.extension.kavaref.hookAll
            import com.highcapable.yukihookapi.hook.param.PackageParam

            fun initialize(app: Application) = Unit
            fun PackageParam.install() {
                Instrumentation::class.resolve().method {
                    name = "callApplicationOnCreate"
                    parameters(Application::class)
                }.hookAll { before { initialize(args[0] as Application) } }
                Instrumentation::class.resolve().optional().apply {
                    val create = firstMethod { name("callApplicationOnCreate").parameterCount(1) }
                    create.self.hook { after { firstArg().get<Application>()?.let(::initialize) } }
                }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(2, Severity.INFORMATIONAL)
            .expectContains("src/test/pkg/Usage.kt:10: Hint: If initialization does not depend on this lifecycle event or its timing")
            .expectContains("src/test/pkg/Usage.kt:16: Hint: Use withApplicationReady for host application initialization")
            .expectFixDiffs("")
    }

    fun testComponentApplicationIntentWithAndWithoutKavaRefSignature() {
        lint().files(invocation, hooker, packageParam, kava, conditionBase, condition, methodResolver, kavaHook, kotlin("src/test/pkg/Usage.kt", """
            package test.pkg
            import android.app.Activity
            import android.app.Application
            import android.app.Instrumentation
            import android.os.Bundle
            import com.highcapable.kavaref.KavaRef.Companion.resolve
            import com.highcapable.yukihookapi.extension.kavaref.hook
            import com.highcapable.yukihookapi.hook.param.PackageParam

            fun initialize(app: Application?) = Unit
            fun PackageParam.install() {
                Instrumentation::class.java.resolve().firstMethod {
                    name = "callActivityOnCreate"
                    parameters(Activity::class, Bundle::class)
                }.hook { after { arg(0).get<Application>() } }
                val create = Instrumentation::class.java.resolve().firstMethod { name = "callActivityOnCreate" }
                create.hook { after { arg(0).get<Application>() } }
                create.hook { after { initialize(args[0] as? Application) } }
                create.hook { after { initialize(arg(0).get<Activity>()?.application) } }
                create.hook { after { arg(0).get<Activity>()?.window } }
                create.hook { after { arg(1).get<Application>() } }
                create.hook { after { arg(0).get<Application>(); arg(0).get<Activity>()?.window } }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(4, Severity.INFORMATIONAL)
            .expectContains("src/test/pkg/Usage.kt:12: Hint: Reading a component as Application does not retrieve its application")
            .expectContains("src/test/pkg/Usage.kt:17: Hint: Reading a component as Application does not retrieve its application")
            .expectContains("src/test/pkg/Usage.kt:18: Hint: Reading a component as Application does not retrieve its application")
            .expectContains("src/test/pkg/Usage.kt:19: Hint: If initialization does not depend on this lifecycle event or its timing")
            .expectFixDiffs("")
    }

    fun testClassFactoriesAndKavaRefBuilderForms() {
        val classFactories = kotlin("""
            package com.highcapable.kavaref.extension

            inline fun <reified T : Any> classOf(primitiveType: Boolean = true): Class<T> = T::class.java
            fun String.toClass(loader: ClassLoader? = null): Class<*> = error("stub")
            fun String.toClassOrNull(loader: ClassLoader? = null): Class<*>? = null
            fun lazyClass(name: String): Lazy<Class<*>> = lazy { error("stub") }
            fun lazyClassOrNull(name: String): Lazy<Class<*>?> = lazy { null }
        """).indented()
        lint().files(invocation, hooker, packageParam, kava, conditionBase, condition, methodResolver, kavaHook, classFactories, kotlin("src/test/pkg/Factories.kt", """
            package test.pkg
            import android.app.Application
            import android.app.Instrumentation
            import com.highcapable.kavaref.KavaRef.Companion.resolve
            import com.highcapable.kavaref.KavaRef.Companion.asResolver
            import com.highcapable.kavaref.condition.MethodCondition
            import com.highcapable.kavaref.condition.base.MemberCondition
            import com.highcapable.kavaref.extension.*
            import com.highcapable.yukihookapi.extension.kavaref.hook
            import com.highcapable.yukihookapi.extension.kavaref.hookAll
            import com.highcapable.yukihookapi.hook.param.PackageParam

            private const val OWNER = "android.app.Instrumentation"
            private const val CREATE = "callApplicationOnCreate"
            private val lazyOwner by lazyClass(OWNER)
            private val appOwner = Application::class.java
            class HostApplication : Application() { override fun onCreate() = Unit }
            class OverloadedApplication : Application() {
                override fun onCreate() = Unit
                fun onCreate(other: String) = Unit
            }
            fun initialize(value: Any?) = Unit
            fun PackageParam.install(dynamic: String) {
                classOf<Instrumentation>().resolve().firstMethod { name = CREATE }.hook { after { initialize(args[0]) } }
                val clazz = Instrumentation::class.java.kotlin
                clazz.resolve().firstMethodOrNull { name(CREATE) }?.hook { before { initialize(args[0]) } }
                OWNER.toClass().resolve().lastMethod { name = CREATE; parameters(Application::class.java) }
                    .hook { after { initialize(args[0]) } }
                OWNER.toClassOrNull()?.resolve()?.lastMethodOrNull { name = CREATE }?.hook { after { initialize(args[0]) } }
                val nullableOwner by lazyClassOrNull(OWNER)
                nullableOwner?.resolve()?.method { name = CREATE }?.hookAll { after { initialize(args[0]) } }
                lazyOwner.resolve().method().name(CREATE).parameters(Application::class).build().first()
                    .hook { after { initialize(args[0]) } }
                val query = MethodCondition<Instrumentation>().apply { name = CREATE; parameterCount(1) }
                classOf<Instrumentation>().resolve().firstMethod(query).hook { after { initialize(args[0]) } }
                classOf<Instrumentation>().resolve().method(query.copy()).hookAll { after { initialize(args[0]) } }
                query.build(MemberCondition.Configuration(Instrumentation::class.java)).hookAll { after { initialize(args[0]) } }
                Instrumentation().asResolver().firstMethod { name = CREATE }.hook { after { initialize(args[0]) } }
                val builder = classOf<Instrumentation>().resolve().method()
                builder.name = CREATE
                builder.parameters += listOf(Application::class)
                builder.build().first().hook { after { initialize(args[0]) } }
                classOf<Instrumentation>().resolve().lastMethod { name(CREATE); returnType = Void.TYPE; parameterCount = 1 }
                    .hook { after { initialize(args[0]) } }
                appOwner.getMethod("onCreate").hook { after { initialize(instance) } }
                HostApplication::class.resolve().firstMethod { name = "onCreate" }.hook { after { initialize(instance) } }
                OverloadedApplication::class.resolve().firstMethod { name = "onCreate" }.hook { after { initialize(instance) } }
                val resolved = classOf<Instrumentation>().resolve().method { name = CREATE }
                resolved[0].hook { after { initialize(args[0]) } }
                resolved.map { it.self }.hookAll { after { initialize(args[0]) } }
                val signature = arrayOf(Application::class.java)
                classOf<Instrumentation>().getMethod(CREATE, *signature).hook { after { initialize(args[0]) } }
                val create = hostClassLoader?.loadClass(OWNER)?.getDeclaredMethod(CREATE, Application::class.java)
                create?.hook { after { initialize(args[0]) } }
                dynamic.toClass().resolve().firstMethod { name = CREATE }.hook { after { initialize(args[0]) } }
                classOf<Instrumentation>().resolve().firstMethod { name { it == CREATE } }.hook { after { initialize(args[0]) } }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(18, Severity.INFORMATIONAL)
            .expectFixDiffs("")
    }

    fun testReflectiveMethodFiltersAndLoops() {
        lint().files(invocation, hooker, packageParam, kotlin("src/test/pkg/Loops.kt", """
            package test.pkg
            import android.app.Application
            import android.app.Instrumentation
            import com.highcapable.yukihookapi.hook.param.PackageParam
            import java.lang.reflect.Method

            private const val CREATE = "callApplicationOnCreate"
            fun initialize(value: Any?) = Unit
            fun PackageParam.install() {
                val methods = Instrumentation::class.java.declaredMethods
                methods.first { it.name == CREATE }.hook { after { initialize(args[0]) } }
                Instrumentation::class.java.methods.filter { method ->
                    method.name == CREATE && method.parameterTypes.contentEquals(arrayOf(Application::class.java))
                }.hookAll { after { initialize(args[0]) } }
                methods.forEach {
                    if (it.name != CREATE) return@forEach
                    it.hook { after { initialize(args[0]) } }
                }
                for (method in methods) {
                    if (method.name == CREATE) method.hook { after { initialize(args[0]) } }
                }
                var i = 0
                var selected: Method? = null
                while (i < methods.size) {
                    val method = methods[i++]
                    if (method.name != CREATE) continue
                    selected = method
                    break
                }
                selected?.hook { after { initialize(args[0]) } }
                fun search(pool: Array<Method>, index: Int): Method? {
                    val method = pool.getOrNull(index) ?: return null
                    return if (method.name == CREATE) method else search(pool, index + 1)
                }
                search(methods, 0)?.hook { after { initialize(args[0]) } }
                for (method in methods) {
                    when (method.name) {
                        CREATE -> method.hook { after { initialize(args[0]) } }
                    }
                }
                methods.first().hook { after { initialize(args[0]) } }
                methods.filter { it.name == CREATE && it.parameterTypes.contentEquals(arrayOf(String::class.java)) }
                    .hookAll { after { initialize(args[0]) } }
                methods.first { it.name == CREATE || it.name == "callActivityOnResume" }.hook { after { initialize(args[0]) } }
                methods.forEach { method ->
                    if (methods.size == 1 && methods[0].name == CREATE) method.toString()
                    method.hook { after { initialize(args[0]) } }
                }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(7, Severity.INFORMATIONAL)
            .expectFixDiffs("")
    }

    fun testOtherStartupPathsAndExistingLifecycleDsl() {
        lint().files(invocation, hooker, packageParam, kotlin("""
            package test.pkg
            import android.app.Activity
            import android.app.Application
            import android.app.Instrumentation
            import android.content.Context
            import android.content.ContextWrapper
            import android.os.Bundle
            import com.highcapable.yukihookapi.hook.param.PackageParam

            class HostApp : Application() { override fun onCreate() = Unit }
            fun initialize(context: Context?) = Unit
            fun PackageParam.install(loader: ClassLoader) {
                HostApp::class.java.getDeclaredMethod("onCreate").hook { after { initialize(instance<HostApp>()) } }
                Application::class.java.getDeclaredMethod("attach", Context::class.java).hook { before { initialize(args[0] as Context) } }
                ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).hook {
                    after { initialize(instance as? Application ?: return@after) }
                }
                Instrumentation::class.java.getMethod("newApplication", Class::class.java, Context::class.java).hook {
                    after { initialize(result as? Application) }
                }
                val owner = loader.loadClass("android.app.LoadedApk")
                owner.getDeclaredMethod("makeApplicationInner", Boolean::class.javaPrimitiveType!!, Instrumentation::class.java).hook {
                    after { initialize(result as? Application) }
                }
                Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java).hook {
                    after { val activity = instance as Activity; initialize(activity.applicationContext) }
                }
                registerAppLifecycle { onCreate { initialize(this) } }
                onAppLifecycle { onCreate { initialize(applicationContext) } }
                withApplicationReady { initialize(it) }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(8, Severity.INFORMATIONAL)
            .expectFixDiffs("")
    }

    fun testComponentAndInvocationDependentHooksAreNotMigrationCandidates() {
        lint().files(invocation, hooker, packageParam, kotlin("""
            package test.pkg
            import android.app.Activity
            import android.app.Application
            import android.app.Instrumentation
            import android.content.Context
            import android.content.ContextWrapper
            import android.os.Bundle
            import com.highcapable.yukihookapi.hook.param.PackageParam

            class Unrelated { fun onCreate() = Unit }
            fun consume(value: Any?) = Unit
            fun PackageParam.install(dynamic: String, other: Application) {
                val create = Application::class.java.getMethod("onCreate")
                create.hook()
                create.hook { after { consume(other) } }
                create.hook { after { consume(instance); result = Unit } }
                create.hook { after { if (!hasThrowable) consume(instance) } }
                listOf(create, Unrelated::class.java.getMethod("onCreate")).hookAll { after { consume(instance) } }
                create.hook { intercept { consume(instance); Unit } }
                create.intercept { consume(instance); proceed() }
                Unrelated::class.java.getMethod("onCreate").hook { after { consume(instance) } }
                Application::class.java.getMethod(dynamic).hook { after { consume(instance) } }
                Application::class.java.hook { after { consume(instance) } }
                Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java).hook { after { consume(instance as Activity) } }
                Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java).hook {
                    after { val activity = instance<Activity>(); consume(activity.applicationContext); consume(activity.window) }
                }
                Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java).hook {
                    after { val activity = instance<Activity>(); consume(activity.applicationContext); consume(activity) }
                }
                ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).hook { after { consume(args[0]) } }
                Instrumentation::class.java.getMethod("callApplicationOnCreate", Application::class.java).hook {
                    before { val incoming = args; consume(incoming[0]); incoming[0] = other }
                }
                registerAppLifecycle { onCreate { consume(other) } }
            }
        """).indented()).testModes(TestMode.DEFAULT).run().expectClean()
    }

    fun testBindAndComponentDispatchRequireApplicationContext() {
        val activityThread = java("""
            package android.app;
            public class ActivityThread {
                public static class AppBindData {}
                public static Application currentApplication() { return null; }
            }
        """).indented()
        lint().files(invocation, hooker, packageParam, activityThread, kotlin("src/test/pkg/Usage.kt", """
            package test.pkg
            import android.app.Activity
            import android.app.ActivityThread
            import android.app.Instrumentation
            import android.content.Context
            import android.content.ContentProvider
            import android.os.Bundle
            import com.highcapable.yukihookapi.hook.param.PackageParam

            fun initialize(context: Context?) = Unit
            fun PackageParam.install() {
                val thread = Class.forName("android.app.ActivityThread")
                val data = Class.forName("android.app.ActivityThread\${'$'}AppBindData")
                val bind = thread.getDeclaredMethod("handleBindApplication", data)
                bind.hook { before { initialize(ActivityThread.currentApplication()) } }
                bind.hook { after { initialize(ActivityThread.currentApplication()) } }
                bind.hook {
                    after { initialize(thread.getDeclaredMethod("currentApplication").invoke(null) as? Context) }
                }
                Instrumentation::class.java.getMethod("callActivityOnCreate", Activity::class.java, Bundle::class.java).hook {
                    after { initialize(firstArg().get<Activity>()?.application) }
                }
                ContentProvider::class.java.getDeclaredMethod("onCreate").hook {
                    after { initialize(instance<ContentProvider>().context?.applicationContext) }
                }
                ContentProvider::class.java.getDeclaredMethod("onCreate").hook {
                    after { initialize(instance<ContentProvider>().context) }
                }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(4, Severity.INFORMATIONAL)
            .expectContains("src/test/pkg/Usage.kt:16: Hint: If initialization does not depend on this lifecycle event or its timing")
            .expectContains("src/test/pkg/Usage.kt:17: Hint: If initialization does not depend on this lifecycle event or its timing")
            .expectContains("src/test/pkg/Usage.kt:20: Hint: If initialization does not depend on this lifecycle event or its timing")
            .expectFixDiffs("")
    }

    fun testLetLambdaCanReturnApplicationFromTheHookedActivity() {
        lint().files(invocation, hooker, packageParam, kotlin("src/test/pkg/ActivityLet.kt", """
            package test.pkg
            import android.app.Activity
            import android.app.Instrumentation
            import android.os.Bundle
            import com.highcapable.yukihookapi.hook.param.PackageParam

            fun consume(value: Any?) = Unit
            fun PackageParam.install(other: Activity) {
                val create = Instrumentation::class.java.getMethod("callActivityOnCreate", Activity::class.java, Bundle::class.java)
                create.hook { after { arg(0).get<Activity>()!!.let { it.application } } }
                create.hook { after { arg(0).get<Activity>()?.let { it.application } } }
                create.hook { after { consume(firstArg().get<Activity>()?.let { activity ->
                    val alias = activity
                    alias.applicationContext
                }) } }
                create.hook { after { firstArg().get<Activity>()?.let { it.let { nested -> nested.application } } } }
                create.hook { after { firstArg().get<Activity>()?.let { it.window; it.application } } }
                create.hook { after { firstArg().get<Activity>()?.let { consume(it); it.application } } }
                create.hook { after { firstArg().get<Activity>()?.let { other.application } } }
                fun Activity.let(block: (Activity) -> Any?) = block(other)
                create.hook { after { firstArg().get<Activity>()?.let { it.application } } }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(4, Severity.INFORMATIONAL)
            .expectContains("src/test/pkg/ActivityLet.kt:10: Hint: If initialization does not depend on this lifecycle event or its timing")
            .expectContains("src/test/pkg/ActivityLet.kt:11: Hint: If initialization does not depend on this lifecycle event or its timing")
            .expectContains("src/test/pkg/ActivityLet.kt:12: Hint: If initialization does not depend on this lifecycle event or its timing")
            .expectContains("src/test/pkg/ActivityLet.kt:16: Hint: If initialization does not depend on this lifecycle event or its timing")
            .expectFixDiffs("")
    }

    fun testCallbackReadsAndWholeHookExpression() {
        lint().files(invocation, hooker, packageParam, kotlin("src/test/pkg/CallbackRanges.kt", """
            package test.pkg
            import android.app.Application
            import android.app.Instrumentation
            import com.highcapable.yukihookapi.hook.param.PackageParam

            fun initialize(value: Any?) = Unit
            fun PackageParam.install() {
                val create = Instrumentation::class.java.getMethod("callApplicationOnCreate", Application::class.java)
                create.hook().after { initialize(lastArg().get<Application>()) }
                create.hook().apply { before { initialize(args.firstOrNull()) } }
                create.intercept { proceed().also { initialize(args.getOrNull(0)) } }
                create.intercept { proceed().also { initialize(args[0]); proceed() } }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expect("""
                src/test/pkg/CallbackRanges.kt:9: Hint: Use withApplicationReady for host application initialization so it also runs after module reload [UseApplicationReady]
                    create.hook().after { initialize(lastArg().get<Application>()) }
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/CallbackRanges.kt:10: Hint: If initialization does not depend on this lifecycle event or its timing, use withApplicationReady to run it again after module reload [UseApplicationReady]
                    create.hook().apply { before { initialize(args.firstOrNull()) } }
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/CallbackRanges.kt:11: Hint: Use withApplicationReady for host application initialization so it also runs after module reload [UseApplicationReady]
                    create.intercept { proceed().also { initialize(args.getOrNull(0)) } }
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 0 warnings, 3 hints
            """.trimIndent())
            .expectFixDiffs("")
    }

    fun testObservingChainKeepsTheDownstreamResult() {
        lint().files(invocation, hooker, packageParam, kotlin("""
            package test.pkg
            import android.app.Application
            import com.highcapable.yukihookapi.hook.param.PackageParam

            fun initialize(app: Application) = Unit
            fun PackageParam.install() {
                val create = Application::class.java.getMethod("onCreate")
                create.intercept {
                    val downstream = proceed()
                    initialize(instance<Application>())
                    downstream
                }
                create.intercept {
                    val downstream = proceed(arrayOf("different"))
                    initialize(instance<Application>())
                    downstream
                }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectCount(1, Severity.INFORMATIONAL)
            .expectFixDiffs("")
    }
}