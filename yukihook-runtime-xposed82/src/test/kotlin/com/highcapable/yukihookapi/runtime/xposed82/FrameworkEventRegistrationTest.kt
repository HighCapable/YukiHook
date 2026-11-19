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
package com.highcapable.yukihookapi.runtime.xposed82

import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.hook.xposed.bridge.caller.YukiHookModuleCaller
import com.highcapable.yukihookapi.hook.xposed.bridge.event.registerFrameworkEvents
import org.junit.Assert
import org.junit.Test

/**
 * Checks framework event registration when an entry delegates its initialization.
 */
class FrameworkEventRegistrationTest {

    @Test
    fun delegatedOnInitRegistersFrameworkEventsOnce() {
        val registrations = mutableListOf<String>()
        val sharedEntry = object : YukiHookXposedModule {
            override fun onInit() = registerFrameworkEvents { registrations += "registered" }
            override fun onHook() = Unit
        }
        val variantEntry = object : YukiHookXposedModule by sharedEntry {}

        YukiHookModuleCaller.callOnInit(variantEntry)
        val duplicate = Assert.assertThrows(IllegalStateException::class.java) {
            YukiHookModuleCaller.callOnInit(variantEntry)
        }
        val late = Assert.assertThrows(IllegalStateException::class.java) { sharedEntry.onInit() }

        Assert.assertEquals(listOf("registered"), registrations)
        Assert.assertEquals("Framework events can only be registered once per module process.", duplicate.message)
        Assert.assertEquals("framework events must be registered in onInit of the current process.", late.message)
    }
}