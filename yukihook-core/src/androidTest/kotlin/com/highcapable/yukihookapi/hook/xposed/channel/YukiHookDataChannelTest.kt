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
package com.highcapable.yukihookapi.hook.xposed.channel

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.highcapable.yukihookapi.hook.xposed.channel.data.ChannelData
import com.highcapable.yukihookapi.hook.xposed.channel.priority.ChannelPriority
import org.junit.Assert
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Checks which public data channel listeners receive a broadcast as their owners change.
 */
@RunWith(AndroidJUnit4::class)
class YukiHookDataChannelTest {

    private companion object {
        const val HOST = "com.example.host"
        const val OTHER_HOST = "com.example.otherhost"
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext

    @Test
    fun routesToTheResumedInstanceAndReplacesItsDuplicateListener() {
        val key = UUID.randomUUID().toString()
        val received = mutableListOf<String>()

        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            val second = awaitFragmentActivityResume {
                scenario.onActivity { first ->
                    first.dataChannel(HOST).wait<String>(key) { received += "replaced:$it" }
                    ContextWrapper(first).dataChannel(HOST).wait<String>(key) { received += "first:$it" }
                    first.dataChannel(OTHER_HOST).wait<String>(key) { received += "other:$it" }
                    first.startActivity(Intent(first, FragmentActivity::class.java))
                }
            }
            try {
                instrumentation.runOnMainSync {
                    second.dataChannel(HOST).wait<String>(key) { received += "second:$it" }
                }

                broadcast { it.dataChannel(HOST).put(key, "visible") }
                awaitFragmentActivityResume { instrumentation.runOnMainSync { second.finish() } }
                broadcast { it.dataChannel(HOST).put(key, "back") }
                broadcast { it.dataChannel(OTHER_HOST).put(key, "isolated") }

                Assert.assertEquals(listOf("second:visible", "first:back", "other:isolated"), received)
            } finally {
                instrumentation.runOnMainSync { second.finish() }
            }
        }
    }

    @Test
    fun sendsASnapshotOfMutableSerializableData() {
        val key = UUID.randomUUID().toString()
        val payload = arrayListOf("sent")
        val received = mutableListOf<List<String>>()

        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity { it.dataChannel(HOST).wait<List<String>>(key) { value -> received += value } }
            val outgoing = captureBroadcast { it.dataChannel(HOST).put(key, payload) }
            payload[0] = "changed after sending"

            deliverBroadcast(outgoing)

            Assert.assertEquals(listOf(listOf("sent")), received)
        }
    }

    @Test
    fun sendsASnapshotOfCustomParcelableDataWithABinder() {
        val key = UUID.randomUUID().toString()
        val binder = Binder()
        val payload = TransportPayload("sent", binder)
        val received = mutableListOf<String>()
        val binders = mutableListOf<IBinder?>()

        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.dataChannel(HOST).wait<TransportPayload>(key) { value ->
                    received += value.text
                    binders += value.binder
                    value.text = "changed by listener"
                }
            }
            val outgoing = captureBroadcast { it.dataChannel(HOST).put(key, payload) }
            payload.text = "changed after sending"

            deliverBroadcast(outgoing)
            deliverBroadcast(outgoing)

            Assert.assertEquals(listOf("sent", "sent"), received)
            Assert.assertEquals(listOf(binder, binder), binders)
        }
    }

    @Test
    fun receivesHostMessageWhileTheModuleTaskIsInTheBackground() {
        val data = ChannelData<String>(UUID.randomUUID().toString())
        val received = mutableListOf<String>()
        val stopped = CountDownLatch(1)

        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.dataChannel(HOST).wait(data) { received += it }
                activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) stopped.countDown()
                })
                Assert.assertTrue(activity.moveTaskToBack(true))
            }
            Assert.assertTrue("The module activity did not stop", stopped.await(5, TimeUnit.SECONDS))

            broadcast { it.dataChannel(HOST).put(data, "from host") }

            Assert.assertEquals(listOf("from host"), received)
        }
    }

    @Test
    fun pausesActivityDeliveryWhileServiceKeepsReceivingAndDropsTheRecreatedInstance() {
        val data = ChannelData<String>(UUID.randomUUID().toString())
        val received = mutableListOf<String>()
        val service = object : Service() {

            init {
                attachBaseContext(this@YukiHookDataChannelTest.application)
            }

            override fun onBind(intent: Intent?) = null
        }
        service.dataChannel(HOST).wait(data) { received += "service:$it" }

        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity { it.dataChannel(HOST).wait(data) { value -> received += "before:$value" } }

            scenario.moveToState(Lifecycle.State.STARTED)
            broadcast { it.dataChannel(HOST).put(data, "paused") }
            scenario.moveToState(Lifecycle.State.RESUMED)
            broadcast { it.dataChannel(HOST).put(data, "resumed") }
            scenario.recreate()
            scenario.onActivity { it.dataChannel(HOST).wait(data) { value -> received += "after:$value" } }
            broadcast { it.dataChannel(HOST).put(data, "recreated") }

            Assert.assertEquals(
                listOf("service:paused", "service:resumed", "before:resumed", "service:recreated", "after:recreated").sorted(),
                received.sorted()
            )
        }
    }

    @Test
    fun fragmentChannelRejectsListenerRegistrationOffTheMainThread() {
        val fragment = ListeningFragment()
        lateinit var channel: YukiHookDataChannel.Namespace

        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.supportFragmentManager.beginTransaction().add(fragment, "thread-check").commitNow()
                channel = fragment.dataChannel(HOST)
            }

            Assert.assertThrows(IllegalStateException::class.java) { channel.wait("thread-check") {} }
        }
    }

    @Test
    fun observersInTheSameActivityReceiveIndependentlyUntilTheirViewsAreDestroyed() {
        val key = UUID.randomUUID().toString()
        val received = mutableListOf<String>()
        var allowSecond = true
        val first = ListeningFragment().apply { register = { dataChannel(HOST).wait(key) { received += "first" } } }
        val second = ListeningFragment().apply {
            register = { dataChannel(HOST).wait(key, ChannelPriority { allowSecond }) { received += "second" } }
        }

        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity { it.supportFragmentManager.beginTransaction().add(first, "first").add(second, "second").commitNow() }
            broadcast { it.dataChannel(HOST).put(key) }
            Assert.assertEquals(listOf("first", "second"), received.sorted())
            received.clear()

            scenario.onActivity {
                it.supportFragmentManager.beginTransaction().detach(first).commitNow()
                allowSecond = false
            }
            broadcast { it.dataChannel(HOST).put(key) }
            Assert.assertTrue(received.isEmpty())

            scenario.onActivity {
                allowSecond = true
                it.supportFragmentManager.beginTransaction().attach(first).commitNow()
            }
            broadcast { it.dataChannel(HOST).put(key) }
            Assert.assertEquals(listOf("first", "second"), received.sorted())
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun deliversToBothResumedInstancesInSplitScreen() {
        Assume.assumeTrue(shell("wm shell help").contains("moveToSideStage"))
        val key = UUID.randomUUID().toString()
        val received = mutableListOf<Int>()
        var firstTask = 0

        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            val second = awaitFragmentActivityResume {
                scenario.onActivity {
                    firstTask = it.taskId
                    it.startActivity(Intent(it, FragmentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
                }
            }
            val secondTask = second.taskId
            try {
                shell("wm shell splitscreen moveToSideStage $firstTask 1")
                val tasks = setOf(firstTask, secondTask)
                val activities = awaitSplitScreenActivities(tasks)
                instrumentation.runOnMainSync {
                    activities.forEach { activity -> activity.dataChannel(HOST).wait(key) { received += activity.taskId } }
                }

                broadcast { it.dataChannel(HOST).put(key) }

                Assert.assertEquals(tasks.sorted(), received.sorted())
            } finally {
                shell("wm shell splitscreen exitSplitScreen $firstTask")
                instrumentation.runOnMainSync { second.finishAndRemoveTask() }
            }
        }
    }

    /**
     * Mutable data whose Binder must survive the broadcast's Parcelable transport.
     */
    class TransportPayload(var text: String, val binder: IBinder?) : Parcelable {

        companion object {

            @JvmField
            val CREATOR = object : Parcelable.Creator<TransportPayload> {
                override fun createFromParcel(source: Parcel) = TransportPayload(checkNotNull(source.readString()), source.readStrongBinder())
                override fun newArray(size: Int) = arrayOfNulls<TransportPayload>(size)
            }
        }

        override fun describeContents() = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeString(text)
            dest.writeStrongBinder(binder)
        }
    }

    /**
     * A view-owning fragment that registers its listener each time the view is created.
     */
    class ListeningFragment : Fragment() {

        /** The listener registration to run from [onViewCreated]. */
        var register: (() -> Unit)? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = View(requireContext())

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            register?.invoke()
        }
    }

    private fun awaitFragmentActivityResume(trigger: () -> Unit): FragmentActivity {
        val resumed = ArrayBlockingQueue<FragmentActivity>(1)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is FragmentActivity && stage == Stage.RESUMED) resumed.offer(activity)
        }
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            trigger()
            return checkNotNull(resumed.poll(5, TimeUnit.SECONDS)) { "FragmentActivity did not resume" }
        } finally {
            instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) }
        }
    }

    private fun awaitSplitScreenActivities(taskIds: Set<Int>): List<FragmentActivity> {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            var activities = emptyList<FragmentActivity>()
            instrumentation.runOnMainSync {
                activities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FragmentActivity>().filter { it.taskId in taskIds && it.isInMultiWindowMode }
            }
            if (activities.size == taskIds.size) return activities
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("Both activities did not resume in split-screen mode")
    }

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }

    private fun captureBroadcast(send: (Context) -> Unit): Intent {
        var outgoing: Intent? = null
        val sender = object : ContextWrapper(application) {

            override fun sendBroadcast(intent: Intent, receiverPermission: String?, options: Bundle?) {
                outgoing = Intent(intent)
            }
        }
        send(sender)
        return checkNotNull(outgoing) { "The data channel did not send a broadcast" }
    }

    private fun deliverBroadcast(intent: Intent) {
        val delivered = CountDownLatch(1)
        val reply = Intent(intent).setAction("yuki_hook_module_data_channel_${application.packageName.hashCode()}")
            .setPackage(application.packageName)
        // Keep the payload from put and route it back through Android's broadcast receiver.
        application.sendOrderedBroadcast(reply, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                delivered.countDown()
            }
        }, null, 0, null, null)
        Assert.assertTrue("The data channel broadcast did not finish", delivered.await(5, TimeUnit.SECONDS))
    }

    private fun broadcast(send: (Context) -> Unit) = deliverBroadcast(captureBroadcast(send))
}