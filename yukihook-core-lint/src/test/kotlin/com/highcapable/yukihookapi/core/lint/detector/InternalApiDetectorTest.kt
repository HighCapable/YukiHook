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
package com.highcapable.yukihookapi.core.lint.detector

import com.android.tools.lint.checks.infrastructure.LintDetectorTest

/**
 * Checks that internal and generated YukiHook declarations are not used by module code.
 */
class InternalApiDetectorTest : LintDetectorTest() {

    override fun getDetector() = InternalApiDetector()
    override fun getIssues() = listOf(InternalApiDetector.ISSUE)

    fun testInternalBridgeUseIsReportedButPublicApiIsAllowed() {
        lint().files(kotlin("""
            package com.highcapable.yukihookapi.runtime
            class YukiHookBridge {
                fun invoke() = Unit
            }
        """).indented(), kotlin("""
            package com.highcapable.yukihookapi
            class PublicApi {
                fun invoke() = Unit
            }
        """).indented(), kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.runtime.YukiHookBridge
            import com.highcapable.yukihookapi.PublicApi
            fun useApis() {
                YukiHookBridge().invoke()
                PublicApi().invoke()
            }
        """).indented()).run()
            .expectErrorCount(2)
            .expectContains("YukiHookBridge is an internal YukiHook API")
    }
}