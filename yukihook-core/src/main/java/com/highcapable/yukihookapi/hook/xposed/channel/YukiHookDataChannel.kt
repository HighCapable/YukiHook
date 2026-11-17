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
 * This file is created by fankes on 2022/5/16.
 */
@file:Suppress("unused", "UNCHECKED_CAST")

package com.highcapable.yukihookapi.hook.xposed.channel

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.os.TransactionTooLargeException
import androidx.core.content.getSystemService
import androidx.core.os.use
import com.highcapable.betterandroid.system.extension.component.BroadcastReceiver
import com.highcapable.betterandroid.system.extension.component.getParcelableCompat
import com.highcapable.betterandroid.system.extension.component.registerReceiver
import com.highcapable.betterandroid.system.extension.component.sendBroadcast
import com.highcapable.betterandroid.ui.extension.component.hostActivity
import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.hook.core.internal.utils.RandomString
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.log.data.YLogData
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.highcapable.yukihookapi.hook.xposed.bridge.XposedModuleRuntime
import com.highcapable.yukihookapi.hook.xposed.bridge.event.ModuleEventDispatcher
import com.highcapable.yukihookapi.hook.xposed.bridge.session.ModuleReloadable
import com.highcapable.yukihookapi.hook.xposed.channel.annotation.SendTooLargeChannelData
import com.highcapable.yukihookapi.hook.xposed.channel.data.ChannelData
import com.highcapable.yukihookapi.hook.xposed.channel.data.wrapper.ChannelDataWrapper
import com.highcapable.yukihookapi.hook.xposed.channel.priority.ChannelPriority
import com.highcapable.yukihookapi.hook.xposed.parasitic.AppParasitism
import com.highcapable.yukihookapi.hook.xposed.parasitic.reference.ModuleClassLoader
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Data channel between the Xposed module and host.
 *
 * Exchanges data by registering [BroadcastReceiver] instances in both the module and host.
 * The module's [Application] must inherit [ModuleApplication] to use this feature.
 *
 * - Note: Requires both the module and host to remain alive.
 */
class YukiHookDataChannel private constructor() {

    internal companion object : ModuleReloadable {

        const val RELOAD_ID = "data-channel"

        override val requiresSavedState = false
        override val requiresMainThread = true

        private var events = ModuleEventDispatcher()

        private val isHostEnvironment = XposedModuleRuntime.isHostEnvironment
        private val moduleGeneratedVersion = YukiHook.Status.compiledTimestamp.toString()

        private const val GET_MODULE_GENERATED_VERSION = "module_generated_version_get"
        private const val GET_YUKI_LOGGER_IN_MEMORY_DATA = "yuki_logger_in_memory_data_get"
        private const val RESULT_MODULE_GENERATED_VERSION = "module_generated_version_result"

        private const val VALUE_WAIT_FOR_LISTENER = "wait_for_listener_value"

        /** How long incomplete segmented data is kept before it is discarded, in milliseconds. */
        private const val SEGMENTS_EXPIRATION_MILLIS = 60_000L

        private val loggerInMemoryDataResult = ChannelData<List<YLogData>>("yuki_logger_in_memory_data_result")

        /**
         * Data-size threshold in bytes for segmenting a system broadcast.
         *
         * The standard limit is 1 MB. Tests show known limits of 1, 2, or 3 MB across different systems.
         * Tests also show that sending 900 KB in segments still fails on one Android 13 device.
         * To accommodate system-specific limits, the default is 500 KB. Supported larger values are split using a smaller target size.
         */
        private var receiverDataMaxByteSize = 500 * 1024

        /**
         * Maximum data-size compression factor for system broadcasts using segmented data.
         *
         * The target is not a hard limit. An indivisible collection element may produce a segment larger than [receiverDataMaxByteSize].
         * This factor is used to divide [receiverDataMaxByteSize].
         * The formula is [receiverDataMaxByteSize] / [receiverDataMaxByteCompressionFactor] = [receiverDataSegmentMaxByteSize].
         */
        private var receiverDataMaxByteCompressionFactor = 3

        /**
         * Gets the target size in bytes for one segmented system-broadcast payload.
         * @return [Int]
         */
        private val receiverDataSegmentMaxByteSize get() = receiverDataMaxByteSize / receiverDataMaxByteCompressionFactor

        @Suppress("StaticFieldLeak")
        private val instance = YukiHookDataChannel()

        /**
         * Gets the [YukiHookDataChannel] singleton.
         * @return [YukiHookDataChannel]
         */
        fun get() = instance

        override fun onAttach(events: ModuleEventDispatcher) {
            this.events = events
        }

        override fun onDispose() = instance.unregister()

        override fun onRestoreState(appInfo: ApplicationInfo, state: Any?) = AppParasitism.withHostApplication(instance::registerToHost)
    }

    /**
     * Listener type for key-value callbacks.
     */
    private enum class CallbackKeyType {
        SINGLE,
        CDATA,
        WAIT_FOR_LISTENER
    }

    private val receiverCallbacks = ConcurrentHashMap<String, Pair<Context?, (String, Intent) -> Unit>>()
    private var receiverContext: Context? = null
    private var handlerReceiver: BroadcastReceiver? = null

    /** The broadcast receiver callback. */
    private val handlerReceiverCallback = BroadcastReceiver { _, intent ->
        events.dispatch {
            intent.action?.also { action ->
                // One failing listener must not stop the others from receiving this action.
                var failure: Throwable? = null
                receiverCallbacks.forEach { (key, param) ->
                    runCatching {
                        when {
                            param.first?.hostActivity?.isDestroyed == true -> receiverCallbacks.remove(key, param)
                            isCurrentBroadcast(param.first) -> param.second(action, intent)
                        }
                    }.onFailure {
                        // A module bug must not crash the host, the module process throws it to the caller.
                        if (isHostEnvironment)
                            YLog.innerE("The module's data channel listener \"$key\" failed to handle received action \"$action\"", it)
                        else if (failure == null) failure = it
                        else failure.addSuppressed(it)
                    }
                }
                failure?.let { throw it }
            }
        }
    }

    /** Checks the API loading state. */
    private fun checkApi() {
        require(!YukiHook.isStandalone) {
            "YukiHookDataChannel is not available in the standalone hook API."
        }
        if (isHostEnvironment) require(XposedModuleRuntime.modulePackageName.isNotBlank()) {
            "Failed to load the Xposed modulePackageName, check its configuration and rebuild the project."
        }
    }

    /**
     * Checks whether this broadcast callback event is currently active.
     * @param context the current instance.
     * @return [Boolean]
     */
    private fun isCurrentBroadcast(context: Context?) = runCatching {
        @Suppress("DEPRECATION")
        context is Application || isHostEnvironment || ((context ?: receiverContext)
            ?.getSystemService<ActivityManager>()
            ?.getRunningTasks(9999)?.any { context?.javaClass?.name == it?.topActivity?.className } == true)
    }.getOrElse {
        YLog.innerW("Failed to get the current Activity status", it)
        false
    }

    /**
     * Gets the host broadcast action name.
     * @param packageName the package name.
     * @return [String]
     */
    private fun hostActionName(packageName: String) = "yuki_hook_host_data_channel_${packageName.trim().hashCode()}"

    /**
     * Gets the module broadcast action name.
     * @param context the optional context instance, null by default.
     * @return [String]
     */
    private fun moduleActionName(context: Context? = null) = "yuki_hook_module_data_channel_${XposedModuleRuntime.modulePackageName.ifBlank {
        context?.packageName ?: ""
    }.trim().hashCode()}"

    /**
     * Registers broadcasts.
     * @param context the global context of the target host or module. Registration stops when null.
     * @param packageName the package name. When empty, it is obtained from [Context.getPackageName] on [context].
     */
    internal fun register(context: Context?, packageName: String = context?.packageName ?: "") {
        if (!YukiHook.Config.dataChannel || context == null) return

        receiverContext = context
        val filter = IntentFilter().apply {
            addAction(if (isHostEnvironment) hostActionName(packageName) else moduleActionName(context))
        }
        handlerReceiver = context.registerReceiver(filter, exported = true, body = handlerReceiverCallback)

        // Prevents the module from registering its own broadcast in the module environment.
        if (!isHostEnvironment) return

        namespace(context, packageName).with {
            // Registers a listener that checks whether module and host versions match.
            wait<String>(GET_MODULE_GENERATED_VERSION) { fromPackageName ->
                namespace(context, fromPackageName).put(RESULT_MODULE_GENERATED_VERSION, moduleGeneratedVersion)
            }
            // Registers a listener for debug log data exchanged between the module and host.
            wait<String>(GET_YUKI_LOGGER_IN_MEMORY_DATA) { fromPackageName ->
                namespace(context, fromPackageName).put(loggerInMemoryDataResult, YLog.createSnapshot())
            }
        }
    }

    /**
     * Registers broadcasts to the host once after its [Application] is ready.
     * @param application the host [Application] instance.
     */
    internal fun registerToHost(application: Application) {
        if (!YukiHook.Config.dataChannel || handlerReceiver != null) return

        runCatching { register(application) }.onFailure {
            events.reportFailure(it)
            YLog.innerE("Data channel initialization failed", it)
        }
    }

    /** Unregisters broadcasts. */
    internal fun unregister() {
        handlerReceiver?.let { receiverContext?.unregisterReceiver(it) }
        handlerReceiver = null
        receiverCallbacks.clear()
        receiverContext = null
    }

    /**
     * Gets a namespace.
     * @param context the context instance.
     * @param packageName the target host package name.
     * @return [Namespace]
     */
    internal fun namespace(context: Context? = null, packageName: String): Namespace {
        checkApi()

        val packageName = if (AppParasitism.isSystemFramework(packageName))
            AppParasitism.FRAMEWORK_PACKAGE_NAME
        else packageName
        return Namespace(context = context ?: receiverContext, packageName)
    }

    /**
     * Temporary collection for segmented data.
     * @param listData the [List] data segments.
     * @param mapData the [Map] data segments.
     * @param setData the [Set] data segments.
     * @param stringData the [String] data segments.
     */
    internal class SegmentsTempData(
        var listData: MutableList<List<*>>? = null,
        var mapData: MutableList<Map<*, *>>? = null,
        var setData: MutableList<Set<*>>? = null,
        var stringData: MutableList<String>? = null
    ) {

        /** The time this data was created, used to discard transfers that lost segments. */
        val createdAt = SystemClock.elapsedRealtime()
    }

    /**
     * Counts the bytes written to it without storing them.
     */
    private class ByteCounter : OutputStream() {

        /** The number of bytes written. */
        var count = 0L
            private set

        override fun write(b: Int) {
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            count += len
        }
    }

    /**
     * [YukiHookDataChannel] namespace.
     *
     * Use [namespace] to obtain [Namespace].
     * @param context the context instance.
     * @param packageName the target host package name.
     */
    inner class Namespace internal constructor(private val context: Context?, private val packageName: String) {

        /** The current temporary segmented data. */
        private val segmentsTempData = ConcurrentHashMap<String, SegmentsTempData>()

        /** Whether sending from this namespace may bypass the data-size limit. */
        private var allowSendTooLargeData = false

        /**
         * Gets the key-value suffix.
         * @param type the callback type.
         * @return [String]
         */
        private fun keyShortName(type: CallbackKeyType) = "${keyNonRepeatName}_${
            if (isHostEnvironment) "X" else context?.javaClass?.name ?: "M"
        }_${type.ordinal}"

        /**
         * Unique key-value name that prevents names used by different hosts from interfering with one another.
         * @return [String]
         */
        private val keyNonRepeatName get() = "_${packageName.hashCode()}"

        /**
         * Creates an invocation scope.
         * @param block the invocation block.
         * @return [Namespace] this namespace for chaining.
         */
        inline fun with(block: Namespace.() -> Unit) = apply(block)

        /**
         * Maximum data size in bytes that [YukiHookDataChannel] may send before segmenting it.
         *
         * The default is 500 KB (500 * 1024). See the documentation for [receiverDataMaxByteSize].
         * The minimum is 100 KB (100 * 1024). Lower values are reset to 100 KB.
         * The value applies globally until the current process ends.
         *
         * Supported larger values are segmented automatically, but individual segments may still exceed this value.
         *
         * - Warning: adjust this value carefully. Exceeding the system limit causes [TransactionTooLargeException].
         * @return [Int]
         */
        var maxDataSizeBytes
            get() = receiverDataMaxByteSize
            set(value) {
                receiverDataMaxByteSize = if (value < 100 * 1024) 100 * 1024 else value
            }

        /**
         * Divisor used to calculate the size of each data segment.
         *
         * The default is 3. See the documentation for [receiverDataMaxByteCompressionFactor].
         * The minimum is 2. Lower values are reset to 2.
         * The value applies globally until the current process ends.
         *
         * [receiverDataMaxByteSize] is divided by this value to determine the target size of each segment.
         *
         * - Warning: adjust this value carefully. Exceeding the system limit causes [TransactionTooLargeException].
         * @return [Int]
         */
        var segmentSizeDivisor
            get() = receiverDataMaxByteCompressionFactor
            set(value) {
                receiverDataMaxByteCompressionFactor = if (value < 2) 2 else value
            }

        /**
         * Removes the outgoing data-size limit and disables segmented sending.
         *
         * Once enabled, this applies to every subsequent sending invocation from the current [Namespace] instance.
         * Obtain a new [Namespace] instance to restore the data-size limit.
         * Declare [SendTooLargeChannelData] across the invocation scope to suppress the warning.
         *
         * - Note: Do not use this feature unless you understand its consequences.
         * @return [Namespace]
         */
        @SendTooLargeChannelData
        fun allowOversizedData(): Namespace {
            allowSendTooLargeData = true
            return this
        }

        /**
         * Sends key-value data.
         * @param key the key name.
         * @param value the value data.
         */
        fun <T> put(key: String, value: T) = parseSendingData(
            ChannelData(key, value).toWrapper(),
            allowSendTooLargeData
        )

        /**
         * Sends key-value data.
         * @param data the key-value instance.
         * @param value the value data, [ChannelData.value] when omitted.
         */
        fun <T> put(data: ChannelData<T>, value: T? = data.value) = parseSendingData(
            ChannelData(data.key, value).toWrapper(),
            allowSendTooLargeData
        )

        /**
         * Sends key-value data.
         * @param data the key-value instances.
         */
        fun put(vararg data: ChannelData<*>) = data.takeIf { it.isNotEmpty() }?.let {
            it.forEach { item -> parseSendingData(item.toWrapper(), allowSendTooLargeData) }
        }

        /**
         * Sends a key-value listener request only, using [VALUE_WAIT_FOR_LISTENER] as the value.
         * @param key the key name.
         */
        fun put(key: String) = put(key, VALUE_WAIT_FOR_LISTENER)

        /**
         * Receives key-value data.
         * @param key the key name.
         * @param priority the response priority, unset by default.
         * @param result the result-data callback.
         */
        fun <T> wait(key: String, priority: ChannelPriority? = null, result: (value: T) -> Unit) {
            receiverCallbacks[key + keyShortName(CallbackKeyType.SINGLE)] = Pair(context) { action, intent ->
                if (priority == null || priority.result)
                    if (action == if (isHostEnvironment) hostActionName(packageName) else moduleActionName(context))
                        parseReceivedData(intent.getDataWrapper(key), result)
            }
        }

        /**
         * Receives key-value data.
         * @param data the key-value instance.
         * @param priority the response priority, unset by default.
         * @param result the result-data callback.
         */
        fun <T> wait(data: ChannelData<T>, priority: ChannelPriority? = null, result: (value: T) -> Unit) {
            receiverCallbacks[data.key + keyShortName(CallbackKeyType.CDATA)] = Pair(context) { action, intent ->
                if (priority == null || priority.result)
                    if (action == if (isHostEnvironment) hostActionName(packageName) else moduleActionName(context))
                        parseReceivedData(intent.getDataWrapper(data.key), result)
            }
        }

        /**
         * Receives only a listener result without key-value data.
         *
         * Only listeners sent with [VALUE_WAIT_FOR_LISTENER] can be received.
         * @param key the key name.
         * @param priority the response priority, unset by default.
         * @param callback the result callback.
         */
        fun wait(key: String, priority: ChannelPriority? = null, callback: () -> Unit) {
            receiverCallbacks[key + keyShortName(CallbackKeyType.WAIT_FOR_LISTENER)] = Pair(context) { action, intent ->
                if (priority == null || priority.result)
                    if (action == if (isHostEnvironment) hostActionName(packageName) else moduleActionName(context))
                        intent.getDataWrapper<String>(key)?.let { if (it.instance.value == VALUE_WAIT_FOR_LISTENER) callback() }
            }
        }

        /**
         * Checks whether the module and host versions match.
         *
         * This function detects a mismatch caused when the Xposed module is updated but the host has not reloaded it.
         * @param priority the response priority, unset by default.
         * @param result the callback receiving whether the versions match.
         */
        fun checkVersionMatch(priority: ChannelPriority? = null, result: (Boolean) -> Unit) {
            wait<String>(RESULT_MODULE_GENERATED_VERSION, priority) { result(it == moduleGeneratedVersion) }
            put(GET_MODULE_GENERATED_VERSION, packageName)
        }

        /**
         * Gets [List]<[YLogData]> data exchanged between the module and host.
         *
         * Because the module and host run in different processes, this bridge provides access to each process's debug log data.
         *
         * Both the module and host must enable [YLog.Config.recording] to obtain debug log data.
         * Android limits transfer size. Large debug logs are sent in segments automatically, and larger data transfers more slowly.
         * @param priority the response priority, unset by default.
         * @param result the [List]<[YLogData]> callback.
         */
        fun obtainLoggerInMemoryData(priority: ChannelPriority? = null, result: (List<YLogData>) -> Unit) {
            wait(loggerInMemoryDataResult, priority) { result(it) }
            put(GET_YUKI_LOGGER_IN_MEMORY_DATA, packageName)
        }

        // region Deprecated properties and functions for backward compatibility

        /** Maximum data size in bytes that [YukiHookDataChannel] may send before segmenting it. */
        @Deprecated(message = "Use maxDataSizeBytes instead.", replaceWith = ReplaceWith("maxDataSizeBytes"))
        var dataMaxByteSize
            get() = maxDataSizeBytes
            set(value) {
                maxDataSizeBytes = value
            }

        /** Divisor used to calculate the size of each data segment. */
        @Deprecated(message = "Use segmentSizeDivisor instead.", replaceWith = ReplaceWith("segmentSizeDivisor"))
        var dataMaxByteCompressionFactor
            get() = segmentSizeDivisor
            set(value) {
                segmentSizeDivisor = value
            }

        /** Removes the outgoing data-size limit through [allowOversizedData]. */
        @Deprecated(message = "Use allowOversizedData instead.", replaceWith = ReplaceWith("allowOversizedData()"))
        @SendTooLargeChannelData
        fun allowSendTooLargeData() = allowOversizedData()

        /** Checks whether the module and host versions match. */
        @Deprecated(message = "Use checkVersionMatch instead.", replaceWith = ReplaceWith("checkVersionMatch(priority, result)"))
        fun checkingVersionEquals(priority: ChannelPriority? = null, result: (Boolean) -> Unit) = checkVersionMatch(priority, result)

        // endregion

        /**
         * Converts arbitrary received data from an [Intent] into a [ChannelDataWrapper]<[T]> instance.
         * @param key the key name.
         * @return [ChannelDataWrapper]<[T]> or null.
         */
        private fun <T> Intent.getDataWrapper(key: String) = runCatching {
            extras?.apply { classLoader = ModuleClassLoader.get() }
                ?.getParcelableCompat<ChannelDataWrapper<*>>(key + keyNonRepeatName) as? ChannelDataWrapper<T>
        }.getOrNull()

        /**
         * Converts [ChannelData]<[T]> into a [ChannelDataWrapper]<[T]> instance.
         * @param id the wrapper instance ID, [RandomString.generate] by default.
         * @param size the total segmented-data size, -1 by default.
         * @param index the currently received segment index, -1 by default.
         * @return [ChannelDataWrapper]<[T]>
         */
        private fun <T> ChannelData<T>.toWrapper(id: String = RandomString.generate(), size: Int = -1, index: Int = -1) =
            ChannelDataWrapper(id, size > 0, size, index, this)

        /** Measures the payload using the same [Parcelable] wrapper sent in the broadcast. */
        private fun ChannelDataWrapper<*>.calDataByteSize() = Parcel.obtain().use { parcel ->
            parcel.writeValue(this)
            parcel.dataSize()
        }

        /**
         * Splits a collection after measuring each appended element, preserving its segment type.
         *
         * Segments are sent through [Parcel.writeSerializable], so each element is written once into the
         * object stream of its segment and the counted bytes match the serialized segment.
         */
        private inline fun <T, C : Serializable> Iterable<T>.toSegments(
            create: () -> C,
            append: C.(T) -> Unit,
            write: ObjectOutputStream.(T) -> Unit
        ): List<C> {
            val segments = mutableListOf<C>()
            var segment: C? = null
            var counter = ByteCounter()
            var stream: ObjectOutputStream? = null

            forEach { element ->
                val current = segment ?: create().also { segment = it }
                val currentStream = stream ?: ObjectOutputStream(ByteCounter().also { counter = it }).also { stream = it }
                current.append(element)
                currentStream.write(element)
                currentStream.flush()

                if (counter.count >= receiverDataSegmentMaxByteSize) {
                    segments.add(current)
                    segment = null
                    stream = null
                }
            }
            segment?.let(segments::add)

            return segments
        }

        /**
         * Processes received broadcast data.
         * @param wrapper the key-value data wrapper.
         * @param result the result-data callback.
         */
        private fun <T> parseReceivedData(wrapper: ChannelDataWrapper<T>?, result: (value: T) -> Unit) {
            if (!YukiHook.Config.dataChannel) return
            if (wrapper == null) return

            if (!wrapper.isSegmented) {
                wrapper.instance.value?.let(result)
                return
            }

            val merged = runCatching {
                // A transfer that lost segments never completes, discard it once it expires.
                val now = SystemClock.elapsedRealtime()
                segmentsTempData.entries.iterator().apply {
                    while (hasNext()) if (now - next().value.createdAt > SEGMENTS_EXPIRATION_MILLIS) remove()
                }

                val tempData = segmentsTempData[wrapper.wrapperId] ?: SegmentsTempData().apply { segmentsTempData[wrapper.wrapperId] = this }
                when (val value = wrapper.instance.value) {
                    is List<*> -> {
                        val segments = tempData.listData ?: mutableListOf<List<*>>().also { tempData.listData = it }
                        mergeReceivedSegments(wrapper, value, segments) { data ->
                            ArrayList<Any?>(data.sumOf { it.size }).apply {
                                for (segment in data) for (element in segment) add(element)
                            }
                        }
                    }
                    is Map<*, *> -> {
                        val segments = tempData.mapData ?: mutableListOf<Map<*, *>>().also { tempData.mapData = it }
                        mergeReceivedSegments(wrapper, value, segments) { data ->
                            LinkedHashMap<Any?, Any?>(data.sumOf { it.size }).apply {
                                for (segment in data) putAll(segment)
                            }
                        }
                    }
                    is Set<*> -> {
                        val segments = tempData.setData ?: mutableListOf<Set<*>>().also { tempData.setData = it }
                        mergeReceivedSegments(wrapper, value, segments) { data ->
                            LinkedHashSet<Any?>(data.sumOf { it.size }).apply {
                                for (segment in data) addAll(segment)
                            }
                        }
                    }
                    is String -> {
                        val segments = tempData.stringData ?: mutableListOf<String>().also { tempData.stringData = it }
                        mergeReceivedSegments(wrapper, value, segments) { data ->
                            buildString(data.sumOf { it.length }) {
                                for (segment in data) append(segment)
                            }
                        }
                    }
                    else -> {
                        YLog.innerE("Unsupported type for segmented data key \"${wrapper.instance.key}\"")
                        null
                    }
                }
            }.getOrElse {
                segmentsTempData.remove(wrapper.wrapperId)
                return YLog.innerE("YukiHookDataChannel cannot merge segmented data key \"${wrapper.instance.key}\"", it)
            }

            // Run the listener outside the merge, its own failure belongs to the listener.
            @Suppress("UNCHECKED_CAST")
            if (merged != null) result(merged as T)
        }

        /**
         * Merges a complete set of segments and releases it.
         * @return [Any] the merged data, or null while segments are still missing.
         */
        private inline fun <V> mergeReceivedSegments(
            wrapper: ChannelDataWrapper<*>,
            value: V,
            segments: MutableList<V>,
            merge: (List<V>) -> Any
        ): Any? {
            if (segments.isEmpty() && wrapper.segmentIndex > 0) return null

            segments.add(wrapper.segmentIndex, value)
            if (segments.size != wrapper.segmentCount) return null

            return merge(segments).also {
                segments.clear()
                segmentsTempData.remove(wrapper.wrapperId)
            }
        }

        /**
         * Processes broadcast data to send.
         * @param wrapper the key-value data wrapper.
         * @param allowSendTooLargeData whether to allow sending data larger than the system limit.
         */
        private fun parseSendingData(wrapper: ChannelDataWrapper<*>, allowSendTooLargeData: Boolean) {
            if (!YukiHook.Config.dataChannel) return

            // The current wrapper instance ID.
            val wrapperId = RandomString.generate()
            // The size in bytes of the data to send.
            val dataByteSize = runCatching { wrapper.calDataByteSize() }.getOrElse {
                YLog.innerE("YukiHookDataChannel cannot serialize data key \"${wrapper.instance.key}\" for sending", it)
                return
            }

            /**
             * Logs a warning when data is too large. This is effective only when [YukiHook.Config.debug] is enabled.
             * @param name the data type name.
             * @param size the total number of segments.
             */
            fun logForTooLargeData(name: String, size: Int) {
                if (YukiHook.Config.debug) YLog.innerW(
                    msg = "Data key \"${wrapper.instance.key}\" of type $name is too large (total ${dataByteSize / 1024f} KB, " +
                        "limit ${receiverDataMaxByteSize / 1024f} KB) and will be split into $size segments"
                )
            }

            /**
             * Logs an error when data is too large and cannot be segmented.
             * @param suggestionMessage the optional suggestion, empty by default.
             */
            fun logForUnprocessableData(suggestionMessage: String = "") = YLog.innerE(
                msg = "YukiHookDataChannel cannot send data key \"${wrapper.instance.key}\" of type ${wrapper.instance.value?.javaClass} " +
                    "because it is too large (total ${dataByteSize / 1024f} KB, " +
                    "limit ${receiverDataMaxByteSize / 1024f} KB) and cannot be segmented.\n" +
                    (if (suggestionMessage.isNotBlank()) "$suggestionMessage\n" else "") +
                    "To lift this restriction, call allowOversizedData when sending, " +
                    "but this may crash the app"
            )

            /**
             * Logs an error when the first element is too large to segment and the segmented array is empty.
             * @param name the data type name.
             */
            fun logForUnprocessableDataByFirstElement(name: String) = logForUnprocessableData(
                suggestionMessage = "Failed to segment $name because its first element exceeds the size limit"
            )
            when {
                wrapper.isSegmented || allowSendTooLargeData -> sendBroadcast(wrapper)
                dataByteSize >= receiverDataMaxByteSize -> {
                    val name: String
                    val segments = when (val value = wrapper.instance.value) {
                        is List<*> -> {
                            name = "List"
                            value.toSegments(
                                create = { ArrayList<Any?>() },
                                append = { add(it) },
                                write = { writeObject(it) }
                            )
                        }
                        is Map<*, *> -> {
                            name = "Map"
                            value.entries.toSegments(
                                create = { LinkedHashMap<Any?, Any?>() },
                                append = { put(it.key, it.value) },
                                write = {
                                    writeObject(it.key)
                                    writeObject(it.value)
                                }
                            )
                        }
                        is Set<*> -> {
                            name = "Set"
                            value.toSegments(
                                create = { LinkedHashSet<Any?>() },
                                append = { add(it) },
                                write = { writeObject(it) }
                            )
                        }
                        is String -> {
                            // Preserve the existing character boundaries, including the trailing empty segment.
                            val twoByteMaxSize = receiverDataMaxByteSize / 2
                            val segmentCount = value.length / twoByteMaxSize + 1
                            if (segmentCount == 1) return sendBroadcast(wrapper)

                            logForTooLargeData(name = "String", segmentCount)
                            repeat(segmentCount) { index ->
                                val start = index * twoByteMaxSize
                                val segment = value.substring(start, minOf(start + twoByteMaxSize, value.length))
                                sendBroadcast(ChannelData(wrapper.instance.key, segment).toWrapper(wrapperId, segmentCount, index))
                            }
                            return
                        }
                        else -> return logForUnprocessableData(
                            suggestionMessage = if (value?.javaClass?.isArray == true)
                                "Primitive Array type like String[], int[] ... cannot be segmented, " +
                                    "the suggestion is send those data using List type"
                            else ""
                        )
                    }
                    logForTooLargeData(name, segments.size)
                    if (segments.isEmpty()) return logForUnprocessableDataByFirstElement(name)

                    segments.forEachIndexed { index, segment ->
                        sendBroadcast(ChannelData(wrapper.instance.key, segment).toWrapper(wrapperId, segments.size, index))
                    }
                }
                else -> sendBroadcast(wrapper)
            }
        }

        /**
         * Sends a broadcast.
         * @param wrapper the key-value data wrapper.
         */
        private fun sendBroadcast(wrapper: ChannelDataWrapper<*>) {
            (context ?: AppParasitism.currentApplication)?.sendBroadcast {
                action = if (isHostEnvironment) moduleActionName() else hostActionName(packageName)
                setPackage(if (isHostEnvironment) XposedModuleRuntime.modulePackageName else packageName)
                putExtra(wrapper.instance.key + keyNonRepeatName, wrapper)
            } ?: YLog.innerE("Failed to send broadcast for data key \"${wrapper.instance.key}\" because the context of \"$packageName\" is null")
        }
    }
}