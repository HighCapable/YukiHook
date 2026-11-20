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
 * This file is created by fankes on 2026/9/16.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.core.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.highcapable.yukihookapi.core.lint.detector.InternalApiDetector
import com.highcapable.yukihookapi.core.lint.detector.KavaRefFallbackDetector
import com.highcapable.yukihookapi.core.lint.detector.MemberHookerDetector
import com.highcapable.yukihookapi.core.lint.detector.ModuleEntryDetector
import com.highcapable.yukihookapi.core.lint.detector.ModuleRuntimeDetector
import com.highcapable.yukihookapi.core.lint.detector.ModuleUsageDetector
import com.highcapable.yukihookapi.core.lint.detector.RuntimeRequirementDetector
import com.highcapable.yukihookapi.generated.YukiHookProperties

class YukiHookIssueRegistry : IssueRegistry() {

    override val issues get() = listOf(
        InternalApiDetector.ISSUE,
        RuntimeRequirementDetector.REQUIRES_YUKIHOOK_RUNTIME_ISSUE,
        RuntimeRequirementDetector.NEW_XPOSED_API_ISSUE,
        ModuleEntryDetector.INVALID_LIBXPOSED_API_VERSION_ISSUE,
        ModuleEntryDetector.INVALID_LIBXPOSED_HOT_RELOAD_ISSUE,
        ModuleEntryDetector.INVALID_MODULE_ENTRY_CLASS_NAME_ISSUE,
        ModuleEntryDetector.SYSTEM_FRAMEWORK_IN_SCOPE_ISSUE,
        ModuleRuntimeDetector.MODULE_REGISTRATION_OUTSIDE_ON_INIT_ISSUE,
        ModuleRuntimeDetector.INVALID_MODULE_HOOK_LOADING_ISSUE,
        ModuleRuntimeDetector.INVALID_MODULE_RETAINED_VALUE_ISSUE,
        ModuleRuntimeDetector.LOAD_APP_FOR_SYSTEM_FRAMEWORK_ISSUE,
        ModuleRuntimeDetector.SYSTEM_FRAMEWORK_IN_LOAD_APP_PACKAGES_ISSUE,
        ModuleRuntimeDetector.PROCESS_NAME_FOR_SYSTEM_FRAMEWORK_ISSUE,
        ModuleRuntimeDetector.DATA_CHANNEL_FOR_SYSTEM_FRAMEWORK_ISSUE,
        ModuleRuntimeDetector.NON_REPLAYABLE_CALL_IN_APP_LIFECYCLE_ISSUE,
        ModuleRuntimeDetector.INVALID_APPLICATION_READY_NESTING_ISSUE,
        MemberHookerDetector.CONFLICTING_CLASSIC_HOOK_CALLBACKS_ISSUE,
        MemberHookerDetector.INVALID_FIELD_HOOK_TARGET_ISSUE,
        ModuleUsageDetector.USE_MODULE_APPLICATION_ISSUE,
        ModuleUsageDetector.USE_HOOK_INVOCATION_API_ISSUE,
        ModuleUsageDetector.USE_HOOK_BLOCK_ISSUE,
        ModuleUsageDetector.USE_APPLICATION_READY_ISSUE,
        KavaRefFallbackDetector.ISSUE
    )

    override val minApi = YukiHookProperties.PROJECT_YUKIHOOK_CORE_LINT_MIN_API
    override val api = CURRENT_API
    override val vendor = Vendor(
        vendorName = YukiHookProperties.PROJECT_NAME,
        identifier = YukiHookProperties.PROJECT_YUKIHOOK_CORE_LINT_IDENTIFIER,
        feedbackUrl = "${YukiHookProperties.PROJECT_URL}/issues",
        contact = YukiHookProperties.PROJECT_URL
    )
}