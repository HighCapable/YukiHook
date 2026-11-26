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
 * This file is created by fankes on 2023/9/27.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.hook.log

import android.util.Log
import com.highcapable.kavaref.KavaRef
import com.highcapable.kavaref.runtime.KavaRefRuntime
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.core.api.helper.YukiHookHelper
import com.highcapable.yukihookapi.hook.log.data.YLogData
import com.highcapable.yukihookapi.hook.xposed.bridge.XposedModuleRuntime
import java.io.File
import java.io.IOException
import java.util.Collections

/**
 * Global log manager.
 */
object YLog {

    /**
     * [YLog] configuration.
     */
    object Config {

        /**
         * Tag.
         *
         * Example output:
         *
         * ```
         * [YukiHook][...][...] ...
         * ```
         */
        const val TAG = 1000

        /**
         * Priority.
         *
         * Example output:
         *
         * ```
         * [...][E][...] ...
         * ```
         */
        const val PRIORITY = 1001

        /**
         * Current process name.
         *
         * Example output:
         *
         * ```
         * [...][com.demo.test][...] ...
         * ```
         */
        const val PROCESS_NAME = 1002

        /**
         * Current host user ID, omitted for the owner user.
         *
         * Example output:
         *
         * ```
         * [...][...][999] ...
         * ```
         */
        const val USER_ID = 1003

        /**
         * Global identifier for debug logs.
         *
         * The default value is [YukiHook.TAG].
         *
         * You can replace it with your own value.
         */
        var tag = YukiHook.TAG

        /**
         * Minimum log level, [Level.INFO] by default.
         *
         * [Level.DEBUG] enables detailed hook logs. [Level.OFF] stops all output and recording.
         */
        var level = Level.INFO
            set(value) {
                field = value
                initKavaRefLoggerIfNot()
                updateKavaRefLogLevel()
            }

        /**
         * Whether debug log recording is enabled, false by default.
         *
         * Enabling this records logs and exception stack traces allowed by [level] in memory.
         *
         * Excessive logging may slow down the host or cause frequent garbage collection.
         * Once enabled, call [saveToFile] to save logs to a file in real time or use [contents] to obtain the current log contents.
         */
        var recording = false
            set(value) {
                field = value
                initKavaRefLoggerIfNot()
            }

        // region Deprecated properties for backward compatibility

        /** Current process name. */
        @Deprecated(message = "Use PROCESS_NAME instead.", replaceWith = ReplaceWith("PROCESS_NAME"))
        const val PACKAGE_NAME = PROCESS_NAME

        /** Whether debug log output is enabled. */
        @Deprecated(message = "Use level instead.")
        var enabled
            get() = level != Level.OFF
            set(value) {
                level = if (value) Level.INFO else Level.OFF
            }

        /** Whether debug log output is enabled. */
        @Suppress("DEPRECATION")
        @Deprecated(message = "Use level instead.")
        var isEnable
            get() = enabled
            set(value) {
                enabled = value
            }

        /** Whether debug log recording is enabled. */
        @Deprecated(message = "Use recording instead.", replaceWith = ReplaceWith("recording"))
        var isRecord
            get() = recording
            set(value) {
                recording = value
            }

        // endregion

        /** The current ordered list of elements. */
        internal var elements = arrayOf(TAG, PRIORITY, PROCESS_NAME, USER_ID)

        /**
         * Customizes the elements displayed in debug logs.
         *
         * This applies only to recorded logs and logs in the host environment.
         * Log elements are displayed in the order specified by [item].
         * Leave [item] empty to hide every element except the log message.
         *
         * Available elements are [TAG], [PRIORITY], [PROCESS_NAME], and [USER_ID].
         *
         * Default order:
         * ```
         * [TAG][PRIORITY][PROCESS_NAME][USER_ID] Message
         * ```
         * @param item the custom element array.
         */
        fun elements(vararg item: Int) {
            elements = arrayOf(*item.toTypedArray())
        }
    }

    /**
     * All currently recorded log data.
     *
     * Log data is isolated between host and module processes.
     */
    val inMemoryData: MutableList<YLogData> = Collections.synchronizedList(mutableListOf())

    /**
     * Gets the current log file contents.
     *
     * Returns an empty string when no logs have been recorded.
     *
     * Log data is isolated between host and module processes.
     * @return [String]
     */
    val contents get() = contents()

    /**
     * Returns a stable snapshot of the current [inMemoryData].
     * @return [List]<[YLogData]>
     */
    internal fun createSnapshot() = synchronized(inMemoryData) { inMemoryData.toList() }

    /**
     * Gets and formats the current log file contents.
     *
     * Returns an empty string when no logs have been recorded and [data] is empty.
     *
     * Log data is isolated between host and module processes.
     * @param data the log data, [inMemoryData] by default.
     * @return [String]
     */
    fun contents(data: List<YLogData> = inMemoryData): String {
        val currentData = if (data === inMemoryData) createSnapshot() else data

        return buildString {
            currentData.forEach {
                append("${it.head}$it\n")
                it.throwable?.also { e ->
                    append("${it.head}Dump stack trace for \"${e.javaClass.name}\":\n")
                    append(e.stackTraceToString())
                }
            }
        }
    }

    /**
     * Clears all recorded logs.
     *
     * You can also clear [inMemoryData] directly.
     *
     * Log data is isolated between host and module processes.
     */
    fun clear() = inMemoryData.clear()

    /**
     * Saves the current logs to a file.
     *
     * Performs no operation when [data] is empty, [inMemoryData] stays empty while [Config.recording] is disabled.
     * Logs are appended to [fileName], which is created automatically when it does not exist.
     *
     * File access depends on the permissions granted to the current host or module.
     * @param fileName the full file name, for example `/data/data/.../files/xxx.log`.
     * @param data the log data, [inMemoryData] by default.
     * @throws IOException if the target path is not writable.
     */
    fun saveToFile(fileName: String, data: List<YLogData> = inMemoryData) {
        val currentData = if (data === inMemoryData) createSnapshot() else data
        if (currentData.isNotEmpty()) File(fileName).appendText(contents(currentData))
    }

    /**
     * Prints a debug-level log.
     *
     * Prints to the console and the host environment.
     * @param msg the log message, empty by default. Set only [e] to print just an exception stack trace.
     * @param e the optional exception stack trace, printed in full automatically.
     * @param tag the log tag, preferably the module name. [Config.tag] by default.
     * @param env the log environment, [EnvType.BOTH] by default.
     */
    fun debug(msg: Any? = null, e: Throwable? = null, tag: String = Config.tag, env: EnvType = EnvType.BOTH) =
        log(Level.DEBUG, env) { YLogData(priority = "D", tag = tag, msg = msg.toString(), throwable = e) }

    /**
     * Prints an info-level log.
     *
     * Prints to the console and the host environment.
     * @param msg the log message, empty by default. Set only [e] to print just an exception stack trace.
     * @param e the optional exception stack trace, printed in full automatically.
     * @param tag the log tag, preferably the module name. [Config.tag] by default.
     * @param env the log environment, [EnvType.BOTH] by default.
     */
    fun info(msg: Any? = null, e: Throwable? = null, tag: String = Config.tag, env: EnvType = EnvType.BOTH) =
        log(Level.INFO, env) { YLogData(priority = "I", tag = tag, msg = msg.toString(), throwable = e) }

    /**
     * Prints a warn-level log.
     *
     * Prints to the console and the host environment.
     * @param msg the log message, empty by default. Set only [e] to print just an exception stack trace.
     * @param e the optional exception stack trace, printed in full automatically.
     * @param tag the log tag, preferably the module name. [Config.tag] by default.
     * @param env the log environment, [EnvType.BOTH] by default.
     */
    fun warn(msg: Any? = null, e: Throwable? = null, tag: String = Config.tag, env: EnvType = EnvType.BOTH) =
        log(Level.WARN, env) { YLogData(priority = "W", tag = tag, msg = msg.toString(), throwable = e) }

    /**
     * Prints an error-level log.
     *
     * Prints to the console and the host environment.
     * @param msg the log message, empty by default. Set only [e] to print just an exception stack trace.
     * @param tag the log tag, preferably the module name. [Config.tag] by default.
     * @param e the optional exception stack trace, printed in full automatically.
     * @param env the log environment, [EnvType.BOTH] by default.
     */
    fun error(msg: Any? = null, e: Throwable? = null, tag: String = Config.tag, env: EnvType = EnvType.BOTH) =
        log(Level.ERROR, env) { YLogData(priority = "E", tag = tag, msg = msg.toString(), throwable = e) }

    /**
     * Final implementation that prints logs to the console and the host environment.
     * @param level the log level.
     * @param env the log environment.
     * @param data builds the log data after level filtering.
     */
    private fun log(level: Level, env: EnvType, data: () -> YLogData) {
        initKavaRefLoggerIfNot()
        if (level.ordinal < Config.level.ordinal) return

        val data = data()
        // Whether this is a valid log.
        val isNotBlankLog = data.msg.isNotBlank() || (data.msg.isBlank() && data.throwable != null)

        /** Prints to [Log]. */
        fun logByLogd() = when (level) {
            Level.DEBUG -> Log.d(data.tag, data.msg, data.throwable)
            Level.INFO -> Log.i(data.tag, data.msg, data.throwable)
            Level.WARN -> Log.w(data.tag, data.msg, data.throwable)
            Level.ERROR -> Log.e(data.tag, data.msg, data.throwable)
            Level.OFF -> Unit
        }

        /** Prints to the host environment. */
        fun logByHooker() {
            if (isNotBlankLog) YukiHookHelper.log(
                priority = when (level) {
                    Level.DEBUG -> Log.DEBUG
                    Level.INFO -> Log.INFO
                    Level.WARN -> Log.WARN
                    Level.ERROR -> Log.ERROR
                    Level.OFF -> return
                },
                tag = data.tag,
                msg = data.toString(),
                e = data.throwable
            )
        }

        @Suppress("DEPRECATION")
        when (env) {
            EnvType.LOGD -> logByLogd()
            EnvType.XPOSED, EnvType.XPOSED_ENVIRONMENT -> logByHooker()
            EnvType.SCOPE -> if (XposedModuleRuntime.isHostEnvironment) logByHooker() else logByLogd()
            EnvType.BOTH -> {
                logByLogd()
                if (XposedModuleRuntime.isHostEnvironment) logByHooker()
            }
        }
        if (Config.recording && isNotBlankLog) inMemoryData.add(data)
    }

    /** Defines the [KavaRef] logger. */
    private val kavaRefLogger = object : KavaRefRuntime.Logger {

        override val tag get() = Config.tag

        override fun debug(msg: Any?, throwable: Throwable?) {
            this@YLog.debug(msg.toString(), throwable)
        }

        override fun error(msg: Any?, throwable: Throwable?) {
            this@YLog.error(msg.toString(), throwable)
        }

        override fun info(msg: Any?, throwable: Throwable?) {
            this@YLog.info(msg.toString(), throwable)
        }

        override fun warn(msg: Any?, throwable: Throwable?) {
            this@YLog.warn(msg.toString(), throwable)
        }
    }

    /** Whether the [KavaRef] logger has been initialized. */
    private var isKavaRefLoggerInit = false

    /** Initializes the [KavaRef] logger on the first call only. */
    private fun initKavaRefLoggerIfNot() {
        if (isKavaRefLoggerInit) return

        KavaRef.setLogger(kavaRefLogger)
        isKavaRefLoggerInit = true
        updateKavaRefLogLevel()
    }

    /** Updates the [KavaRef] logger level. */
    private fun updateKavaRefLogLevel() {
        KavaRef.logLevel = when (Config.level) {
            Level.DEBUG -> KavaRefRuntime.LogLevel.DEBUG
            Level.INFO -> KavaRefRuntime.LogLevel.INFO
            Level.WARN -> KavaRefRuntime.LogLevel.WARN
            Level.ERROR -> KavaRefRuntime.LogLevel.ERROR
            Level.OFF -> KavaRefRuntime.LogLevel.OFF
        }
    }

    /**
     * Log levels for [YukiHook].
     * @param levelName the name of the log level.
     */
    enum class Level(val levelName: String) {
        /** DEBUG */
        DEBUG("debug"),

        /** INFO */
        INFO("info"),

        /** WARN */
        WARN("warn"),

        /** ERROR */
        ERROR("error"),

        /** OFF (Turns off logging) */
        OFF("off")
    }

    /**
     * Log output environment type.
     *
     * Determines how logs are printed in module and host environments.
     */
    enum class EnvType {
        /** Uses only [Log]. */
        LOGD,

        /**
         * Uses only the host environment.
         *
         * Available only in the host environment and has no effect in the module environment.
         */
        XPOSED,

        /**
         * Uses environment-specific output.
         *
         * The host environment uses only [XPOSED].
         *
         * The module environment uses only [LOGD].
         */
        SCOPE,

        /**
         * Uses both outputs.
         *
         * The host environment uses [LOGD] and [XPOSED].
         *
         * The module environment uses only [LOGD].
         */
        BOTH,

        // region Deprecated properties for backward compatibility

        /** Uses only the host environment. */
        @Deprecated("Use XPOSED instead.", ReplaceWith("XPOSED"))
        XPOSED_ENVIRONMENT

        // endregion
    }
}