# 调试日志

> 日志是调试过程最重要的一环，`YukiHook` 为开发者封装了一套稳定高效的调试日志功能。

::: tip

如果你正在使用 KavaRef，它的日志可以由其自身单独管理，详细的配置方案你可以参考 [这里](https://highcapable.github.io/KavaRef/zh-cn/library/kavaref-core#log-management)，这将跳转到 KavaRef 的文档。

YukiHook 默认接管了 KavaRef 的日志功能，你也可以自己配置 KavaRef 的日志功能。

:::

## 日志配置

你可以在入口类的 `onInit` 中使用 `logging` 对日志功能进行配置。

> 示例如下

```kotlin
override fun onInit() = configure {
    logging {
        // 全局调试用的 TAG，在 Logcat 控制台过滤此 TAG 可找到详细日志
        tag = "YourModule"
        // 最小日志级别，默认为 INFO
        level = YLog.Level.DEBUG
        // 是否启用调试日志的记录功能，默认不启用
        // 开启后将会在内存中记录通过日志级别过滤的日志和异常堆栈
        recording = false
        // 自定义调试日志对外显示的元素
        elements(TAG, PRIORITY, PROCESS_NAME, USER_ID)
    }
}
```

`level` 用于设置日志输出与记录的最小级别，默认为 `INFO`，可选值如下。

| 级别    | 允许的日志                         |
| ------- | ---------------------------------- |
| `DEBUG` | `DEBUG`、`INFO`、`WARN` 和 `ERROR` |
| `INFO`  | `INFO`、`WARN` 和 `ERROR`          |
| `WARN`  | `WARN` 和 `ERROR`                  |
| `ERROR` | 仅 `ERROR`                         |
| `OFF`   | 不输出或记录任何日志               |

`elements` 只对记录的日志以及宿主环境中的日志生效，日志元素的排列将按照你设置的顺序进行显示，留空时将不显示除日志内容外的全部元素。

## 普通日志

你可以调用 `YLog.debug`、`YLog.info`、`YLog.warn` 来向控制台打印普通日志。

> 示例如下

```kotlin
YLog.debug("This is a log")
```

此时，YukiHook 会调用 `android.util.Log` 与宿主环境中的日志功能同时打印这条日志。

日志默认的 `TAG` 为你在 `YLog.Config.tag` 中设置的值，你也可以动态自定义这个值，但是不建议随意修改 `TAG` 防止过滤不到日志。

> 示例如下

```kotlin
YLog.debug("This is a log", tag = "YukiHook")
```

打印的结果为如下所示。

> 示例如下

```text:no-line-numbers
[YukiHook][D][进程名] This is a log
```

你还可以使用 `YLog.EnvType` 自定义日志打印的环境。

| 类型     | 说明                                                                |
| -------- | ------------------------------------------------------------------- |
| `BOTH`   | 默认类型，宿主环境同时使用 `android.util.Log` 与宿主环境的日志功能  |
| `LOGD`   | 仅使用 `android.util.Log`                                           |
| `XPOSED` | 仅使用宿主环境的日志功能，在模块环境中无效                          |
| `SCOPE`  | 宿主环境仅使用宿主环境的日志功能，模块环境仅使用 `android.util.Log` |

> 示例如下

```kotlin
YLog.debug("This is a log", env = YLog.EnvType.LOGD)
```

## 错误日志

你可以调用 `YLog.error` 来向控制台打印 `E` 级别的日志。

> 示例如下

```kotlin
YLog.error("This is an error")
```

你还可以在后面加上一个异常堆栈。

> 示例如下

```kotlin
// 假设这就是被抛出的异常
val throwable = Throwable(...)
// 打印日志
YLog.error("This is an error", e = throwable)
```

打印的结果为如下所示。

> 示例如下

```text:no-line-numbers
[YukiHook][E][进程名] This is an error
```

同时，日志会帮你打印整个异常堆栈。

> 示例如下

```text:no-line-numbers
java.lang.Throwable
    at com.demo.Test.<init>(...)
    at com.demo.Test.doTask(...)
    at com.demo.Test.stop(...)
    at com.demo.Test.init(...)
    at a.a.a(...)
    ... 3 more
```

在错误日志中，你同样也可以使用 `YLog.EnvType` 来指定当前打印日志的环境。

## 保存日志

启用 `recording` 后，你可以使用 `YLog.saveToFile` 方法直接保存当前已记录的全部日志到文件。

> 示例如下

```kotlin
YLog.saveToFile("/sdcard/Documents/debug_log.log")
```

你还可以使用 `YLog.contents` 获取当前已记录的全部日志文件内容。

> 示例如下

```kotlin
val fileContent = YLog.contents
```

如果你需要一个实时日志的数据结构数组，你可以直接获取 `YLog.inMemoryData` 的内容。

> 示例如下

```kotlin
YLog.inMemoryData.forEach {
    it.timestamp // 获取时间戳
    it.time // 获取 UTC 时间
    it.priority // 获取优先级
    it.msg // 获取消息
    it.throwable // 获取异常
    // ...
}
```

如果你想对得到的自定义日志数据进行格式化或保存到文件，你只需要使用如下方法即可。

> 示例如下

```kotlin
// 假设这就是你得到的自定义日志数据
val data: List<YLogData>
// 格式化日志数据到字符串
val dataString = YLog.contents(data)
// 保存日志数据到文件
YLog.saveToFile("/sdcard/Documents/debug_log.log", data)
```

::: tip

如果你需要在模块中实时得到宿主的日志数据，请参考 [模块与宿主通讯桥 → 获取宿主日志](./data-channel.md#获取宿主日志)。

:::

::: danger

保存的文件路径在当前环境中必须拥有读写权限，否则会抛出异常。

你需要启用 `recording` 才能获取到 `YLog.inMemoryData` 的内容。

获取到的日志数据在宿主及模块进程中是相互隔离的，你只能在对应的进程中获取对应的日志数据。

:::