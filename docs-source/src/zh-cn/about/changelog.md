# 更新日志

> 这里记录了 `YukiHook` 的版本更新历史。

::: danger

我们只会对最新的 API 版本进行维护，若你正在使用过时的 API 版本则代表你自愿放弃一切维护的可能性。

:::

### 1.5.1 | 2026.11.20 &ensp;<Badge type="latest" text="最新" vertical="middle" />

- 新增 YukiHook Agent Skill，可为 AI 编程助手提供模块配置、API 用法与迁移指引，详情请参考 [Agent Skills](https://github.com/HighCapable/YukiHook/blob/1.x/README-zh-CN.md#agent-skills)

#### yukihook-core

- <u>**⚠️ 破坏性更新：**</u>在宿主中修改模块的共享偏好设置时，现在会抛出 `UnsupportedOperationException`，修改前请使用 `isWritable` 判断是否可写
- <u>**⚠️ 破坏性更新：**</u>`ModulePreferenceFragment` 的 `onCreatePreferences` 不再允许覆写，已移除 `onCreatePreferencesInModuleApp`，请将初始化代码迁移至 `onPreferencesReady`
- <u>**⚠️ 破坏性更新：**</u>`Method.invokeSpecial<T>()` 的返回值类型改为 `T?`，请处理可能的 `null` 返回值
- <u>**⚠️ 破坏性更新：**</u>从 Java 调用带配置块的 `Collection<Member>.hookAll` 时，请使用 `hookAllByMember`，Kotlin 调用方重新编译即可
- 调试日志统一显示进程名，弃用了 ~~`YLog.Config.PACKAGE_NAME`~~ 与 ~~`YLogData.packageName`~~，现在请使用 `YLog.Config.PROCESS_NAME` 与 `YLogData.processName`
- 共享偏好设置不可用时改用临时内存存储，数据会在进程退出后丢失，也不会在存储恢复可用后写回，请使用 `isAvailable` 判断当前是否可用
- 新增 `Fragment.dataChannel` 与 `Fragment.systemDataChannel`，同一 `Activity` 中的不同 `Fragment` 可以各自监听相同的 `key`，监听会在视图销毁时自动移除
- 数据通道现在按任务向最近一次进入 `RESUMED` 状态的模块 `Activity` 分发消息，任务退到后台后仍可接收
- 新增经典 Hook 的 `ArgumentEditor.getOrNull<T>()`，读取不到参数或类型不匹配时返回 `null`
- 链式 Hook 新增 `argOrNull`、`firstArgOrNull` 与 `lastArgOrNull`，可以安全读取可选参数
- `registerModuleLifecycle` 现在也可在 Xposed 82 底座的 `onInit` 中注册，执行热重载仍需要支持热重载的底座
- 修复通过委托实现 `onInit` 时，`registerModuleLifecycle` 与 `registerFrameworkEvents` 可能无法注册的问题
- 在模块入口的 `onHook` 之外调用模块版 `encase` 时，现在会抛出异常
- 修复应用生命周期中的广播回调未遵循 `throwToHostOnFailure` 设置的问题
- 修复宿主创建未附加 `Context` 的辅助 `Application` 时，生命周期回调可能导致宿主崩溃的问题
- 修复分片消息的监听回调抛出异常时，被误报为数据通道分片合并失败的问题
- 修复 Android 11 及以上系统中，模块 APK 路径不可用时资源注入可能直接抛出异常的问题
- 修复模块资源不可用时热重载仍执行初始化代码，可能导致宿主崩溃的问题
- 修复热重载后旧模块可能因 `Parcelable` 缓存无法释放的问题，以及从 `1.5.0` 热重载到 `1.5.1` 时可能出现的状态恢复失败问题
- 改进 [Lint 静态检查规范](../config/lint-rules.md) 对模块入口调用和底座模块专属 API 的检查
- 扩展 `UseHookInvocationApi`，为经典 Hook 和链式 Hook 中的参数、返回值及调用链写法提供提示和快速修复
- 新增 `UseHookBlock`，建议使用 `hook { ... }` 与 `hookAll { ... }` 配置 Hook
- 新增 `UseApplicationReady`，在手动 Hook 生命周期来获取宿主 `Application` 时，提示可以使用支持热重载的 `withApplicationReady`

#### yukihook-compiler

- 修复使用低于 JDK 21 的版本生成 libxposed 模块入口时构建失败的问题

#### yukihook-extension-kavaref

- <u>**⚠️ 破坏性更新：**</u>`MethodResolver.invokeSpecial<T>()` 的返回值类型改为 `T?`，请处理可能的 `null` 返回值
- `AndroidHiddenApiBypassResolver` 新增在 Android 9 及以上系统中查找隐藏字段的支持
- 修复部分 KavaRef 调用被 Lint 误报为未使用宿主类加载器的问题

#### yukihook-runtime-libxposed

- `YukiHook.Status.observeFrameworkService` 新增主线程标注，请在主线程注册监听
- 修复 `onModuleLoaded` 回调抛出异常会使已完成的模块初始化被判定为失败的问题

#### yukihook-runtime-xposed82

- 修复模块初始化失败后仍调用 `onInitZygote` 回调的问题

### 1.5.0 | 2026.10.20 &ensp;<Badge type="warning" text="过旧" vertical="middle" />

- 这是一次重大更新，项目正式更名为 YukiHook，并拆分为多个模块进行发布，详情请参考 [迁移至 YukiHook 1.5.x](../config/migration-1-5-x.md)
- 全部模块采用统一版本发布，依赖的 `groupId` 与代码的包名依然为 `com.highcapable.yukihookapi`
- 新增 [yukihook-bom](../library/yukihook-bom.md)，用于统一管理全部模块的版本
- 新增底座模块 [yukihook-runtime-libxposed](../library/yukihook-runtime-libxposed.md) 与 [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md)，分别对接 [libxposed API](https://github.com/libxposed) 与 [Xposed Framework API](https://api.xposed.info)，一个构建变体中只能引入其中一个
- 原有的 Xposed 模块可以使用 [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md) 继续运行在原有的 Hook 框架上，它的 `AndroidManifest.xml` 已声明 `xposedmodule` 与 `xposedminversion`
- [yukihook-runtime-libxposed](../library/yukihook-runtime-libxposed.md) 支持 Remote `SharedPreferences`、共享文件、模块作用域以及模块热重载
- 新增扩展模块 [yukihook-extension-kavaref](../library/yukihook-extension-kavaref.md)，用于在 YukiHook 中使用 [KavaRef](https://github.com/HighCapable/KavaRef) 的相关功能，KavaRef 的依赖需要自行引入

#### yukihook-bom

- 首个版本提交至 Maven

#### yukihook-core

- <u>**⚠️ 破坏性更新：**</u>原 `api` 依赖更名为 [yukihook-core](../library/yukihook-core.md)，现在需要同时引入 [yukihook-compiler](../library/yukihook-compiler.md) 与一个底座模块才能工作
- <u>**⚠️ 破坏性更新：**</u>移除了 Resources Hook，`resources().hook { ... }` 已不再可用
- <u>**⚠️ 破坏性更新：**</u>移除了在 `1.3.0` 版本中弃用的自身反射 API 以及 `injectMember` 等旧版 Hook 写法，现在请迁移到 [KavaRef](https://github.com/HighCapable/KavaRef)
- <u>**⚠️ 破坏性更新：**</u>`onXposedEvent` 与 `YukiXposedEvent` 已不再被回调，现在请使用底座模块提供的 `registerFrameworkEvents`
- <u>**⚠️ 破坏性更新：**</u>在 `loadApp` 的多个包名中混入 `android` 或 `system` 时将抛出异常，现在请使用 `loadSystem`
- <u>**⚠️ 破坏性更新：**</u>KavaRef 相关的 Hook 方法以及 `String.toClass`、`VariousClass.toClass`、`lazyClass` 已迁移至 [yukihook-extension-kavaref](../library/yukihook-extension-kavaref.md)
- <u>**⚠️ 破坏性更新：**</u>`AndroidHiddenApiBypassResolver` 已迁移至 [yukihook-extension-kavaref](../library/yukihook-extension-kavaref.md)，并移除了 `AndroidHiddenApiBypassResolver.get()`，现在请直接创建它的实例
- <u>**⚠️ 破坏性更新：**</u>移除了 `ExecutorType`，`YukiHookAPI.Status.Executor.type` 已无法使用，现在请使用 `YukiHook.Status.Executor.name` 获取 Hook 框架的名称
- <u>**⚠️ 破坏性更新：**</u>`throwToHostOnFailure` 现在只对设置它的回调生效，不再同时作用于 `before` 与 `after`
- 弃用了 ~~`YukiHookAPI`~~，现在请使用 `YukiHook`
- 弃用了 ~~`@InjectYukiHookWithXposed`~~、~~`IYukiHookXposedInit`~~，现在请使用与底座模块对应的入口注解以及 `YukiHookXposedModule`
- 弃用了 ~~`YukiHookAPI.configs`~~，现在请使用 `YukiHook.configure`，全部配置项也已重新命名
- 弃用了 ~~`prefs`~~、~~`YukiHookPrefsBridge`~~、~~`PrefsData`~~，现在请使用 `preferences`、`YukiHookPreferences`、`PreferenceData`
- 弃用了 ~~`onAppLifecycle`~~、~~`registerModuleAppActivities`~~、~~`injectModuleAppResources`~~，现在请使用 `registerAppLifecycle`、`registerModuleActivities`、`injectModuleResources`
- 弃用了 ~~`loadZygote`~~、~~`YukiHook.encase(baseContext)`~~，它们现在仅在 [yukihook-runtime-xposed82](../library/yukihook-runtime-xposed82.md) 中可用
- 弃用了 `HookParam` 与 Hook 回调中的大量旧版方法，现在请使用 `arg`、`firstArg`、`lastArg`、`intercept`、`throwToHost` 等新的方法
- 新增链式 Hook，可以使用 `intercept` 与 `HookChain` 通过 `proceed` 继续执行调用链，回调中发生异常时默认将按照此 Hook 不存在的方式继续调用，你也可以使用 `throwToHostOnFailure` 将异常抛给调用方
- 经典 Hook 的 `intercept` 现在会返回 `HookCallback`，可以使用 `throwToHostOnFailure` 将异常抛给宿主，`throwToHost` 现在也会在 `intercept` 中生效
- 新增 `Handle.replace`，可以在不重新 Hook 的情况下替换已注册的回调
- 新增 Hook 静态初始化、解除内联化以及 `invokeSpecial`、`newInstanceOriginal`、`newInstanceSpecial` 等调用方法
- 新增 `withApplicationReady`，可以在宿主的 `Application` 可用时执行代码，模块热重载后会再次回调
- `AppLifecycle` 中的 `registerReceiver` 新增 `exported` 参数，模块热重载时将自动注销并重新注册
- 新增 `module.resources`、`module.sourceDir` 等 `PackageParam.ModuleSpec` 功能，用于获取当前模块的信息
- 新增 `YukiHookSharedFiles`，可以在模块与宿主之间共享文件
- 新增 `YukiHookModuleScope`，可以在模块中查询与请求模块的作用域
- 新增模块热重载功能，以及 `YukiHookModuleReload`、`registerModuleLifecycle` 与 `remember`
- 新增 `YukiHook.Status.observeFrameworkService`、`YukiHook.Status.runtime`
- 新增一套 [Lint 静态检查规范](../config/lint-rules.md)，可以在编译期发现错误的 API 调用
- 修复 `intercept` 回调发生异常后调用原始方法时，原始方法自身抛出异常会导致其被执行两次的问题
- 修复模块进程中 `onInit` 发生的异常不会被打印的问题
- 调整异常处理方案，现在只会保护宿主而不会保护模块，模块进程中发生的异常将直接抛出，宿主进程中的内部异常仍会被打印到日志

#### yukihook-compiler

- <u>**⚠️ 破坏性更新：**</u>原 `ksp-xposed` 依赖更名为 [yukihook-compiler](../library/yukihook-compiler.md)
- <u>**⚠️ 破坏性更新：**</u>移除了 `sourcePath` 参数，入口文件与配置文件现在会按照每个构建变体生成在构建目录中，源码目录中存在这些文件时将拒绝构建
- 新增对 `@YukiHookLibXposedEntry` 与 `@YukiHookXposed82Entry` 入口注解的处理，并会检查入口注解与底座模块是否匹配
- 新增为 libxposed API 生成 `META-INF/xposed` 中的 `java_init.list`、`module.prop`、`scope.list` 与 `native_init.list`

#### yukihook-extension-kavaref

- 首个版本提交至 Maven

#### yukihook-runtime-libxposed

- 首个版本提交至 Maven

#### yukihook-runtime-xposed82

- 首个版本提交至 Maven

## 历史版本

### api & ksp-xposed

#### 1.3.2 | 2026.05.30 &ensp;<Badge type="warning" text="过旧" vertical="middle" />

- 更新目标 SDK 版本到 37
- 更新依赖以解决与 `BetterAndroid` 冲突的问题
- 对 API 代码风格进行调整，但不实际改变函数签名

#### 1.3.1 | 2025.09.13 &ensp;<Badge type="warning" text="过旧" vertical="middle" />

- 更新依赖以解决与 `BetterAndroid` 冲突的问题
- 修复 Android 9 及以下系统 `Activity` 代理的问题
- 修复在 Hook 方法返回值为 `Object` 时依然检查返回值的问题

#### 1.3.0 | 2025.06.25 &ensp;<Badge type="warning" text="过旧" vertical="middle" />

- 这是一次重大更新
- 弃用了 YukiHook 自身的反射 API，现在请迁移到全新的 [KavaRef](https://github.com/HighCapable/KavaRef)
- 弃用了重复 Hook 的限制，现在你可以重复 Hook 同一个方法
- 弃用了 ~~`ModuleAppActivity`~~、~~`ModuleAppCompatActivity`~~，现在请使用 `ModuleActivity` 创建自己的代理 `Activity`
- `YLog` 现已允许 `msg` 传入任意对象，将自动转换为字符串进行打印
- `FreeReflection` 已被弃用，现已切换至 [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)

#### 1.2.1 | 2024.06.20 &ensp;<Badge type="warning" text="过旧" vertical="middle" />

- 捕获单例 Hooker 中的异常，防止其阻断整个进程
- 在自动处理程序中添加自动使用 "`" 来修复 Kotlin 关键字为包名的情况，感谢 [Fengning Zhu](https://github.com/zhufengning) 的 [PR](https://github.com/HighCapable/YukiHook/pull/70)
- 适配 Kotlin 2.0.0+，修复在自动处理过程中无法通过编译的问题，感谢 [xihan123](https://github.com/xihan123) 的 [PR](https://github.com/HighCapable/YukiHook/pull/76)

#### 1.2.0 | 2023.10.07 &ensp;<Badge type="warning" text="过旧" vertical="middle" />

- 许可协议由 `MIT` 变更为 `Apache-2.0`，在此之后的版本将由此许可协议进行分发，您在使用此版本后应变更相关许可协议
- 这是一次重大更新
- 适配 Android 14，感谢 [BlueCat300](https://github.com/BlueCat300) 的 [PR](https://github.com/HighCapable/YukiHook/pull/44)
- 修复 `findAllInterfaces` 相关问题，感谢 [buffcow](https://github.com/buffcow) 的 [PR](https://github.com/HighCapable/YukiHook/pull/38)
- 修复 Hook 过程中的延迟回调问题，感谢 [cesaryuan](https://github.com/cesaryuan) 的 [Issue](https://github.com/HighCapable/YukiHook/issues/47)
- 新增 Resources Hook 相关功能支持，详情请参考这个 [Issue](https://github.com/HighCapable/YukiHook/issues/36)
- 新增 `YukiHookAPI.TAG`
- 作废了 ~~`YukiHookAPI.API_VERSION_NAME`~~、~~`YukiHookAPI.API_VERSION_CODE`~~，统一合并到 `YukiHookAPI.VERSION`
- 作废了 ~~`YukiMemberHookCreator`~~ 中的 ~~`useDangerousOperation`~~ 方法
- 作废了 ~~`YukiMemberHookCreator`~~ 中的 ~~`instanceClass`~~ 功能，不再推荐使用
- 修改 `HookParam` 中的 `instanceClass` 为空安全返回值类型
- 分离全部使用 `injectMember` 创建的 Hook 对象到 `LegacyCreator`
- 修改 `PackageParam` 中的 `appClassLoader` 为空安全返回值类型
- 重构全部 `logger...` 方法到新用法 `YLog`
- 移除了打印日志功能后方的 `-->` 样式
- 修复并改进在使用 `namespace` 后通过 KSP 无法获取模块包名的问题
- 是否启用模块激活状态等功能现已移动到 `InjectYukiHookWithXposed` 注解中
- 分离 [FreeReflection](https://github.com/tiann/FreeReflection) 不再自动生成，将作为依赖自动导入
- 新增重复 Hook 同一个方法时将自动打印警告日志
- 作废了 `PackageParam` 中的 `findClass(...)` 方法，请迁移到 `"...".toClass()` 方法
- 作废了 `PackageParam` 中的 `String.hook { ... }` 方法，推荐使用新方式进行 Hook
- `AppLifecycle` 现在可以在不同 Hooker 中重复创建
- 作废了旧版 Hook 优先级写法，统一迁移到 `YukiHookPriority`
- 移除了 Hook 过程中的 `tag` 功能
- 重构方法查找中的 `remendy` 功能，现在可以对其进行分步打印异常
- 多重方法查找结果类型由 `HashSet` 改为 `MutableList`
- 新增使用 `method()`、`constructor()`、`field()` 可直接获取到类中的所有对象功能
- `constructor()` 的行为不再是 `constructor { emptyParam() }`
- 新增 `lazyClass`、`lazyClassOrNull` 方法，可延迟装载 `Class`

#### 1.1.11 | 2023.04.25 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复从 `1.1.5` 版本开始的一个严重问题，`Member` 缓存未生效且持续存储最终引发 APP 内存溢出 (OOM)，感谢 [Art-Chen](https://github.com/Art-Chen)
- 移除 `Member` 的直接缓存功能并作废 ~~`YukiHookAPI.Configs.isEnableMemberCache`~~，保留 `Class` 的缓存功能
- 对接查找功能到 `Sequence`，优化 `Member` 的查找速度与性能
- 移除 `YukiHookPrefsBridge` 的直接键值缓存功能并移除 `LruCache` 相关功能
- 作废了 ~~`YukiHookAPI.Configs.isEnablePrefsBridgeCache`~~
- 作废了 `YukiHookPrefsBridge` 中的 ~~`direct`~~、~~`clearCache`~~ 方法

#### 1.1.10 | 2023.04.21 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- `Activity` 代理功能新增每个被代理的 `Activity` 指定单独的代理 `Activity` 功能
- 修复 `YukiHookPrefsBridge` 中的 `contains`、`all` 方法未判断 `native` 功能的问题
- 整合 `YukiHookPrefsBridge` 中的缓存功能到 `PreferencesCacheManager` 并使用 `LruCache` 作为键值对前置缓存
- 修改 `YukiHookPrefsBridge` 键值对缓存功能在所有环境中生效 (模块、宿主)
- 修改部分用于缓存的 `HashMap` 到 `ArrayMap` 以减少内存消耗
- 修复一些其它可能出现的问题

#### 1.1.9 | 2023.04.17 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 将依赖库的类型由 **Java Library** (jar) 修改为 **Android Library** (aar)
- 移除通过 Hook 或反射 API 内部方法、参数的检查功能
- 修复 `YukiHookDataChannel` 自动分段发送数据功能不能正常生效 (依然会抛出异常) 的问题
- 新增可以手动根据目标设备的限制修改 `YukiHookDataChannel` 允许一次发送的最大数据字节大小
- 移除 `YukiHookDataChannel` 只能在模块 `Activity` 中使用的限制，现在你可以在任何地方使用它
- 修改并规范 `YukiHookDataChannel` 使用的广播 Action 名称
- 修复 `YukiHookDataChannel` 在不同模块同一宿主的情况下出现 `BadParcelableException` 异常的问题
- 新增 `ExecutorType`，可以通过 `YukiHookAPI.Status.Executor.type` 来获取已知 Hook Framework 的类型
- ~~`YukiHookModulePrefs`~~ 更名为 `YukiHookPrefsBridge`
- 修改 `YukiHookPrefsBridge` 为非单例实现，作为单例可能发生数据混淆的问题
- 作废了 ~~`Context.modulePrefs(...)`~~ 方法，请迁移到 `Context.prefs(...)`
- `YukiHookPrefsBridge` 新增 `native` 方法，支持直接作为原生存储在模块和宿主中存储私有数据
- 整合 `YukiHookPrefsBridge` 中的存储方法到 `YukiHookPrefsBridge.Editor`，请使用 `edit` 方法来存储数据
- `YukiHookPrefsBridge` 新增 `contains` 方法
- 缓存 `YukiHookPrefsBridge` 中动态创建的代理对象，尝试修复可能会导致宿主、模块出现 OOM 的问题
- 修改 `Activity` 代理功能的代理类为动态生成，防止不同模块注入宿主后造成冲突
- 修复一些其它可能出现的问题

#### 1.1.8 | 2023.02.01 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复底层 Hook 方法在回调时修改 `result` 等参数时时不能同步更新修改后的状态问题，感谢 [Yongzheng Lai](https://github.com/elvizlai) 的 [Issue](https://github.com/HighCapable/YukiHook/issues/23)
- 移动 YukiHook 自动生成的入口类名称文件 `assets/yukihookapi_init` 到 `resources/META-INF/yukihookapi_init`
- 允许在仅打印异常堆栈时 `msg` 参数为空并可以不设置 `msg` 参数，留空 `msg` 参数的日志除非异常堆栈不为空否则将不会被记录
- 修复 Hook 回调方法体内发生的异常打印的日志无具体方法信息的 BUG
- `HookParam` 新增 `instanceOrNull` 变量与方法，可以在不确定 Hook 实例是否为空的前提下使用以防止 Hook 实例为空抛出异常
- 解耦合所有 `Member` 查找功能中的 Hooker 到 `MemberBaseFinder.MemberHookerManager`
- 修改了 `YukiMemberHookCreator` 中的 `by` 条件用法，现在可以重复使用 `by` 方法设置多个条件
- 移除了 Android `type` 中的错误 `Class` 对象声明
- `PackageParam.AppLifecycle` 中的 `registerReceiver` 方法新增直接使用 `IntentFilter` 创建系统广播监听的功能
- 修复在 `PackageParam.AppLifecycle` 中可能存在多次注册生命周期的问题
- Revert: 1.1.7 版本由于有一个严重问题已经撤回，请直接更新到此版本即可 (更新日志同 1.1.7 版本)

#### 1.1.6 | 2023.01.21 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 Xposed 模块装载时可能存在同一个进程多个包名的情况导致 `PackageParam` 保持单例后 `ClassLoader` 不符的严重问题
- 新增同一个进程多个包名的情况下未区分包名时，停止装载单例化的子 Hooker 并打印警告信息
- 修复 `HookParam.callOriginal`、`HookParam.invokeOriginal` 等方法调用原始方法时参数个数不正确的问题
- 修改 `MethodFinder`、`ConstructorFinder`、`ReflectionFactory` 中反射调用的方法参数名 `param` 为 `args`
- 新增 Xposed 模块自动处理程序中判断入口类构造方法参数功能，入口类需要保证其不存在任何构造方法参数

#### 1.1.5 | 2023.01.13 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 规范并优化整体代码风格
- 对部分内部调用的 API 进行了私有化处理
- 底层 API 接口整体解耦合，为兼容更多 Hook Framework 做准备
- 将部分集成于 API 中的功能移动到 `ksp-xposed` 依赖 (解耦合)，单独引入 `api` 依赖将不再包含第三方库等功能的引用
- 文档 [快速开始](../guide/quick-start) 页面加入 `YukiHookAPI.Configs.isDebug` 何时需要关闭的说明
- 规范类型定义中的 Java 原始类型 (Primitive Type) 并同步更新到文档
- Java `type` 新增 `NumberClass` 类型
- 改进了 (Xposed) 宿主环境的识别能力
- 接管了 Xposed 模块装载后的全部异常，若发生异常将会自动拦截并打印错误日志
- 修改类型定义中较低 Android 系统版本 (Android 5.0) 中不存在的 `Class` 为空安全类型
- 适配并支持原生 Xposed，最低推荐版本为 Android 7.0
- Hook 入口类新增支持声明为 `object` 类型 (单例)
- 修复 Android 8 以下系统不支持 `Executable` 类型导致 Hook 失效的问题
- 修复 Android 9 以下系统在使用 `Activity` 代理功能时报错问题并限制此功能最低支持版本为 Android 7.0
- 新增禁止资源注入与 `Activity` 代理功能注入当前模块自身实例进程，防止发生问题
- 修复一个 Hook 过程中方法返回值的对象是目标的继承类和接口时被识别为返回值不符的严重错误
- 修复在当前 Hook 的实例对象是静态的情况下调用 `HookParam.callOriginal`、`HookParam.invokeOriginal` 出现对象为空问题
- 优化对太极激活方法相关判断功能以及同步更新文档相关说明
- 作废了 ~~`YukiHookAPI.Status.executorName`~~、~~`YukiHookAPI.Status.executorVersion`~~，请迁移到 `YukiHookAPI.Status.Executor`
- 适配了一些第三方 Hook Framework 的 `YukiHookAPI.Status.Executor.name` 名称显示功能
- 新增 `Class.extends`、`Class.implements` 等方法，可更加方便地判断当前 `Class` 的继承与接口关系
- 新增 `Class.toClass`、`Class.toClassOrNull` 等相关方法的同名泛型方法，可使用泛型来约束已知 `Class` 的实例对象类型
- 修改 `classOf<T>` 方法的返回值为泛型 `T`，以约束已知 `Class` 的实例对象类型
- 新增 `Class` 相关扩展方法的 `initialize` 参数，可控制在得到 `Class` 对象时是否同时初始化其静态方法块
- 变量、方法、构造方法查找功能中新增 `param { ... }`、`type { ... }` 等用法，可对查找的对象增加更加具体的条件判断
- `PackageParam` 的 `loadApp` 方法新增 `isExcludeSelf` 参数，可用于排除 Hook 相关功能注入模块自身实例进程
- `PackageParam` 的 `onAppLifecycle` 方法新增 `isOnFailureThrowToApp` 参数，可将生命周期方法体内发生的异常直接抛给宿主
- 修改 `PackageParam` 中的 `appClassLoader` 为可修改变量，可在 Hook 过程中动态设置宿主使用的 `ClassLoader`
- `HookParam` 中新增 `dataExtra` 功能，可用于临时存储 Hook 方法体中的数据
- 作废 `YukiHookModulePrefs` 中的 ~~`isRunInNewXShareMode`~~、~~`isXSharePrefsReadable`~~，统一合并到 `isPreferencesAvailable`
- `Class.allFields`、`Class.allMethods` 等相关方法新增 `isAccessible` 参数，可控制成员对象何时可被设置为可访问类型
- 修复 `YukiHookDataChannel` 存在多个宿主时在一个 `Activity` 中接收相同键值数据时仅会回调最后一个方法体的问题
- `YukiHookDataChannel` 的 `wait` 等相关方法中新增 `priority` 参数，可传入 `ChannelPriority` 来自定义回调数据结果的条件
- `YukiHookDataChannel` 新增发送数据时自动使用 `ChannelDataWrapper` 类型包装功能，提升使用体验并增强数据保护
- `YukiHookDataChannel` 新增限制一次性发送数据的最大字节大小功能，防止数据过大造成 APP 崩溃
- `YukiHookDataChannel` 新增发送数据过大时自动分段发送功能，仅支持 `List`、`Map`、`Set`、`String` 类型
- `YukiHookLogger` 新增 `contents` 方法与 `saveToFile` 的 `data` 参数，可传入自定义的调试日志数据进行格式化或保存到文件
- 修复 `YukiHookLogger` 处理后的调试日志数据包名可能在 (Xposed) 宿主环境不正确的问题
- 修复 Xposed 模块装载资源钩子 (Resources Hook) 事件时在部分系统上 (部分系统 APP 中) 包名可能不正确的问题

#### 1.1.4 | 2022.10.04 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 `YukiHookDataChannel` 可能不能响应系统框架中响应广播事件的问题，在 Android 13 中复现
- 修复 `YukiHookDataChannel` 长达多个版本在 (Xposed) 宿主环境无法与模块通讯的问题
- `YukiHookDataChannel` 中新增 `obtainLoggerInMemoryData` 方法，可在模块与宿主之间共享调试日志数据
- 修改 `YukiHookLogger.inMemoryData` 的类型为 `ArrayList` 并修改 `YukiLoggerData` 为 `data class`
- 修复 `YukiLoggerData` 在模块中包名为空打印空白的问题
- `PackageParam` 中新增 `loadApp`、`loadZygote`、`loadSystem`、`withProcess` 的同名多参数方法
- 修复了一些可能存在的 BUG

#### 1.1.3 | 2022.09.30 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复一个无法自定义 Hook 入口类名的致命错误
- 添加 `LoggerFactory` 中的部分代码注释文案并更新特色功能文档

#### 1.1.2 | 2022.09.30 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 文档 基础知识 页面新增 English 版本友情链接
- 修复 `YukiBaseHooker` 注释中的 English 文档链接错误问题
- 修复 `ModuleClassLoader` 中的 `ClassCastException` 问题
- 修正并规范部分代码注释
- 新增 `ModuleClassLoader` 排除列表功能，可使用 `excludeHostClasses` 和 `excludeModuleClasses` 方法来自定义排除列表
- 新增 `YukiLoggerData` 实时日志数据类，可实时通过 `YukiHookLogger.inMemoryData` 获取日志数组
- 新增 `ClassLoader.listOfClasses` 方法，可直接获取当前 `Dex` 中的全部 `Class`

#### 1.1.1 | 2022.09.28 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复了文档 基础知识 页面友情链接错误的问题
- 修复了文档 `favicon` 不显示的问题
- 修复 `DexClassFinder` 查找条件中的 BUG

#### 1.1.0 | 2022.09.28 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 这是一次大版本更新，有关更新日志中提到的变化及用法请参考 [KDoc](kdoc://yukihook-core) 
- 更换帮助文档框架到 [VuePress](https://v2.vuepress.vuejs.org)
- 统一并规范文档中的术语名词，例如“查询”一律更改为了“查找”，`XposedHelper` 拼写错误修改为了 `XposedHelpers`
- 文档 基础知识 页面加入友情链接，仅限简体中文
- 将 Hook App Demo 的 `Class` 与 `Method` 转为 Java 以提供更好的演示效果
- 修正了 Hook Module Demo 中的代码注释命名
- 重构了大量底层 Hook 逻辑及 Xposed API 的对接方式
- 移除了 `HookParamWrapper`，现已将其直接与 `YukiBridgeFactory` 对接
- 移动部分 `YukiHookBridge` 中的方法到 `AppParasitics`
- 移除了 `HookParam.args` 与底层的直接对接方法 `setArgs`，直接获取并设置当前数组的对象
- 优化自动处理程序，将引用的 `jar` 合并到 `stub` 项目
- 修复多项目打包时模块包名无法正确匹配的问题以及修改自动处理程序的模块包名匹配逻辑，感谢 [5ec1cff](https://github.com/5ec1cff) 的反馈及提供的解决方案
- 对 API 私有工具类的方法进行了 internal 闭包处理，避免污染顶级命名空间
- 修正了所有反射和 Hook 类的 `Creater` 命名到 `Creator`
- 新增 `YukiHookAPI.Status.compiledTimestamp` 功能，可以在作为 Xposed 模块使用时获取编译完成的时间戳
- 新增 `YukiHookAPI.Status.isXposedEnvironment` 功能，可以判断当前为 (Xposed) 宿主环境还是模块环境
- 调试日志功能进行了大改版，现已将 `YukiHookAPI.Configs.debugTag` 等功能合并到 `YukiHookLogger.Configs` 中
- 调试日志新增可指定打印使用的方法为 `XposedBridge.log` 或 `Logd`
- 调试日志中默认加入当前宿主的包名以及当前用户 ID，以供调试，你可以在 `debugLog` 配置中自行更改
- 新增 `generic` 功能，可对泛型进行反射和调用，你可以在 `Class` 或 `CurrentClass` 中使用它
- 作废 `buildOfAny` 方法，现在请直接使用 `buildOf` 方法 (不带泛型) 来使用构造方法创建新对象并得到结果 `Any`
- 修复 `hasExtends` 使用过程发生空指针异常的问题
- `CurrentClass` 新增非 **lambda** 方式的调用方法
- `CurrentClass` 新增 `name` 与 `simpleName` 功能
- 完全重写 `ReflectionTool` 的核心方法，将不同的查找条件进行了整理分类
- 修复 `ReflectionTool` 中可能的直接调用 `declared` 获取的 `Member` 抛出异常的问题
- 修复 `ReflectionTool` 中 `UndefinedType` 未在 `Method` 与 `Constructor` 条件中正确判断的问题
- 新增反射查找结果发生异常时的友好提示方式，可具体定位到指定条件找不到 `Member` 的问题
- 反射查找 `Method`、`Constructor` 中新增 `VagueType` 条件，可使用在 `param` 条件中用于忽略你不想填写的参数
- 反射查找 `Method`、`Constructor` 中新增 `paramCount { ... }` 条件，现在你可以直接拿到其中的 `it` 来自定义你的判断条件
- `FieldFinder` 结果中新增 `current` 方法，可直接对结果实例创建调用空间
- 修改了反射查找功能中的 `modifiers` 条件以及 `name` 条件，现在你需要对此方法体结尾返回一个 `Boolean` 以使条件成立
- `ModifierRules` 中的 `as*` 功能改名为 `is*`，感谢 [Kitsune](https://github.com/KyuubiRan) 的建议
- `FieldFinder` 中新增 `RemedyPlan` 功能
- 新增 `Dex` 中的 `Class` 模糊查找功能 (Beta)，你现在可以直接使用 `searchClass` 功能来使用指定条件模糊查找 `Class`
- 新增反射查找中的多重查找功能，可使用相对查找条件同时获取多个查找结果，感谢 **AA** 以及 [Kitsune](https://github.com/KyuubiRan) 的建议
- 修复 `appClassLoader` 获取到的对象在某些系统中的系统应用中不正确的问题，感谢 [Luckyzyx](https://github.com/luckyzyx) 的反馈
- 修改了 `XposedBridge.invokeOriginalMethod` 的调用方式并在 `MethodFinder.Result.Instance` 中增加 `original` 功能
- 修复 `YukiHookModulePrefs` 中 `getStringSet` 方法取值错误的问题并优化代码风格，感谢 [Teddy_Zhu](https://github.com/Teddy-Zhu) 的 [PR](https://github.com/HighCapable/YukiHook/pull/19)
- 修改 `YukiHookModulePrefs`，拦截 `XSharePreference` 可能不存在的异常
- 修复 `YukiHookDataChannel` 在某些第三方 ROM 系统框架中无法注册成功的问题
- 安全化 `YukiHookDataChannel`，现在它只能在来自指定包名的模块与宿主之间通信
- 新增自动 Hook `SharedPreferences` 以修复部分系统中文件权限不是 `0664` 的问题，感谢 [5ec1cff](https://github.com/5ec1cff) 的反馈及提供的实现代码
- 新增 `YukiHookAPI.Configs.isEnableHookSharedPreferences` 功能，默认关闭，若 `SharedPreferences` 的权限错误可进行启用
- 修复查找 `Constructor` 时无参构造方法在不填写查找条件时无法找到的 BUG，感谢 **B5 KAKA** 的反馈
- 分离位于 `injectMember` 中 `method`、`constructor` 的 `Result` 实例到 `Process`
- 在 Hook 过程中新增 `useDangerousOperation` 方法，未进行声明时在 Hook 危险列表中的功能后会自动停止 Hook 并打印错误
- 新增模块资源注入与 `Activity` 代理功能，你可以调用 `injectModuleAppResources` 及 `registerModuleAppActivities` 来使用
- 新增 `ModuleContextThemeWrapper` 功能，你可以调用 `applyModuleTheme` 在任意 `Activity` 中创建模块的 `Context`
- 新增 `ClassLoader.onLoadClass` 功能，可用于监听 `ClassLoader` 的 `loadClass` 方法被调用的事件
- 作废了 `classOf` 与 `clazz` 扩展方法，新增 `toClass` 以及 `toClassOrNull` 用法，请现在迁移到新的方法
- `VariousClass` 新增 `getOrNull` 方法，可在匹配不到 `Class` 的时候不抛出异常而是返回 `null`
- `PackageParam.hook` 中移除了 `isUseAppClassLoader` 参数，修改为 `isForceUseAbsolute` 并自动匹配目标 `Class`
- `PackageParam` 新增 `systemContext` 功能，你可以在任意时间调用此功能获取一个持久化的 `Context`
- 不再对外开放 `HookClass` 中的任何方法
- `HookParam` 中新增 `throwToApp` 功能，可将异常直接抛给宿主
- Hook 回调中新增 `onFailureThrowToApp` 功能，可在发生异常时直接抛给宿主
- 修改了调试日志的打印逻辑，反射查找功能中的耗时记录仅会在 Hook 过程中进行打印
- Hook 过程中新增解除 Hook 功能，可使用 `remove` 及 `removeSelf` 方法解除 Hook
- 修复在 ReplaceHook 失败的时候导致宿主抛出异常的问题，现修改为调用原始方法保证宿主功能正常运行
- 新增 Hook 过程中对方法返回值的检查功能，在返回值不匹配的情况下会根据情景自动抛出异常或打印错误
- Resources Hook 中新增 `array` 类型，感谢 [GSWXXN](https://github.com/GSWXXN) 的 [PR](https://github.com/HighCapable/YukiHook/pull/12)
- 移动 `me.weishu.reflection` 到 `thirdparty` 防止同时引入的同名依赖冲突
- 移除 Hook 方法体为空时抛出的异常，修改为打印警告日志
- 修改 `AppLifecycle` 的异常处理逻辑，当其发生异常时直接抛给宿主
- 更新 Demo 的 API 版本到 33

#### 1.0.92 | 2022.05.31 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修正了大量方法中 callback 的命名方法
- 更换方案再次修复 `YukiHookDataChannel` 在低于 **Android 12** 的设备上不能回调当前 `Activity` 广播的问题
- `InjectYukiHookWithXposed` 注解新增 `isUsingResourcesHook` 功能，现在你可以选择性关闭自动生成 `IXposedHookInitPackageResources` 的依赖接口了

#### 1.0.91 | 2022.05.29 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复部分设备的定制系统在 LSPosed 环境下开机启动获取的 `ClassLoader` 错误的问题，感谢 [Luckyzyx](https://github.com/luckyzyx) 的反馈
- 修复 `YukiHookDataChannel` 在 **ZUI** 以及低于 **Android 12** 的系统上不能回调当前 `Activity` 广播的问题
- 整合 `YukiHookModuleStatus` 功能到 `YukiHookAPI.Status`，重写了大量方法，现在你可以在模块与宿主中双向判断模块激活等状态信息

#### 1.0.90 | 2022.05.27 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 `YukiHookDataChannel` 在模块设置监听回调时闪退的问题
- 修复 `YukiHookDataChannel` 在非当前 `Activity` 情况下依然会回调的问题
- 移除 `YukiHookDataChannel` 回调事件的默认值，没有即不回调
- 移除 `YukiHookModulePrefs` 在 XShare 不可读的情况下打印的警告
- 新增 `YukiHookModulePrefs` 中的 `isXSharePrefsReadable` 方法，可判断当前的 XShare 是否可用

#### 1.0.89 | 2022.05.26 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 `YukiHookDataChannel` 不能重复设置监听的问题，并加入在模块不同 `Activity` 中重复响应和自动跟随 `Activity` 销毁监听功能
- 新增 `YukiHookDataChannel` 重复监听用例说明文档
- 加入 `onAlreadyHooked` 方法，可判断当前方法是否被重复 Hook
- 修改部分重复添加 HashMap 的逻辑，移除 `putIfAbsent` 方法，允许覆盖添加
- 修复了几处可能的 BUG

#### 1.0.88 | 2022.05.25 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 对 Xposed API 完全解耦合
- 增加了 `type` 中的 `android` 类型
- 将 `YukiHookModuleStatus` 从自动生成代码中分离，并加入 `isEnableHookModuleStatus` 的开关，由你决定是否启用
- 对 API 大量类的构造方法进行了 internal 闭包处理
- 将 `YukiHookModulePrefs` 设置为单例运行，防止重复创建浪费系统资源
- 修复自 `1.0.80` 版本后无法嵌套 Hook 的 BUG，并优化嵌套 Hook 相关功能
- 修改 Hooker 存储方案由 HashSet 到 HashMap，防止重复添加 Hooker 的问题
- 修改 Hook 核心实现方法，加入查重，避免重复 Hook 多次回调 `HookParam` 方法
- `MethodFinder` 与 `FieldFinder` 加入查找模糊方法、变量名称功能，可调用 `name { ... }` 来设置查找条件，支持正则
- 优化并修改 `appContext` 的获取方式，降低会取到空的问题的可能性
- 修改自动生成的代码中 `logger` 的打印 `TAG` 默认为你自定义的名称，方便进行调试
- 优化 `YukiHookBridge` 的 `Hooker` 实现方式，提升 Hook 性能
- `PackageParam` 增加 `onAppLifecycle` 方法，可原生监听宿主的生命周期以及实现注册系统广播功能
- 新增 `YukiHookDataChannel` 功能，可在模块与宿主保持存活的情况下使用系统无序广播进行通讯
- `YukiHookDataChannel` 增加 `checkingVersionEquals` 方法，可通过监听来验证模块更新后宿主并未更新版本不匹配问题
- `demo-module` 的示例代码中新增 Java 版本的示例，仅供参考

#### 1.0.87 | 2022.05.10 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 新增 `refreshModuleAppResources` 功能，以适配语言区域、字体大小、分辨率改变等情况下的 Resources 刷新
- 新增 `isEnableModuleAppResourcesCache` 功能，可自行设置是否自动缓存当前模块的 Resources

#### 1.0.86 | 2022.05.06 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复不支持 Resources Hook(资源钩子) 的情况下在 `initZygote` 时持续报错的问题，复现在 **ZUI**/**LSPosed CI(1.8.3-6550)**
- 优化并对 Resources Hook 进行异常处理，只有被使用后不支持才会打印错误和警告

#### 1.0.85 | 2022.05.04 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复无法 Hook 系统框架的严重问题，从 `1.0.80` 开始出现
- 调试日志中新增区分 `initZygote` 装载的包名为 `android-zygote`，`packageName` 保持 `android` 不变

#### 1.0.83 | 2022.05.04 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 `YukiHookModuleStatus` 在 `loadSystem` 后大量报错的问题
- 新增 `type` 中的 `android` 类型
- 更新帮助文档的示例说明

#### 1.0.82 | 2022.05.04 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复了一处概念混淆错误，区分 `initZygote` 与系统框架的关系，之前的注释和文档有问题，非常抱歉
- `PackageParam` 新增 `loadSystem` 方法，不需要再写 `loadApp(name = "android")` 即可 Hook 系统框架

#### 1.0.81 | 2022.05.04 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复使用 `by` 方法设置条件后 Hook 方法体内查找不到的方法、构造方法依然输出错误日志的问题
- 在执行 Hook 过程中加入全局日志显示当前 Hook APP 的包名，并修复一处错误日志打印样式的问题

#### 1.0.80 | 2022.05.01 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- `InjectYukiHookWithXposed` 注解新增 `entryClassName` 功能，可自定义生成的 `xposed_init` 入口类名
- ~~`YukiHookXposedInitProxy`~~ 更名为 `IYukiHookXposedInit`，原接口名称已作废，将在后续版本中直接被删除
- 新增 `initZygote` 与 Resources Hook 功能，支持 Hook Layout
- 新增 `onXposedEvent` 方法，可监听原生 Xposed API 的全部事件
- 对 Hook 功能的 **lambda** 进行 `inline` 处理，避免生成过碎的匿名类，提升编译后的运行性能
- 修复 `PrefsData` 编译后的方法体复制过大的问题
- 增加 `XSharePreference` 可读性测试，失败后会自动打印警告日志
- `PackageParam` 新增 `appResources`、`moduleAppResources`、`moduleAppFilePath` 功能
- `PackageParam` 的 `loadApp` 新增不写 `name` 功能，默认筛选全部 APP
- `PackageParam` 新增 `loadZygote` 方法，可直接 Hook 系统框架
- `PackageParam` 新增 `resources().hook` 功能
- 优化方法、构造方法、变量查找功能，找不到的错误日志将优先显示已设置的查找条件
- 增加 `hasExtends` 扩展方法，可判断当前 `Class` 是否有继承关系
- 增加 `isSupportResourcesHook` 功能，判断当前是否支持资源钩子(Resources Hook)
- `current` 功能新增 `superClass` 方法调用父类
- 查找方法、构造方法、变量新增 `superClass` 查找条件，可继续在父类中查找
- YukiHook 大量方法与 Xposed API 解耦合
- 新增 Xposed API 的原生 Hook 优先级功能
- 修复 `isFirstApplication` 可能判断不准确的问题
- 屏蔽 MIUI 系统上 MiuiCatcherPatch 重复调用 Hook 入口方法的问题
- 优化 Hook 入口调用方法，避免因为 Hook Framework 问题导致多次调用
- 修复 Hook `ClassLoader` 导致 Hook 卡死的问题，感谢 [WankkoRee](https://github.com/WankkoRee) 的反馈
- 提升 `XC_Callback` 接口对接后的性能
- Java `type` 新增 `ClassLoader` 类型
- 优化 API 帮助文档，修复可能持续缓存页面的问题

#### 1.0.78 | 2022.04.18 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- `YukiHookModulePrefs` 新增 `isRunInNewXShareMode` 方法，可用于判断模块当前是否处于 `New XSharePreference` 模式
- 修复 `YukiHookModulePrefs` 在 `New XSharePreference` 模式下工作的部分问题
- 新增 `ModulePreferenceFragment`，现在，你可以完全替换掉 `PreferenceFragmentCompat` 并开始使用新的功能
- 适配 `PreferenceFragmentCompat` 的 Sp 数据存储解决方案，感谢 [mahoshojoHCG](https://github.com/mahoshojoHCG) 的反馈
- 更新自动处理程序以及 Kotlin 依赖到最新版本
- 修正部分文档和代码注释中的错误

#### 1.0.77 | 2022.04.15 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- `YukiHookModulePrefs` 新增 `clear` 方法，感谢 [WankkoRee](https://github.com/WankkoRee) 的建议
- `YukiHookModulePrefs` 新增 `getStringSet`、`putStringSet`、`all` 方法
- `HookParam` 的 `args` 增加 `any` 方法
- 新增 `ModuleApplication`，可在模块中继承此类实现更多功能
- 对接全部的 `findClass` 功能到 Xposed API，在非宿主环境继续使用原生 `ClassLoader`
- 修复了一些可能存在的 BUG

#### 1.0.75 | 2022.04.13 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 更正了自动处理程序的逻辑识别部分，感谢 [ApeaSuperz](https://github.com/ApeaSuperz) 的贡献
- 修正一处文档注释的引用未更改的问题
- `HookParam` 中删除了 `firstArgs` 与 `lastArgs` 方法，现在你可以使用 `args().first()` 与 `args().last()` 来取代它
- `HookParam` 中删除了 `args()` 中的默认参数 `index = 0`，现在你可以使用 `args().first()` 或 `args(index = 0)` 来取代它
- `HookParam` 中 `result` 功能增加了泛型匹配，现在你可以使用 `result<T>` 来匹配你的目标方法已知返回值类型了
- 方法、构造方法查找功能新增 `emptyParam` 条件，并完善了文档相关需要注意的查找条件误区
- 增加了 `type` 中的 `android` 类型

#### 1.0.73 | 2022.04.10 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修正几处文档的中文翻译错误，感谢 [WankkoRee](https://github.com/WankkoRee) 的贡献
- 修复在某些情况下 `XC_LoadPackage.LoadPackageParam` 内容为空抛出异常的问题，感谢 [Luckyzyx](https://github.com/luckyzyx) 的反馈
- 修复一些已知的 BUG，提升 Hook 稳定性

#### 1.0.72 | 2022.04.09 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 更新 API 文档到新的地址
- `PackageParam` 中加入 `appContext` 功能
- 修复一些已知的 BUG，提升 Hook 稳定性

#### 1.0.71 | 2022.04.04 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 VariousClass 无法匹配时会停止 Hook 抛出异常的严重问题

#### 1.0.70 | 2022.04.04 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 `instanceClass` 在静态实例中调用后报错问题
- 在 Hook 过程中加入 `isUseAppClassLoader` 功能，感谢 [WankkoRee](https://github.com/WankkoRee) 的反馈
- 加入 `withProcess` 功能，可根据 APP 当前指定进程进行 Hook
- 修复查找方法、构造类和变量的严重逻辑错误问题
- 修复 Hook 目标类不存在的时候无法忽略异常输出的问题
- 修复部分情况下 APP 启动方法装载过快导致 Hook 不能生效的问题
- 修复 `allMethods` 未 Hook 到方法时不会抛出异常的问题，感谢 [WankkoRee](https://github.com/WankkoRee) 的反馈
- 加入 Hook 状态监听功能，感谢 [WankkoRee](https://github.com/WankkoRee) 的建议
- 修改 Xposed 入口注入类的方式，重新声明 API 的定义域
- 加入混淆的方法以及变量的查找功能，可使用不同类型筛选 `index` 定位指定的方法和变量，感谢 [WankkoRee](https://github.com/WankkoRee) 提供的思路
- 查找方法、变量时允许传入多种类型，例如 `String` 声明的类名和 `VariousClass`
- 加入全新的 `current` 功能，可对任意的类构建一个反射方法操作空间，方便地调用和修改其中的方法和变量
- 修复了 Hook 过程中的大量 BUG，感谢 [WankkoRee](https://github.com/WankkoRee) 对此项目所做出的贡献

#### 1.0.69 | 2022.03.30 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 添加并改进一些方法功能的注释
- 增加 Demo 中的更多示例 Hook 内容
- 修复在一个 Hook 实例中，`allMethods` 多次使用时只有最后一个生效的问题，感谢 [WankkoRee](https://github.com/WankkoRee) 的反馈

#### 1.0.68 | 2022.03.29 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 增加 Demo 中的新用例和 LSPosed 作用域
- 增加 `Member` 查找缓存和查找缓存配置开关
- 移除和修改 `MethodFinder`、`FieldFinder` 以及 `HookParam` 相关方法的调用
- 增加更多 `Finder` 中的 `cast` 类型并支持 `cast` 为数组
- 整体的性能和稳定性提升
- 修复上一个版本可能存在的 BUG

#### 1.0.67 | 2022.03.27 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 增加三个 `Finder` 中的 `modifiers` 功能，可筛选 `static`、`native`、`public`、`abstract` 等诸多描述类型
- 增加方法和构造方法查找时可模糊方法参数类型为指定个数进行查找
- 增加 `Member` 的 `hasModifiers` 扩展功能
- 增加 `MethodFinder` 和 `ConstructorFinder` 中的 `give` 方法，可获得原始类型
- 增加 `YukiHookModulePrefs` 中的 `PrefsData` 模板功能
- 彻底对方法、构造方法及变量的查找方案进行重构
- 优化代码注释，修复了可能产生的 BUG

#### 1.0.66 | 2022.03.25 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 `MethodFinder` 中的一个严重问题
- 增加 `hookParam` 中的 `args` 调用方法
- 修复其它可能存在的问题以及修复部分类的注释问题

#### 1.0.65 | 2022.03.25 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 重新发布版本修复 Maven 仓库因为缓存问题新版本不正确的情况
- 增加 `MethodFinder` 与 `FieldFinder` 新的返回值调用方法
- 修复可能存在的问题，并修复太极使用过程中可能存在的问题
- 修复自动生成 Xposed 入口类可能发生的问题
- 增加了 `type` 中的 `android` 类型以及 Java 类型

#### 1.0.6 | 2022.03.20 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 `YukiHookModulePrefs` 在使用一次 `direct` 忽略缓存后每次都忽略的 BUG
- 增加新的 API，作废了 `isActive` 判断模块激活的传统用法
- 修复非 Xposed 环境使用 API 时打印调试日志的问题
- 修复查找 `Field` 时的日志输出问题和未拦截的异常问题
- 解耦合 `ReflectionUtils` 中的 Xposed API
- 增加 `YukiHookModuleStatus` 方法名称的混淆，以精简模块生成的体积
- 装载模块自身 Hook 时将不再打印欢迎信息
- 修复上一个版本仍然存在的某些 BUG

#### 1.0.55 | 2022.03.18 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修正一处注释错误
- 临时修复一个 BUG
- 增加了 `type` 中的大量 `android` 类型以及少量 Java 类型
- 修复新版与旧版 Kotlin APIs 的兼容性问题

#### 1.0.5 | 2022.03.18 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复旧版本 LSPosed 框架情况下欢迎信息多次打印的问题
- 添加 `onInit` 方法来配置 YukiHook
- 新增 `executorName` 和 `executorVersion` 来获取当前 Hook Framework 的名称和版本号
- 新增 `by` 方法来设置 Hook 的时机和条件
- `YukiHookModulePrefs` 新增可控制的键值缓存，可在宿主底座模块动态更新数据
- 修复了一些可能存在的 BUG

#### 1.0.4 | 2022.03.06 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 LSPosed 在最新版本中启用“只有模块classloader可以使用Xposed API”选项后找不到 `XposedBridge` 的问题
- 添加 YukiHook 的常量版本名称和版本号
- 新增 `hasField` 方法以及 `isAllowPrintingLogs` 配置参数
- 新增 `isDebug` 开启的情况下 API 将自动打印欢迎信息测试模块是否生效

#### 1.0.3 | 2022.03.02 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复一个潜在性的异常未拦截 BUG
- 增加 `ignoredError` 功能
- 增加了 `type` 中的 `android` 类型
- 增加监听 `hook` 后的 `ClassNotFound` 功能

#### 1.0.2 | 2022.02.18 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 修复 Windows 下无法找到项目路径的问题
- 移除部分反射 API，合并至 `BaseFinder` 进行整合
- 增加直接使用字符串创建 Hook 的方法

#### 1.0.1 | 2022.02.15 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- `RemedyPlan` 增加 `onFind` 功能
- 整合并修改了部分反射 API 代码
- 增加了 `type` 中的 Java 类型
- 修复忽略错误在控制台仍然输出的问题

#### 1.0 | 2022.02.14 &ensp;<Badge type="danger" text="过期" vertical="middle" />

- 首个版本提交至 Maven