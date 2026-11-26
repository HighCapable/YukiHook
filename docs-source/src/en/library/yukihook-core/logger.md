# Debug Logs

> Logs are the most important part of debugging, `YukiHook` provides a stable and efficient debug log feature for developers.

::: tip

If you are using KavaRef, its logs can be managed by KavaRef itself, for the configuration you can refer to [here](https://highcapable.github.io/KavaRef/en/library/kavaref-core#log-management),
this will jump to the KavaRef documentation.

YukiHook takes over the logging of KavaRef by default, you can also configure the logging of KavaRef yourself.

:::

## Log Configuration

You can configure logging with `logging` in `onInit` of the entry class.

> The following example

```kotlin
override fun onInit() = configure {
    logging {
        // The TAG for global debugging, filter this TAG in the Logcat console to find detailed logs.
        tag = "YourModule"
        // Minimum log level, INFO by default.
        level = YLog.Level.DEBUG
        // Whether to enable debug log recording, disabled by default.
        // When enabled, logs that pass level and their exception stacks are recorded in memory.
        recording = false
        // Customize the elements displayed in debug logs.
        elements(TAG, PRIORITY, PROCESS_NAME, USER_ID)
    }
}
```

`level` sets the minimum level of logs to output and record. It defaults to `INFO`, with the following options.

| Level   | Logs allowed                        |
| ------- | ----------------------------------- |
| `DEBUG` | `DEBUG`, `INFO`, `WARN` and `ERROR` |
| `INFO`  | `INFO`, `WARN` and `ERROR`          |
| `WARN`  | `WARN` and `ERROR`                  |
| `ERROR` | `ERROR` only                        |
| `OFF`   | No output or recording              |

`elements` only applies to recorded logs and logs in the host environment. Log elements are displayed in the order you set, leave it empty to hide all elements except the log message.

## Normal Logs

You can call `YLog.debug`, `YLog.info` and `YLog.warn` to print normal logs to the console.

> The following example

```kotlin
YLog.debug("This is a log")
```

YukiHook then prints this log with both `android.util.Log` and the log function of the host environment.

The default `TAG` of the log is the value you set in `YLog.Config.tag`. You can also customize it dynamically, but it is not recommended to change the `TAG` easily so that logs can still be filtered.

> The following example

```kotlin
YLog.debug("This is a log", tag = "YukiHook")
```

The printed result is as follows.

> The following example

```text:no-line-numbers
[YukiHook][D][process name] This is a log
```

You can also use `YLog.EnvType` to customize where the log is printed.

| Type     | Description                                                                                                                  |
| -------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `BOTH`   | The default type, the host environment uses both `android.util.Log` and the log function of the host environment             |
| `LOGD`   | Uses only `android.util.Log`                                                                                                 |
| `XPOSED` | Uses only the log function of the host environment, has no effect in the module environment                                  |
| `SCOPE`  | The host environment uses only the log function of the host environment, the module environment uses only `android.util.Log` |

> The following example

```kotlin
YLog.debug("This is a log", env = YLog.EnvType.LOGD)
```

## Error Logs

You can call `YLog.error` to print `E` level logs to the console.

> The following example

```kotlin
YLog.error("This is an error")
```

You can also add an exception stack after it.

> The following example

```kotlin
// Suppose this is the thrown exception.
val throwable = Throwable(...)
// Print the log.
YLog.error("This is an error", e = throwable)
```

The printed result is as follows.

> The following example

```text:no-line-numbers
[YukiHook][E][process name] This is an error
```

The log also prints the whole exception stack for you.

> The following example

```text:no-line-numbers
java.lang.Throwable
    at com.demo.Test.<init>(...)
    at com.demo.Test.doTask(...)
    at com.demo.Test.stop(...)
    at com.demo.Test.init(...)
    at a.a.a(...)
    ... 3 more
```

In error logs, you can also use `YLog.EnvType` to decide where the log is printed.

## Save Logs

After enabling `recording`, you can use `YLog.saveToFile` to save all recorded logs to a file directly.

> The following example

```kotlin
YLog.saveToFile("/sdcard/Documents/debug_log.log")
```

You can also use `YLog.contents` to get the contents of all recorded logs.

> The following example

```kotlin
val fileContent = YLog.contents
```

If you need the log data as a structured array in real time, you can get the content of `YLog.inMemoryData` directly.

> The following example

```kotlin
YLog.inMemoryData.forEach {
    it.timestamp // Get the timestamp.
    it.time // Get the UTC time.
    it.priority // Get the priority.
    it.msg // Get the message.
    it.throwable // Get the exception.
    // ...
}
```

If you want to format custom log data or save it to a file, just use the following functions.

> The following example

```kotlin
// Suppose this is the custom log data you got.
val data: List<YLogData>
// Format the log data to a string.
val dataString = YLog.contents(data)
// Save the log data to a file.
YLog.saveToFile("/sdcard/Documents/debug_log.log", data)
```

::: tip

If you need to get the log data of the host from the module in real time, please refer to [Module and Host Channel → Get Host Logs](./data-channel.md#get-host-logs).

:::

::: danger

The saved file path must be readable and writable in the current environment, otherwise an exception is thrown.

You need to enable `recording` to get the content of `YLog.inMemoryData`.

The log data is isolated between the host and module processes, you can only get the log data of the process you are in.

:::