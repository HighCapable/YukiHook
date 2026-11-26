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
 * This file is created by fankes on 2026/11/27.
 */
@file:Suppress("UnusedReceiverParameter")

package com.highcapable.yukihookapi.core.lint.detector

import com.android.tools.lint.checks.infrastructure.LintDetectorTest

/**
 * Checks that app lifecycle callbacks do not declare one-shot setup work.
 */
class ModuleRuntimeLifecycleTest : LintDetectorTest() {

    companion object {

        private val packageParam = kotlin("""
            package com.highcapable.yukihookapi.hook.param
            import android.app.Application
            open class PackageParam {
                fun withApplicationReady(block: (Application) -> Unit) = Unit
                fun registerAppLifecycle(block: AppLifecycle.() -> Unit) = Unit
                fun loadApp(name: String, block: PackageParam.() -> Unit) = Unit
                class AppLifecycle {
                    fun onCreate(block: () -> Unit) = Unit
                }
            }
        """).indented()
    }

    override fun getDetector() = ModuleRuntimeDetector()
    override fun getIssues() = listOf(
        ModuleRuntimeDetector.NON_REPLAYABLE_CALL_IN_APP_LIFECYCLE_ISSUE,
        ModuleRuntimeDetector.INVALID_APPLICATION_READY_NESTING_ISSUE
    )

    fun testAppLifecycleRegistrationInsideApplicationReadyIsReported() {
        lint().files(packageParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.PackageParam
            class Hooker : PackageParam() {
                fun setup() {
                    withApplicationReady {
                        registerAppLifecycle {}
                    }
                }
            }
        """).indented()).run()
            .expectErrorCount(1)
            .expectContains("The host Application has already been created in withApplicationReady")
    }

    fun testPackageLoadingInsideAppLifecycleIsReported() {
        lint().files(packageParam, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.PackageParam
            class Hooker : PackageParam() {
                fun setup() {
                    registerAppLifecycle {
                        onCreate {
                            loadApp("com.example") {}
                        }
                    }
                }
            }
        """).indented()).run()
            .expectWarningCount(1)
            .expectContains("loadApp in onCreate is not called again after module hot reload")
    }
}