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
 * Checks that the manifest Application of a module inherits ModuleApplication.
 */
class ModuleApplicationDetectorTest : LintDetectorTest() {

    companion object {

        private val moduleApplication = kotlin("""
            package com.highcapable.yukihookapi.hook.xposed.application
            open class ModuleApplication : android.app.Application()
        """).indented()

        private val manifest = xml("AndroidManifest.xml", """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                <application android:name="test.pkg.ModuleApp" />
            </manifest>
        """).indented()
    }

    override fun getDetector() = ModuleUsageDetector()
    override fun getIssues() = listOf(ModuleUsageDetector.USE_MODULE_APPLICATION_ISSUE)

    fun testManifestApplicationMustInheritModuleApplication() {
        lint().files(moduleApplication, manifest, kotlin("""
            package test.pkg
            class ModuleApp : android.app.Application()
        """).indented()).run()
            .expectWarningCount(1)
            .expectContains("ModuleApp should inherit ModuleApplication to enable full YukiHook functionality")
    }

    fun testModuleApplicationSubclassIsAccepted() {
        lint().files(moduleApplication, xml("AndroidManifest.xml", """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                <application android:name="test.pkg.ModuleApp" />
            </manifest>
        """).indented(), kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
            class ModuleApp : ModuleApplication()
        """).indented()).run().expectClean()
    }
}