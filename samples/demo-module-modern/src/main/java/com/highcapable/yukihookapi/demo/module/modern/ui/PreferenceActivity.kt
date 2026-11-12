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
 * This file is created by fankes on 2022/4/18.
 */
package com.highcapable.yukihookapi.demo.module.modern.ui

import android.os.Bundle
import android.view.Window
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.preference.SwitchPreference
import com.highcapable.betterandroid.ui.extension.component.replace
import com.highcapable.hikage.core.layout.LayoutParams
import com.highcapable.hikage.extension.addView
import com.highcapable.hikage.widget.android.widget.FrameLayout
import com.highcapable.yukihookapi.demo.module.modern.R
import com.highcapable.yukihookapi.demo.module.modern.ui.base.BaseActivity
import com.highcapable.yukihookapi.hook.xposed.preference.ui.ModulePreferenceFragment

class PreferenceActivity : BaseActivity() {

    override val moduleTheme get() = R.style.Theme_Default_ActionBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.demo_preferences_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) findViewById<FrameLayout>(Window.ID_ANDROID_CONTENT).apply {
            addView {
                FrameLayout(
                    lparams = LayoutParams(matchParent = true),
                    init = {
                        updatePadding(top = 100.dp)

                        doOnLayout {
                            SettingsFragment().replace(this@PreferenceActivity, this)
                        }
                    }
                )
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class SettingsFragment : ModulePreferenceFragment() {

        override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_preferences, rootKey)
            findPreference<SwitchPreference>("show_dialog_when_demo_app_opened")?.isEnabled = isWritable
        }
    }
}