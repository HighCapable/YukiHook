# Changelog

> The version update history of `YukiHook` is recorded here.

::: danger

We will only maintain the latest API version, if you are using an outdated API version, you voluntarily renounce any possibility of maintenance.

:::

::: warning

Time zone of version release date: **UTC+8**

:::

### 1.5.1 | 2026.11.20 &ensp;<Badge type="latest" text="latest" vertical="middle" />

- Added a YukiHook Agent Skill to guide AI coding assistants through module setup, API usage and migration. See [Agent Skills](https://github.com/HighCapable/YukiHook/blob/1.x/README.md#agent-skills) for details

#### yukihook-core

- <u>**⚠️ Breaking Change:**</u> Editing the module's shared preferences in a host now throws `UnsupportedOperationException`. Check `isWritable` before editing
- <u>**⚠️ Breaking Change:**</u> `ModulePreferenceFragment.onCreatePreferences` is now final and `onCreatePreferencesInModuleApp` has been removed. Move your initialization code to `onPreferencesReady`
- <u>**⚠️ Breaking Change:**</u> `Method.invokeSpecial<T>()` now returns `T?`. Handle possible `null` results
- <u>**⚠️ Breaking Change:**</u> Use `hookAllByMember` from Java to call `Collection<Member>.hookAll` with a configuration block. Kotlin callers only need to recompile
- Debug logs now consistently show the process name. Deprecated ~~`YLog.Config.PACKAGE_NAME`~~ and ~~`YLogData.packageName`~~, please use `YLog.Config.PROCESS_NAME` and `YLogData.processName` now
- Shared preferences now use temporary in-memory storage when unavailable. These changes are lost when the process exits and are never written back when persistent storage becomes available. Check `isAvailable` before relying on persistent storage
- Added `Fragment.dataChannel` and `Fragment.systemDataChannel` so fragments in the same `Activity` can listen to the same key independently. Listeners are removed when their views are destroyed
- The data channel now delivers messages to the most recently resumed module `Activity` in each task, including tasks in the background
- Added `ArgumentEditor.getOrNull<T>()` for classic hooks to return `null` when the argument is missing or its type does not match
- Added `argOrNull`, `firstArgOrNull` and `lastArgOrNull` for safely reading optional arguments in chain hooks
- `registerModuleLifecycle` can now be called from `onInit` with the Xposed 82 base module. Hot reload still requires a base module that supports it
- Fixed a registration failure for `registerModuleLifecycle` and `registerFrameworkEvents` when `onInit` is delegated
- Calling the module overloads of `encase` outside the entry point's `onHook` now throws an exception
- Fixed application lifecycle broadcast callbacks ignoring the `throwToHostOnFailure` setting
- Fixed a host crash when an application lifecycle callback encounters an auxiliary `Application` without an attached `Context`
- Fixed an issue where a listener exception for a segmented data channel message was reported as a failure to reassemble the message
- Fixed an exception when injecting module resources on Android 11 and above if the module APK path is unavailable
- Fixed a possible host crash when hot reload ran initialization code with unavailable module resources
- Fixed an issue that kept old module classes in memory after hot reload because of `Parcelable` caching, and a state restoration failure when hot reloading from `1.5.0` to `1.5.1`
- Improved [Lint Rules](../config/lint-rules.md) for module entry calls and APIs specific to a base module
- Extended `UseHookInvocationApi` with suggestions and quick fixes for argument access, return values and continuing the hook chain
- Added `UseHookBlock` to suggest configuring hooks with `hook { ... }` and `hookAll { ... }`
- Added `UseApplicationReady` to suggest using `withApplicationReady`, which supports hot reload, when lifecycle hooks are used to obtain the host's `Application`

#### yukihook-compiler

- Fixed a build failure when generating the libxposed module entry on JDK versions below 21

#### yukihook-extension-kavaref

- <u>**⚠️ Breaking Change:**</u> `MethodResolver.invokeSpecial<T>()` now returns `T?`. Handle possible `null` results
- Added support for finding hidden fields on Android 9 and above with `AndroidHiddenApiBypassResolver`
- Fixed incorrect Lint warnings about the host class loader for some KavaRef calls

#### yukihook-runtime-libxposed

- `YukiHook.Status.observeFrameworkService` is now annotated with `@MainThread`. Register listeners on the main thread
- Fixed an exception in `onModuleLoaded` causing a successfully initialized module to be marked as failed

#### yukihook-runtime-xposed82

- Fixed `onInitZygote` being called after module initialization failed

### 1.5.0 | 2026.10.20 &ensp;<Badge type="warning" text="stale" vertical="middle" />

- This is a major update, the project has been officially renamed to YukiHook and is now published as multiple modules, please refer to [Migration to YukiHook 1.5.x](../config/migration-1-5-x.md) for details
- All modules are released with a unified version, and the `groupId` of the dependencies and the package name of the code are still `com.highcapable.yukihookapi`
- Added [yukihook-bom](../library/yukihook-bom.md) for unified version management of all modules
- Added the base modules [yukihook-runtime-libxposed](../library/yukihook-runtime-libxposed.md) and [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md) for [libxposed API](https://github.com/libxposed) and [Xposed Framework API](https://api.xposed.info), only one of them can be added to a build variant
- Existing Xposed modules can keep running on their existing hook frameworks with [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md), its `AndroidManifest.xml` already declares `xposedmodule` and `xposedminversion`
- [yukihook-runtime-libxposed](../library/yukihook-runtime-libxposed.md) supports remote `SharedPreferences`, shared files, the module scope and module hot reload
- Added the extension module [yukihook-extension-kavaref](../library/yukihook-extension-kavaref.md) for using [KavaRef](https://github.com/HighCapable/KavaRef) related features in YukiHook, you need to add the KavaRef dependencies yourself

#### yukihook-bom

- The first version is submitted to Maven

#### yukihook-core

- <u>**⚠️ Breaking Change:**</u> Renamed the `api` dependency to [yukihook-core](../library/yukihook-core.md), it now needs [yukihook-compiler](../library/yukihook-compiler.md) and one base module to work
- <u>**⚠️ Breaking Change:**</u> Removed Resources Hook, `resources().hook { ... }` is no longer available
- <u>**⚠️ Breaking Change:**</u> Removed the own reflection API deprecated in version `1.3.0` and old hook styles such as `injectMember`, please migrate to [KavaRef](https://github.com/HighCapable/KavaRef) now
- <u>**⚠️ Breaking Change:**</u> `onXposedEvent` and `YukiXposedEvent` are no longer called, please use `registerFrameworkEvents` provided by the base module now
- <u>**⚠️ Breaking Change:**</u> `loadApp` now throws an exception when `android` or `system` is mixed into its multiple package names, please use `loadSystem` now
- <u>**⚠️ Breaking Change:**</u> KavaRef related hook functions and `String.toClass`, `VariousClass.toClass` and `lazyClass` have been moved to [yukihook-extension-kavaref](../library/yukihook-extension-kavaref.md)
- <u>**⚠️ Breaking Change:**</u> `AndroidHiddenApiBypassResolver` has been moved to [yukihook-extension-kavaref](../library/yukihook-extension-kavaref.md) and `AndroidHiddenApiBypassResolver.get()` has been removed, please create its instance directly now
- <u>**⚠️ Breaking Change:**</u> Removed `ExecutorType`, `YukiHookAPI.Status.Executor.type` can no longer be used, please use `YukiHook.Status.Executor.name` to get the name of the hook framework now
- <u>**⚠️ Breaking Change:**</u> `throwToHostOnFailure` now only applies to the callback it is set on, it no longer applies to both `before` and `after`
- Deprecated ~~`YukiHookAPI`~~, please use `YukiHook` now
- Deprecated ~~`@InjectYukiHookWithXposed`~~ and ~~`IYukiHookXposedInit`~~, please use the entry annotation of your base module and `YukiHookXposedModule` now
- Deprecated ~~`YukiHookAPI.configs`~~, please use `YukiHook.configure` now, all config properties have also been renamed
- Deprecated ~~`prefs`~~, ~~`YukiHookPrefsBridge`~~ and ~~`PrefsData`~~, please use `preferences`, `YukiHookPreferences` and `PreferenceData` now
- Deprecated ~~`onAppLifecycle`~~, ~~`registerModuleAppActivities`~~ and ~~`injectModuleAppResources`~~, please use `registerAppLifecycle`, `registerModuleActivities` and `injectModuleResources` now
- Deprecated ~~`loadZygote`~~ and ~~`YukiHook.encase(baseContext)`~~, they are now only available in [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md)
- Deprecated many old functions in `HookParam` and hook callbacks, please use new functions such as `arg`, `firstArg`, `lastArg`, `intercept` and `throwToHost` now
- Added chain hooks, you can use `intercept` and `HookChain` to continue the chain with `proceed`, when an exception occurs in the callback, the invocation continues as if this hook did not exist by default, and you can also use `throwToHostOnFailure` to throw the exception to the caller
- `intercept` of classic hooks now returns `HookCallback`, you can use `throwToHostOnFailure` to throw exceptions to the host, and `throwToHost` now also takes effect in `intercept`
- Added `Handle.replace` to replace registered callbacks without hooking again
- Added hooking static initializers, deoptimizing, and calling functions such as `invokeSpecial`, `newInstanceOriginal` and `newInstanceSpecial`
- Added `withApplicationReady` to run code when the `Application` of the host is available, it is called again after the module hot reloads
- Added the `exported` parameter to `registerReceiver` in `AppLifecycle`, the receivers are unregistered and registered again automatically when the module hot reloads
- Added `PackageParam.ModuleSpec` features such as `module.resources` and `module.sourceDir` to get information about the current module
- Added `YukiHookSharedFiles` to share files between the module and hosts
- Added `YukiHookModuleScope` to query and request the scope of the module in the module
- Added module hot reload, together with `YukiHookModuleReload`, `registerModuleLifecycle` and `remember`
- Added `YukiHook.Status.observeFrameworkService` and `YukiHook.Status.runtime`
- Added a set of [Lint Rules](../config/lint-rules.md) to find wrong API calls at compile time
- Fixed an issue where the original method ran twice when it threw an exception itself while being called after an exception occurred in the `intercept` callback
- Fixed an issue where exceptions in `onInit` in the module process were not printed
- Adjusted the exception handling, it now only protects the host and not the module, exceptions in the module process are thrown directly while internal exceptions in the host process are still printed to the log

#### yukihook-compiler

- <u>**⚠️ Breaking Change:**</u> Renamed the `ksp-xposed` dependency to [yukihook-compiler](../library/yukihook-compiler.md)
- <u>**⚠️ Breaking Change:**</u> Removed the `sourcePath` parameter, entry and configuration files are now generated in the build directory for each build variant, and the build is rejected when these files exist in the source directory
- Added processing of the `@YukiHookLibXposedEntry` and `@YukiHookXposed82Entry` entry annotations, and checks whether the entry annotation matches the base module
- Added generating `java_init.list`, `module.prop`, `scope.list` and `native_init.list` in `META-INF/xposed` for libxposed API

#### yukihook-extension-kavaref

- The first version is submitted to Maven

#### yukihook-runtime-libxposed

- The first version is submitted to Maven

#### yukihook-runtime-xposed82

- The first version is submitted to Maven

## Historical Versions

### api & ksp-xposed

#### 1.3.2 | 2026.05.30 &ensp;<Badge type="warning" text="stale" vertical="middle" />

- Updated target SDK version to 37
- Updated dependencies to resolve conflict issues with `BetterAndroid`
- Adjusted API code style without actually changing function signatures

#### 1.3.1 | 2025.09.13 &ensp;<Badge type="warning" text="stale" vertical="middle" />

- Updated dependencies to resolve conflict issues with `BetterAndroid`
- Fixed `Activity` proxy issues on Android 9 and below
- Fixed an issue where return values were still checked when the Hook method return type was `Object`

#### 1.3.0 | 2025.06.25 &ensp;<Badge type="warning" text="stale" vertical="middle" />

- This is a major update
- The reflection API of YukiHook is deprecated, please move to the brand new [KavaRef](https://github.com/HighCapable/KavaRef)
- The limitation of duplicate Hooks has been deprecated, now you can repeat the same method of the Hook
- Deprecated ~~`ModuleAppActivity`~~, ~~`ModuleAppCompatActivity`~~, please use `ModuleActivity` to create your own proxy `Activity`
- `YLog` is now allowed to pass `msg` into any object and will be automatically converted to a string for printing
- `FreeReflection` has been deprecated and has now switched to [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)

#### 1.2.1 | 2024.06.20 &ensp;<Badge type="warning" text="stale" vertical="middle" />

- Catch exceptions in singleton Hooker to prevent it from blocking the entire process
- Add automatic use of "`" in the automatic handler to fix the situation where Kotlin keywords are package names, thanks to [Fengning Zhu](https://github.com/zhufengning) for [PR](https://github.com/HighCapable/YukiHook/pull/70)
- Adapt to Kotlin 2.0.0+, fix the problem that it cannot be compiled during automatic processing, thanks to [xihan123](https://github.com/xihan123) for [PR](https://github.com/HighCapable/YukiHook/pull/76)

#### 1.2.0 | 2023.10.07 &ensp;<Badge type="warning" text="stale" vertical="middle" />

- The license agreement has been changed from `MIT` to `Apache-2.0`, subsequent versions will be distributed under this license agreement, you should change the relevant license agreement after using this version
- This is a breaking update
- Adapted to Android 14, thanks to [BlueCat300](https://github.com/BlueCat300) for [PR](https://github.com/HighCapable/YukiHook/pull/44)
- Fixed `findAllInterfaces` related issues, thanks to [buffcow](https://github.com/buffcow) for [PR](https://github.com/HighCapable/YukiHook/pull/38)
- Fixed the delayed callback problem in the Hook process, thanks to [cesaryuan](https://github.com/cesaryuan) for his [Issue](https://github.com/HighCapable/YukiHook/issues/47)
- Added support for Resources Hook related functions, please refer to this [Issue](https://github.com/HighCapable/YukiHook/issues/36) for details
- Added `YukiHookAPI.TAG`
- Deprecated ~~`YukiHookAPI.API_VERSION_NAME`~~, ~~`YukiHookAPI.API_VERSION_CODE`~~, merged into `YukiHookAPI.VERSION`
- Added `YukiHookAPI.TAG`
- Deprecated ~~`YukiHookAPI.API_VERSION_NAME`~~, ~~`YukiHookAPI.API_VERSION_CODE`~~, merged into `YukiHookAPI.VERSION`
- Deprecated ~~`useDangerousOperation`~~ method in `YukiMemberHookCreator`
- Deprecated ~~`instanceClass`~~ function in `YukiMemberHookCreator`, it is no longer recommended
- Modify `instanceClass` in `HookParam` to be a null safe return value type
- Detach all Hook objects created using `injectMember` to `LegacyCreator`
- Modify `appClassLoader` in `PackageParam` to be a null safe return value type
- Refactor all `logger...` methods to new usage `YLog`
- Removed the `-->` style behind the print log function
- Fixed and improved the problem that the module package name cannot be obtained through KSP after using `namespace`
- Functions such as whether to enable module activation status have now been moved to the `InjectYukiHookWithXposed` annotation
- Detached [FreeReflection](https://github.com/tiann/FreeReflection) will no longer be automatically generated and will be automatically imported as a dependency
- Added a warning log that will be automatically printed when the same Hook method is repeated
- The `findClass(...)` method in `PackageParam` is obsolete, please migrate to the `"...".toClass()` method
- The `String.hook { ... }` method in `PackageParam` is obsolete, and it is recommended to use a new method for Hook
- `AppLifecycle` can now be created repeatedly in different Hookers
- The old version of Hook priority writing is obsolete and migrated to `YukiHookPriority`
- Removed the `tag` function in the Hook process
- Refactored `remendy` functionality in find methods, which now prints exceptions in steps
- The multi-method find result type is changed from `HashSet` to `MutableList`
- Added `method()`, `constructor()`, `field()` to directly obtain all object functions in the class
- `constructor()` no longer behaves like `constructor { emptyParam() }`
- Added `lazyClass` and `lazyClassOrNull` methods to lazily load `Class`

#### 1.1.11 | 2023.04.25 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed a critical issue since `1.1.5` version where the `Member` cache did not take effect and persistent storage eventually caused app out of memory (OOM), thanks to [Art-Chen](https://github.com/Art-Chen)
- Remove the direct cache function of `Member` and deprecated ~~`YukiReflection.Configs.isEnableMemberCache`~~, keep the cache function of `Class`
- Modified finder to `Sequence`, optimize the finding speed and performance of `Member`
- Remove the `YukiHookPrefsBridge`'s direct key-value cache function and removed `LruCache` related functions
- Deprecated ~~`YukiHookAPI.Configs.isEnablePrefsBridgeCache`~~
- Deprecated ~~`direct`~~, ~~`clearCache`~~ functions in `YukiHookPrefsBridge`

#### 1.1.10 | 2023.04.21 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- The `Activity` proxy function adds the function of specifying a separate proxy `Activity` for each proxied `Activity`
- Fixed problem that the `contains` and `all` methods in `YukiHookPrefsBridge` did not judge the `native` function
- Integrate the cache function in `YukiHookPrefsBridge` into `PreferencesCacheManager` and use `LruCache` as a key-value pair cache
- Modify `YukiHookPrefsBridge` key-value pair caching function to take effect in all environments (Module Apps, Host Apps)
- Modify part of `HashMap` used for caching to `ArrayMap` to reduce memory consumption
- Fix some other possible problems

#### 1.1.9 | 2023.04.17 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Change the type of dependent library from **Java Library** (jar) to **Android Library** (aar)
- Remove the inspection function of internal methods and parameters through Hook or reflection API
- Fixed the problem that `YukiHookDataChannel` automatically segmented data sending function could not work normally (exception would still be thrown)
- Added the ability to manually modify the maximum data byte size allowed by `YukiHookDataChannel` to be sent at one time according to the limitations of the target device
- Remove the restriction that `YukiHookDataChannel` can only be used in module `Activity`, now you can use it anywhere
- Modify and standardize the broadcast Action name used by `YukiHookDataChannel`
- Fix the problem that `BadParcelableException` occurs when `YukiHookDataChannel` has different modules with the same host
- Added `ExecutorType`, you can get the type of known Hook Framework through `YukiHookAPI.Status.Executor.type`
- ~~`YukiHookModulePrefs`~~ renamed to `YukiHookPrefsBridge`
- Modify `YukiHookPrefsBridge` to be implemented as a non-singleton, as a singleton may cause data confusion
- Deprecated ~~`Context.modulePrefs(...)`~~ method, please move to `Context.prefs(...)`
- `YukiHookPrefsBridge` adds `native` method, which supports storing private data in modules and hosts directly as native storage
- Integrate the storage method in `YukiHookPrefsBridge` to `YukiHookPrefsBridge.Editor`, please use `edit` method to store data
- `YukiHookPrefsBridge` adds `contains` method
- Cache dynamically created proxy objects in `YukiHookPrefsBridge`, try to fix problems that may cause OOM in the host and modules
- Modify the proxy class of the `Activity` proxy function to be dynamically generated to prevent conflicts caused by injecting different modules into the host
- Fixed some other possible problems

#### 1.1.8 | 2023.02.01 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the problem that the underlying Hook method cannot update the modified state synchronously when modifying parameters such as `result` during callback, thanks to the [Issue](https://github.com/HighCapable/YukiHook/issues/23) of [Yongzheng Lai](https://github.com/elvizlai)
- Move the entry class name file automatically generated by YukiHook from `assets/yukihookapi_init` to `resources/META-INF/yukihookapi_init`
- When only printing the exception stack, the `msg` parameter is allowed to be empty and the `msg` parameter can not be set, and the log with the `msg` parameter left blank will not be logged unless the exception stack is not empty
- Fixed the bug that the log printed by the exception that occurs in the body of the Hook callback method has no specific method information
- `HookParam` adds `instanceOrNull` variable and method, which can be used on the premise that the Hook instance is not sure whether it is empty to prevent the Hook instance from being empty and throw an exception
- Decoupled all hookers in `Member` lookup functionality to `MemberBaseFinder.MemberHookerManager`
- Modified the usage of `by` condition in `YukiMemberHookCreator`, now you can reuse `by` method to set multiple conditions
- Removed wrong `Class` object declaration in Android `type`
- The `registerReceiver` method in `PackageParam.AppLifecycle` adds the function of directly using `IntentFilter` to create a system broadcast listener
- Fixed the problem that there may be multiple registration lifecycles in `PackageParam.AppLifecycle`
- Revert: The 1.1.7 version has been withdrawn due to a serious problem, please update to this version directly (the update log is the same as version 1.1.7)

#### 1.1.6 | 2023.01.21 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the serious problem that `ClassLoader` does not match after `PackageParam` keeps a single instance when there may be multiple package names in the same process when Xposed Module is loaded
- When the package name is not distinguished when there are multiple package names in the same process, stop loading the singleton child Hooker and print a warning message
- Fixed the problem that the number of parameters is incorrect when methods such as `HookParam.callOriginal`, `HookParam.invokeOriginal` call the original method
- Modify the method parameter name `param` of reflection calls in `MethodFinder`, `ConstructorFinder`, `ReflectionFactory` to `args`
- Added the function of judging the parameters of the entry class constructor in the automatic processing program of the Xposed Module, the entry class needs to ensure that it does not have any constructor parameters

#### 1.1.5 | 2023.01.13 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Standardize and optimize the overall code style
- Privatized some APIs called internally
- The underlying API interface is decoupled as a whole to prepare for compatibility with more Hook Frameworks
- Move some of the functions integrated in the API to `ksp-xposed` dependencies (decoupling), and the separate introduction of `api` dependencies will no longer contain references to functions such as third-party libraries
- Documentation [Quick Start](../guide/quick-start) page added instructions on when `YukiHookAPI.Configs.isDebug` needs to be closed
- Standardize Java Primitive Types in type definitions and sync update to docs
- Java `type` adds `NumberClass` type
- Improved (Xposed) Host environment recognition
- Take over all exceptions after loading the Xposed Module, if an exception occurs, it will automatically intercept and print the error log
- Modify the `Class` that does not exist in the lower Android system version (Android 5.0) in the type definition to be an empty safe type
- Adapt and support native Xposed, the minimum recommended version is Android 7.0
- Added support for Hook entry class declared as `object` type (singleton)
- Fixed the problem that the system below Android 8 does not support the `Executable` type, causing the Hook to fail
- Fixed the problem of reporting an error when using the `Activity` proxy function for systems below Android 9 and limit the minimum supported version of this function to Android 7.0
- Added the prohibition of resource injection and `Activity` proxy function injection into the current module's own instance process to prevent problems
- Fixed a serious error that the return value of a method in the Hook process is not consistent with the target's inherited class and interface.
- Fixed the problem that the object is empty when calling `HookParam.callOriginal` and `HookParam.invokeOriginal` when the current Hook instance object is static
- Optimize the function of judging the Tai Chi activation method and update the relevant instructions of the document synchronously
- Obsolete ~~`YukiHookAPI.Status.executorName`~~, ~~`YukiHookAPI.Status.executorVersion`~~, please move to `YukiHookAPI.Status.Executor`
- Adapted the `YukiHookAPI.Status.Executor.name` name display function of some third-party Hook Frameworks
- Added `Class.extends`, `Class.implements` and other methods, which can more conveniently judge the inheritance and interface relationship of the current `Class`
- Added generic methods of the same name as `Class.toClass`, `Class.toClassOrNull` and other related methods, you can use generics to constrain the instance object type of known `Class`
- Modify the return value of the `classOf<T>` method to the generic type `T` to constrain the instance object type of the known `Class`
- Added `initialize` parameter of `Class` related extension method, which can control whether to initialize its static method block at the same time when getting `Class` object
- Added `param { ... }`, `type { ... }` and other usages in the variable, method, and construction method search functions, which can add more specific conditional judgments to the searched objects
- The `loadApp` method of `PackageParam` adds the `isExcludeSelf` parameter, which can be used to exclude Hook-related functions from injecting into the module's own instance process
- The `onAppLifecycle` method of `PackageParam` adds the `isOnFailureThrowToApp` parameter, which can directly throw the exception that occurs in the lifecycle method body to the host
- Modify `appClassLoader` in `PackageParam` to be a modifiable variable, which can dynamically set the `ClassLoader` used by the host in the Hook process
- Added `dataExtra` function in `HookParam`, which can be used to temporarily store the data in the Hook method body
- Obsolete ~~`isRunInNewXShareMode`~~, ~~`isXSharePrefsReadable`~~ in `YukiHookModulePrefs`, merged into `isPreferencesAvailable`
- `Class.allFields`, `Class.allMethods` and other related methods add the `isAccessible` parameter, which can control when the member object can be set as an accessible type
- Fixed the problem that only the last method body will be called back when receiving the same key-value data in an Activity when there are multiple hosts in `YukiHookDataChannel`
- Added `priority` parameter in `wait` and other related methods of `YukiHookDataChannel`, you can pass in `ChannelPriority` to customize the conditions for callback data results
- `YukiHookDataChannel` adds the function of automatically using `ChannelDataWrapper` type wrapper when sending data, which improves the user experience and enhances data protection
- `YukiHookDataChannel` has added the function of limiting the maximum byte size of data sent at one time to prevent the app from crashing due to excessive data
- `YukiHookDataChannel` has added the function of automatically segmenting when the sent data is too large, only supports `List`, `Map`, `Set`, `String` types
- `YukiHookLogger` adds the `contents` method and the `data` parameter of `saveToFile`, which can be passed in custom debug log data for formatting or saving to a file
- Fixed the problem that the debug log data package name processed by `YukiHookLogger` may be incorrect in the (Xposed) Host environment
- Fixed the problem that the package name may be incorrect on some systems (in some system apps) when the Xposed Module loads the Resource Hook event

#### 1.1.4 | 2022.10.04 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the issue that `YukiHookDataChannel` may not respond to broadcast events in the system framework, reproduced in A13
- Fixed the issue that `YukiHookDataChannel` could not communicate with Module App in Host App for multiple versions
- Added `obtainLoggerInMemoryData` method in `YukiHookDataChannel` to share debug log data between module and host
- Modify the type of `YukiHookLogger.inMemoryData` to `ArrayList` and change `YukiLoggerData` to `data class`
- Fixed `YukiLoggerData` printing blank when the package name is empty in the module
- Added `loadApp`, `loadZygote`, `loadSystem`, `withProcess` multi-parameter methods of the same name in `PackageParam`
- Fixed some possible bugs

#### 1.1.3 | 2022.09.30 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed a fatal bug where the Hook entry class name could not be customized
- Added some code notes in `LoggerFactory` and updated special features documentation

#### 1.1.2 | 2022.09.30 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Documentation Basic Knowledge page add a friend link to the English version
- Fixed `YukiBaseHooker` comments in English code note link errors
- Fixed `ClassCastException` in `ModuleClassLoader`
- Fixed and standardize some code notes
- Added `ModuleClassLoader` exclusion list function, you can use `excludeHostClasses` and `excludeModuleClasses` methods to customize the exclusion list
- Added `YukiLoggerData` real-time log data class, you can get the log array in real time through `YukiHookLogger.inMemoryData`
- Added `ClassLoader.listOfClasses` method, which can directly get all `Class` in the current `Dex`

#### 1.1.1 | 2022.09.28 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the problem of wrong document friend links in Basic Knowledge page
- Fixed document `favicon` not showing up
- Fixed bug in `DexClassFinder` search conditions

#### 1.1.0 | 2022.09.28 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- This is a major version update, please refer to [KDoc](kdoc://yukihook-core) for the changes and usage mentioned in the changelog
- Change the help documentation framework to [VuePress](https://v2.vuepress.vuejs.org)
- Unify and standardize the terms and nouns in the document, for example, "query" is always changed to "find", `XposedHelper` is misspelled and changed to `XposedHelpers`
- Documentation Basic Knowledge page to add friend links, Simplified Chinese only
- Convert `Class` and `Method` of Hook App Demo to Java to provide better demo effect
- Fixed code comment naming in Hook Module Demo
- Refactored a lot of low-level Hook logic and the docking method of Xposed API
- Removed `HookParamWrapper`, it now interfaces directly with `YukiBridgeFactory`
- Moved methods in section `YukiHookBridge` to `AppParasitics`
- Removed `HookParam.args` and the underlying direct connection method `setArgs`, directly get and set the object of the current array
- Optimized automatic handler to merge referenced `jar` into `stub` project
- Fixed the problem that the module package name cannot be correctly matched when multi-project packaging, and modify the module package name matching logic of the automatic handler, thanks to [5ec1cff](https://github.com/5ec1cff) for the feedback and solutions provided
- Internal closure processing for the methods of API private tool classes to avoid polluting the top-level namespace
- Fixed `Creater` naming to `Creator` for all reflection and Hook classes
- Added `YukiHookAPI.Status.compiledTimestamp` function, which can get the compilation completion timestamp when used as an Xposed module
- Added `YukiHookAPI.Status.isXposedEnvironment` function, which can determine whether the current (Xposed) host environment or module environment is
- The debug logging function has been overhauled, and functions such as `YukiHookAPI.Configs.debugTag` have been merged into `YukiHookLogger.Configs`
- The debug log can be added to specify the printing method as `XposedBridge.log` or `Logd`
- The package name of the current host and the current user ID are added to the debug log by default for debugging, you can change it yourself in the `debugLog` configuration
- Added `generic` function to reflect and call generics, you can use it in `Class` or `CurrentClass`
- obsolete the `buildOfAny` method, now use the `buildOf` method directly (without generics) to use the constructor to create a new object and get the result `Any`
- Fixed the issue of null pointer exception when using `hasExtends`
- `CurrentClass` added non-**lambda** method of calling
- `CurrentClass` adds `name` and `simpleName` functions
- Completely rewrite the core method of `ReflectionTool`, sorting and classifying different search conditions
- Fixed the problem that `Member` obtained by directly calling `declared` in `ReflectionTool` throws an exception
- Fixed `UndefinedType` in `ReflectionTool` is not correctly judged in `Method` and `Constructor` conditions
- Added a friendly prompt method when the reflection search result is abnormal, which can specifically locate the problem that `Member` cannot be found under specified conditions
- Added `VagueType` condition in `Method` and `Constructor` for reflection search, which can be used in `param` condition to ignore parameters you don't want to fill in
- Added `paramCount { ... }` condition in `Method` and `Constructor` of reflection search, now you can directly get `it` in it to customize your judgment condition
- The `current` method is added to the `FieldFinder` result, which can directly create a call space for the result instance
- Modified the `modifiers` condition and `name` condition in the reflection lookup function, now you need to return a `Boolean` at the end of the method body to make the condition true
- `as*` function in `ModifierRules` renamed to `is*`, thanks to [Kitsune](https://github.com/KyuubiRan) suggestion
- Added `RemedyPlan` feature in `FieldFinder`
- Added `Class` fuzzy search function (Beta) in `Dex`, you can now directly use `searchClass` function to fuzzy search `Class` with specified conditions
- Added multiple search function in reflection search, you can use relative search conditions to obtain multiple search results at the same time, thanks to **AA** and [Kitsune](https://github.com/KyuubiRan) for suggestions
- Fixed the problem that the object obtained by `appClassLoader` is incorrect in system applications in some systems, thanks to [Luckyzyx](https://github.com/luckyzyx) for the feedback
- Modified the calling method of `XposedBridge.invokeOriginalMethod` and added `original` function in `MethodFinder.Result.Instance`
- Fixed the problem of wrong value of `getStringSet` method in `YukiHookModulePrefs` and optimize the code style, thanks to [Teddy_Zhu](https://github.com/Teddy-Zhu) [PR](https://github.com/HighCapable/YukiHook/pull/19)
- Modify `YukiHookModulePrefs` to intercept exceptions that may not exist in `XSharePreference`
- Fixed the problem that `YukiHookDataChannel` could not be successfully registered in some third-party ROM system frameworks
- Secured `YukiHookDataChannel`, now it can only communicate between modules from the specified package name and the host
- Added automatic hook `SharedPreferences` to fix the problem that file permissions are not `0664` in some systems, thanks to [5ec1cff](https://github.com/5ec1cff) for the feedback and implementation code provided
- Added `YukiHookAPI.Configs.isEnableHookSharedPreferences` function, which is disabled by default and can be enabled if the permission of `SharedPreferences` is incorrect
- Fixed the bug that the no-parameter construction method cannot be found when searching for `Constructor` without filling in the search conditions, thanks **B5 KAKA** for the feedback
- Detach `Result` instances located in `method`, `constructor` in `injectMember` to `Process`
- Added the `useDangerousOperation` method in the Hook process, which will automatically stop the Hook and print an error after the function in the Hook Dangerous List is not declared
- Added module resource injection and `Activity` proxy functions, you can call `injectModuleAppResources` and `registerModuleAppActivities` to use
- Added `ModuleContextThemeWrapper` function, you can call `applyModuleTheme` to create the `Context` of a module in any `Activity`
- Added `ClassLoader.onLoadClass` function, which can be used to listen for events when the `loadClass` method of `ClassLoader` is called
- obsolete `classOf` and `clazz` extension methods, add `toClass` and `toClassOrNull` usage, please move to the new method now
- `VariousClass` adds a `getOrNull` method, which can return `null` instead of throwing an exception when it can't match `Class`
- Removed `isUseAppClassLoader` parameter in `PackageParam.hook`, changed it to `isForceUseAbsolute` and automatically matched the target `Class`
- `PackageParam` adds `systemContext` function, you can call this function at any time to get a persistent `Context`
- no longer expose any methods in `HookClass`
- Added `throwToApp` function in `HookParam`, which can throw exceptions directly to the host
- The `onFailureThrowToApp` function is added to the Hook callback, which can be directly thrown to the host when an exception occurs
- Modified the printing logic of the debug log, the time-consuming records in the reflection search function will only be printed during the Hook process
- Added the function of removing Hook in the Hook process, you can use the `remove` and `removeSelf` methods to remove the hook
- Fixed the issue that caused the host to throw an exception when ReplaceHook failed, and now it is modified to call the original method to ensure the normal operation of the host function
- Added the function of checking the return value of the method in the Hook process. If the return value does not match, it will automatically throw an exception or print an error according to the situation
- Added `array` type to Resources Hook, thanks to [PR](https://github.com/HighCapable/YukiHook/pull/12) of [GSWXXN](https://github.com/GSWXXN)
- Moved `me.weishu.reflection` to `thirdparty` to prevent conflicting dependencies of the same name introduced at the same time
- Remove the exception thrown when the Hook method body is empty, and modify it to print the warning log
- Modify the exception handling logic of `AppLifecycle` and throw it directly to the host when an exception occurs
- Updated Demo API version to 33

#### 1.0.92 | 2022.05.31 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the naming method of callback in a large number of methods
- Changed the solution to fix the problem that `YukiHookDataChannel` cannot call back the current `Activity` broadcast on devices lower than **Android 12**
- The `InjectYukiHookWithXposed` annotation adds the `isUsingResourcesHook` function, now you can selectively disable the dependency interface that automatically generates `IXposedHookInitPackageResources`

#### 1.0.91 | 2022.05.29 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the `ClassLoader` error when the customized system of some devices is booted in the LSPosed environment, thanks to [Luckyzyx](https://github.com/luckyzyx) for the feedback
- Fixed `YukiHookDataChannel` not being able to call back the current `Activity` broadcast on **ZUI** and systems below **Android 12**
- Integrate the `YukiHookModuleStatus` function into `YukiHookAPI.Status`, rewrite a lot of methods, now you can judge the status information such as module activation in the module and the host in both directions

#### 1.0.90 | 2022.05.27 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed `YukiHookDataChannel` crashing when the module sets the listener callback
- Fixed `YukiHookDataChannel` still calling back when not in current `Activity`
- Remove the default value of `YukiHookDataChannel` callback event, no callback
- Removed `YukiHookModulePrefs` warning printed if XShare is unreadable
- Added the `isXSharePrefsReadable` method in `YukiHookModulePrefs` to determine whether the current XShare is available

#### 1.0.89 | 2022.05.26 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the problem that `YukiHookDataChannel` cannot be repeatedly set to monitor, and added the function of repeating response in different `Activity` modules and automatically following `Activity` to destroy the monitor function
- Added `YukiHookDataChannel` repeated listening use case description document
- Add the `onAlreadyHooked` method to determine whether the current method is repeated Hook
- Modify part of the logic of repeatedly adding HashMap, remove the `putIfAbsent` method, allow to override the addition
- Fixed several possible bugs

#### 1.0.88 | 2022.05.25 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fully decoupled from Xposed API
- Added `android` type in `type`
- Separate `YukiHookModuleStatus` from auto-generated code and add `isEnableHookModuleStatus` switch, it is up to you to enable or not
- Internal closure processing for the constructors of a large number of classes in the API
- Set `YukiHookModulePrefs` to run as a singleton to prevent repeated creation and waste of system resources
- Fixed the bug that Hook cannot be nested since version `1.0.80`, and optimize the related functions of nested Hook
- Modify the Hooker storage scheme from HashSet to HashMap to prevent the problem of repeatedly adding Hookers
- Modify the core implementation method of Hook, add duplicate checking to avoid repeating the Hook multiple callbacks to the `HookParam` method
- `MethodFinder` and `FieldFinder` add the function of finding fuzzy methods and variable names, you can call `name { ... }` to set search conditions, and support regular expressions
- Optimize and modify the way to get `appContext` to reduce the possibility of getting empty
- Modify the print `TAG` of `logger` in the automatically generated code to default to your custom name, which is convenient for debugging
- Optimize the `Hooker` implementation of `YukiHookBridge` to improve Hook performance
- `PackageParam` adds the `onAppLifecycle` method, which can natively monitor the life cycle of the host and implement the registration system broadcast function
- Added `YukiHookDataChannel` function to communicate using system out-of-order broadcast while the module and the host remain alive
- `YukiHookDataChannel` adds the `checkingVersionEquals` method, which can be monitored to verify that the host has not updated the version mismatch problem after the module is updated
- Added Java version example in the example code of `demo-module` for reference only

#### 1.0.87 | 2022.05.10 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Added `refreshModuleAppResources` function to adapt Resources refresh when the language region, font size, resolution changes, etc.
- Added `isEnableModuleAppResourcesCache` function, you can set whether to automatically cache the resources of the current module

#### 1.0.86 | 2022.05.06 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the problem of continuous error reporting during `initZygote` when Resources Hook is not supported, reproduced in **ZUI**/**LSPosed CI(1.8.3-6550)**
- Optimize and handle exceptions for Resources Hook, only print errors and warnings if they are used and not supported

#### 1.0.85 | 2022.05.04 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed a serious problem of not being able to hook the system framework, since `1.0.80`
- Added in the debug log to distinguish the package name loaded by `initZygote` as `android-zygote`, `packageName` keeps `android` unchanged

#### 1.0.83 | 2022.05.04 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed `YukiHookModuleStatus` reporting a lot of errors after `loadSystem`
- Added `android` type in `type`
- Updated example descriptions in help documentation

#### 1.0.82 | 2022.05.04 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed a concept confusion error, distinguishing the relationship between `initZygote` and the system framework, there are problems with the previous comments and documentation, I am very sorry
- `PackageParam` adds `loadSystem` method, no need to write `loadApp(name = "android")` to hook the system framework

#### 1.0.81 | 2022.05.04 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the problem that the method and constructor that cannot be found in the Hook method body still output the error log after setting the condition using the `by` method
- Added a global log to display the package name of the current Hook APP during the execution of the Hook, and fixed a problem with the printing style of the error log

#### 1.0.80 | 2022.05.01 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- The `InjectYukiHookWithXposed` annotation adds the `entryClassName` function, which can customize the generated `xposed_init` entry class name
- ~~`YukiHookXposedInitProxy`~~ renamed to `IYukiHookXposedInit`, the original interface name has been invalidated and will be deleted directly in subsequent versions
- Added `initZygote` and Resources Hook functions to support Hook Layout
- Added `onXposedEvent` method to listen to all events of native Xposed API
- Perform `inline` processing on the **lambda** of the Hook function to avoid generating excessively broken anonymous classes and improve the running performance after compilation
- Fixed `PrefsData` compiled method body copy is too large
- Added `XSharePreference` readability test, which will automatically print a warning log if it fails
- `PackageParam` adds `appResources`, `moduleAppResources`, `moduleAppFilePath` functions
- `loadApp` of `PackageParam` adds the function of not writing `name`, and all APPs are filtered by default
- `PackageParam` adds the `loadZygote` method, which can directly hook the system framework
- `PackageParam` added `resources().hook` function
- Optimization method, construction method, variable search function, the error log that cannot be found will display the set query conditions first
- Added `hasExtends` extension method to determine whether the current `Class` has an inheritance relationship
- Added `isSupportResourcesHook` function to determine whether resource hooks are currently supported (Resources Hook)
- `current` function adds `superClass` method to call superclass
- New `superClass` query conditions for search methods, construction methods and variables, you can continue to search in the parent class
- YukiHook lots of methods are decoupled from Xposed API
- Added native Hook priority function of Xposed API
- Fixed the problem that `isFirstApplication` may be inaccurate
- Block the problem that MiuiCatcherPatch repeatedly calls the Hook entry method on the MIUI system
- Optimize Hook entry calling method to avoid multiple calls due to Hook Framework issues
- Fixed the problem that Hook `ClassLoader` causes Hook to freeze, thanks to [WankkoRee](https://github.com/WankkoRee) for the feedback
- Improve the performance after the `XC_Callback` interface is connected
- Java `type` added `ClassLoader` type
- Optimize the API help documentation, fix the problem that the page may be continuously cached

#### 1.0.78 | 2022.04.18 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- `YukiHookModulePrefs` adds `isRunInNewXShareMode` method, which can be used to determine whether the module is currently in `New XSharePreference` mode
- Fixed `YukiHookModulePrefs` working in `New XSharePreference` mode
- Added `ModulePreferenceFragment`, now you can completely replace `PreferenceFragmentCompat` and start using the new functionality
- Adapt the Sp data storage solution of `PreferenceFragmentCompat`, thanks to [mahoshojoHCG](https://github.com/mahoshojoHCG) for feedback
- Update autohandlers and Kotlin dependencies to the latest version
- Fixed some bugs in documentation and code comments

#### 1.0.77 | 2022.04.15 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- `YukiHookModulePrefs` added `clear` method, thanks to [WankkoRee](https://github.com/WankkoRee) for the suggestion
- `YukiHookModulePrefs` added `getStringSet`, `putStringSet`, `all` methods
- Added `any` method to `args` of `HookParam`
- Added `ModuleApplication`, which can be inherited in modules to achieve more functions
- Connect all `findClass` functions to the Xposed API, and continue to use native `ClassLoader` in non-hosted environments
- Fixed some possible bugs

#### 1.0.75 | 2022.04.13 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Corrected the logic recognition part of the automatic handler, thanks to [ApeaSuperz](https://github.com/ApeaSuperz) contribution
- Fixed an issue where the reference to a doc comment was not changed
- `firstArgs` and `lastArgs` methods have been removed from `HookParam`, now you can use `args().first()` and `args().last()` instead of it
- Removed default parameter `index = 0` in `args()` in `HookParam`, now you can use `args().first()` or `args(index = 0)` to replace it
- The `result` function in `HookParam` adds generic matching, now you can use `result<T>` to match the known return value type of your target method
- The `emptyParam` condition is added to the method and constructor query function, and the misunderstanding of the query condition that needs to be paid attention to in the document has been improved
- Added `android` type in `type`

#### 1.0.73 | 2022.04.10 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed some Chinese translation errors in documents, thanks to [WankkoRee](https://github.com/WankkoRee) for their contributions
- Fixed the problem that `XC_LoadPackage.LoadPackageParam` throws an exception when the content is empty in some cases, thanks to [Luckyzyx](https://github.com/luckyzyx) for the feedback
- Fixed some known bugs and improve Hook stability

#### 1.0.72 | 2022.04.09 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Update API documentation to new address
- Add `appContext` function to `PackageParam`
- Fixed some known bugs and improve Hook stability

#### 1.0.71 | 2022.04.04 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed a serious issue that would stop the Hook from throwing an exception when VariousClass could not be matched

#### 1.0.70 | 2022.04.04 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed `instanceClass` reporting an error after being called in a static instance
- Add `isUseAppClassLoader` function in Hook process, thanks to [WankkoRee](https://github.com/WankkoRee) for feedback
- Added the `withProcess` function, which can be hooked according to the currently specified process of the APP
- Fixed critical logic errors in lookup methods, constructor classes and variables
- Fixed the problem that the abnormal output cannot be ignored when the Hook target class does not exist
- Fixed the problem that the Hook could not take effect due to the fast loading of the APP startup method in some cases
- Fixed `allMethods` not throwing an exception when it is not hooked to a method, thanks to [WankkoRee](https://github.com/WankkoRee) for the feedback
- Added Hook status monitoring function, thanks to [WankkoRee](https://github.com/WankkoRee) for the suggestion
- Modify the way the Xposed entry is injected into the class, and redefine the definition domain of the API
- Added obfuscated method and variable lookup function, you can use different types of filter `index` to locate the specified method and variable, thanks to [WankkoRee](https://github.com/WankkoRee) for the ideas provided
- When looking for methods and variables, multiple types are allowed, such as the class name declared by `String` and `VariousClass`
- Add a new `current` function, which can build a reflection method operation space for any class, and easily call and modify the methods and variables in it
- Fixed a lot of bugs in the hook process, thanks to [WankkoRee](https://github.com/WankkoRee) for contributing to this project

#### 1.0.69 | 2022.03.30 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Added and improved annotations for some method functions
- Added more example Hook content in Demo
- Fixed the issue that only the last one takes effect when `allMethods` is used multiple times in a Hook instance, thanks to [WankkoRee](https://github.com/WankkoRee) for the feedback

#### 1.0.68 | 2022.03.29 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Added new use case and LSPosed scope in Demo
- Added `Member` lookup cache and lookup cache configuration switches
- Removed and modified `MethodFinder`, `FieldFinder` and `HookParam` related method calls
- Add more `cast` types in `Finder` and support `cast` as array
- Overall performance and stability improvements
- Fixed bugs that may exist in the previous version

#### 1.0.67 | 2022.03.27 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Added three `modifiers` functions in `Finder`, which can filter `static`, `native`, `public`, `abstract` and many other description types
- When searching for methods and constructors, the method parameter type can be blurred to a specified number for searching
- Added `hasModifiers` extension for `Member`
- Added `give` method in `MethodFinder` and `ConstructorFinder` to get primitive types
- Added `PrefsData` template function in `YukiHookModulePrefs`
- Completely refactored method, constructor and variable lookup scheme
- Optimized code comments and fixed possible bugs

#### 1.0.66 | 2022.03.25 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed a serious bug in `MethodFinder`
- Added `args` call method in `hookParam`
- Fixed other possible problems and fix some class annotation problems

#### 1.0.65 | 2022.03.25 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Republished version to fix the incorrect new version of the Maven repository due to cache issues
- Added `MethodFinder` and `FieldFinder` new return value calling methods
- Fixed possible problems and fix possible problems during the use of Tai Chi
- Fixed possible problems with auto-generated Xposed entry classes
- Added `android` type and Java type in `type`

#### 1.0.6 | 2022.03.20 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed `YukiHookModulePrefs` being ignored every time after using `direct` once to ignore cache
- Added new API, abolished the traditional usage of `isActive` to judge module activation
- Fixed the issue of printing debug logs when using the API in a non-Xposed environment
- Fixed log output issue and unintercepted exception issue when looking for `Field`
- Decoupling Xposed API in `ReflectionUtils`
- Added `YukiHookModuleStatus` method name confusion to reduce the size of module generation
- The welcome message will no longer be printed when loading the module's own Hook
- Fixed some bugs that still exist in the previous version

#### 1.0.55 | 2022.03.18 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed an annotation error
- Temporarily fix a bug
- Added a large number of `android` types in `type` and a small number of Java types
- Fixed compatibility issues between new and old Kotlin APIs

#### 1.0.5 | 2022.03.18 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the problem that the welcome message was printed multiple times in the case of the old version of the LSPosed framework
- Added `onInit` method to configure YukiHook
- Added `executorName` and `executorVersion` to get the name and version number of the current hook framework
- Added `by` method to set the timing and condition of Hook
- `YukiHookModulePrefs` adds a controllable key-value cache, which can dynamically update data when the host is running
- Fixed some possible bugs

#### 1.0.4 | 2022.03.06 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed LSPosed cannot find `XposedBridge` after enabling "Only module classloader can use Xposed API" option in latest version
- Added constant version name and version number for YukiHook
- Added `hasField` method and `isAllowPrintingLogs` configuration parameter
- Added `isDebug` to enable the API to automatically print the welcome message to test whether the module is valid

#### 1.0.3 | 2022.03.02 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed a potential exception not intercepted BUG
- Added `ignoredError` function
- Added `android` type in `type`
- Added `ClassNotFound` function after listening to `hook`

#### 1.0.2 | 2022.02.18 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- Fixed the problem that the project path cannot be found under Windows
- Remove part of reflection API, merge into `BaseFinder` for integration
- Add a method to create Hook directly using string

#### 1.0.1 | 2022.02.15 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- `RemedyPlan` adds `onFind` function
- Integrate and modify some reflection API code
- Added Java type in `type`
- Fixed the issue that ignored errors still output in the console

#### 1.0 | 2022.02.14 &ensp;<Badge type="danger" text="outdated" vertical="middle" />

- The first version is submitted to Maven