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

import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import com.highcapable.yukihookapi.hook.xposed.reload.lifecycle.registerModuleLifecycle
import org.junit.Assert
import org.junit.Test

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
}