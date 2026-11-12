# 迁移至 YukiHook 1.5.x

> YukiHook 从 `1.5.0` 版本开始进行了全面的重构，你可以继续向下阅读以查看有哪些注意事项和新功能。

::: warning

此前的 `1.2.x` (2023 年发布) 和 `1.3.x` (2025 年发布) 的迁移文档因为过于久远已经被移除，此文档为基于从 `1.3.x` 版本更新而来的用户提供，若你正在使用更早的版本，请先参考 [更新日志](../about/changelog.md) 了解每个版本的变更。

`1.5.x` 保留了大部分旧版兼容写法，它们已被标记为弃用，你可以根据 IDE 的提示完成大部分迁移，这些兼容写法将会在未来的版本中被移除。

:::

## 项目更名

这是目前最大的变更，项目从 **YukiHookAPI** 正式更名为 **YukiHook**。

入口对象 `YukiHookAPI` 已被弃用，请迁移到 `YukiHook`。

> 示例如下

```kotlin
// 旧版写法
YukiHookAPI.encase { ... }
YukiHookAPI.Status.isModuleActive
YukiHookAPI.VERSION
// 新版写法
YukiHook.encase { ... }
YukiHook.Status.isModuleActive
YukiHook.VERSION
```

为了保持兼容，依赖的 `groupId` 与代码的包名依然为 `com.highcapable.yukihookapi`，你基本不需要修改 `import`。

## 依赖变更

YukiHook 现在被拆分为多个模块，你需要引入一个底座模块来决定对接哪一套 Xposed API。

| 旧版依赖                                 | 新版依赖                                                                                                                                       |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `com.highcapable.yukihookapi:api`        | [yukihook-core](../library/yukihook-core.md)                                                                                                   |
| `com.highcapable.yukihookapi:ksp-xposed` | [yukihook-compiler](../library/yukihook-compiler.md)                                                                                           |
| `de.robv.android.xposed:api`             | [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md) 或 [yukihook-runtime-libxposed](../library/yukihook-runtime-libxposed.md) |

如果你希望保持与之前完全一致的运行环境，请选择 [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md)，如果你的目标 Hook 框架支持 libxposed API (例如 LSPosed)，推荐使用 [yukihook-runtime-libxposed](../library/yukihook-runtime-libxposed.md)。

如果你希望同时兼容多个底座，请参考 [多入口变体](../library/yukihook-core/hook-entry/multi-entry-variants.md)。

YukiHook 现已使用 BOM 统一管理依赖版本，请参考 [yukihook-bom](../library/yukihook-bom.md)。

KavaRef 相关功能已从 [yukihook-core](../library/yukihook-core.md) 中移除，详情请参考下方的 [关于 KavaRef](#关于-kavaref)。

## 关于 KavaRef

在 `1.3.x` 版本中，YukiHook 直接依赖 KavaRef 并在 `PackageParam` 中提供了 KavaRef 相关的方法，现在它们已从 [yukihook-core](../library/yukihook-core.md) 中移除并迁移至 [yukihook-extension-kavaref](../library/yukihook-extension-kavaref.md)。

若你在项目中使用了 KavaRef，请引入这个扩展模块，此模块不会传递 KavaRef 的依赖，引入方式请参考 [这里](../library/yukihook-extension-kavaref.md#配置依赖)。

在 `1.3.x` 版本中，下方的大部分方法是 `PackageParam` 的成员方法，无需导入即可使用，现在你需要手动导入它们，`...hook.core.api.reflect` 下的方法与类也需要迁移到新的包名。

<div class="migration-table">

| 旧版 API                                                                                    | 新版 API                                                                                |
| ------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `PackageParam` 中的 `MemberResolver.hook`、`hookAll`                                        | `com.highcapable.yukihookapi.extension.kavaref.hook`、`hookAll`                         |
| `PackageParam` 中的 `String.toClass`、`toClassOrNull`                                       | `com.highcapable.yukihookapi.extension.kavaref.toClass`、`toClassOrNull`                |
| `PackageParam` 中的 `VariousClass.toClass`、`toClassOrNull`                                 | `com.highcapable.yukihookapi.extension.kavaref.toClass`、`toClassOrNull`                |
| `PackageParam` 中的 `lazyClass`、`lazyClassOrNull`                                          | `com.highcapable.yukihookapi.extension.kavaref.lazyClass`、`lazyClassOrNull`            |
| `com.highcapable.yukihookapi.hook.core.api.reflect.invokeOriginal`、`invokeOriginalQuietly` | `com.highcapable.yukihookapi.extension.kavaref.invokeOriginal`、`invokeOriginalQuietly` |
| `com.highcapable.yukihookapi.hook.core.api.reflect.AndroidHiddenApiBypassResolver`          | `com.highcapable.yukihookapi.extension.kavaref.resolver.AndroidHiddenApiBypassResolver` |

</div>

> 示例如下

```kotlin
// 旧版写法
import com.highcapable.yukihookapi.hook.core.api.reflect.AndroidHiddenApiBypassResolver
import com.highcapable.yukihookapi.hook.core.api.reflect.invokeOriginal
// 新版写法
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.invokeOriginal
import com.highcapable.yukihookapi.extension.kavaref.resolver.AndroidHiddenApiBypassResolver
import com.highcapable.yukihookapi.extension.kavaref.toClass
```

`AndroidHiddenApiBypassResolver` 不再提供 `get` 方法，请直接创建它的实例。

> 示例如下

```kotlin
// 旧版写法
resolve().processor(AndroidHiddenApiBypassResolver.get())
// 新版写法
resolve().processor(AndroidHiddenApiBypassResolver())
```

这些方法现在只能在 `PackageParam` 中使用，在 `1.3.x` 版本中可以在任意位置使用的 `invokeOriginal` 同样如此。

::: danger

KavaRef 中的 `toClass`、`toClassOrNull` 以及 `lazyClass`、`lazyClassOrNull` 与此模块提供的方法同名，请不要在同一个文件中同时导入它们，否则将出现重载歧义。

若你只导入了 KavaRef 中的这些方法，代码虽然可以通过编译，但它们在未传入宿主的 `ClassLoader` 时会使用模块的 `ClassLoader`，这可能会导致无法找到宿主中的 `Class`。

:::

## 入口变更

`@InjectYukiHookWithXposed` 与 `IYukiHookXposedInit` 已被弃用，请迁移到与底座模块对应的入口注解与 `YukiHookXposedModule`。

> 示例如下

```kotlin
// 旧版写法
@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = false
    }

    override fun onHook() = encase {
        // Your code here.
    }
}
// 新版写法
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

`@InjectYukiHookWithXposed` 中的参数变更如下。

| 旧版参数                    | 新版参数                                             |
| --------------------------- | ---------------------------------------------------- |
| `sourcePath`                | 已移除，编译器将自动查找项目路径                     |
| `modulePackageName`         | `@YukiHookXposed82Entry` 中的 `modulePackageName`    |
| `entryClassName`            | 两个入口注解中的 `entryClassName`                    |
| `isUsingXposedModuleStatus` | `@YukiHookXposed82Entry` 中的 `generateModuleStatus` |
| `isUsingResourcesHook`      | 已移除，Resources Hook 已不再被支持                  |

::: danger

入口文件现在会按照每个构建变体生成在构建目录中，请手动删除源码目录中旧版本生成的 `assets/xposed_init`、`assets/yukihookapi_init` 与 `resources/META-INF/yukihookapi_init`，否则会导致编译失败。

:::

`onXposedEvent` 与 `YukiXposedEvent.events` 已不再被回调，请迁移到底座模块提供的 `registerFrameworkEvents`，并在 `onInit` 中调用它。

> 示例如下

```kotlin
// 旧版写法
override fun onXposedEvent() {
    YukiXposedEvent.events {
        onHandleLoadPackage {
            // ...
        }
    }
}
// 新版写法
override fun onInit() {
    registerFrameworkEvents {
        onHandleLoadPackage {
            // ...
        }
    }
}
```

详情请参考 [yukihook-runtime-xposed82 → 框架事件](../library/yukihook-runtime-xposed82.md#框架事件) 与 [yukihook-runtime-libxposed → 框架事件](../library/yukihook-runtime-libxposed.md#框架事件)

## 配置变更

`YukiHookAPI.configs` 已被弃用，请迁移到 `YukiHook.configure`。

| 旧版配置                          | 新版配置                |
| --------------------------------- | ----------------------- |
| `debugLog { ... }`                | `logging { ... }`       |
| `isDebug`                         | `debug`                 |
| `isEnableDataChannel`             | `dataChannel`           |
| `isEnableHookSharedPreferences`   | `hookSharedPreferences` |
| `isEnableModuleAppResourcesCache` | 已弃用，不再有任何作用  |
| `YLog.Configs.isEnable`           | `YLog.Config.enabled`   |
| `YLog.Configs.isRecord`           | `YLog.Config.recording` |

## 移除的功能

以下功能已在 `1.5.x` 版本中被移除或不再生效。

- **Resources Hook**：`resources().hook { ... }` 已被移除，`handleInitPackageResources` 事件将不会再被回调
- **自身反射 API**：自 `1.3.0` 版本中弃用的 `method { ... }`、`current()`、`searchClass` 以及 `StringClass` 等类型定义已被移除，请迁移至 [KavaRef](https://github.com/HighCapable/KavaRef)，详情请参考 [这里](https://highcapable.github.io/KavaRef/zh-cn/config/migration)
- **旧版 Hook 写法**：`TargetClass.hook { injectMember { ... } }` 已被移除，请参考 [Hook 用法](../library/yukihook-core/hook-usage.md)
- **作为 Hook API 使用**：`YukiHook.encase(baseContext)` 仅在 [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82) 中可用且已被弃用，请使用 Hook 框架原生的 API
- **loadZygote**：仅在 [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82) 中可用且已被弃用，请将对应用的 Hook 迁移到 `loadApp`，将对系统框架的 Hook 迁移到 `loadSystem`
- **太极、无极激活状态判断**：太极已于 2024.05.08 停止维护，`YukiHookAPI.Status.isTaiChiModuleActive` 已被弃用，`isXposedModuleActive` 的状态已迁移至 `isModuleActive`，它将不再包含模块在太极、无极中的激活状态
- **Hook 框架类型**：因为其存在脏状态维护问题，已不再支持，`ExecutorType` 已被移除，`YukiHookAPI.Status.Executor.type` 已无法使用，请使用 `YukiHook.Status.Executor.name` 获取 Hook 框架的名称
- **Hook 相关类型**：`YukiMemberHookCreator` 以及其中的 `MemberHookCreator`、`MemberHookCreator.Result`、`MemberHookCreator.HookCallback` 已被移除，若你在代码中显式声明了这些类型，请分别迁移至 `YukiHookCreator.ClassicMemberHooker`、`YukiHookCreator.ClassicMemberHooker.Handle`、`YukiHookCreator.ClassicMemberHooker.HookCallback`

## 项目构建工具

由于现代 Android 开发模板的快速迭代给项目维护带来了较大影响，[YukiHookAPI-ProjectBuilder](https://github.com/HighCapable/YukiHookAPI-ProjectBuilder) 的价值已消失，现在这个项目已正式停止维护并被弃用，请参考 [快速开始](../guide/quick-start.md) 手动配置项目。

## 重命名的 API

以下 API 已被重命名，旧的名称已被标记为弃用。

<div class="migration-table">

| 旧版 API                                          | 新版 API                                         |
| ------------------------------------------------- | ------------------------------------------------ |
| `prefs`、`Context.prefs()`                        | `preferences`、`Context.preferences()`           |
| `YukiHookPrefsBridge`                             | `YukiHookPreferences`                            |
| `PrefsData`                                       | `PreferenceData`                                 |
| `...prefs.ui.ModulePreferenceFragment`            | `...preference.ui.ModulePreferenceFragment`      |
| `onAppLifecycle(isOnFailureThrowToApp)`           | `registerAppLifecycle(throwToHostOnFailure)`     |
| `Context.registerModuleAppActivities()`           | `Context.registerModuleActivities()`             |
| `Context.injectModuleAppResources()`              | `Context.injectModuleResources()`                |
| `appClassLoader`                                  | `hostClassLoader`                                |
| `appContext`                                      | `hostApplication`                                |
| `appUserId`                                       | `userId`                                         |
| `appResources`                                    | `hostApplication?.resources`                     |
| `moduleAppResources`                              | `module.resources`                               |
| `moduleAppFilePath`                               | `module.sourceDir`                               |
| `refreshModuleAppResources()`                     | `module.reloadResources()`                       |
| `replaceAny`、`replaceUnit`、`replaceTo` 等       | `intercept`                                      |
| `args(index)`、`args().first()`、`args().last()`  | `arg(index)`、`firstArg()`、`lastArg()`          |
| `cast<T>()`、`int()`、`string()` 等               | `get<T>()`                                       |
| `resultTrue()`、`resultFalse()`、`resultNull()`   | `result = ...`                                   |
| `instanceClass`                                   | `instanceOrNull?.javaClass`                      |
| `Throwable.throwToApp()`                          | `Throwable.throwToHost()`                        |
| `onFailureThrowToApp()`                           | `throwToHostOnFailure()`                         |
| `result { ... }`                                  | `apply { ... }`                                  |
| `onConductFailure`、`ignoredConductFailure`       | `onCallbackFailure`、`ignoreCallbackFailure`     |
| `onAllFailure`、`ignoredAllFailure`               | `onInvocationFailure`、`ignoreInvocationFailure` |
| `checkingVersionEquals`                           | `checkVersionMatch`                              |
| `allowSendTooLargeData()`                         | `allowOversizedData()`                           |
| `dataMaxByteSize`、`dataMaxByteCompressionFactor` | `maxDataSizeBytes`、`segmentSizeDivisor`         |
| `YukiHookAPI.Status.isXposedEnvironment`          | `YukiHook.Status.isHostEnvironment`              |
| `YukiHookAPI.Status.Executor.apiLevel`            | `YukiHook.Status.Executor.apiVersion`            |
| `YukiHookAPI.Status.Executor.versionName`         | `YukiHook.Status.Executor.version`               |
| `YLog.EnvType.XPOSED_ENVIRONMENT`                 | `YLog.EnvType.XPOSED`                            |
| `ModuleApplication.appContext`                    | `ModuleApplication.requireContext()`             |

</div>

`onHooked`、`onHookingFailure`、`ignoredHookingFailure` 以及 `by { ... }` 已不再生效，Hook 注册失败时将直接打印到日志中，请直接移除这些调用。

## 行为变更

- `loadApp` 不再包含系统框架，在 `loadApp` 中传入 `android` 或 `system` 时将会被视为 `loadSystem`，在多个包名中混入它们将在运行时抛出异常，请直接使用 `loadSystem`
- `YukiHookPrefsBridge.isPreferencesAvailable` 已被移除，请使用 `isAvailable` 或 `isWritable`
- `throwToHostOnFailure` 只对设置它的回调生效，在 `1.3.x` 版本中，为 `before` 或 `after` 其中之一设置 `onFailureThrowToApp` 后，另一个回调中的异常同样会抛给宿主，若你依赖这种行为，请分别为它们设置
- `intercept` 回调中的 `throwToHost` 现在会生效，在 `1.3.x` 版本中，`replaceAny` 等回调中设置的异常会被回调的返回值覆盖，`intercept` 现在也会返回 `HookCallback`，你可以使用 `throwToHostOnFailure` 在发生异常时将其抛给宿主而不是调用原始方法

## 了解新功能

`1.5.x` 版本带来了以下新功能，你可以点击查看详细的用法。

- [libxposed API 支持](../library/yukihook-runtime-libxposed.md)
- [链式 Hook](../library/yukihook-core/hook-usage.md#链式-hook) (同时兼容 Xposed 82 底座)
- [Hook 静态初始化](../library/yukihook-core/hook-usage.md#hook-静态初始化)
- [替换 Hook 回调](../library/yukihook-core/hook-usage.md#替换-hook-回调)
- [解除内联化](../library/yukihook-core/hook-usage.md#解除内联化)
- [模块共享文件](../library/yukihook-core/shared-files.md)
- [模块作用域](../library/yukihook-core/module-status.md#模块作用域)
- [模块热重载](../library/yukihook-core/hot-reload.md)