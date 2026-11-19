# Lint Rules

> To help developers discover potential problems at compile time and keep API usage consistent, `YukiHook` provides a built-in set of Android Lint rules.

## Rule List

The following are all Lint rules currently in effect.

### yukihook-core

<div class="lint-rules-table">

| Issue ID                                                                                                                                                     | Category       | Severity        | Priority | Brief Description                                                       |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------- | --------------- | -------- | ----------------------------------------------------------------------- |
| [YukiHookInternalApi](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/InternalApiDetector.kt)                | `CORRECTNESS`  | `ERROR`         | `10`     | YukiHook internal API must not be used directly                         |
| [RequiresYukiHookRuntime](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/RuntimeRequirementDetector.kt)     | `CORRECTNESS`  | `ERROR`         | `10`     | Incompatible YukiHook runtime or Xposed API version                     |
| [NewXposedApi](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/RuntimeRequirementDetector.kt)                | `CORRECTNESS`  | `WARNING`       | `5`      | Using a YukiHook API newer than the module's minimum Xposed API version |
| [InvalidLibXposedApiVersion](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleEntryDetector.kt)         | `CORRECTNESS`  | `ERROR`         | `10`     | Invalid Xposed API version for module entry                             |
| [InvalidLibXposedHotReload](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleEntryDetector.kt)          | `CORRECTNESS`  | `ERROR`         | `10`     | Invalid hot reload configuration for module entry                       |
| [InvalidModuleEntryClassName](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleEntryDetector.kt)        | `CORRECTNESS`  | `ERROR`         | `10`     | Invalid generated entry class name for module entry                     |
| [SystemFrameworkInScope](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleEntryDetector.kt)             | `CORRECTNESS`  | `WARNING`       | `5`      | Framework package used as the system framework scope                    |
| [ModuleRegistrationOutsideOnInit](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)  | `CORRECTNESS`  | `ERROR`         | `10`     | Module registration must run during onInit                              |
| [InvalidModuleHookLoading](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)         | `CORRECTNESS`  | `ERROR`         | `10`     | Invalid module hook loading                                             |
| [InvalidModuleRetainedValue](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)       | `CORRECTNESS`  | `ERROR`         | `10`     | Invalid retained module value                                           |
| [LoadAppForSystemFramework](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)        | `CORRECTNESS`  | `WARNING`       | `5`      | System framework loaded through loadApp                                 |
| [SystemFrameworkInLoadAppPackages](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt) | `CORRECTNESS`  | `ERROR`         | `10`     | System framework mixed into loadApp package names                       |
| [ProcessNameForSystemFramework](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)    | `CORRECTNESS`  | `ERROR`         | `10`     | Framework package name used as the system server process name           |
| [DataChannelForSystemFramework](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)    | `CORRECTNESS`  | `WARNING`       | `5`      | System framework connected through dataChannel                          |
| [NonReplayableCallInAppLifecycle](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)  | `CORRECTNESS`  | `WARNING`       | `5`      | Non-replayable call in an app lifecycle callback                        |
| [InvalidApplicationReadyNesting](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleRuntimeDetector.kt)   | `CORRECTNESS`  | `ERROR`         | `10`     | Invalid withApplicationReady nesting                                    |
| [ConflictingClassicHookCallbacks](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/MemberHookerDetector.kt)   | `CORRECTNESS`  | `ERROR`         | `10`     | Conflicting classic hook callbacks                                      |
| [InvalidFieldHookTarget](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/MemberHookerDetector.kt)            | `CORRECTNESS`  | `ERROR`         | `10`     | Field used as a hook target                                             |
| [UseModuleApplication](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleUsageDetector.kt)               | `CORRECTNESS`  | `WARNING`       | `5`      | Inherit ModuleApplication to enable full functionality                  |
| [UseHookInvocationApi](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleUsageDetector.kt)               | `PRODUCTIVITY` | `INFORMATIONAL` | `5`      | Use HookInvocation's API instead of casting                             |
| [UseHookBlock](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/ModuleUsageDetector.kt)                       | `PRODUCTIVITY` | `INFORMATIONAL` | `5`      | Use a hook configuration block instead of apply                         |
| [KavaRefWithoutHostClassLoader](repo://tree/1.x/yukihook-core-lint/src/main/java/com/highcapable/yukihookapi/core/lint/detector/KavaRefFallbackDetector.kt)  | `CORRECTNESS`  | `WARNING`       | `5`      | KavaRef class loaded without the host class loader                      |

</div>

### yukihook-extension-kavaref

<div class="lint-rules-table">

| Issue ID                                                                                                                                                                                | Category       | Severity        | Priority | Brief Description                                       |
| --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- | --------------- | -------- | ------------------------------------------------------- |
| [ClassLoadingWithoutHostClassLoader](repo://tree/1.x/yukihook-extension-kavaref-lint/src/main/java/com/highcapable/yukihookapi/extension/kavaref/lint/detector/PackageParamDetector.kt) | `CORRECTNESS`  | `WARNING`       | `5`      | KavaRef class loaded without the host class loader      |
| [ClassLoadingWithHostClassLoader](repo://tree/1.x/yukihook-extension-kavaref-lint/src/main/java/com/highcapable/yukihookapi/extension/kavaref/lint/detector/PackageParamDetector.kt)    | `PRODUCTIVITY` | `INFORMATIONAL` | `5`      | KavaRef class loaded with an explicit host class loader |

</div>

## How to Disable or Adjust Rules

If you want to disable a specific rule or change its severity, you can create a `lint.xml` file in the project root.

> The following example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <issue id="UseModuleApplication" severity="ignore" />
    <issue id="NewXposedApi" severity="error" />
</lint>
```

Common `severity` values are:

- `ignore`
- `warning`
- `error`
- `fatal`

You can also control them directly in Gradle.

> The following example

```kotlin
android {
    lint {
        disable += "UseModuleApplication"
        error += "NewXposedApi"
    }
}
```

If you only want to check part of the rules, you can also use `checkOnly`.

> The following example

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

## Feedback

Currently, most Lint detectors are completed by AI Agent, and there may still be issues, such as complex UAST scenarios that have not been tested.
If you find that a Lint rule is misreporting or the Quick Fix cannot correctly fix the problem during use, you can provide an Issue ID to directly feedback to us on [GitHub Issues](repo://issues).

If you think some rules are not reasonable and overly restrictive on the code, or if you have some new rule suggestions,
please also feel free to provide feedback to us, and we will evaluate and adjust them.