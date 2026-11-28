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
 * This file is created by fankes on 2026/11/19.
 */
package com.highcapable.yukihookapi.hook.xposed.channel.lifecycle

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.highcapable.betterandroid.ui.extension.component.LifecycleEventObserver
import com.highcapable.betterandroid.ui.extension.component.hostActivity
import com.highcapable.yukihookapi.hook.log.YLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the lifetime of data channel registrations and delivers broadcasts to active owners.
 * @param isHost whether the current process is the host process.
 */
internal class ChannelLifecycle(private val isHost: Boolean) : Application.ActivityLifecycleCallbacks {

    /**
     * Distinguishes the forms for the same key.
     */
    enum class Kind {
        VALUE,
        TYPED_VALUE,
        NOTIFICATION
    }

    private class Key(
        val packageName: String,
        val name: String,
        val kind: Kind,
        val activity: Activity?,
        val lifecycle: Lifecycle?
    ) {
        override fun equals(other: Any?) = other is Key && packageName == other.packageName && name == other.name &&
            kind == other.kind && activity === other.activity && lifecycle === other.lifecycle

        override fun hashCode(): Int {
            var hash = packageName.hashCode()
            hash = 31 * hash + name.hashCode()
            hash = 31 * hash + kind.hashCode()
            hash = 31 * hash + System.identityHashCode(activity)
            return 31 * hash + System.identityHashCode(lifecycle)
        }
    }

    private class Listener(val activity: Activity?, val receive: (String, Intent, String?) -> Unit) {
        var observer: LifecycleEventObserver? = null
    }

    private val listeners = ConcurrentHashMap<Key, Listener>()

    // Backgrounding a task does not replace its last resumed module activity.
    private val lastResumedByTask = mutableMapOf<Int, Activity>()

    /**
     * Registers a listener, replacing an existing listener for the same key and scope.
     * @param context the context to use for the listener, or null if not needed.
     * @param packageName the package name of the module or host.
     * @param lifecycle the lifecycle to bind the listener to, or null if not needed.
     * @param name the name of the listener.
     * @param kind the kind of listener.
     * @param receive the callback receiving the action, intent and verified sender package, which is null on older systems.
     */
    fun register(
        context: Context?,
        packageName: String,
        lifecycle: Lifecycle?,
        name: String,
        kind: Kind,
        receive: (String, Intent, String?) -> Unit
    ) {
        if (lifecycle != null) {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "Register lifecycle-bound data channel listeners on the main thread."
            }
            if (lifecycle.currentState == Lifecycle.State.DESTROYED) return
        }

        val activity = context?.hostActivity
        val key = Key(packageName, name, kind, if (isHost) null else activity, lifecycle)
        val listener = Listener(activity, receive)
        val previous = synchronized(listeners) {
            if (activity?.isDestroyed == true) return
            listeners.put(key, listener)
        }
        previous?.observer?.let { lifecycle?.removeObserver(it) }
        if (lifecycle != null) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) remove(key, listener)
            }
            listener.observer = observer
            lifecycle.addObserver(observer)
        }
    }

    /**
     * Delivers the broadcast to eligible listeners and handles failures according to the process.
     * @param action the action of the broadcast.
     * @param intent the intent of the broadcast.
     * @param senderPackage the verified sender package, or null on systems without sender identity support.
     */
    fun dispatch(action: String, intent: Intent, senderPackage: String?) {
        var failure: Throwable? = null
        listeners.forEach { (key, listener) ->
            runCatching {
                val activity = listener.activity
                when {
                    activity?.isDestroyed == true || key.lifecycle?.currentState == Lifecycle.State.DESTROYED -> remove(key, listener)
                    activity == null || isHost || lastResumedByTask.values.any { it === activity } -> listener.receive(action, intent, senderPackage)
                }
            }.onFailure {
                // One failing listener must not stop the others. Module listener failures must not crash the host.
                if (isHost) YLog.error(
                    "The module's data channel listener \"${key.name}\" for \"${key.packageName}\" " +
                        "failed to handle action \"$action\"", it
                )
                else if (failure == null) failure = it
                else failure.addSuppressed(it)
            }
        }
        failure?.let { throw it }
    }

    /** Removes all listeners and clears the tracked [Activity] state. */
    fun clear() {
        lastResumedByTask.clear()
        synchronized(listeners) {
            listeners.forEach { (key, listener) -> remove(key, listener) }
        }
    }

    private fun remove(key: Key, listener: Listener) {
        listeners.remove(key, listener)
        listener.observer?.let { key.lifecycle?.removeObserver(it) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        lastResumedByTask[activity.taskId] = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        lastResumedByTask.values.removeAll { it === activity }
        synchronized(listeners) {
            listeners.forEach { (key, listener) -> if (listener.activity === activity) remove(key, listener) }
        }
    }
}