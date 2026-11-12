# Migration to YukiHook 1.5.x

> YukiHook has been completely refactored since version `1.5.0`, you can read on to see what to pay attention to and what is new.

::: warning

The migration documents of the earlier `1.2.x` (released in 2023) and `1.3.x` (released in 2025) have been removed because they are too old.
This document is intended for users updating from version `1.3.x`. If you are using an earlier version,
please refer to the [changelog](../about/changelog.md) first to learn the changes of each version.

Version `1.5.x` keeps compatible forms for most of the old usages, they are marked as deprecated.
You can finish most of the migration by following the hints of the IDE, and these compatible forms will be removed in a future version.

:::

## Project Renaming

This is the biggest change so far, the project has been officially renamed from **YukiHookAPI** to **YukiHook**.

The entry object `YukiHookAPI` has been deprecated, please migrate to `YukiHook`.

> The following example

```kotlin
// Old usage.
YukiHookAPI.encase { ... }
YukiHookAPI.Status.isModuleActive
YukiHookAPI.VERSION
// New usage.
YukiHook.encase { ... }
YukiHook.Status.isModuleActive
YukiHook.VERSION
```

To stay compatible, the `groupId` of the dependencies and the package name of the code are still `com.highcapable.yukihookapi`,
you basically don't need to change any `import`.

## Dependency Changes

YukiHook is now split into multiple modules, and you need to add a base module to decide which Xposed API to work with.

| Old Dependency                           | New Dependency                                                                                                                                 |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `com.highcapable.yukihookapi:api`        | [yukihook-core](../library/yukihook-core.md)                                                                                                   |
| `com.highcapable.yukihookapi:ksp-xposed` | [yukihook-compiler](../library/yukihook-compiler.md)                                                                                           |
| `de.robv.android.xposed:api`             | [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md) or [yukihook-runtime-libxposed](../library/yukihook-runtime-libxposed.md) |

If you want to keep exactly the same runtime environment as before, choose [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md).
If your target hook framework supports the libxposed API (such as LSPosed), we recommend using [yukihook-runtime-libxposed](../library/yukihook-runtime-libxposed.md).

If you want to be compatible with multiple base modules at the same time, please refer to [Multi-Entry Variants](../library/yukihook-core/hook-entry/multi-entry-variants.md).

YukiHook now uses a BOM to manage the dependency versions in a unified way, please refer to [yukihook-bom](../library/yukihook-bom.md).

The KavaRef related features have been removed from [yukihook-core](../library/yukihook-core.md),
for details please refer to [About KavaRef](#about-kavaref) below.

## About KavaRef

In version `1.3.x`, YukiHook depended on KavaRef directly and provided KavaRef related functions in `PackageParam`,
now they have been removed from [yukihook-core](../library/yukihook-core.md) and moved to [yukihook-extension-kavaref](../library/yukihook-extension-kavaref.md).

If you use KavaRef in your project, please add this extension module. It does not pass the KavaRef dependencies,
so you still need to add KavaRef yourself, please refer to [here](../library/yukihook-extension-kavaref.md#configure-dependency).

In version `1.3.x`, most of the functions below were members of `PackageParam` and could be used without imports,
now you need to import them manually. The functions and classes under `...hook.core.api.reflect` also need to be migrated to the new package name.

<div class="migration-table">

| Old API                                                                                        | New API                                                                                    |
| ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `MemberResolver.hook` and `hookAll` in `PackageParam`                                          | `com.highcapable.yukihookapi.extension.kavaref.hook` and `hookAll`                         |
| `String.toClass` and `toClassOrNull` in `PackageParam`                                         | `com.highcapable.yukihookapi.extension.kavaref.toClass` and `toClassOrNull`                |
| `VariousClass.toClass` and `toClassOrNull` in `PackageParam`                                   | `com.highcapable.yukihookapi.extension.kavaref.toClass` and `toClassOrNull`                |
| `lazyClass` and `lazyClassOrNull` in `PackageParam`                                            | `com.highcapable.yukihookapi.extension.kavaref.lazyClass` and `lazyClassOrNull`            |
| `com.highcapable.yukihookapi.hook.core.api.reflect.invokeOriginal` and `invokeOriginalQuietly` | `com.highcapable.yukihookapi.extension.kavaref.invokeOriginal` and `invokeOriginalQuietly` |
| `com.highcapable.yukihookapi.hook.core.api.reflect.AndroidHiddenApiBypassResolver`             | `com.highcapable.yukihookapi.extension.kavaref.resolver.AndroidHiddenApiBypassResolver`    |

</div>

> The following example

```kotlin
// Old usage.
import com.highcapable.yukihookapi.hook.core.api.reflect.AndroidHiddenApiBypassResolver
import com.highcapable.yukihookapi.hook.core.api.reflect.invokeOriginal
// New usage.
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.invokeOriginal
import com.highcapable.yukihookapi.extension.kavaref.resolver.AndroidHiddenApiBypassResolver
import com.highcapable.yukihookapi.extension.kavaref.toClass
```

`AndroidHiddenApiBypassResolver` no longer provides the `get` function, please create its instance directly.

> The following example

```kotlin
// Old usage.
resolve().processor(AndroidHiddenApiBypassResolver.get())
// New usage.
resolve().processor(AndroidHiddenApiBypassResolver())
```

These functions can only be used in `PackageParam` now, including `invokeOriginal`, which could be used anywhere in version `1.3.x`.

::: danger

`toClass`, `toClassOrNull`, `lazyClass` and `lazyClassOrNull` in KavaRef have the same names as the functions provided by this module,
do not import both of them in the same file, otherwise an overload resolution ambiguity will occur.

If you only import these functions from KavaRef, the code still compiles, but they use the `ClassLoader` of
the module when the `ClassLoader` of the host is not passed, which may cause the `Class` in the host to not be found.

:::

## Entry Changes

`@InjectYukiHookWithXposed` and `IYukiHookXposedInit` have been deprecated, please migrate to the entry annotation of your base module and `YukiHookXposedModule`.

> The following example

```kotlin
// Old usage.
@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = false
    }

    override fun onHook() = encase {
        // Your code here.
    }
}
// New usage.
@YukiHookXposed82Entry
object HookEntry : YukiHookXposedModule {

    override fun onInit() = configure {
        debug = false
    }

    override fun onHook() = encase {
        // Your code here.
    }
}
```

The parameters of `@InjectYukiHookWithXposed` changed as follows.

| Old Parameter               | New Parameter                                              |
| --------------------------- | ---------------------------------------------------------- |
| `sourcePath`                | Removed, the compiler finds the project path automatically |
| `modulePackageName`         | `modulePackageName` in `@YukiHookXposed82Entry`            |
| `entryClassName`            | `entryClassName` in both entry annotations                 |
| `isUsingXposedModuleStatus` | `generateModuleStatus` in `@YukiHookXposed82Entry`         |
| `isUsingResourcesHook`      | Removed, Resources Hook is no longer supported             |

::: danger

Entry files are now generated in the build directory for each build variant. Please delete `assets/xposed_init`, `assets/yukihookapi_init`
and `resources/META-INF/yukihookapi_init` generated by older versions from your source directory manually, otherwise the build fails.

:::

`onXposedEvent` and `YukiXposedEvent.events` are no longer called, please migrate to `registerFrameworkEvents` provided by the base module and call it in `onInit`.

> The following example

```kotlin
// Old usage.
override fun onXposedEvent() {
    YukiXposedEvent.events {
        onHandleLoadPackage {
            // ...
        }
    }
}
// New usage.
override fun onInit() {
    registerFrameworkEvents {
        onHandleLoadPackage {
            // ...
        }
    }
}
```

For details, please refer to [yukihook-runtime-xposed82 → Framework Events](../library/yukihook-runtime-xposed82.md#framework-events)
and [yukihook-runtime-libxposed → Framework Events](../library/yukihook-runtime-libxposed.md#framework-events).

## Config Changes

`YukiHookAPI.configs` has been deprecated, please migrate to `YukiHook.configure`.

| Old Config                        | New Config                           |
| --------------------------------- | ------------------------------------ |
| `debugLog { ... }`                | `logging { ... }`                    |
| `isDebug`                         | `debug`                              |
| `isEnableDataChannel`             | `dataChannel`                        |
| `isEnableHookSharedPreferences`   | `hookSharedPreferences`              |
| `isEnableModuleAppResourcesCache` | Deprecated, no longer has any effect |
| `YLog.Configs.isEnable`           | `YLog.Config.enabled`                |
| `YLog.Configs.isRecord`           | `YLog.Config.recording`              |

## Removed Features

The following features have been removed or no longer take effect in version `1.5.x`.

- **Resources Hook**: `resources().hook { ... }` has been removed, and the `handleInitPackageResources` event is no longer called
- **Own reflection API**: `method { ... }`, `current()`, `searchClass` and type definitions such as `StringClass` deprecated since version `1.3.0` have been removed, please migrate to [KavaRef](https://github.com/HighCapable/KavaRef), for details please refer to [here](https://highcapable.github.io/KavaRef/en/config/migration)
- **Old hook style**: `TargetClass.hook { injectMember { ... } }` has been removed, please refer to [Hook Usage](../library/yukihook-core/hook-usage.md)
- **Use as a hook API**: `YukiHook.encase(baseContext)` is only available in [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md) and has been deprecated, please use the native API of the hook framework
- **loadZygote**: only available in [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md) and has been deprecated, please move hooks of apps to `loadApp` and hooks of the system framework to `loadSystem`
- **TaiChi and Wuji activation checks**: TaiChi stopped being maintained on 2024.05.08, `YukiHookAPI.Status.isTaiChiModuleActive` has been deprecated, and the status of `isXposedModuleActive` has been moved to `isModuleActive`, which no longer includes the activation status of the module in TaiChi and Wuji
- **Hook framework type**: no longer supported because of dirty state maintenance issues, `ExecutorType` has been removed and `YukiHookAPI.Status.Executor.type` can no longer be used, please use `YukiHook.Status.Executor.name` to get the name of the hook framework
- **Hook related types**: `YukiMemberHookCreator` and its `MemberHookCreator`, `MemberHookCreator.Result` and `MemberHookCreator.HookCallback` have been removed, if you declare these types explicitly in your code, please migrate them to `YukiHookCreator.ClassicMemberHooker`, `YukiHookCreator.ClassicMemberHooker.Handle` and `YukiHookCreator.ClassicMemberHooker.HookCallback` respectively

## Project Builder

Because the rapid iteration of modern Android development templates has greatly affected project maintenance,
the value of [YukiHookAPI-ProjectBuilder](https://github.com/HighCapable/YukiHookAPI-ProjectBuilder) has gone.
This project is no longer maintained and has been officially deprecated, please configure your project manually by referring to [Quick Start](../guide/quick-start.md).

## Renamed APIs

The following APIs have been renamed, and the old names are marked as deprecated.

<div class="migration-table">

| Old API                                            | New API                                          |
| -------------------------------------------------- | ------------------------------------------------ |
| `prefs`, `Context.prefs()`                         | `preferences`, `Context.preferences()`           |
| `YukiHookPrefsBridge`                              | `YukiHookPreferences`                            |
| `PrefsData`                                        | `PreferenceData`                                 |
| `...prefs.ui.ModulePreferenceFragment`             | `...preference.ui.ModulePreferenceFragment`      |
| `onAppLifecycle(isOnFailureThrowToApp)`            | `registerAppLifecycle(throwToHostOnFailure)`     |
| `Context.registerModuleAppActivities()`            | `Context.registerModuleActivities()`             |
| `Context.injectModuleAppResources()`               | `Context.injectModuleResources()`                |
| `appClassLoader`                                   | `hostClassLoader`                                |
| `appContext`                                       | `hostApplication`                                |
| `appUserId`                                        | `userId`                                         |
| `appResources`                                     | `hostApplication?.resources`                     |
| `moduleAppResources`                               | `module.resources`                               |
| `moduleAppFilePath`                                | `module.sourceDir`                               |
| `refreshModuleAppResources()`                      | `module.reloadResources()`                       |
| `replaceAny`, `replaceUnit`, `replaceTo` and so on | `intercept`                                      |
| `args(index)`, `args().first()`, `args().last()`   | `arg(index)`, `firstArg()`, `lastArg()`          |
| `cast<T>()`, `int()`, `string()` and so on         | `get<T>()`                                       |
| `resultTrue()`, `resultFalse()`, `resultNull()`    | `result = ...`                                   |
| `instanceClass`                                    | `instanceOrNull?.javaClass`                      |
| `Throwable.throwToApp()`                           | `Throwable.throwToHost()`                        |
| `onFailureThrowToApp()`                            | `throwToHostOnFailure()`                         |
| `result { ... }`                                   | `apply { ... }`                                  |
| `onConductFailure`, `ignoredConductFailure`        | `onCallbackFailure`, `ignoreCallbackFailure`     |
| `onAllFailure`, `ignoredAllFailure`                | `onInvocationFailure`, `ignoreInvocationFailure` |
| `checkingVersionEquals`                            | `checkVersionMatch`                              |
| `allowSendTooLargeData()`                          | `allowOversizedData()`                           |
| `dataMaxByteSize`, `dataMaxByteCompressionFactor`  | `maxDataSizeBytes`, `segmentSizeDivisor`         |
| `YukiHookAPI.Status.isXposedEnvironment`           | `YukiHook.Status.isHostEnvironment`              |
| `YukiHookAPI.Status.Executor.apiLevel`             | `YukiHook.Status.Executor.apiVersion`            |
| `YukiHookAPI.Status.Executor.versionName`          | `YukiHook.Status.Executor.version`               |
| `YLog.EnvType.XPOSED_ENVIRONMENT`                  | `YLog.EnvType.XPOSED`                            |
| `ModuleApplication.appContext`                     | `ModuleApplication.requireContext()`             |

</div>

`onHooked`, `onHookingFailure`, `ignoredHookingFailure` and `by { ... }` no longer take effect, hook registration failures are printed to the log directly, please remove these calls.

## Behavior Changes

- `loadApp` no longer includes the system framework. Passing `android` or `system` to `loadApp` is treated as `loadSystem`,
- and mixing them into multiple package names throws an exception at runtime, please use `loadSystem` directly
- `YukiHookPrefsBridge.isPreferencesAvailable` has been removed, please use `isAvailable` or `isWritable`
- `throwToHostOnFailure` only applies to the callback it is set on. In version `1.3.x`, after setting `onFailureThrowToApp`
- on either `before` or `after`, exceptions in the other callback were also thrown to the host, if you rely on this behavior, please set it on each of them
- `throwToHost` now takes effect in the `intercept` callback. In version `1.3.x`, exceptions set in callbacks such as
- `replaceAny` were overwritten by the return value of the callback. `intercept` now also returns `HookCallback`,
- you can use `throwToHostOnFailure` to throw exceptions to the host instead of calling the original method when they occur

## Learn About New Features

Version `1.5.x` brings the following new features, click to view their detailed usage.

- [libxposed API support](../library/yukihook-runtime-libxposed.md)
- [Chain Hook](../library/yukihook-core/hook-usage.md#chain-hook) (Also compatible with the Xposed 82 base module)
- [Hook Static Initializers](../library/yukihook-core/hook-usage.md#hook-static-initializers)
- [Replace Hook Callbacks](../library/yukihook-core/hook-usage.md#replace-hook-callbacks)
- [Deoptimize](../library/yukihook-core/hook-usage.md#deoptimize)
- [Module Shared Files](../library/yukihook-core/shared-files.md)
- [Module Scope](../library/yukihook-core/module-status.md#module-scope)
- [Module Hot Reload](../library/yukihook-core/hot-reload.md)