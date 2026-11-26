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
 * This file is created by fankes on 2026/9/10.
 */
package com.highcapable.yukihookapi.compiler.subprocessor.spec

import com.highcapable.yukihookapi.compiler.DeclaredSymbol
import com.squareup.kotlinpoet.ClassName

/**
 * Spec used to generate legacy Xposed entry sources and assets.
 * @param modulePackageName the resolved module application ID.
 * @param generateModuleStatus whether legacy self-hook status generation is enabled.
 */
data class Xposed82GenerateSpec(
    override var entryPackageName: String = "",
    override var entryClassName: ClassName,
    override var initClassName: String = "",
    override var isObjectEntry: Boolean = false,
    var modulePackageName: String = "",
    var generateModuleStatus: Boolean = true
) : GenerateSpec {

    companion object {

        const val ANNOTATION_NAME = "${DeclaredSymbol.API_PACKAGE_NAME}.annotation.xposed.YukiHookXposed82Entry"
        const val ENTRY_CLASS_NAME = "${DeclaredSymbol.API_PACKAGE_NAME}.runtime.xposed82.entry.Xposed82Entry"
        const val RUNTIME_MODULE_NAME = "yukihook-runtime-xposed82"

        const val INIT_FILE_PATH = "assets/xposed_init"

        const val LEGACY_ANNOTATION_NAME = "${DeclaredSymbol.API_PACKAGE_NAME}.annotation.xposed.InjectYukiHookWithXposed"
        const val LEGACY_ASSET_INIT_FILE_PATH = "assets/yukihookapi_init"
        const val LEGACY_RESOURCE_INIT_FILE_PATH = "META-INF/yukihookapi_init"
    }
}