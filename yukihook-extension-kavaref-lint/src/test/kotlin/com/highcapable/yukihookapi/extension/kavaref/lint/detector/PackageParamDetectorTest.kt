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

package com.highcapable.yukihookapi.extension.kavaref.lint.detector

import com.android.tools.lint.checks.infrastructure.LintDetectorTest

/**
 * Checks host class-loader defaults for KavaRef extension calls.
 */
class PackageParamDetectorTest : LintDetectorTest() {

    companion object {

        private val packageParam = kotlin("""
            package com.highcapable.yukihookapi.hook.param
            open class PackageParam {
                val hostClassLoader: ClassLoader get() = javaClass.classLoader!!
            }
            fun loadApp(block: PackageParam.() -> Unit) = PackageParam().block()
        """).indented()

        private val kavaRef = kotlin("src/com/highcapable/kavaref/extension/Loading.kt", """
            package com.highcapable.kavaref.extension
            fun String.toClass(loader: ClassLoader? = null): Class<*> = String::class.java
        """).indented()

        private val yukiHookExtension = kotlin("src/com/highcapable/yukihookapi/extension/kavaref/Loading.kt", """
            package com.highcapable.yukihookapi.extension.kavaref
            import com.highcapable.yukihookapi.hook.param.PackageParam
            context(param: PackageParam)
            fun String.toClass(loader: ClassLoader? = param.hostClassLoader): Class<*> = String::class.java
        """).indented()
    }

    override fun getDetector() = PackageParamDetector()
    override fun getIssues() = listOf(
        PackageParamDetector.CLASS_LOADING_WITHOUT_HOST_CLASS_LOADER_ISSUE,
        PackageParamDetector.CLASS_LOADING_WITH_HOST_CLASS_LOADER_ISSUE
    )

    fun testKavaRefCallWithHostClassLoaderSuggestsYukiHookExtension() {
        lint().files(packageParam, kavaRef, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.loadApp
            import com.highcapable.kavaref.extension.toClass
            fun loadHostClass() {
                loadApp {
                    "com.example.HostClass".toClass(hostClassLoader)
                }
            }
        """).indented()).run()
            .expectContains("uses hostClassLoader by default, import it instead")
    }

    fun testYukiHookExtensionWithoutExplicitLoaderIsClean() {
        lint().files(packageParam, yukiHookExtension, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.param.loadApp
            import com.highcapable.yukihookapi.extension.kavaref.toClass
            fun loadHostClass() {
                loadApp {
                    "com.example.HostClass".toClass()
                }
            }
        """).indented()).run().expectClean()
    }

    fun testUnrelatedScopeIsIgnored() {
        lint().files(packageParam, kavaRef, kotlin("""
            package test.pkg
            import com.highcapable.kavaref.extension.toClass
            fun loadOutsideScope(loader: ClassLoader) {
                "com.example.HostClass".toClass(loader)
            }
        """).indented()).run().expectClean()
    }
}