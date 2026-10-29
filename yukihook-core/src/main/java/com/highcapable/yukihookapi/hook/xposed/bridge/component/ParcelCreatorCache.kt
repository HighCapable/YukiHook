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
 * This file is created by fankes on 2026/10/29.
 */
package com.highcapable.yukihookapi.hook.xposed.bridge.component

import android.os.Parcel
import android.os.Parcelable
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.core.internal.HiddenApiBypassResolver
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.bridge.session.ModuleReloadable
import com.highcapable.yukihookapi.hook.xposed.parasitic.AppParasitism
import com.highcapable.yukihookapi.hook.xposed.parasitic.reference.ModuleClassLoader

/**
 * Removes the class loaders of this module instance from the [Parcel] creator cache.
 *
 * [Parcel] caches [Parcelable.Creator] instances by the [ClassLoader] used for reading and never removes them,
 * so a module instance that has read a [Parcelable] with its own class loader cannot be collected after a reload.
 */
internal object ParcelCreatorCache : ModuleReloadable {

    const val RELOAD_ID = "parcel-creator-cache"

    private val resolver = HiddenApiBypassResolver()

    override fun onDispose() {
        // Workaround: the creator cache lives in private static maps of `Parcel` with no API to clear it.
        // Android 14 (API 34) added `sPairedCreators` and locks both maps on it, earlier versions only lock `mCreators`.
        runCatching {
            val creators = creatorsOf("mCreators") ?: error("Parcel.mCreators is unavailable.")
            val pairedCreators = creatorsOf("sPairedCreators")

            synchronized(pairedCreators ?: creators) {
                creators.keys.removeAll(::isOwnedLoader)
                pairedCreators?.keys?.removeAll(::isOwnedLoader)
            }
        }.onFailure { YLog.innerW("Failed to remove module class loaders from the Parcel creator cache", it) }
    }

    private fun creatorsOf(fieldName: String) = Parcel::class.resolve()
        .processor(resolver)
        .optional(silent = true)
        .firstFieldOrNull { name = fieldName }
        ?.get<MutableMap<ClassLoader?, *>>()

    private fun isOwnedLoader(loader: ClassLoader?) = loader === AppParasitism.baseClassLoader || loader is ModuleClassLoader
}