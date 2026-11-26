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
 * This file is created by fankes on 2022/4/30.
 * This file is modified by fankes on 2022/1/10.
 */
@file:Suppress("unused", "UNUSED_PARAMETER", "DEPRECATION")

package com.highcapable.yukihookapi.hook.xposed.bridge.event

/**
 * Registers listeners for native Xposed Framework API loading events.
 *
 * All functions are deprecated no-ops. Callback parameters use [Any] and expose no native framework types.
 */
@Deprecated(message = "This API has no effect. Use the runtime's registerFrameworkEvents in onInit instead.")
object YukiXposedEvent {

    /**
     * Configures [YukiXposedEvent].
     * @param block the configuration block.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(message = "This API has no effect. Use the runtime's registerFrameworkEvents in onInit instead.")
    inline fun events(block: YukiXposedEvent.() -> Unit) = Unit

    /**
     * Sets the `initZygote` event listener.
     * @param callback the event callback.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(message = "This API has no effect. Use the runtime's registerFrameworkEvents in onInit instead.")
    fun onInitZygote(callback: (Any) -> Unit) = Unit

    /**
     * Sets the `handleLoadPackage` event listener.
     * @param callback the event callback.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(message = "This API has no effect. Use the runtime's registerFrameworkEvents in onInit instead.")
    fun onHandleLoadPackage(callback: (Any) -> Unit) = Unit

    /**
     * Sets the `handleInitPackageResources` event listener.
     * @param callback the event callback.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(message = "This API has no effect. Resource hooks are no longer supported.")
    fun onHandleInitPackageResources(callback: (Any) -> Unit) = Unit
}