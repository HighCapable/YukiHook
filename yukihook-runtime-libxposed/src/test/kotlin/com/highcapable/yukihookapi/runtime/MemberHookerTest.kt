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
@file:Suppress("RedundantNullableReturnType")

package com.highcapable.yukihookapi.runtime

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.HookChain
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.runtime.bridge.ExecutableInvokerBridge
import com.highcapable.yukihookapi.runtime.bridge.HookRegistrationBridge
import com.highcapable.yukihookapi.runtime.bridge.hooker.ChainHookerBridge
import com.highcapable.yukihookapi.runtime.bridge.hooker.ClassicHookerBridge
import com.highcapable.yukihookapi.runtime.bridge.hooker.HookPoint
import com.highcapable.yukihookapi.runtime.bridge.hooker.HookerBridge
import com.highcapable.yukihookapi.runtime.libxposed.adapter.ClassicInvocationAdapter
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * Exercises the user-facing classic and chain APIs through the runtime registration boundary.
 */
class MemberHookerTest {

    private val scope = object : YukiBaseHooker() {
        override fun onHook() = Unit
    }

    private val method = String::class.resolve().firstMethod {
        name = "substring"
        parameters(Int::class)
    }.self

    private val otherMethod = String::class.resolve().firstMethod {
        name = "length"
        emptyParameters()
    }.self

    private val voidMethod = StringBuilder::class.resolve().firstMethod {
        name = "setLength"
        parameters(Int::class)
    }.self

    private val hookers = mutableListOf<HookerBridge>()
    private val removed = mutableListOf<Member>()
    private val previousLevel = YLog.Config.level
    private val registerHook = { target: HookPoint, _: Int, hooker: HookerBridge, _: String? ->
        val member = (target as HookPoint.Executable).value
        hookers += hooker
        object : HookerBridge {
            override fun remove() { removed += member }
        }
    }

    private fun registrations(
        onHook: (HookPoint, Int, HookerBridge, String?) -> HookerBridge
    ) = object : HookRegistrationBridge {
        override val defaultPriority = 50
        override val lowestPriority = -10000
        override val highestPriority = 10000
        override fun hook(target: HookPoint, priority: Int, hooker: HookerBridge, id: String?) =
            onHook(target, priority, hooker, id)

        override fun replace(handle: HookerBridge, replacement: HookerBridge): HookerBridge =
            error("Unexpected hook replacement.")

        override fun deoptimize(member: Member): Boolean = error("Unexpected deoptimization.")
    }

    @Before
    fun installRuntime() {
        YLog.Config.level = YLog.Level.OFF
        YukiHookBridge.attach(YukiHookBridge.ApiSpec(hookRegistrationBridge = registrations(registerHook)))
    }

    @After
    fun restoreRuntime() {
        YLog.Config.level = previousLevel
    }

    @Test
    fun typedAndUntypedProceedOverloadsForwardArgumentsAndReceiver() {
        val chain = RecordingChain(method)
        val arguments = arrayOf<Any?>(2)
        val receiver = "another"
        scope.run {
            method.intercept {
                val invocation: HookChain = this
                Assert.assertSame(method, invocation.member)
                Assert.assertEquals("value", invocation.instance<String>())
                Assert.assertEquals(1, invocation.arg<Int>(0))
                Assert.assertSame(chain.args, args)

                Assert.assertEquals("downstream", proceed())
                Assert.assertEquals("downstream", proceed<String>())
                Assert.assertEquals("downstream", proceed(arguments))
                Assert.assertEquals("downstream", proceed<String>(arguments))
                Assert.assertSame(arguments, chain.forwardedArgs)

                Assert.assertEquals("downstream", proceedWith(receiver))
                Assert.assertEquals("downstream", proceedWith<String>(receiver))
                Assert.assertEquals("downstream", proceedWith(receiver, arguments))
                val result = proceedWith<String>(receiver, arguments)
                Assert.assertSame(receiver, chain.forwardedInstance)
                Assert.assertSame(arguments, chain.forwardedArgs)
                Assert.assertEquals(listOf(1), args)
                result
            }
        }
        Assert.assertEquals("downstream", (hookers.single() as ChainHookerBridge).intercept(chain))
        Assert.assertEquals(8, chain.calls)
    }

    @Test
    fun nullableChainReadsReturnArgumentsOrNullForInvalidReads() {
        val target = String::class.resolve().firstMethod {
            name = "startsWith"
            parameters(String::class, Int::class)
        }.self
        val chain = RecordingChain(target, args = listOf("prefix", 2))
        val chainWithNull = RecordingChain(target, args = listOf(null, 2))
        val observed = mutableListOf<List<Any?>>()
        scope.run {
            target.intercept {
                observed += listOf(
                    argOrNull(0), argOrNull<Int>(1),
                    firstArgOrNull(), firstArgOrNull<CharSequence>(),
                    lastArgOrNull(), lastArgOrNull<Int>(),
                    argOrNull(-1), argOrNull<String>(2),
                    argOrNull<Int>(0), firstArgOrNull<Int>(), lastArgOrNull<String>()
                )
                true
            }
        }

        val hooker = hookers.single() as ChainHookerBridge
        hooker.intercept(chain)
        hooker.intercept(chainWithNull)

        Assert.assertEquals(listOf(
            listOf("prefix", 2, "prefix", "prefix", 2, 2, null, null, null, null, null),
            listOf(null, 2, null, null, 2, 2, null, null, null, null, null)
        ), observed)
    }

    @Test
    fun nullableChainReadsHandleAnEmptyInvocation() {
        val chain = RecordingChain(otherMethod, args = emptyList())
        var observed: List<Any?>? = null
        scope.run {
            otherMethod.intercept {
                Assert.assertThrows(IndexOutOfBoundsException::class.java) { arg(0) }
                Assert.assertThrows(IndexOutOfBoundsException::class.java) { firstArg() }
                Assert.assertThrows(IndexOutOfBoundsException::class.java) { lastArg() }
                observed = listOf(
                    argOrNull(0), argOrNull<Int>(0),
                    firstArgOrNull(), firstArgOrNull<Int>(),
                    lastArgOrNull(), lastArgOrNull<Int>()
                )
                0
            }
        }

        (hookers.single() as ChainHookerBridge).intercept(chain)

        Assert.assertEquals(listOf(null, null, null, null, null, null), observed)
    }

    @Test
    fun chainShortCircuitAndFailureDoNotRetryTheRemainingChain() {
        val shortCircuit = RecordingChain(method)
        val failing = RecordingChain(method)
        val failure = IllegalStateException("after proceed")
        var observed: HookChain? = null
        var observedFailure: Throwable? = null
        val callbackFailures = mutableListOf<Throwable>()
        val registration = scope.run {
            method.intercept { "short-circuit" }
            method.intercept {
                proceed()
                throw failure
            }.onCallbackFailure { invocation, throwable ->
                observed = invocation
                callbackFailures += throwable
            }.onInvocationFailure {
                observedFailure = it
            }
        }
        Assert.assertEquals("short-circuit", (hookers[0] as ChainHookerBridge).intercept(shortCircuit))
        Assert.assertEquals(0, shortCircuit.calls)
        // A failure after proceed keeps the proceeded result instead of running the remaining chain again.
        Assert.assertEquals("downstream", (hookers[1] as ChainHookerBridge).intercept(failing))
        Assert.assertEquals(1, failing.calls)
        Assert.assertSame(method, checkNotNull(observed).member)
        Assert.assertEquals(listOf<Throwable>(failure), callbackFailures)
        Assert.assertSame(failure, observedFailure)

        registration.ignoreInvocationFailure()
        registration.throwToHostOnFailure()
        observed = null
        observedFailure = null
        Assert.assertSame(failure, Assert.assertThrows(IllegalStateException::class.java) {
            (hookers[1] as ChainHookerBridge).intercept(failing)
        })
        Assert.assertEquals(2, failing.calls)
        Assert.assertSame(method, checkNotNull(observed).member)
        Assert.assertNull(observedFailure)
    }

    @Test
    fun failuresFromTheRemainingChainAreNotReportedAsCallbackFailures() {
        val downstreamFailure = IllegalStateException("downstream")
        val replacedFailure = UnsupportedOperationException("replaced")
        val callbackFailures = mutableListOf<Throwable>()
        val invocationFailures = mutableListOf<Throwable>()
        scope.run {
            method.intercept { proceed() }
                .onCallbackFailure { _, throwable -> callbackFailures += throwable }
                .onInvocationFailure { invocationFailures += it }
            method.intercept {
                try {
                    proceed()
                } catch (_: IllegalStateException) {
                    throw replacedFailure
                }
            }.onCallbackFailure { _, throwable -> callbackFailures += throwable }
        }

        val passing = RecordingChain(method, downstreamFailure = downstreamFailure)
        Assert.assertSame(downstreamFailure, Assert.assertThrows(IllegalStateException::class.java) {
            (hookers[0] as ChainHookerBridge).intercept(passing)
        })
        Assert.assertEquals(1, passing.calls)
        Assert.assertTrue(callbackFailures.isEmpty())
        Assert.assertTrue(invocationFailures.isEmpty())

        // A different failure thrown by the callback itself is reported, and the invocation keeps the proceeded failure.
        val replacing = RecordingChain(method, downstreamFailure = downstreamFailure)
        Assert.assertSame(downstreamFailure, Assert.assertThrows(IllegalStateException::class.java) {
            (hookers[1] as ChainHookerBridge).intercept(replacing)
        })
        Assert.assertEquals(1, replacing.calls)
        Assert.assertEquals(listOf<Throwable>(replacedFailure), callbackFailures)
    }

    @Test
    fun chainFailureBeforeProceedContinuesTheRemainingChainOnce() {
        val failure = IllegalStateException("before proceed")
        val callbackFailures = mutableListOf<Throwable>()
        scope.run {
            method.intercept { throw failure }
                // A failing listener must not stop the recovery.
                .onCallbackFailure { _, throwable ->
                    callbackFailures += throwable
                    throw UnsupportedOperationException("listener")
                }
        }

        val chain = RecordingChain(method)
        Assert.assertEquals("downstream", (hookers.single() as ChainHookerBridge).intercept(chain))
        Assert.assertEquals(1, chain.calls)
        Assert.assertEquals(listOf<Throwable>(failure), callbackFailures)
    }

    @Test
    fun chainFailureThrownToHostDoesNotRunTheRemainingChain() {
        val failure = IllegalStateException("thrown to host")
        scope.run { method.intercept { throw failure }.throwToHostOnFailure() }

        val chain = RecordingChain(method)
        Assert.assertSame(failure, Assert.assertThrows(IllegalStateException::class.java) {
            (hookers.single() as ChainHookerBridge).intercept(chain)
        })
        Assert.assertEquals(0, chain.calls)
    }

    @Test
    fun failedClassicInterceptionPassesTheOriginalFailureToTheCallerOnce() {
        val originalFailure = IllegalArgumentException("original")
        var originalCalls = 0
        YukiHookBridge.attach(YukiHookBridge.ApiSpec(
            hookRegistrationBridge = registrations(registerHook),
            executableInvokerBridge = object : ExecutableInvokerBridge {
                override fun invokeOriginal(member: Member, instance: Any?, args: Array<out Any?>): Any? {
                    originalCalls++
                    throw InvocationTargetException(originalFailure)
                }

                override fun invokeSpecial(member: Member, instance: Any, args: Array<out Any?>) = error("Unexpected special invocation.")
                override fun newInstanceOriginal(constructor: Constructor<*>, args: Array<out Any?>) = error("Unexpected construction.")
                override fun newInstanceSpecial(constructor: Constructor<*>, subclass: Class<*>, args: Array<out Any?>) =
                    error("Unexpected construction.")
            }
        ))
        val callbackFailure = IllegalStateException("callback")
        val callbackFailures = mutableListOf<Throwable>()
        scope.run {
            method.hook { intercept { throw callbackFailure } }.onCallbackFailure { _, throwable -> callbackFailures += throwable }
        }

        var proceedCalls = 0
        val adapterFailures = mutableListOf<Throwable>()
        val invocation = ClassicInvocationAdapter.from(method, "value", arrayOf(1)) { adapterFailures += it }
        Assert.assertSame(originalFailure, Assert.assertThrows(IllegalArgumentException::class.java) {
            invocation.intercept(hookers.single() as ClassicHookerBridge) {
                proceedCalls++
                "original"
            }
        })
        Assert.assertEquals(1, originalCalls)
        Assert.assertEquals(0, proceedCalls)
        Assert.assertTrue(adapterFailures.isEmpty())
        Assert.assertEquals(listOf<Throwable>(callbackFailure), callbackFailures)
    }

    @Test
    fun classicThrowToHostOnFailureAppliesOnlyToItsOwnCallback() {
        val beforeFailure = IllegalStateException("before")
        val afterFailure = IllegalStateException("after")
        scope.run {
            method.hook {
                before { throw beforeFailure }
                after { throw afterFailure }.throwToHostOnFailure()
            }.ignoreCallbackFailure()
        }

        var originalCalls = 0
        val invocation = ClassicInvocationAdapter.from(method, "value", arrayOf(1)) { throw it }
        // The before failure is only reported, so the original still runs before the after failure is thrown.
        Assert.assertSame(afterFailure, Assert.assertThrows(IllegalStateException::class.java) {
            invocation.intercept(hookers.single() as ClassicHookerBridge) {
                originalCalls++
                "original"
            }
        })
        Assert.assertEquals(1, originalCalls)
    }

    @Test
    fun classicInterceptionThrowsToHostWithoutInvokingTheOriginal() {
        var originalCalls = 0
        YukiHookBridge.attach(YukiHookBridge.ApiSpec(
            hookRegistrationBridge = registrations(registerHook),
            executableInvokerBridge = object : ExecutableInvokerBridge {
                override fun invokeOriginal(member: Member, instance: Any?, args: Array<out Any?>): Any? {
                    originalCalls++
                    return "original"
                }

                override fun invokeSpecial(member: Member, instance: Any, args: Array<out Any?>) = error("Unexpected special invocation.")
                override fun newInstanceOriginal(constructor: Constructor<*>, args: Array<out Any?>) = error("Unexpected construction.")
                override fun newInstanceSpecial(constructor: Constructor<*>, subclass: Class<*>, args: Array<out Any?>) =
                    error("Unexpected construction.")
            }
        ))
        val callbackFailure = IllegalStateException("callback")
        val hostFailure = UnsupportedOperationException("host")
        scope.run {
            method.hook { intercept { throw callbackFailure }.throwToHostOnFailure() }.ignoreCallbackFailure()
            method.hook {
                intercept {
                    hostFailure.throwToHost()
                    "ignored"
                }
            }
        }

        var proceedCalls = 0
        val proceed = { _: Array<Any?> ->
            proceedCalls++
            "original"
        }
        Assert.assertSame(callbackFailure, Assert.assertThrows(IllegalStateException::class.java) {
            ClassicInvocationAdapter.from(method, "value", arrayOf(1)) { throw it }.intercept(hookers[0] as ClassicHookerBridge, proceed)
        })
        // An exception set through `throwToHost` takes the place of the returned value.
        Assert.assertSame(hostFailure, Assert.assertThrows(UnsupportedOperationException::class.java) {
            ClassicInvocationAdapter.from(method, "value", arrayOf(1)) { throw it }.intercept(hookers[1] as ClassicHookerBridge, proceed)
        })
        Assert.assertEquals(0, originalCalls)
        Assert.assertEquals(0, proceedCalls)
    }

    @Test
    fun failedRegistrationDoesNotInterruptLaterMembersAndHookers() {
        val failure = UnsupportedOperationException("unsupported hooker")
        var attempts = 0
        YukiHookBridge.attach(YukiHookBridge.ApiSpec(
            hookRegistrationBridge = registrations { target, priority, hooker, id ->
                attempts++
                if (attempts == 1) throw failure
                registerHook(target, priority, hooker, id)
            }
        ))
        scope.run {
            listOf(method, otherMethod).interceptAll {
                removeSelf()
                proceed()
            }
            method.hook { after {} }
        }
        Assert.assertEquals(3, attempts)
        Assert.assertEquals(2, hookers.size)
        val chain = RecordingChain(otherMethod, downstreamResult = 5)
        (hookers.first() as ChainHookerBridge).intercept(chain)
        Assert.assertEquals(listOf(otherMethod), removed)
        Assert.assertTrue(hookers.last() is ClassicHookerBridge)
    }

    @Test
    fun classicCallbacksKeepMutableArgumentsResultsAndRemoval() {
        var observed: HookParam? = null
        val registration = scope.run {
            method.hook {
                before {
                    observed = this
                    arg(0).set(2)
                    Assert.assertEquals(2, arg(0).get<Int>())
                }
                after {
                    Assert.assertEquals("lue", result<String>())
                    result = "changed"
                    removeSelf()
                }
            }.onCallbackFailure { invocation, throwable ->
                observed = invocation
                throw throwable
            }.apply {
                onInvocationFailure { throw it }
            }
        }
        val invocation = ClassicInvocationAdapter.from(method, "value", arrayOf(1)) { throw it }
        Assert.assertEquals("changed", invocation.intercept(hookers.single() as ClassicHookerBridge) {
            "value".substring(it[0] as Int)
        })
        Assert.assertSame(method, checkNotNull(observed).member)
        Assert.assertEquals(listOf(method), removed)
        registration.remove { Assert.assertFalse(it) }
    }

    @Test
    fun classicResultsWithMismatchedTypesAreRejectedWithoutReplacingTheResult() {
        val failures = mutableListOf<Throwable>()
        scope.run {
            method.hook {
                before { result = 1 }
                after { result = 2 }
            }.onCallbackFailure { _, throwable -> failures += throwable }
        }
        val invocation = ClassicInvocationAdapter.from(method, "value", arrayOf(1)) { throw it }
        Assert.assertEquals("alue", invocation.intercept(hookers.single() as ClassicHookerBridge) {
            "value".substring(it[0] as Int)
        })
        Assert.assertEquals(2, failures.size)
        failures.forEach { Assert.assertTrue(it is IllegalStateException) }
    }

    @Test
    fun nullResultsForPrimitiveReturnTypesAreRejected() {
        YukiHookBridge.attach(YukiHookBridge.ApiSpec(
            hookRegistrationBridge = registrations(registerHook),
            executableInvokerBridge = object : ExecutableInvokerBridge {
                override fun invokeOriginal(member: Member, instance: Any?, args: Array<out Any?>) = (member as Method).invoke(instance, *args)
                override fun invokeSpecial(member: Member, instance: Any, args: Array<out Any?>) = error("Unexpected special invocation.")
                override fun newInstanceOriginal(constructor: Constructor<*>, args: Array<out Any?>) = error("Unexpected construction.")
                override fun newInstanceSpecial(constructor: Constructor<*>, subclass: Class<*>, args: Array<out Any?>) =
                    error("Unexpected construction.")
            }
        ))
        val failures = mutableListOf<Throwable>()
        scope.run {
            otherMethod.hook { intercept { null } }.onCallbackFailure { _, throwable -> failures += throwable }
            otherMethod.intercept { null }
        }

        // A failed classic interception falls back to the original member instead of returning null to the framework.
        val invocation = ClassicInvocationAdapter.from(otherMethod, "value", emptyArray()) { throw it }
        Assert.assertEquals(5, invocation.intercept(hookers[0] as ClassicHookerBridge) { "value".length })
        Assert.assertTrue(failures.single() is IllegalStateException)
        // A rejected chain result is recovered by running the remaining chain.
        Assert.assertEquals(5, (hookers[1] as ChainHookerBridge).intercept(RecordingChain(otherMethod, downstreamResult = 5)))
    }

    @Test
    fun hookCanRemoveItselfWhileNativeRegistrationIsCompleting() {
        var removalReported = false
        YukiHookBridge.attach(YukiHookBridge.ApiSpec(
            hookRegistrationBridge = registrations { target, _, hooker, _ ->
                val member = (target as HookPoint.Executable).value
                ClassicInvocationAdapter.from(member, "value", arrayOf(1)) { throw it }
                    .intercept(hooker as ClassicHookerBridge) { "original" }
                object : HookerBridge {
                    override fun remove() {
                        removed += member
                    }
                }
            }
        ))

        scope.run {
            method.hook().before {
                removeSelf { removalReported = it }
            }
        }

        Assert.assertFalse(removalReported)
        Assert.assertEquals(listOf(method), removed)
    }

    @Test
    fun classicInterceptionSkipsOriginalAndNormalizesVoidResult() {
        var callbackInvoked = false
        var valueOriginalInvoked = false
        var voidOriginalInvoked = false
        scope.run {
            method.hook { intercept("changed") }
            voidMethod.hook {
                intercept {
                    callbackInvoked = true
                    "ignored"
                }
            }
        }

        val valueInvocation = ClassicInvocationAdapter.from(method, "value", arrayOf(1)) { throw it }
        val value = valueInvocation.intercept(hookers[0] as ClassicHookerBridge) {
            valueOriginalInvoked = true
            "original"
        }
        val voidInvocation = ClassicInvocationAdapter.from(voidMethod, StringBuilder("value"), arrayOf(1)) { throw it }
        val void = voidInvocation.intercept(hookers[1] as ClassicHookerBridge) {
            voidOriginalInvoked = true
        }

        Assert.assertEquals("changed", value)
        Assert.assertNull(void)
        Assert.assertTrue(callbackInvoked)
        Assert.assertFalse(valueOriginalInvoked)
        Assert.assertFalse(voidOriginalInvoked)
    }

    @Test
    fun resolverBlockHooksDeferRegistrationAndBuilderHooksRemainImmediate() {
        val resolver = String::class.resolve().firstMethod {
            name = "substring"
            parameters(Int::class)
        }
        val resolvers = listOf(resolver, StringBuilder::class.resolve().firstConstructor { emptyParameters() })
        scope.run {
            resolver.hook {
                before {}
                after {}
                Assert.assertTrue(hookers.isEmpty())
            }
            Assert.assertEquals(1, hookers.size)

            resolvers.hookAll {
                before {}
                after {}
                Assert.assertEquals(1, hookers.size)
            }
            Assert.assertEquals(3, hookers.size)

            val single = resolver.hook()
            val batch = resolvers.hookAll()
            Assert.assertEquals(3, hookers.size)
            single.before {}
            Assert.assertEquals(4, hookers.size)
            batch.after {}
            Assert.assertEquals(6, hookers.size)

            single.after {}
            batch.before {}
            Assert.assertEquals(6, hookers.size)
        }
    }

    private class RecordingChain(
        override val member: Member,
        private val downstreamResult: Any? = "downstream",
        private val downstreamFailure: Throwable? = null,
        override val args: List<Any?> = listOf(1)
    ) : ChainHookerBridge.Chain {

        override val instance = "value"
        var calls = 0
        var forwardedInstance: Any? = null
        var forwardedArgs: Array<out Any?>? = null

        override fun getArg(index: Int) = args[index]
        override fun proceed() = record(instance, null)
        override fun proceed(args: Array<out Any?>) = record(instance, args)
        override fun proceedWith(instance: Any) = record(instance, null)
        override fun proceedWith(instance: Any, args: Array<out Any?>) = record(instance, args)

        private fun record(instance: Any?, args: Array<out Any?>?): Any? {
            calls++
            forwardedInstance = instance
            forwardedArgs = args
            downstreamFailure?.let { throw it }
            return downstreamResult
        }
    }
}