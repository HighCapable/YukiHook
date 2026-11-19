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
package com.highcapable.yukihookapi.hook.xposed.bridge.caller

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.hook.xposed.reload.lifecycle.registerModuleLifecycle
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Checks lifecycle registration from a shared hook entry.
 */
class YukiHookModuleCallerTest {

    @Test
    fun delegatedOnInitRunsLifecycleConfigurationAndClosesRegistrationOnFailure() {
        val configurationFailure = IllegalArgumentException("Invalid lifecycle configuration")
        val sharedEntry = object : YukiHookXposedModule {
            override fun onInit() = registerModuleLifecycle { throw configurationFailure }
            override fun onHook() = Unit
        }
        val variantEntry = object : YukiHookXposedModule by sharedEntry {}

        val failure = Assert.assertThrows(IllegalArgumentException::class.java) {
            YukiHookModuleCaller.callOnInit(variantEntry)
        }

        Assert.assertSame(configurationFailure, failure)
        Assert.assertThrows(IllegalStateException::class.java) { sharedEntry.registerModuleLifecycle {} }
    }

    @Test
    fun registrationOnAnotherThreadIsRejectedWhileOnInitIsRunning() {
        val entry = object : YukiHookXposedModule {
            override fun onInit() {
                val registration = FutureTask {
                    runCatching { registerModuleLifecycle { error("Configuration ran on the wrong thread") } }.exceptionOrNull()
                }
                thread(isDaemon = true) { registration.run() }

                Assert.assertEquals(
                    "module lifecycle callbacks must be registered in onInit of the current process.",
                    registration.get(5, TimeUnit.SECONDS)?.message
                )
            }

            override fun onHook() = Unit
        }

        YukiHookModuleCaller.callOnInit(entry)
    }

    @Test
    fun moduleEncaseRejectsBothOverloadsOutsideOnHook() {
        val entry = object : YukiHookXposedModule {
            override fun onInit() {
                val failure = Assert.assertThrows(IllegalStateException::class.java) { YukiHook.encase {} }
                Assert.assertEquals("Declare module hooks in onHook.", failure.message)
            }

            override fun onHook() = Unit
        }

        YukiHookModuleCaller.callOnInit(entry)
        val failure = Assert.assertThrows(IllegalStateException::class.java) { YukiHook.encase(hookers = emptyArray()) }
        Assert.assertEquals("Declare module hooks in onHook.", failure.message)
    }
}