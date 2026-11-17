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
 * This file is created by fankes on 2026/11/18.
 */
package com.highcapable.yukihookapi.hook.xposed.bridge.session

import android.content.pm.ApplicationInfo
import com.highcapable.yukihookapi.hook.xposed.bridge.component.ParcelCreatorCache
import com.highcapable.yukihookapi.hook.xposed.bridge.session.state.ModuleHandoffState
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * Checks component-state requirements and lifecycle dispatch across module versions.
 */
class ModuleLifecycleManagerTest {

    companion object {

        private val calls = mutableListOf<Pair<String, Any?>>()
        private val required = object : ModuleReloadable {}
        private val prerequisite = object : ModuleReloadable {
            override val requiresSavedState = false
            override val restorePhase = ModuleReloadable.RestorePhase.PREREQUISITE

            override fun onPrepareRestore(appInfo: ApplicationInfo, state: Any?) {
                calls += "prepare-prerequisite" to state
            }

            override fun onRestoreState(appInfo: ApplicationInfo, state: Any?) {
                calls += "restore-prerequisite" to state
            }
        }
        private val rebuild = object : ModuleReloadable {
            override val requiresSavedState = false

            override fun onPrepareRestore(appInfo: ApplicationInfo, state: Any?) {
                calls += "prepare-rebuild" to state
            }

            override fun onRestoreState(appInfo: ApplicationInfo, state: Any?) {
                calls += "restore-rebuild" to state
            }
        }
    }

    @Before
    fun registerComponents() {
        calls.clear()
        ModuleLifecycleManager.register("required", required)
        ModuleLifecycleManager.register("optional-prerequisite", prerequisite)
        ModuleLifecycleManager.register("optional-rebuild", rebuild)
        ModuleLifecycleManager.register(ParcelCreatorCache.RELOAD_ID, ParcelCreatorCache)
    }

    @After
    fun closeRestore() = ModuleLifecycleManager.abortRestore()

    @Test
    fun olderHandoffWithoutStatelessComponentsRunsBothRestorePhases() {
        val encoded = ModuleHandoffState.encode(ModuleHandoffState(null, mapOf("required" to null)))
        val appInfo = ApplicationInfo()

        val restored = ModuleLifecycleManager.beginRestore(encoded)
        ModuleLifecycleManager.prepareRestore(appInfo, restored)
        ModuleLifecycleManager.restoreComponents(appInfo, restored, ModuleReloadable.RestorePhase.PREREQUISITE)
        ModuleLifecycleManager.restoreComponents(appInfo, restored, ModuleReloadable.RestorePhase.REBUILD)
        ModuleLifecycleManager.finishRestore(null)

        Assert.assertEquals(
            listOf(
                "prepare-prerequisite" to null,
                "prepare-rebuild" to null,
                "restore-prerequisite" to null,
                "restore-rebuild" to null
            ),
            calls
        )
    }

    @Test
    fun missingRequiredComponentStillRejectsRestore() {
        val encoded = ModuleHandoffState.encode(ModuleHandoffState(null, emptyMap()))

        val failure = Assert.assertThrows(IllegalStateException::class.java) {
            ModuleLifecycleManager.beginRestore(encoded)
        }

        Assert.assertEquals("Missing reload component 'required'", failure.message)
        Assert.assertTrue(calls.isEmpty())
    }

    @Test
    fun optionalComponentsStillReceiveTheirSavedValues() {
        val encoded = ModuleHandoffState.encode(ModuleHandoffState(null, mapOf(
            "required" to null,
            "optional-prerequisite" to "previous-prerequisite",
            "optional-rebuild" to "previous-rebuild",
            ParcelCreatorCache.RELOAD_ID to null
        )))
        val appInfo = ApplicationInfo()

        val restored = ModuleLifecycleManager.beginRestore(encoded)
        ModuleLifecycleManager.prepareRestore(appInfo, restored)
        ModuleLifecycleManager.restoreComponents(appInfo, restored, ModuleReloadable.RestorePhase.PREREQUISITE)
        ModuleLifecycleManager.restoreComponents(appInfo, restored, ModuleReloadable.RestorePhase.REBUILD)
        ModuleLifecycleManager.finishRestore(null)

        Assert.assertEquals(
            listOf(
                "prepare-prerequisite" to "previous-prerequisite",
                "prepare-rebuild" to "previous-rebuild",
                "restore-prerequisite" to "previous-prerequisite",
                "restore-rebuild" to "previous-rebuild"
            ),
            calls
        )
    }
}