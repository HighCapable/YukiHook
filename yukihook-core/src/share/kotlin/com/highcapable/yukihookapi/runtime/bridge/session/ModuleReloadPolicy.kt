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
 * This file is created by fankes on 2026/9/27.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.runtime.bridge.session

import androidx.annotation.RestrictTo

/**
 * Controls whether the current module instance accepts hot reload requests.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
enum class ModuleReloadPolicy {
    /** Does not accept new reload requests. */
    NONE,

    /** Accepts requests started by the module. */
    MANUAL,

    /** Accepts requests and enables automatic reload after an APK update. */
    AUTO
}