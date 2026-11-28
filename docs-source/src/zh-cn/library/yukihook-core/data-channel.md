# 模块与宿主通讯桥

> 这是一个使用系统无序广播在模块与宿主之间发送和接收数据的解决方案。

::: danger 需要满足的条件

模块与宿主均需要保持存活状态，否则无法建立通讯。

模块的 `Application` 需要继承于 `ModuleApplication`，且 `YukiHook.Config.dataChannel` 处于启用状态 (默认启用)。

:::

## 基本用法

这里描述了 `wait` 与 `put` 方法的基本使用方法。

通过使用 `dataChannel` 来实现模块与宿主之间的通讯桥，原理为发送接收系统无序广播。

> 示例如下 (模块)

```kotlin
// 从指定包名的宿主获取
dataChannel("io.github.myapp").wait<String>("key-from-host") { value ->
    // Your code here.
}
// 发送给指定包名的宿主
dataChannel("io.github.myapp").put("key-from-module", "I am module")
```

> 示例如下 (宿主)

```kotlin
// 从模块获取
dataChannel.wait<String>("key-from-module") { value ->
    // Your code here.
}
// 发送给模块
dataChannel.put("key-from-host", "I am host")
```

你可以不设置 `dataChannel` 的 `value` 来达到仅通知模块或宿主回调 `wait` 方法。

> 示例如下 (模块)

```kotlin
// 从指定包名的宿主获取
dataChannel("io.github.myapp").wait("listening-from-host") {
    // Your code here.
}
// 发送给指定包名的宿主
dataChannel("io.github.myapp").put("listening-from-module")
```

> 示例如下 (宿主)

```kotlin
// 从模块获取
dataChannel.wait("listening-from-module") {
    // Your code here.
}
// 发送给模块
dataChannel.put("listening-from-host")
```

若你需要与系统框架通讯，请在模块中使用 `systemDataChannel`，在宿主中需要在 `loadSystem` 内使用 `dataChannel`。

> 示例如下

```kotlin
systemDataChannel().put("key-from-module", "I am module")
```

::: warning

在 `dataChannel` 中传入 `android` 或 `system` 时将会被视为 `systemDataChannel`，请直接使用 `systemDataChannel`。

:::

若你需要发送大量的固定数据，推荐使用 `ChannelData` 来创建模板。

> 示例如下

```kotlin
object MyData {
    val Foo = ChannelData<String>("foo")
}
```

> 示例如下

```kotlin
// 发送数据
dataChannel("io.github.myapp").put(MyData.Foo, "I am module")
// 接收数据
dataChannel.wait(MyData.Foo) { value ->
    // Your code here.
}
```

## 检查版本匹配

YukiHook 为你提供了在用户更新模块后，判断模块是否与宿主版本匹配的即时解决方案。

你只需要调用 `checkVersionMatch` 方法，即可实现这个功能。

在模块与宿主中可进行双向判断。

你可以在模块中判断指定包名的宿主是否与当前模块的版本匹配。

> 示例如下

```kotlin
// 从指定包名的宿主获取
dataChannel("io.github.myapp").checkVersionMatch { isMatched ->
    // Your code here.
}
```

你还可以在宿主中判断是否自身与当前模块的版本匹配。

> 示例如下

```kotlin
// 从模块获取
dataChannel.checkVersionMatch { isMatched ->
    // Your code here.
}
```

::: warning

如果不满足使用条件，或模块单方面取消了激活且没有重启宿主，那么回调将永远不会被触发。

:::

## 获取宿主日志

你可以在模块中获取宿主中记录的调试日志。

> 示例如下

```kotlin
dataChannel("io.github.myapp").obtainLoggerInMemoryData { data ->
    // Your code here.
}
```

::: warning

模块与宿主需要同时启用调试日志功能，详情请参考 [调试日志 → 保存日志](./logger.md#保存日志)。

:::

## 响应回调事件

这里只列出了在模块中使用的例子。**不同的宿主**可以使用相同的 `key`。

::: danger

同一个宿主、同一个 `key` 的同类回调在同一监听范围内重复注册时，后注册的回调会替换先前的回调。模块中的每个 `Activity` 实例都有各自的监听范围，其余 `Context` 默认使用进程级的监听范围。

:::

> 示例如下

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 回调事件 A
        dataChannel("io.github.myapp1").wait("foo") {
            // Your code here.
        }
        // 回调事件 B
        dataChannel("io.github.myapp1").wait("foo") {
            // Your code here.
        }
        // 回调事件 C
        dataChannel("io.github.myapp1").wait("bar") {
            // Your code here.
        }
        // 回调事件 D
        dataChannel("io.github.myapp2").wait("bar") {
            // Your code here.
        }
    }
}

class OtherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 回调事件 E
        dataChannel("io.github.myapp1").wait("foo") {
            // Your code here.
        }
        // 回调事件 F
        dataChannel("io.github.myapp2").wait("foo") {
            // Your code here.
        }
    }
}
```

在上述示例中，虽然回调事件 A 与 B 是同一个宿主中的回调事件，但是它们的 `key` 相同，所以回调事件 A 会被回调事件 B 替换掉。

回调事件 C 的 `key` 不与其他重复，虽然回调事件 D 的 `key` 与回调事件 C 相同，但是它们的宿主不同，所以可以同时存在。

回调事件 E 在另一个 `Activity` 中，回调事件 F 与回调事件 E 的 `key` 虽然相同，但它们也不是同一个宿主，所以可以同时存在。

综上所述，最终回调事件 B、C、D、E、F 都可被创建成功。

::: danger

每个任务中，最近一次进入 `RESUMED` 状态的模块 `Activity` 可以接收匹配的广播，即使任务已退到后台。同一任务切换到另一个模块 `Activity` 后，原实例不再接收，直到它再次进入 `RESUMED` 状态。不同任务（包括分屏中的任务）可以各自接收。实例销毁时会移除其监听。

在模块中，`Application`、`Service` 等不关联 `Activity` 的监听可在进程存活时接收匹配的广播，仍可通过 `ChannelPriority` 设置回调条件。

在 `Fragment` 的 `onViewCreated` 中直接使用 `dataChannel(packageName)`，同一 `Activity` 中的不同 `Fragment` 就可以各自监听相同的 `key`。请在主线程获取通道并注册监听，视图销毁时会移除对应监听。

:::

如果你希望在同一个 `Activity` 中手动设置每个回调事件的响应优先级 (条件)，你可以使用 `ChannelPriority`。

例如，你正在使用一个 `Activity` 绑定多个 `Fragment` 的情况，这将能够解决这个问题。

> 示例如下

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

## 数据大小限制

Android 系统对广播能够传输的数据大小存在限制，YukiHook 会在数据超过 `maxDataSizeBytes` (默认为 500 KB) 时自动对 `List`、`Map`、`Set` 以及 `String` 类型的数据进行分段发送。

> 示例如下

```kotlin
dataChannel("io.github.myapp").with {
    // 调整分段发送的数据大小阈值，最小为 100 KB
    maxDataSizeBytes = 500 * 1024
    // 调整每段数据大小的除数，最小为 2
    segmentSizeDivisor = 3
}
```

::: danger

请谨慎调整这些数值，超出系统限制将会造成 `TransactionTooLargeException` 异常。

你还可以使用 `allowOversizedData` 取消数据大小限制，除非你了解这样做的后果，否则请不要使用此功能。

:::

## 安全性说明

YukiHook 会自动指定宿主和模块的包名，将通讯数据发送给指定的应用。

在 Android 14 及以上系统中，YukiHook 会校验广播发送方的身份，宿主只能接收模块发送的通讯数据，模块只能接收对应包名的宿主发送的通讯数据，无法获取发送方身份或身份不匹配的广播将被忽略。

::: danger

低于 Android 14 的系统不支持身份校验，指定接收包名并不能阻止其他应用伪造通讯数据，此时请勿执行敏感操作或返回敏感数据。

:::