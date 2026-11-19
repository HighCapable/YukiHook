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
package com.highcapable.yukihookapi.core.lint.detector

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestMode

/**
 * Checks runtime guards in code shared by both bottom runtimes.
 */
class RuntimeRequirementDetectorTest : LintDetectorTest() {

    companion object {

        private val runtime = kotlin("""
            package com.highcapable.yukihookapi.runtime

            enum class YukiHookRuntime { LIBXPOSED, XPOSED_82 }
        """).indented()

        private val requirement = kotlin("""
            package com.highcapable.yukihookapi.annotation.internal

            import com.highcapable.yukihookapi.runtime.YukiHookRuntime

            annotation class RequiresRuntime(val value: YukiHookRuntime)
        """).indented()

        private val yukiHook = kotlin("""
            package com.highcapable.yukihookapi

            import com.highcapable.yukihookapi.annotation.internal.RequiresRuntime
            import com.highcapable.yukihookapi.runtime.YukiHookRuntime

            object YukiHook {
                object Status {
                    val runtime get() = YukiHookRuntime.XPOSED_82
                    @get:RequiresRuntime(YukiHookRuntime.LIBXPOSED)
                    val sharedFiles get() = "files"
                }
            }
        """).indented()
    }

    override fun getDetector() = RuntimeRequirementDetector()
    override fun getIssues() = listOf(RuntimeRequirementDetector.REQUIRES_YUKIHOOK_RUNTIME_ISSUE)

    fun testRuntimeCheckInPropertyGetterGuardsSharedFiles() {
        lint().files(runtime, requirement, yukiHook, kotlin("""
            package test.pkg

            import com.highcapable.yukihookapi.YukiHook
            import com.highcapable.yukihookapi.runtime.YukiHookRuntime

            object SystemUIHooker {
                private val isSharedCacheAvailable get() = YukiHook.Status.runtime == YukiHookRuntime.LIBXPOSED

                fun attachHost() {
                    if (isSharedCacheAvailable) attach(YukiHook.Status.sharedFiles)
                }

                fun refresh() {
                    if (isSharedCacheAvailable.not()) return
                    attach(YukiHook.Status.sharedFiles)
                }

                private fun attach(files: String) = Unit
            }
        """).indented()).testModes(TestMode.DEFAULT).run().expectClean()
    }

    fun testPropertyNameAndWrongRuntimeDoNotGuardSharedFiles() {
        lint().files(runtime, requirement, yukiHook, kotlin("""
            package test.pkg

            import com.highcapable.yukihookapi.YukiHook
            import com.highcapable.yukihookapi.runtime.YukiHookRuntime

            object SystemUIHooker {
                private val isSharedCacheAvailable get() = YukiHook.Status.runtime == YukiHookRuntime.XPOSED_82

                fun attachHost() {
                    if (isSharedCacheAvailable) println(YukiHook.Status.sharedFiles)
                }
            }
        """).indented()).testModes(TestMode.DEFAULT).run()
            .expectErrorCount(1)
            .expectContains("sharedFiles requires the yukihook-runtime-libxposed runtime")
    }
}