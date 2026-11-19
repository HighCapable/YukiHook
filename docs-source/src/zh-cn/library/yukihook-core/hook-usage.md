# Hook 用法

> 这里介绍了 `YukiHook` 的基本 Hook 方式以及展示了常用的 Hook 案例。

## 结构图解

下方的结构描述了 YukiHook 的基本工作方式和原理。

```text:no-line-numbers
Host Environment
└─ PackageParam
   └─ Member
      ├─ hook
      │  ├─ before
      │  ├─ after
      │  └─ intercept
      └─ intercept
         └─ proceed
```

YukiHook 提供了两种 Hook 方式。

- `hook`：经典 Hook 方式，在 `before`、`after` 回调中得到 `HookParam`，或使用 `intercept` 替换整个方法
- `intercept`：链式 Hook 方式，在回调中得到 `HookChain`，通过 `proceed` 决定是否继续执行后续的调用链

## 基本用法

假设，我们要 Hook `io.github.myapp` 中每个 `Activity` 的 `onCreate` 方法在其执行后弹出一个对话框。

> 示例如下

```kotlin
loadApp("io.github.myapp") {
    val activityClass = Activity::class.java
    // 使用 Java 反射方案得到 onCreate 方法
    val onCreate = activityClass.getDeclaredMethod(
        "onCreate", Bundle::class.java
    )

    onCreate.hook {
        after {
            // 得到当前 Hook 方法所在的实例
            val context = instance<Activity>()

            AlertDialog.Builder(context)
                .setTitle("Hooked")
                .setMessage("onCreate has been hooked!")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
```

至此，`onCreate` 方法将被成功 Hook 并在 `io.github.myapp` 中的每个 `Activity` 启动时弹出此对话框。

你可以在仅需要一个 Hook 回调事件的时候简写 `hook { ... }` 方法体。

> 示例如下

```kotlin
// 假设这就是你需要 Hook 的方法
val method: Method
// 进行 Hook
method.hook().before {
    // Your code here.
}
```

如果你需要同时 Hook 多个方法，可以使用 `hookAll`。

> 示例如下

```kotlin
// 假设这就是你需要 Hook 的方法列表
val methods: List<Method>
// 进行 Hook
methods.hookAll {
    after {
        // Your code here.
    }
}
// 或者
methods.hookAll().after {
    // Your code here.
}
```

::: tip

YukiHook 支持对 `Method`、`Constructor` 以及 `Class` 成员进行 Hook，其中对 `Class` 的作用是 Hook 它的 `<clinit>` 静态初始化块，详情请参考本节的 [Hook 静态初始化](#hook-静态初始化)。

不同 Hook 方案对底座的要求不同，在使用的方案不支持当前底座时你将会收到显式的 Lint 提示或错误。

:::

## 经典 Hook

此方案提供了 Xposed Framework API 时代的前后回调式 Hook，`before` 与 `after` 分别会在方法执行之前与执行之后被回调，你可以同时设置它们。

在回调方法体中，你可以得到 `HookParam` 对象，两个方法的回调均为此对象。

> 示例如下

```kotlin
hook {
    before {
        // 得到当前 Hook 的实例
        instance
        // 得到并 cast 当前 Hook 的实例为指定类型 T
        instance<T>()
        // 静态方法中没有实例，请使用 `instanceOrNull`
        instanceOrNull
        // 得到并 cast 当前 Hook 的实例为指定类型 T，实例不存在或类型不匹配时为 null
        instanceOrNull<T>()
        // 得到当前 Hook 的方法或构造方法
        member
        // 得到当前 Hook 的方法
        method
        // 得到当前 Hook 的构造方法
        constructor
        // 得到方法参数数组
        args
        // 得到方法参数的第一位 T
        firstArg().get<T>()
        // 得到方法参数的最后一位 T
        lastArg().get<T>()
        // 得到方法参数的任意下标 T，这里用 2 举例
        arg(2).get<T>()
        // 按类型 T 读取参数，参数不存在、值为 null 或类型不匹配时返回 null
        arg(2).getOrNull<T>()
        // 设置方法参数的任意下标，这里用 2 举例
        arg(2).set(...)
        // 获取当前回调方法体范围内的数据存储实例
        dataExtra
    }
    after {
        // 得到返回值
        result
        // 得到返回值并 cast 为 T
        result<T>()
        // 修改返回值内容
        result = ...
    }
}
```

::: danger

为 `result` 设置与方法返回值类型不匹配的内容时，将会抛出异常且原有的返回值保持不变。

:::

### 拦截方法

如果你需要替换整个方法的逻辑，可以使用 `intercept`。

> 示例如下

```kotlin
hook {
    intercept {
        // 在这里实现被替换的逻辑
        // 需要返回方法对应的返回值，无需写 return，只需将返回值放到最后一位
        0
    }
}
```

如果你只需要将方法替换为一个固定的返回值，可以直接传入这个值。

> 示例如下

```kotlin
hook {
    // 替换为你需要的返回值
    intercept(true)
}
```

不传入任何参数时将拦截方法并返回 `null`，对于无返回值的方法与构造方法，`intercept` 的返回值将被忽略。

::: danger

`intercept` 会替换 `before` 与 `after` 回调，请不要在同一个 `hook` 方法体中同时使用它们。

为返回值为 `Int`、`Boolean` 等基本类型的方法返回 `null` 可能会造成宿主抛出异常。

:::

### 调用原始方法

在回调方法体中，你可以调用未经 Hook 的原始方法。

> 示例如下

```kotlin
hook {
    intercept {
        // 使用原始方法参数调用原始方法，可以省略泛型
        callOriginal<Any?>()
        // 自定义方法参数调用原始方法，可以省略泛型
        invokeOriginal<Any?>(...)
    }
}
```

在 Hook 回调以外的地方，你也可以使用 `invokeOriginal` 调用一个 `Method` 未经 Hook 的原始方法。

> 示例如下

```kotlin
// 假设这就是当前类的方法
val method: Method
// 假设这就是当前类的实例
val instance: Any
// 调用当前方法的原始实现
method.invokeOriginal(instance)
```

::: tip

你还可以使用 `invokeSpecial`、`newInstanceOriginal`、`newInstanceSpecial` 等方法非虚调用方法或调用未经 Hook 的原始构造方法。

这些功能中部分需要对应的 [底座](../../guide/quick-start.md#choose-a-base-module) 支持。

:::

## 链式 Hook

此方案提供了一种链式拦截的 Hook 方式，回调中得到的 `HookChain` 对象可以通过 `proceed` 继续执行后续的调用链。

不调用 `proceed` 时，原始方法将不会被执行，`intercept` 方法体最后一位的值将作为方法的返回值。

> 示例如下

```kotlin
// 假设这就是你需要 Hook 的方法
val method = TextView::class.java.getDeclaredMethod(
    "setText", CharSequence::class.java, TextView.BufferType::class.java
)
// 进行 Hook
method.intercept {
    // 修改第一个参数后继续执行调用链
    if (firstArg<CharSequence>() == "Hello")
        proceed(arrayOf("Hello YukiHook", lastArg()))
    else proceed()
}
```

在 `HookChain` 中，你可以得到以下内容。

> 示例如下

```kotlin
intercept {
    // 得到当前 Hook 的实例
    instance
    // 得到只读的方法参数数组
    args
    // 得到方法参数的任意下标 T，这里用 2 举例
    arg<T>(2)
    // 按类型 T 读取参数，参数不存在、值为 null 或类型不匹配时返回 null
    argOrNull<T>(2)
    // 使用当前参数继续执行调用链
    proceed()
    // 使用新的参数继续执行调用链
    proceed(arrayOf(...))
    // 使用新的实例继续执行调用链
    proceedWith(instance = ...)
}
```

`HookChain` 中的参数是只读的，若要修改参数，请将新的参数数组传入 `proceed`。

如果你需要同时拦截多个方法，可以使用 `interceptAll`。

::: warning

在 [yukihook-runtime-xposed82](../yukihook-runtime-xposed82.md) 底座中，链式 Hook 是通过一个低优先级的 Hook 进行模拟的，`proceed` 只会执行当前模块中剩余的链式 Hook 与原始方法，其他模块与经典 Hook 不属于这个调用链。

:::

## Hook 静态初始化

你可以直接对 `Class` 使用 `hook` 或 `intercept` 来 Hook 它的静态初始化方法。

> 示例如下

```kotlin
// 假设这是你需要 Hook 的类
val clazz: Class<*>
// 对 <clinit> 进行 Hook
clazz.hook {
    after {
        // Your code here.
    }
}
```

::: warning

请确保得到这个 `Class` 时没有对其进行初始化，若 `Class` 已经被初始化，这个 Hook 将永远不会被回调。

拦截静态初始化方法将跳过其执行，静态变量将保持 JVM 的默认值。

YukiHook 在早先的版本中曾对 `Class` 提供过 `hook` 方法，属于 API 设计歧义，后续已被弃用并在 `1.5.0` 正式被移除，它与当前功能无关。

:::

::: danger

此功能需要 [yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) 底座支持。

:::

## Hook 优先级

你可以在 `hook` 与 `intercept` 中设置 `YukiHookPriority` 来决定回调的优先级。

> 示例如下

```kotlin
hook(priority = YukiHookPriority.HIGHEST) {
    // Your code here.
}
```

| 优先级    | 说明                       |
| --------- | -------------------------- |
| `DEFAULT` | 默认优先级                 |
| `HIGHEST` | 在优先级较低的回调之前回调 |
| `LOWEST`  | 在优先级较高的回调之后回调 |

## 解除 Hook

YukiHook 提供了两种方法来解除 Hook。

第一种方法，保存 `hook` 或 `intercept` 返回的 `Handle` 实例，在适当的时候调用 `remove` 即可解除 Hook。

> 示例如下

```kotlin
// 设置一个变量保存当前实例
val handle = method.hook {
    after {
        // ...
    }
}
// 在适当的时候调用
handle.remove()
```

第二种方法，在 Hook 回调方法体中调用 `removeSelf` 移除自身。

> 示例如下

```kotlin
method.hook {
    after {
        // 直接调用
        removeSelf()
    }
}
```

对于使用 `hookAll` 等方法同时 Hook 的多个方法，`remove` 与 `removeSelf` 将移除整个 Hook 组。

## 替换 Hook 回调

你可以使用 `Handle` 的 `replace` 方法替换已注册的回调，而不需要重新 Hook。

> 示例如下

```kotlin
val handle = method.hook {
    after {
        // ...
    }
}
// 替换为新的回调
handle.replace {
    before {
        // ...
    }
}
```

::: warning

正在执行中的调用将继续使用之前的回调。

:::

::: danger

此功能需要 [yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) 底座支持。

:::

## 解除内联化

ART 可能会将一些较短的方法内联到调用它的方法中，此时 Hook 被内联的方法将不会生效。

你可以对**调用方**使用 `deoptimize` 使其不再内联被调用的方法。

> 示例如下

```kotlin
// 假设这就是调用的方法
val callingMethod: Method
// 解除内联化
callingMethod.deoptimize()
```

::: warning

需要解除内联化的是包含内联调用的方法，而不是被 Hook 的方法。

:::

::: danger

此功能需要 [yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) 底座支持。

:::

## 异常处理

YukiHook 重新设计了对异常的监听，Hook 回调中的异常默认不会抛给宿主，避免宿主进程崩溃。

### 监听异常

你可以处理 Hook 回调过程中发生的异常。

> 示例如下

```kotlin
hook {
    // Your code here.
}.apply {
    // 处理 Hook 回调中的异常
    onCallbackFailure { param, throwable -> }
    // 处理 Hook 调用中的全部异常
    onInvocationFailure { throwable -> }
}
```

未设置监听时，异常将会被打印到日志中，你也可以使用 `ignoreCallbackFailure`、`ignoreInvocationFailure` 忽略这些日志。

经典 Hook 的 `intercept` 回调中发生异常时，YukiHook 将会调用未经 Hook 的原始方法以保证宿主正常运行，若原始方法自身抛出异常，该异常将会抛给调用方。

### 抛出异常

若想在 Hook 回调方法体中将一个异常直接抛给宿主，可以有如下实现方法。

> 示例如下

```kotlin
hook {
    after {
        RuntimeException("Exception Test").throwToHost()
    }
}
```

你也可以直接在 Hook 回调方法体中抛出异常，然后标识将异常抛给宿主。

> 示例如下

```kotlin
hook {
    after {
        throw RuntimeException("Exception Test")
    }.throwToHostOnFailure()
}
```

以上两种方法均可在宿主接收到异常从而使宿主进程崩溃。

以上两种方法同样适用于 `intercept` 回调，使用 `throwToHost` 后回调的返回值将被忽略，使用 `throwToHostOnFailure` 后发生异常时 YukiHook 将不再调用原始方法。

链式 Hook 中抛出异常的方法请参考下方的 [链式 Hook 中的异常](#链式-hook-中的异常)。

::: warning

`throwToHostOnFailure` 只对设置它的回调方法体生效，例如只为 `after` 设置时，`before` 中发生的异常仍然不会抛给宿主。

:::

### 链式 Hook 中的异常

链式 Hook 的回调中发生异常时，YukiHook 将会按照此 Hook 不存在的方式继续调用，后续的调用链不会被重复执行。

- 异常发生在调用 `proceed` 之前时，YukiHook 将会继续执行后续的调用链。
- 异常发生在调用 `proceed` 之后时，YukiHook 将会返回最后一次 `proceed` 的结果或抛出其异常。

回调返回与方法返回值类型不匹配的内容时，同样会按照上述方式处理。

`proceed` 抛出的异常来自后续的调用链或原始方法，它们会原样抛给调用方，不会触发 `onCallbackFailure` 与 `onInvocationFailure`，如果你需要处理这些异常，请在回调中自行处理。

> 示例如下

```kotlin
intercept {
    try {
        proceed()
    } catch (e: IllegalStateException) {
        // 处理异常并返回其它结果
        "fallback"
    }
}
```

若想将回调中的异常直接抛给调用方，可以有如下实现方法。

> 示例如下

```kotlin
intercept {
    throw RuntimeException("Exception Test")
}.throwToHostOnFailure()
```

::: warning

设置 `throwToHostOnFailure` 后，因为后续的调用链可能已经执行，YukiHook 不会对其进行重试。

:::