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
 * This file is created by fankes on 2022/2/9.
 */
package com.highcapable.yukihookapi.demo.module.modern.hook

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.highcapable.betterandroid.system.extension.utils.AndroidVersion
import com.highcapable.betterandroid.ui.extension.component.startActivity
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.ArrayClass
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.demo.module.modern.R
import com.highcapable.yukihookapi.demo.module.modern.data.DataConst
import com.highcapable.yukihookapi.demo.module.modern.generated.DemoProperties
import com.highcapable.yukihookapi.demo.module.modern.ui.MainActivity
import com.highcapable.yukihookapi.demo.module.modern.ui.component.compatStyle
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.highcapable.yukihookapi.extension.kavaref.intercept
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.factory.applyModuleTheme
import com.highcapable.yukihookapi.hook.factory.registerModuleActivities
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule

/**
 * Modern Xposed module hook entry.
 */
@YukiHookLibXposedEntry(
    targetApiVersion = 102,
    scope = [DemoProperties.PROJECT_SAMPLES_DEMO_APP_PACKAGE_NAME],
    // Enable hot reload, which can be set to [YukiHookLibXposedEntry.HotReload.AUTO] or [YukiHookLibXposedEntry.HotReload.MANUAL]
    // 启用热重载，可设置为 [YukiHookLibXposedEntry.HotReload.AUTO] 或 [YukiHookLibXposedEntry.HotReload.MANUAL]
    hotReload = YukiHookLibXposedEntry.HotReload.AUTO
)
object HookEntry : YukiHookXposedModule {

    override fun onInit() {
        // Configures YukiHook
        // Can be shortened to configure {}
        // 配置 YukiHook
        // 可简写为 configure {}
        YukiHook.configure {
            // Configures YLog
            // 配置 YLog
            logging {
                // TAG for global debugging
                // Filter this TAG in the Logcat console to find detailed logs
                // 全局调试用的 TAG
                // 在 Logcat 控制台过滤此 TAG 可找到详细日志
                tag = "YukiHook-Demo"
                // Log level, INFO by default
                // Use INFO in release builds, or OFF to stop all new output and recording
                // 日志级别 - 默认为 INFO
                // 发布版本建议使用 INFO，OFF 会停止全部新的日志输出与记录
                level = YLog.Level.DEBUG
                // Whether to enable the logging function of debug log, not enabled by default
                // When enabled, logs that pass level and their exception stacks are recorded in memory
                // Please note, excessive logging may slow down the host or cause frequent GCs
                // After enabling, you can call [YukiHookLogger.saveToFile] to save the log to file in real-time
                // Or use [YukiHookLogger.contents] to get the real-time log file contents
                // 是否启用调试日志的记录功能 - 默认不启用
                // 开启后将会在内存中记录通过日志级别过滤的日志和异常堆栈
                // 请注意 - 过量的日志可能会导致宿主运行缓慢或造成频繁 GC
                // 开启后你可以调用 [YukiHookLogger.saveToFile] 实时保存日志到文件或使用 [YukiHookLogger.contents] 获取实时日志文件
                recording = false
                // Customize the elements displayed by the debug log externally
                // Only valid for logging and [XposedBridge.log]
                // The arrangement of log elements will be displayed in the order you set in [item]
                // You can also leave [item] blank to not show all elements except the log content
                // Available elements are: [TAG], [PRIORITY], [PROCESS_NAME], [USER_ID]
                // 自定义调试日志对外显示的元素
                // 只对日志记录和 [XposedBridge.log] 生效
                // 日志元素的排列将按照你在 [item] 中设置的顺序进行显示
                // 你还可以留空 [item] 以不显示除日志内容外的全部元素
                // 可用的元素有：[TAG]、[PRIORITY]、[PROCESS_NAME]、[USER_ID]
                elements(TAG, PRIORITY, PROCESS_NAME, USER_ID)
            }
            // Whether to enable [YukiHook] debug mode, disabled by default
            // The demo enables it with the DEBUG log level to help check module activation
            // Disable it in release builds
            // 是否启用 [YukiHook] 调试模式 - 默认不启用
            // Demo 配合 DEBUG 日志级别启用，方便检查模块是否激活
            // 发布版本请关闭此功能
            debug = true
            // Whether to enable the [YukiHookDataChannel] function of the current Xposed module interacting with the host
            // Please make sure the Xposed module's [Application] extends [ModuleApplication] to be valid
            // This feature is enabled by default, when disabled it will not load [YukiHookDataChannel] when the feature is initialized
            // When the feature is enabled, it will automatically register the Hook [Application] lifecycle method when the host starts
            // 是否启用当前 Xposed 模块与宿主交互的 [YukiHookDataChannel] 功能
            // 请确保 Xposed 模块的 [Application] 继承于 [ModuleApplication] 才能有效
            // 此功能默认启用 - 关闭后将不会在功能初始化的时候装载 [YukiHookDataChannel]
            // 功能启用后 - 将会在宿主启动时自动 Hook [Application] 的生命周期方法进行注册
            dataChannel = true
        }
    }

    override fun onHook() {
        // Start your hook
        // Can be shortened to encase {}
        // 开始你的 Hook
        // 可简写为 encase {}
        YukiHook.encase {
            // Load the app to be hooked
            // 装载需要 Hook 的 app
            loadApp(DemoProperties.PROJECT_SAMPLES_DEMO_APP_PACKAGE_NAME) {
                // Register Activity Proxy
                // 注册模块 Activity 代理
                if (AndroidVersion.isAtLeast(AndroidVersion.N)) withApplicationReady { it.registerModuleActivities() }
                // Find Class to hook
                // 得到需要 Hook 的 Class
                Activity::class.resolve()
                    // 由于 Activity 的方法比较多，演示的时候将其静音防止刷爆日志
                    // Since there are many methods in Activity, it is muted during the demo to prevent flooding the log
                    .optional(silent = true)
                    .firstMethod {
                        name = "onCreate"
                        parameters(Bundle::class)
                    }.hook {
                        after {
                            // Add text after the [Activity] title
                            // 在 [Activity] 标题后方加入文字
                            instance<Activity>().apply {
                                title = module.resources.getString(R.string.demo_hooked_activity_title, title)
                            }
                        }
                    }
                // Find Class to hook
                // 得到需要 Hook 的 Class
                "$packageName.ui.MainActivity".toClass().resolve().apply {
                    // Inject the method to be hooked
                    // 注入要 Hook 的方法
                    firstMethod {
                        name = "getFirstText"
                        emptyParameters()
                        returnType = String::class
                    }.hook {
                        // Replaced hook
                        // 执行替换 Hook
                        intercept("Hello YukiHook!")
                    }
                    // Inject the method to be hooked
                    // 注入要 Hook 的方法
                    firstMethod {
                        name = "onCreate"
                        parameters(Bundle::class)
                    }.hook {
                        // Before hook the method
                        // 在方法执行之前拦截
                        before {
                            // The instance is "$packageName.ui.MainActivity"
                            // We used "apply" function on the top
                            // 当前实例为 "$packageName.ui.MainActivity"
                            // 我们在顶部使用了 "apply" 方法
                            firstField {
                                name = "secondText"
                                type = String::class
                            }.of(instance).set("I am hook result")
                        }
                        // After hook the method
                        // 在执行方法之后拦截
                        after {
                            if (preferences.getBoolean("show_dialog_when_demo_app_opened"))
                                MaterialAlertDialogBuilder(instance<Activity>().applyModuleTheme(R.style.Theme_Default))
                                    .setTitle(module.resources.getString(R.string.demo_hooked_dialog_title))
                                    .setMessage(
                                        module.resources.getString(
                                            R.string.demo_hooked_dialog_message,
                                            "${YukiHook.Status.Executor.name} " +
                                                "${YukiHook.Status.Executor.version} " +
                                                "(${YukiHook.Status.Executor.versionCode})",
                                            YukiHook.Status.Executor.apiVersion
                                        )
                                    )
                                    .setPositiveButton(module.resources.getString(R.string.demo_dialog_ok), null)
                                    .show().compatStyle()
                        }
                    }
                    // Inject the method to be hooked
                    // 注入要 Hook 的方法
                    firstMethod {
                        name = "getRegularText"
                        parameters(String::class)
                        returnType = String::class
                    }.hook {
                        // Before hook the method
                        // 在方法执行之前拦截
                        before {
                            // Set the 0th param (recomment)
                            // 设置 0 号 param (推荐)
                            firstArg().set("I am hook method param")
                            // The following method is also ok
                            // 下面这种方式也是可以的
                            // args[0] = "I am hook method param"
                        }
                    }
                    // Inject the method to be hooked
                    // 注入要 Hook 的方法
                    firstMethod {
                        name = "getArray"
                        parameters(ArrayClass(String::class))
                        returnType = ArrayClass(String::class)
                    }.hook {
                        // Before hook the method
                        // 在方法执行之前拦截
                        before {
                            // Set the 0th param
                            // 设置 0 号 param
                            firstArg().get<Array<String>>()?.set(0, "peach")
                        }
                    }
                    // Inject the method to be hooked
                    // 注入要 Hook 的方法
                    firstMethod {
                        name = "toast"
                        emptyParameters()
                        returnType = Void.TYPE
                    }.hook {
                        // Intercept the entire method
                        // 拦截整个方法
                        intercept {
                            instance<Activity>().applyModuleTheme(R.style.Theme_Default).also { context ->
                                MaterialAlertDialogBuilder(context)
                                    .setTitle(module.resources.getString(R.string.demo_hooked_dialog_title))
                                    .setMessage(module.resources.getString(R.string.demo_hooked_toast_message))
                                    .apply {
                                        if (AndroidVersion.isAtLeast(AndroidVersion.N))
                                            setPositiveButton(module.resources.getString(R.string.demo_start_parasitic)) { _, _ ->
                                                MaterialAlertDialogBuilder(context)
                                                    .setTitle(module.resources.getString(R.string.demo_start_parasitic_title))
                                                    .setMessage(module.resources.getString(R.string.demo_start_parasitic_message))
                                                    .setPositiveButton(module.resources.getString(R.string.demo_dialog_yes)) { _, _ ->
                                                        context.startActivity<MainActivity>()
                                                    }.setNegativeButton(module.resources.getString(R.string.demo_dialog_no), null).show().compatStyle()
                                            }
                                    }.setNegativeButton(module.resources.getString(R.string.demo_send_message_to_module)) { _, _ ->
                                        dataChannel.put(
                                            DataConst.TEST_CN_DATA,
                                            value = module.resources.getString(R.string.demo_message_from_host)
                                        )
                                    }.setNeutralButton(module.resources.getString(R.string.demo_remove_hook)) { _, _ ->
                                        removeSelf()
                                    }.show().compatStyle()
                            }
                        }
                    }
                    // Inject the method to be hooked
                    // 注入要 Hook 的方法
                    firstMethod {
                        name = "getDataText"
                        emptyParameters()
                        returnType = String::class
                    }.hook {
                        // Replaced hook
                        // 执行替换 Hook
                        intercept(preferences.get(DataConst.TEST_KV_DATA))
                    }
                }
                // Find Class to hook
                // 得到需要 Hook 的 Class
                "$packageName.test.Main".toClass().resolve().apply {
                    // Inject the method to be hooked
                    // 注入要 Hook 的方法
                    firstConstructor { parameters(String::class) }.hook {
                        // Before hook the method
                        // 在方法执行之前拦截
                        before {
                            // Set the 0th param
                            // 设置 0 号 param
                            firstArg().set("I am hook constructor param")
                        }
                    }
                    // Inject the method to be hooked
                    // 注入要 Hook 的方法
                    firstMethod {
                        name = "getSuperString"
                        emptyParameters()
                        // This method is not in the current Class
                        // Just set this find condition to automatically go to the super class of the current Class to find
                        // 这个方法不在当前的 Class
                        // 只需要设置此查找条件即可自动前往当前 Class 的父类查找
                        superclass()
                    }.hook {
                        // Replaced hook
                        // 执行替换 Hook
                        intercept("I am hook super class method")
                    }
                    // Inject the method to be hooked
                    // 注入要 Hook 的方法
                    method {
                        name = "getTestResultFirst"
                    }.hookAll {
                        // Replaced hook
                        // 执行替换 Hook
                        intercept("I am hook all methods first")
                    }
                    // Inject the method to be hooked
                    // 注入要 Hook 的方法
                    method {
                        name = "getTestResultLast"
                    }.hookAll {
                        // Replaced hook
                        // 执行替换 Hook
                        intercept("I am hook all methods last")
                    }
                }
                // Intercept the text with HookChain
                // 使用 HookChain 拦截文本
                TextView::class.resolve().firstMethod {
                    name = "setText"
                    parameters(CharSequence::class, TextView.BufferType::class)
                }.intercept {
                    // Continue the chain with new arguments, leaving other text unchanged
                    // 向后续调用链传入新参数，其余文本保持不变
                    if (firstArg() == "This is the last sentence")
                        proceed(arrayOf("I am hook last sentence", lastArg()))
                    else proceed()
                }
            }
        }
    }
}