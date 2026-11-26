# Module Entry

> Here describes how to create a hook entry class, configure `YukiHook` and load hookers.

## Create the Entry Class

Create a hook entry class in your project, implement the `YukiHookXposedModule` interface, and add the entry annotation that matches the base module you selected.

> The following example

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

The entry annotation is processed by [yukihook-compiler](../yukihook-compiler.md), which generates the entry class and configuration files the Xposed framework needs at compile time.
For details, please refer to the corresponding [base module](../../guide/quick-start.md#choose-a-base-module).

The entry class can be defined with `class` or `object`, but it is recommended to use `object` to make sure each injected process runs it as a singleton.

`onInit` is called when YukiHook initializes, and `onHook` is called when hook loading starts.

::: danger

**Only one** entry annotation can exist in a build variant, and the `Class` with the entry annotation must implement the `YukiHookXposedModule` interface, otherwise the build will fail.

`onInit` is only for initialization, do not run any hook operations in it.

When the `Application` of the module inherits `ModuleApplication`, `onInit` is also called in the module's own process,
so only configure and register things in it, and do not run code that depends on the host or the hook framework environment.

:::

If you need to make one module project compatible with multiple base modules, please refer to [Multi-Entry Variants](./hook-entry/multi-entry-variants.md).

## Configuration

You can configure YukiHook with `YukiHook.configure` or its shorthand `configure` in `onInit`.

> The following example

```kotlin
override fun onInit() = configure {
    // Configure YLog.
    logging {
        tag = "YourModule"
        // Minimum log level, INFO by default.
        level = if (BuildConfig.DEBUG) YLog.Level.DEBUG else YLog.Level.INFO
        recording = false
    }
    // Whether to enable debug mode, disabled by default.
    // Once enabled, detailed hook logs are printed, it is recommended to disable it in release builds.
    debug = BuildConfig.DEBUG
    // Whether to enable the module and host channel, enabled by default.
    dataChannel = true
}
```

For `logging`, please refer to [Debug Logs](./logger.md), for `dataChannel`, please refer to [Module and Host Channel](./data-channel.md).

We recommend making the `Application` of your module inherit `ModuleApplication` for the full experience.

> The following example

```kotlin
class YourApplication : ModuleApplication() {

    override fun onCreate() {
        super.onCreate()
        // Your code here.
    }
}
```

`ModuleApplication` initializes the features of the corresponding base module, and provides the globally available `ModuleApplication.current` and `ModuleApplication.requireContext()`.

## Load Hookers

The most important part of an Xposed module is creating and using hookers, YukiHook provides two ways to do it.

### Create with DSL

This is the simplest way. If your module has few features and little code, and does not need to be organized into categories, it is recommended to use this way.

`encase` is where every hook begins. `encase` can only take effect once in a module and is used to create hookers.

`PackageParam` is the important instance of the host, all hook operations on the current hook target are done through `PackageParam`.

> The following example

```kotlin
override fun onHook() = encase {
    loadApp("io.github.myapp") {
        // Your code here.
    }
}
```

### Create with Custom Hookers

This way is more suitable for large projects, for example when you need to organize hookers or hook targets into categories.

`YukiBaseHooker` inherits `PackageParam`, your child hookers need to inherit `YukiBaseHooker`.

> The following example

```kotlin
object CustomHooker : YukiBaseHooker() {

    override fun onHook() {
        loadApp("io.github.myapp") {
            // Your code here.
        }
    }
}
```

As a child hooker, you can also call `loadApp` outside and start hooking directly inside.

> The following example

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

You can use `loadHooker` to load another hooker from a child hooker, with as many levels as you like.

> The following example

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

After all hookers are set up, you can load them in the `onHook` method of the entry class.

> The following example

```kotlin
override fun onHook() = encase(FirstHooker, SecondHooker, ThirdHooker)
```

::: tip

Child hookers are **recommended** to be created as singleton `object`, but in rare cases a process may contain multiple package names.

When YukiHook finds that a child hooker is already bound to another hook entry, it ignores the current loading and prints a warning.
You need to change `object` to `class`, or check the package name before loading it.

:::

::: warning

You don't need to call `encase` again in the `onHook` method of `YukiBaseHooker`, this is wrong. Start writing your hook code directly.
You also cannot call `encase` again in a `PackageParam` or inside another `encase` block.

:::

## Handle Scopes

`encase` is called in every hooked process. You need the following functions to distinguish the scope of each piece of loading code, otherwise your code will run repeatedly in all scopes.

| Function               | Scope                                                                     |
| ---------------------- | ------------------------------------------------------------------------- |
| `loadApp(name)`        | The app with the given package name, the system framework is not included |
| `loadApp(vararg name)` | Apps with the given package names, the system framework is not included   |
| `loadApp()`            | All apps, use `excludeSelf` to exclude the module itself                  |
| `loadSystem`           | The system framework (`system_server`)                                    |
| `withProcess`          | The given process of the current host                                     |

> The following example

```kotlin
override fun onHook() = encase {
    // Hook the given host.
    loadApp("io.github.myapp") {
        // Your code here.
    }
    // Hook multiple hosts.
    loadApp("io.github.myapp", "io.github.anotherapp") {
        // Your code here.
    }
    // Hook the system framework.
    loadSystem {
        // Your code here.
    }
}
```

If you need to run hooks after the `Application` of the host is created, you can use `withApplicationReady`.

This feature is mainly used to initialize with the `Context` of the host or to get the `ClassLoader` provided by the current context, you no longer need to hook `Application` or `Instrumentation` manually.

> The following example

```kotlin
loadApp("io.github.myapp") {
    withApplicationReady { application ->
        // Your code here.
    }
}
```

If the `Application` of the host is already available, the callback runs immediately, otherwise it runs after `Application.onCreate` of the host completes.

When using [Module Hot Reload](./hot-reload.md), the callback runs again immediately with the existing `Application` after the module reloads.

::: tip

`withApplicationReady` only takes effect for the host that owns the first `Application` in the current process. In the system framework, the callback runs after system services start.

:::

If the host you hook has multiple processes, you can use `withProcess` to hook them separately.

> The following example

```kotlin
loadApp("io.github.myapp") {
    // Hook the main process.
    withProcess(mainProcessName) {
        // Your code here.
    }
    // Hook other processes.
    withProcess("$packageName:tool") {
        // Your code here.
    }
}
```

::: danger

The system framework is a separate hook scope. Passing `android` or `system` to `loadApp` is treated as `loadSystem`, please use `loadSystem` directly.

`loadApp` can only load apps, mixing `android` or `system` into its package names throws an exception at runtime.

The process name of the system framework is `system`, not `android`, and its UI process is `android:ui`.

:::

::: warning

`loadZygote` from earlier versions is now only available in [yukihook-runtime-xposed82](../yukihook-runtime-xposed82) and has been deprecated, it is no longer recommended.
Please move app hooks to `loadApp` and system framework hooks to `loadSystem`.

:::

## Host Information

In `PackageParam`, you can get information about the current host.

> The following example

```kotlin
loadApp("io.github.myapp") {
    // The package name of the current host.
    packageName
    // The process name of the current host.
    processName
    // The main process name of the current host.
    mainProcessName
    // Whether the current host is the first `Application` in the current process.
    isFirstApplication
    // The `ApplicationInfo` of the current host.
    appInfo
    // The user ID of the current host.
    userId
    // The `ClassLoader` of the current host.
    hostClassLoader
    // The `Application` of the current host, may be null during initial loading.
    hostApplication
    // The `Context` of the system framework in the current process.
    systemContext
    // The `ApplicationInfo` of the current Xposed module.
    // Note: Only available in some base modules.
    module.appInfo
    // The `Resources` of the current Xposed module.
    module.resources
    // The APK path of the current Xposed module.
    module.sourceDir
}
```

::: tip

`isFirstApplication` only means that the current package owns the first `Application` in the current process, it is also `true` in a sub-process such as `io.github.myapp:tool`.
To check for the main process, use `processName == mainProcessName`.

If you need to load a `Class` with the `ClassLoader` of the host implicitly in `PackageParam`, you can use KavaRef. YukiHook provides an extension for KavaRef,
for details please refer to [Load Classes from the Host](../yukihook-extension-kavaref.md#load-classes-from-the-host).

:::