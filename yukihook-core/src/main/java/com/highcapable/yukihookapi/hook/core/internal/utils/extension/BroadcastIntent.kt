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
 * This file is created by fankes on 2026/11/26.
 */
package com.highcapable.yukihookapi.hook.core.internal.utils.extension

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import androidx.core.os.use
import com.highcapable.yukihookapi.hook.xposed.parasitic.reference.ModuleClassLoader

/**
 * Copies extras through [Parcel] without decoding their values or discarding binder references.
 * @receiver the [Bundle] to copy.
 * @param loader the [ClassLoader] to use for decoding the extras, or null to use the default.
 * @return [Bundle] containing the copied extras.
 */
internal fun Bundle.parcelledCopy(loader: ClassLoader?) = Parcel.obtain().use { parcel ->
    parcel.writeBundle(this)
    parcel.setDataPosition(0)

    checkNotNull(parcel.readBundle(loader))
}

/**
 * Gives module callbacks their own extras instead of decoding the shared broadcast's extras.
 *
 * Bundle copies can share lazy values, so decoding needs a separate Parcel-backed bundle.
 * @receiver the [Intent] to copy the extras from.
 * @return [Intent] containing the copied extras.
 */
internal fun Intent.forModuleReceiver() = Intent(this).replaceExtras(extras?.parcelledCopy(ModuleClassLoader.get()))