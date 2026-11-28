# Module and Host Channel

> This is a solution that uses unordered system broadcasts to send and receive data between the module and hosts.

::: danger Conditions that need to be met

The module and the host need to stay alive, otherwise the channel cannot be established.

The `Application` of the module needs to inherit `ModuleApplication`, and `YukiHook.Config.dataChannel` needs to be enabled (enabled by default).

:::

## Basic Usage

Here describes the basic usage of the `wait` and `put` functions.

Use `dataChannel` to create a channel between the module and hosts, it works by sending and receiving unordered system broadcasts.

> Module example

```kotlin
// Get from the host with the given package name.
dataChannel("io.github.myapp").wait<String>("key-from-host") { value ->
    // Your code here.
}
// Send to the host with the given package name.
dataChannel("io.github.myapp").put("key-from-module", "I am module")
```

> Host example

```kotlin
// Get from the module.
dataChannel.wait<String>("key-from-module") { value ->
    // Your code here.
}
// Send to the module.
dataChannel.put("key-from-host", "I am host")
```

You can omit the `value` of `dataChannel` to only notify the `wait` callback of the module or host.

> Module example

```kotlin
// Get from the host with the given package name.
dataChannel("io.github.myapp").wait("listening-from-host") {
    // Your code here.
}
// Send to the host with the given package name.
dataChannel("io.github.myapp").put("listening-from-module")
```

> Host example

```kotlin
// Get from the module.
dataChannel.wait("listening-from-module") {
    // Your code here.
}
// Send to the module.
dataChannel.put("listening-from-host")
```

To communicate with the system framework, use `systemDataChannel` in the module, and use `dataChannel` inside `loadSystem` in the host.

> The following example

```kotlin
systemDataChannel().put("key-from-module", "I am module")
```

::: warning

Passing `android` or `system` to `dataChannel` is treated as `systemDataChannel`, please use `systemDataChannel` directly.

:::

If you need to send a lot of fixed data, it is recommended to create templates with `ChannelData`.

> The following example

```kotlin
object MyData {
    val Foo = ChannelData<String>("foo")
}
```

> The following example

```kotlin
// Send data.
dataChannel("io.github.myapp").put(MyData.Foo, "I am module")
// Receive data.
dataChannel.wait(MyData.Foo) { value ->
    // Your code here.
}
```

## Check Version Match

YukiHook provides an instant solution for checking whether the module matches the host version after the user updates the module.

Just call the `checkVersionMatch` function to do it.

The check works in both directions between the module and the host.

In the module, you can check whether the host with the given package name matches the current module version.

> The following example

```kotlin
// Get from the host with the given package name.
dataChannel("io.github.myapp").checkVersionMatch { isMatched ->
    // Your code here.
}
```

In the host, you can also check whether it matches the current module version.

> The following example

```kotlin
// Get from the module.
dataChannel.checkVersionMatch { isMatched ->
    // Your code here.
}
```

::: warning

If the conditions are not met, or the module has been deactivated without restarting the host, the callback will never be triggered.

:::

## Get Host Logs

You can get the debug logs recorded in a host from the module.

> The following example

```kotlin
dataChannel("io.github.myapp").obtainLoggerInMemoryData { data ->
    // Your code here.
}
```

::: warning

Both the module and the host need to enable debug log recording, for details please refer to [Debug Logs → Save Logs](./logger.md#save-logs).

:::

## Respond to Callback Events

The examples below run in the module. **Different hosts** can use the same `key`.

::: danger

Within the same listener scope, registering a callback of the same kind for the same host and `key` replaces the earlier callback. In the module, each `Activity` instance has its own scope. Other contexts use a process-wide scope by default.

:::

> The following example

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Callback event A.
        dataChannel("io.github.myapp1").wait("foo") {
            // Your code here.
        }
        // Callback event B.
        dataChannel("io.github.myapp1").wait("foo") {
            // Your code here.
        }
        // Callback event C.
        dataChannel("io.github.myapp1").wait("bar") {
            // Your code here.
        }
        // Callback event D.
        dataChannel("io.github.myapp2").wait("bar") {
            // Your code here.
        }
    }
}

class OtherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Callback event E.
        dataChannel("io.github.myapp1").wait("foo") {
            // Your code here.
        }
        // Callback event F.
        dataChannel("io.github.myapp2").wait("foo") {
            // Your code here.
        }
    }
}
```

In the example above, callback events A and B belong to the same host and have the same `key`, so callback event A is replaced by callback event B.

The `key` of callback event C is not repeated. Callback event D has the same `key` as C, but they belong to different hosts, so they can exist at the same time.

Callback event E is in another `Activity`, and callback event F has the same `key` as E, but they do not belong to the same host either, so they can exist at the same time.

In summary, callback events B, C, D, E and F can all be created.

::: danger

In each task, the module `Activity` that most recently resumed can receive matching broadcasts, even after the task moves to the background.
When another module `Activity` resumes in the same task, the previous instance stops receiving until it resumes again.
Separate tasks, including those in split-screen, receive independently. Destroying an instance removes its listeners.

Listeners registered from an `Application`, `Service`, or other context without an `Activity` can receive matching broadcasts while the process is alive.
Use `ChannelPriority` to set additional callback conditions.

Call `dataChannel(packageName)` directly in `Fragment.onViewCreated` to let different fragments in the same activity listen for the same `key`.
Obtain the channel and register listeners on the main thread. Listeners are removed when the fragment's view is destroyed.

:::

If you want to set the response priority (condition) of each callback event in the same `Activity` manually, you can use `ChannelPriority`.

For example, when one `Activity` hosts multiple `Fragment` instances, this solves the problem.

> The following example

```kotlin
class MainFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        dataChannel("io.github.myapp1")
            .wait("foo", ChannelPriority { isResumed && isVisible }) {
                // Your code here.
            }
    }
}
```

## Data Size Limits

Android limits the size of data a broadcast can carry. YukiHook splits `List`, `Map`, `Set` and `String` data into segments automatically when the data exceeds `maxDataSizeBytes` (500 KB by default).

> The following example

```kotlin
dataChannel("io.github.myapp").with {
    // Adjust the threshold for segmented sending, the minimum is 100 KB.
    maxDataSizeBytes = 500 * 1024
    // Adjust the divisor for the size of each segment, the minimum is 2.
    segmentSizeDivisor = 3
}
```

::: danger

Adjust these values carefully, exceeding the system limit causes a `TransactionTooLargeException`.

You can also use `allowOversizedData` to remove the data size limit, do not use it unless you understand the consequences.

:::

## Security Notes

YukiHook specifies the package names of the host and the module automatically to send data to the given app.

On Android 14 and above, YukiHook verifies the identity of the broadcasting app. Hosts can only receive data from the module,
and the module can only receive data from the host with the corresponding package name.
Broadcasts with an unavailable or mismatched sender identity are ignored.

::: danger

Below Android 14, this identity check is not supported. Specifying the receiving package does not prevent other apps from sending forged data, do not perform sensitive operations or return sensitive data based only on data channel messages.

:::