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
 * Checks dedicated system-server routing for package and process names.
 */
class ModuleRuntimeSystemNamesTest : LintDetectorTest() {

    companion object {

        private val packageParam = kotlin("""
            package com.highcapable.yukihookapi.hook.param
            open class PackageParam {
                val processName: String get() = ""
                fun loadApp(name: String, block: PackageParam.() -> Unit) = Unit
                fun loadApp(vararg name: String, block: PackageParam.() -> Unit) = Unit
                fun loadSystem(block: PackageParam.() -> Unit) = Unit
                fun withProcess(name: String, block: PackageParam.() -> Unit) = Unit
                fun <T> remember(key: String, block: () -> T): T = block()
            }
        """).indented()

        private val factory = kotlin("src/com/highcapable/yukihookapi/hook/factory/YukiHookFactory.kt", """
            package com.highcapable.yukihookapi.hook.factory
            fun dataChannel(packageName: String) = Unit
            fun systemDataChannel() = Unit
        """).indented()
    }

    override fun getDetector() = ModuleRuntimeDetector()
    override fun getIssues() = listOf(
        ModuleRuntimeDetector.LOAD_APP_FOR_SYSTEM_FRAMEWORK_ISSUE,
        ModuleRuntimeDetector.SYSTEM_FRAMEWORK_IN_LOAD_APP_PACKAGES_ISSUE,
        ModuleRuntimeDetector.PROCESS_NAME_FOR_SYSTEM_FRAMEWORK_ISSUE,
        ModuleRuntimeDetector.DATA_CHANNEL_FOR_SYSTEM_FRAMEWORK_ISSUE,
        ModuleRuntimeDetector.INVALID_MODULE_RETAINED_VALUE_ISSUE
    )

    fun testSystemFrameworkPackageAndProcessNamesUseDedicatedEntryPoints() {
        lint().files(packageParam, factory, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.PackageParam
            import com.highcapable.yukihookapi.hook.factory.dataChannel
            class Hooker : PackageParam() {
                fun load() {
                    loadApp("android") {}
                    loadApp("system") {}
                    loadApp("com.example", "android") {}
                    withProcess("android") {}
                    if (processName == "android") Unit
                    dataChannel("system")
                    remember(" ") { "value" }
                }
            }
        """).indented()).run()
            .expectErrorCount(4)
            .expectWarningCount(3)
            .expectContains("use loadSystem instead")
            .expectContains("cannot be loaded together with app package names")
            .expectContains("use systemDataChannel instead")
            .expectContains("the system server process is \"system\"")
            .expectContains("remember key must not be blank")
    }
}