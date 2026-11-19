# Lint 静态检查规范

> 为了帮助开发者在编译期及时发现潜在问题、规范 API 调用，`YukiHook` 内置了一套 Android Lint 规则。

## 规则列表

以下是目前生效的所有 Lint 规则列表。

### yukihook-core

<div class="lint-rules-table">

| Issue ID                                                                                                                                                     | 类别           | 级别            | 优先级 | 简要描述                                                                |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- | --------------- | ------ | ----------------------------------------------------------------------- |
| [YukiHookInternalApi](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/InternalApiDetector.kt)                | `CORRECTNESS`  | `ERROR`         | `10`   | YukiHook internal API must not be used directly                         |
| [RequiresYukiHookRuntime](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/RuntimeRequirementDetector.kt)     | `CORRECTNESS`  | `ERROR`         | `10`   | Incompatible YukiHook runtime or Xposed API version                     |
| [NewXposedApi](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/RuntimeRequirementDetector.kt)                | `CORRECTNESS`  | `WARNING`       | `5`    | Using a YukiHook API newer than the module's minimum Xposed API version |
| [InvalidLibXposedApiVersion](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleEntryDetector.kt)         | `CORRECTNESS`  | `ERROR`         | `10`   | Invalid Xposed API version for module entry                             |
| [InvalidLibXposedHotReload](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleEntryDetector.kt)          | `CORRECTNESS`  | `ERROR`         | `10`   | Invalid hot reload configuration for module entry                       |
| [InvalidModuleEntryClassName](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleEntryDetector.kt)        | `CORRECTNESS`  | `ERROR`         | `10`   | Invalid generated entry class name for module entry                     |
| [SystemFrameworkInScope](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleEntryDetector.kt)             | `CORRECTNESS`  | `WARNING`       | `5`    | Framework package used as the system framework scope                    |
| [ModuleRegistrationOutsideOnInit](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)  | `CORRECTNESS`  | `ERROR`         | `10`   | Module registration must run during onInit                              |
| [InvalidModuleHookLoading](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)         | `CORRECTNESS`  | `ERROR`         | `10`   | Invalid module hook loading                                             |
| [InvalidModuleRetainedValue](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)       | `CORRECTNESS`  | `ERROR`         | `10`   | Invalid retained module value                                           |
| [LoadAppForSystemFramework](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)        | `CORRECTNESS`  | `WARNING`       | `5`    | System framework loaded through loadApp                                 |
| [SystemFrameworkInLoadAppPackages](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt) | `CORRECTNESS`  | `ERROR`         | `10`   | System framework mixed into loadApp package names                       |
| [ProcessNameForSystemFramework](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)    | `CORRECTNESS`  | `ERROR`         | `10`   | Framework package name used as the system server process name           |
| [DataChannelForSystemFramework](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)    | `CORRECTNESS`  | `WARNING`       | `5`    | System framework connected through dataChannel                          |
| [NonReplayableCallInAppLifecycle](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)  | `CORRECTNESS`  | `WARNING`       | `5`    | Non-replayable call in an app lifecycle callback                        |
| [InvalidApplicationReadyNesting](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)   | `CORRECTNESS`  | `ERROR`         | `10`   | Invalid withApplicationReady nesting                                    |
| [ConflictingClassicHookCallbacks](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/MemberHookerDetector.kt)   | `CORRECTNESS`  | `ERROR`         | `10`   | Conflicting classic hook callbacks                                      |
| [InvalidFieldHookTarget](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/MemberHookerDetector.kt)            | `CORRECTNESS`  | `ERROR`         | `10`   | Field used as a hook target                                             |
| [UseModuleApplication](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleUsageDetector.kt)               | `CORRECTNESS`  | `WARNING`       | `5`    | Inherit ModuleApplication to enable full functionality                  |
| [UseHookInvocationApi](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleUsageDetector.kt)               | `PRODUCTIVITY` | `INFORMATIONAL` | `5`    | Use HookInvocation's API instead of casting                             |
| [UseHookBlock](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleUsageDetector.kt)                       | `PRODUCTIVITY` | `INFORMATIONAL` | `5`    | Use a hook configuration block instead of apply                         |
| [KavaRefWithoutHostClassLoader](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/KavaRefFallbackDetector.kt)  | `CORRECTNESS`  | `WARNING`       | `5`    | KavaRef class loaded without the host class loader                      |

</div>

### yukihook-extension-kavaref

<div class="lint-rules-table">

| Issue ID                                                                                                                                                                                | 类别           | 级别            | 优先级 | 简要描述                                                |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | --------------- | ------ | ------------------------------------------------------- |
| [ClassLoadingWithoutHostClassLoader](repo://tree/1.x/yukihook-extension-kavaref-lint/src/main/java/com/highcapable/yukihookapi/extension/kavaref/lint/detector/PackageParamDetector.kt) | `CORRECTNESS`  | `WARNING`       | `5`    | KavaRef class loaded without the host class loader      |
| [ClassLoadingWithHostClassLoader](repo://tree/1.x/yukihook-extension-kavaref-lint/src/main/java/com/highcapable/yukihookapi/extension/kavaref/lint/detector/PackageParamDetector.kt)    | `PRODUCTIVITY` | `INFORMATIONAL` | `5`    | KavaRef class loaded with an explicit host class loader |

</div>

## 如何关闭或调整规则

如果你想关闭某一条规则，或将某一条规则调整为其它级别，可以在项目根目录创建 `lint.xml`。

> 示例如下

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <issue id="UseModuleApplication" severity="ignore" />
    <issue id="NewXposedApi" severity="error" />
</lint>
```

其中 `severity` 常用值如下：

- `ignore`
- `warning`
- `error`
- `fatal`

你也可以直接在 Gradle 中进行控制。

> 示例如下

```kotlin
android {
    lint {
        disable += "UseModuleApplication"
        error += "NewXposedApi"
    }
}
```

当你只想检查部分规则时，也可以使用 `checkOnly`。

> 示例如下

```kotlin
android {
    lint {
        checkOnly += setOf(
            "UseModuleApplication",
            "NewXposedApi"
        )
    }
}
```

## 问题反馈

目前大部分 Lint 检测器均由 AI Agent 代为完成，可能仍然存在问题，例如尚未测试过的复杂 UAST 语法树场景。如果你在使用过程中发现了 Lint 规则出现了误报或 Quick Fix 无法正确修复问题，可以通过提供 Issue ID 在 [GitHub Issues](repo://issues) 直接向我们反馈。

如果你认为一些规则不够合理出现过于约束代码的情况，或者你有一些新的规则建议，也欢迎向我们反馈，我们会进行评估和调整。