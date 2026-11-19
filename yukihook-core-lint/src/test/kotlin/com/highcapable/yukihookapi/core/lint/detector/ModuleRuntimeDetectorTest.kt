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
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestMode

/**
 * Checks how Lint follows module entry phases through calls and callbacks.
 */
class ModuleRuntimeDetectorTest : LintDetectorTest() {

    companion object {

        private val entryType = kotlin("""
            package com.highcapable.yukihookapi.hook.xposed
            interface YukiHookXposedModule {
                fun onInit() = Unit
                fun onHook()
            }
        """).indented()

        private val packageParam = kotlin("""
            package com.highcapable.yukihookapi.hook.param
            import android.app.Application
            open class PackageParam {
                fun withApplicationReady(block: (Application) -> Unit) = Unit
            }
        """).indented()

        private val yukiHook = kotlin("""
            package com.highcapable.yukihookapi
            import com.highcapable.yukihookapi.hook.param.PackageParam
            object YukiHook {
                fun encase(block: PackageParam.() -> Unit) = Unit
                inline fun configure(block: () -> Unit) = block()
            }
        """).indented()

        private val lifecycle = kotlin("src/com/highcapable/yukihookapi/hook/xposed/reload/lifecycle/ModuleLifecycle.kt", """
            package com.highcapable.yukihookapi.hook.xposed.reload.lifecycle
            import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
            class ModuleLifecycle {
                fun onReloaded(block: () -> Unit) = Unit
            }
            fun YukiHookXposedModule.registerModuleLifecycle(block: ModuleLifecycle.() -> Unit) = ModuleLifecycle().block()
        """).indented()
    }

    override fun getDetector() = ModuleRuntimeDetector()
    override fun getIssues() = listOf(
        ModuleRuntimeDetector.MODULE_REGISTRATION_OUTSIDE_ON_INIT_ISSUE,
        ModuleRuntimeDetector.INVALID_MODULE_HOOK_LOADING_ISSUE
    )

    private fun module(vararg files: TestFile) = lint().files(entryType, packageParam, yukiHook, lifecycle, *files).testModes(TestMode.DEFAULT)

    fun testDelegatedEntryUsesCrossFileHelpersAndIgnoresUnusedCaller() {
        module(kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
            object SharedEntry : YukiHookXposedModule {
                override fun onInit() = register(this)
                override fun onHook() = installHooks()
            }
            object ModernEntry : YukiHookXposedModule by SharedEntry
        """).indented(), kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.YukiHook
            import com.highcapable.yukihookapi.hook.param.PackageParam
            import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
            import com.highcapable.yukihookapi.hook.xposed.reload.lifecycle.registerModuleLifecycle
            fun installHooks() = YukiHook.encase {}
            private fun unused() = installHooks()
            private inline fun withOptionalPackage(block: (PackageParam?) -> Unit) = block(null)
            fun register(entry: YukiHookXposedModule) {
                withOptionalPackage { entry.registerModuleLifecycle {} }
            }
        """).indented()).run().expectClean()
    }

    fun testRegistrationInWrongPhaseThroughCrossFileHelper() {
        module(kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
            object Entry : YukiHookXposedModule {
                override fun onHook() = register(this)
            }
        """).indented(), kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
            import com.highcapable.yukihookapi.hook.xposed.reload.lifecycle.registerModuleLifecycle
            fun register(entry: YukiHookXposedModule) = entry.registerModuleLifecycle {}
        """).indented()).run().expectErrorCount(1)
            .expectContains("registerModuleLifecycle must be called from onInit of the current module entry")
    }

    fun testInlineCallsAndReferencesKeepTheEntryPhase() {
        module(kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.YukiHook
            import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
            import com.highcapable.yukihookapi.hook.xposed.reload.lifecycle.registerModuleLifecycle
            object Entry : YukiHookXposedModule {
                override fun onInit() {
                    run { YukiHook.encase {} }
                    run(::installHooks)
                }
                override fun onHook() = YukiHook.configure { registerModuleLifecycle {} }
                private fun installHooks() { YukiHook.encase {} }
            }
        """).indented()).run().expectErrorCount(3)
            .expectContains("encase must be called from onHook")
            .expectContains("registerModuleLifecycle must be called from onInit")
    }

    fun testDeferredRegistrationAndPackageCallbacksAreRejected() {
        module(kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.YukiHook
            import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
            import com.highcapable.yukihookapi.hook.xposed.reload.lifecycle.registerModuleLifecycle
            import kotlin.concurrent.thread
            object Entry : YukiHookXposedModule {
                override fun onInit() {
                    registerModuleLifecycle {
                        onReloaded { registerModuleLifecycle {} }
                    }
                    thread { registerModuleLifecycle {} }
                }
                override fun onHook() = YukiHook.encase {
                    registerModuleLifecycle {}
                    withApplicationReady { setup() }
                }
                private fun setup() = registerModuleLifecycle {}
            }
        """).indented()).run().expectErrorCount(4)
    }

    fun testInheritedInitFollowsAbstractHelperImplementation() {
        module(kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.YukiHook
            import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
            import com.highcapable.yukihookapi.hook.xposed.reload.lifecycle.registerModuleLifecycle
            abstract class BaseEntry : YukiHookXposedModule {
                override fun onInit() = setup()
                protected abstract fun setup()
            }
            object Entry : BaseEntry() {
                override fun setup() {
                    registerModuleLifecycle {}
                    YukiHook.encase {}
                }
                override fun onHook() = Unit
            }
        """).indented()).run().expectErrorCount(1).expectContains("encase must be called from onHook")
    }

    fun testMixedCallersDoNotOfferToDeleteAValidHookDeclaration() {
        module(kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.YukiHook
            import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
            object Entry : YukiHookXposedModule {
                override fun onInit() = installHooks()
                override fun onHook() = installHooks()
                private fun installHooks() {
                    YukiHook.encase {}
                }
            }
        """).indented()).run().expectErrorCount(1).expectFixDiffs("")
    }

    fun testLegacyEncaseExtensionRequiresOnHook() {
        module(kotlin("""
            package com.highcapable.yukihookapi.hook.xposed.proxy
            import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
            interface IYukiHookXposedInit : YukiHookXposedModule
        """).indented(), kotlin("src/com/highcapable/yukihookapi/hook/factory/YukiHookFactory.kt", """
            package com.highcapable.yukihookapi.hook.factory
            import com.highcapable.yukihookapi.hook.param.PackageParam
            import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
            fun IYukiHookXposedInit.encase(block: PackageParam.() -> Unit) = Unit
        """).indented(), kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.factory.encase
            import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
            object Entry : IYukiHookXposedInit {
                override fun onInit() = encase {}
                override fun onHook() = encase {}
            }
        """).indented()).run().expectErrorCount(1).expectContains("encase must be called from onHook")
    }
}