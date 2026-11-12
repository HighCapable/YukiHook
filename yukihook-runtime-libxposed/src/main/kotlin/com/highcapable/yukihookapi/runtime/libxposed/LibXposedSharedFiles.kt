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
 * This file is created by fankes on 2026/9/18.
 */
package com.highcapable.yukihookapi.runtime.libxposed

import com.highcapable.yukihookapi.runtime.bridge.SharedFilesBridge
import io.github.libxposed.api.XposedInterface

/**
 * Shared files accessed through the module service.
 */
internal object LibXposedSharedFiles {

    fun open(hostEnvironment: Boolean) = if (hostEnvironment) Host.requireAttached() else Module

    fun attach(api: XposedInterface) = Host.attach(api)

    private object Module : SharedFilesBridge {

        override val isAvailable get() = LibXposedService.isRemoteFileAccessAvailable
        override val isWritable get() = isAvailable

        override fun list() = LibXposedService.listRemoteFiles()
        override fun open(name: String) = LibXposedService.openRemoteFile(name)
        override fun delete(name: String) = LibXposedService.deleteRemoteFile(name)
    }

    private object Host : SharedFilesBridge {

        private var xposedApi: XposedInterface? = null
        private var remoteAvailable: Boolean? = null

        private val api get() = checkNotNull(xposedApi) {
            "libxposed API host remote files are unavailable."
        }

        @Synchronized
        fun attach(api: XposedInterface) {
            remoteAvailable = null
            xposedApi = api
        }

        fun requireAttached(): SharedFilesBridge {
            api
            return this
        }

        override val isAvailable
            @Synchronized
            get() = remoteAvailable ?: xposedApi?.frameworkProperties?.let { capability ->
                (capability and XposedInterface.PROP_CAP_REMOTE != 0L).also { remoteAvailable = it }
            } ?: false

        override val isWritable = false

        override fun list() = api.listRemoteFiles().toList()
        override fun open(name: String) = api.openRemoteFile(name)
        override fun delete(name: String) = throw UnsupportedOperationException("delete is not supported in the host.")
    }
}