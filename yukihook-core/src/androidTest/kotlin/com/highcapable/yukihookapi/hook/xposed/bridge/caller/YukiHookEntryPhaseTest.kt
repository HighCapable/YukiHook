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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.hook.xposed.reload.lifecycle.registerModuleLifecycle
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the module hook phase through the Android startup entry point.
 */
@RunWith(AndroidJUnit4::class)
class YukiHookEntryPhaseTest {

    @Test
    fun delegatedOnHookAllowsEncaseAndClosesThePhaseOnFailure() {
        val appInfo = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo
        val completed = mutableListOf<String>()
        val sharedEntry = object : YukiHookXposedModule {
            override fun onHook() {
                YukiHook.encase {}
                completed += "block"
                YukiHook.encase(hookers = emptyArray())
                completed += "hookers"

                val registration = Assert.assertThrows(IllegalStateException::class.java) { registerModuleLifecycle {} }
                Assert.assertEquals("module lifecycle callbacks must be registered in onInit of the current process.", registration.message)
                completed += "registration rejected"
                error("Hook entry failed")
            }
        }
        val variantEntry = object : YukiHookXposedModule by sharedEntry {}

        val initialized = YukiHookModuleCaller.callOnHook({ variantEntry }, appInfo, processName = appInfo.processName)
        val late = Assert.assertThrows(IllegalStateException::class.java) { YukiHook.encase {} }

        Assert.assertFalse(initialized)
        Assert.assertEquals(listOf("block", "hookers", "registration rejected"), completed)
        Assert.assertEquals("Declare module hooks in onHook.", late.message)
    }
}