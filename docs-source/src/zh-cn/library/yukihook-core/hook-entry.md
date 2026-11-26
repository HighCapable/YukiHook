# 模块入口

> 这里介绍了如何创建 Hook 入口类、配置 `YukiHook` 以及装载 Hooker。

## 创建入口类

在你的项目中创建一个 Hook 入口类，实现 `YukiHookXposedModule` 接口，并根据你选择的底座模块加入对应的入口注解。

> 示例如下

::: code-group

```kotlin [yukihook-runtime-libxposed]
@YukiHookLibXposedEntry(targetApiVersion = 101)
object HookEntry : YukiHookXposedModule {

    override fun onInit() = configure {
        // Your code here.
    }

    override fun onHook() = encase {
        // Your code here.
    }
}
```

```kotlin [yukihook-runtime-xposed82]
@YukiHookXposed82Entry
object HookEntry : YukiHookXposedModule {

    override fun onInit() = configure {
        // Your code here.
    }

    override fun onHook() = encase {
        // Your code here.
    }
}
```

:::

入口注解由 [yukihook-compiler](../yukihook-compiler.md) 处理，它会在编译时自动生成 Xposed 框架所需要的入口类与配置文件，详情请参考对应的 [底座](../../guide/quick-start.md#choose-a-base-module)。

入口类可以使用 `class` 或 `object` 定义，但是建议使用 `object` 定义来保证每一个注入的进程都是单例运行。

`onInit` 会在 YukiHook 初始化时被调用，`onHook` 会在开始装载 Hook 时被调用。

::: danger

一个构建变体中**只能存在一个**入口注解，入口注解的 `Class` 必须实现 `YukiHookXposedModule` 接口，否则将导致编译失败。

`onInit` 只能用于初始化，请不要在其中进行任何 Hook 操作。

模块的 `Application` 继承 `ModuleApplication` 时，`onInit` 也会在模块自身的进程中被调用，请只在其中进行配置与注册，不要执行依赖宿主或 Hook 框架环境的代码。

:::

如果你需要让同一个模块项目同时兼容多个底座，请参考 [多入口变体](./hook-entry/multi-entry-variants.md)。

## 功能配置

你可以在 `onInit` 中使用 `YukiHook.configure` 或其简写形式 `configure` 对 YukiHook 进行配置。

> 示例如下

```kotlin
override fun onInit() = configure {
    // 配置 YLog
    logging {
        tag = "YourModule"
        // 最小日志级别，默认为 INFO
        level = if (BuildConfig.DEBUG) YLog.Level.DEBUG else YLog.Level.INFO
        recording = false
    }
    // 是否启用调试模式，默认不启用
    // 启用后将会输出详细的 Hook 日志，建议在发布版本中关闭
    debug = BuildConfig.DEBUG
    // 是否启用模块与宿主通讯桥，默认启用
    dataChannel = true
}
```

`logging` 的用法请参考 [调试日志](./logger.md)，`dataChannel` 的用法请参考 [模块与宿主通讯桥](./data-channel.md)。

我们建议你将模块的 `Application` 继承于 `ModuleApplication` 以实现完整使用体验。

> 示例如下

```kotlin
class YourApplication : ModuleApplication() {

    override fun onCreate() {
        super.onCreate()
        // Your code here.
    }
}
```

`ModuleApplication` 会初始化对应底座的功能，并提供全局可用的 `ModuleApplication.current` 与 `ModuleApplication.requireContext()`。

## 装载 Hooker

一个 Xposed 模块最重要的地方就是 Hooker 的创建与使用，YukiHook 提供了两种使用方法。

### 通过 DSL 创建

这种方案是最简单的，如果你的模块功能不多，代码数量不大，不需要进行分类处理，推荐使用这种方式进行创建。

`encase` 是一切 Hook 生命的开始，在一个模块中 `encase` 方法只能作用一次，用于创建 Hooker。

`PackageParam` 为宿主的重要实例对象，通过 `PackageParam` 来实现对当前 Hook 作用对象的全部 Hook 操作。

> 示例如下

```kotlin
override fun onHook() = encase {
    loadApp("io.github.myapp") {
        // Your code here.
    }
}
```

### 通过自定义 Hooker 创建

这种方案更加适用于大型项目，例如需要对 Hooker 进行分类或对 Hook 的作用对象进行分类。

`YukiBaseHooker` 继承于 `PackageParam`，你需要将你的子 Hooker 继承于 `YukiBaseHooker`。

> 示例如下

```kotlin
object CustomHooker : YukiBaseHooker() {

    override fun onHook() {
        loadApp("io.github.myapp") {
            // Your code here.
        }
    }
}
```

作为子 Hooker 使用，你还可以在外部调用 `loadApp` 方法，然后在内部直接开始 Hook。

> 示例如下

```kotlin
object HookEntry : YukiHookXposedModule {

    override fun onHook() = encase {
        loadApp("io.github.myapp", ChildCustomHooker)
    }
}

object ChildCustomHooker : YukiBaseHooker() {

    override fun onHook() {
        // Your code here.
    }
}
```

你可以使用 `loadHooker` 方法在子 Hooker 中多层装载另一个 Hooker。

> 示例如下

```kotlin
object FirstHooker : YukiBaseHooker() {

    override fun onHook() {
        loadApp("io.github.myapp") {
            loadHooker(SecondHooker)
            loadHooker(ThirdHooker)
        }
    }
}
```

搭建完全部 Hooker 后，你就可以在入口类的 `onHook` 方法中装载你的 Hooker 了。

> 示例如下

```kotlin
override fun onHook() = encase(FirstHooker, SecondHooker, ThirdHooker)
```

::: tip

子 Hooker **建议使用**单例 `object` 创建，但在极少数情况下一个进程中可能存在多个包名。

此时 YukiHook 发现子 Hooker 已经绑定到另一个 Hook 入口时，将会忽略当前的装载并打印一条警告信息，你需要将 `object` 修改为 `class` 或在装载前判断包名。

:::

::: warning

你无需在 `YukiBaseHooker` 的 `onHook` 方法中再次调用 `encase`，这是错误的，你应该直接开始实现你的 Hook 代码。也同样不能在 `PackageParam` 中或另一个 `encase` 方法体内再次调用 `encase`。

:::

## 处理作用域

`encase` 会在每一个被 Hook 的进程中被调用，你需要使用如下方法来区分每一次装载代码的作用域，否则你的代码将会在全部作用域中重复、多次执行。

| 方法                   | 作用域                                      |
| ---------------------- | ------------------------------------------- |
| `loadApp(name)`        | 指定包名的应用，不包含系统框架              |
| `loadApp(vararg name)` | 多个包名的应用，不包含系统框架              |
| `loadApp()`            | 全部应用，可使用 `excludeSelf` 排除模块自身 |
| `loadSystem`           | 系统框架 (`system_server`)                  |
| `withProcess`          | 当前宿主的指定进程                          |

> 示例如下

```kotlin
override fun onHook() = encase {
    // Hook 指定宿主
    loadApp("io.github.myapp") {
        // Your code here.
    }
    // Hook 多个宿主
    loadApp("io.github.myapp", "io.github.anotherapp") {
        // Your code here.
    }
    // Hook 系统框架
    loadSystem {
        // Your code here.
    }
}
```

如果你需要在宿主的 `Application` 创建完成后再执行 Hook，你可以使用 `withApplicationReady`。

此功能主要用于使用宿主的 `Context` 进行初始化或者获取当前上下文提供的 `ClassLoader`，你不再需要手动 Hook `Application` 或 `Instrumentation`。

> 示例如下

```kotlin
loadApp("io.github.myapp") {
    withApplicationReady { application ->
        // Your code here.
    }
}
```

宿主的 `Application` 已经可用时将会立即回调，否则将在宿主的 `Application.onCreate` 执行完成后回调。

使用 [模块热重载](./hot-reload.md) 时，模块重载后将会再次使用已有的 `Application` 实例立即回调。

::: tip

`withApplicationReady` 只会对当前进程中拥有第一个 `Application` 的宿主生效，在系统框架中将会在系统服务启动后回调。

:::

如果你 Hook 的宿主有多个进程，你可以使用 `withProcess` 方法体来对它们分别进行 Hook。

> 示例如下

```kotlin
loadApp("io.github.myapp") {
    // Hook 主进程
    withProcess(mainProcessName) {
        // Your code here.
    }
    // Hook 其他进程
    withProcess("$packageName:tool") {
        // Your code here.
    }
}
```

::: danger

系统框架是独立的 Hook 作用域，在 `loadApp` 中传入 `android` 或 `system` 时将会被视为 `loadSystem`，请直接使用 `loadSystem`。

`loadApp` 只能装载应用程序，在其多个包名中混入 `android` 或 `system` 将在运行时抛出异常。

系统框架的进程名为 `system`，而不是 `android`，其 UI 进程为 `android:ui`。

:::

::: warning

在早先版本中的 `loadZygote` 现仅在 [yukihook-runtime-xposed82](../yukihook-runtime-xposed82) 中可用且已被弃用，不再推荐使用，请将对应用程序的 Hook 迁移至 `loadApp`，将系统框架的 Hook 迁移至 `loadSystem`。

:::

## 宿主信息

在 `PackageParam` 中，你可以得到当前宿主的相关信息。

> 示例如下

```kotlin
loadApp("io.github.myapp") {
    // 当前宿主的包名
    packageName
    // 当前宿主的进程名
    processName
    // 当前宿主的主进程名
    mainProcessName
    // 当前宿主是否为当前进程中的第一个 `Application`
    isFirstApplication
    // 当前宿主的 `ApplicationInfo`
    appInfo
    // 当前宿主的用户 ID
    userId
    // 当前宿主的 `ClassLoader`
    hostClassLoader
    // 当前宿主的 `Application`，初次装载时可能为空
    hostApplication
    // 当前进程中系统框架的 `Context`
    systemContext
    // 当前 Xposed 模块的 `ApplicationInfo`
    // 注意：仅在部分底座中可用
    module.appInfo
    // 当前 Xposed 模块的 `Resources`
    module.resources
    // 当前 Xposed 模块的 APK 路径
    module.sourceDir
}
```

::: tip

`isFirstApplication` 仅表示当前包名拥有当前进程中的第一个 `Application`，在 `io.github.myapp:tool` 这样的子进程中同样为 `true`，若要判断主进程，请使用 `processName == mainProcessName`。

如果你需要在 `PackageParam` 中隐式使用宿主的 `ClassLoader` 装载 `Class`，可以使用 KavaRef 来完成，YukiHook 为 KavaRef 提供了扩展功能，详情请参考 [装载宿主中的类](../yukihook-extension-kavaref.md#装载宿主中的类)。

:::