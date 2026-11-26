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
 * Checks the core Lint fallback for KavaRef class loading in module package scopes.
 */
class KavaRefFallbackDetectorTest : LintDetectorTest() {

    companion object {

        private val packageParam = kotlin("""
            package com.highcapable.yukihookapi.hook.param
            open class PackageParam
            fun loadApp(block: PackageParam.() -> Unit) = PackageParam().block()
        """).indented()

        private val kavaRef = kotlin("src/com/highcapable/kavaref/extension/Loading.kt", """
            package com.highcapable.kavaref.extension
            fun String.toClass(loader: ClassLoader? = null): Class<*> = String::class.java
        """).indented()
    }

    override fun getDetector() = KavaRefFallbackDetector()
    override fun getIssues() = listOf(KavaRefFallbackDetector.ISSUE)

    fun testClassLoadingWithoutLoaderInPackageParamIsReported() {
        lint().files(packageParam, kavaRef, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.loadApp
            import com.highcapable.kavaref.extension.toClass
            fun loadHostClass() {
                loadApp {
                    "com.example.HostClass".toClass()
                }
            }
        """).indented()).run()
            .expectWarningCount(1)
            .expectContains("toClass from KavaRef does not use hostClassLoader")
    }

    fun testExplicitLoaderAndCallsOutsidePackageParamAreIgnored() {
        lint().files(packageParam, kavaRef, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.loadApp
            import com.highcapable.kavaref.extension.toClass
            fun loadHostClass(loader: ClassLoader) {
                loadApp {
                    "com.example.HostClass".toClass(loader)
                }
            }
            fun loadOutsideScope() = "com.example.HostClass".toClass()
        """).indented()).run().expectClean()
    }
}