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
 * Checks generated-entry annotation configuration errors.
 */
class ModuleEntryDetectorTest : LintDetectorTest() {

    companion object {

        private val entryAnnotation = kotlin("""
            package com.highcapable.yukihookapi.annotation.xposed
            import kotlin.reflect.KClass
            annotation class YukiHookLibXposedEntry(
                val entryClassName: String = "",
                val minApiVersion: Int = 101,
                val targetApiVersion: Int,
                val scope: Array<String> = [],
                val hotReload: HotReload = HotReload.NONE,
                val javaEntries: Array<KClass<*>> = []
            ) {
                enum class HotReload { NONE, MANUAL, AUTO }
            }
        """).indented()
    }

    override fun getDetector() = ModuleEntryDetector()
    override fun getIssues() = listOf(
        ModuleEntryDetector.INVALID_LIBXPOSED_API_VERSION_ISSUE,
        ModuleEntryDetector.INVALID_LIBXPOSED_HOT_RELOAD_ISSUE,
        ModuleEntryDetector.INVALID_MODULE_ENTRY_CLASS_NAME_ISSUE,
        ModuleEntryDetector.SYSTEM_FRAMEWORK_IN_SCOPE_ISSUE
    )

    fun testMinimumAndTargetApiVersionsMustBeOrdered() {
        lint().files(entryAnnotation, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
            @YukiHookLibXposedEntry(minApiVersion = 100, targetApiVersion = 99)
            class Entry
        """).indented()).run()
            .expectErrorCount(2)
            .expectContains("minApiVersion must be at least 101")
            .expectContains("targetApiVersion must be at least minApiVersion")
    }

    fun testHotReloadRequiresApi102AndNoJavaEntries() {
        lint().files(entryAnnotation, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
            @YukiHookLibXposedEntry(
                targetApiVersion = 101,
                hotReload = YukiHookLibXposedEntry.HotReload.AUTO,
                javaEntries = [NativeEntry::class]
            )
            class Entry
            class NativeEntry
        """).indented()).run()
            .expectErrorCount(2)
            .expectContains("javaEntries must be empty when hot reload is enabled")
            .expectContains("Hot reload requires targetApiVersion to be at least 102")
    }

    fun testInvalidGeneratedEntryNamesAreReported() {
        lint().files(entryAnnotation, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
            @YukiHookLibXposedEntry(targetApiVersion = 102, entryClassName = "bad-name")
            class Entry
            @YukiHookLibXposedEntry(targetApiVersion = 102, entryClassName = "2Entry")
            class AnotherEntry
            @YukiHookLibXposedEntry(targetApiVersion = 102, entryClassName = "Same")
            class Same
        """).indented()).run()
            .expectErrorCount(3)
            .expectContains("entryClassName can only contain letters, digits and underscores")
            .expectContains("entryClassName must not be the same as the entry class name")
    }

    fun testFrameworkPackageInScopeUsesSystemVirtualPackage() {
        lint().files(entryAnnotation, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
            @YukiHookLibXposedEntry(targetApiVersion = 102, scope = ["android", "system"])
            class Entry
        """).indented()).run()
            .expectWarningCount(1)
            .expectContains("\"android\" is not the system framework in scope, use \"system\" instead")
    }

    fun testValidEntryConfigurationIsClean() {
        lint().files(entryAnnotation, kotlin("""
            package test.pkg
            import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
            @YukiHookLibXposedEntry(minApiVersion = 101, targetApiVersion = 102, scope = ["system"])
            class Entry
        """).indented()).run().expectClean()
    }
}