# 模块资源注入

> `YukiHook` 提供了将模块资源、`Activity` 组件以及包装宿主 `Context` 的扩展功能。

## 初始配置

在使用此功能之前，为防止模块与宿主的资源 ID 互相冲突，你需要在当前 Xposed 模块项目的 `build.gradle.kts` 中修改资源 ID。

> 示例如下

```kotlin
android {
    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id", "--package-id", "0x64"
    )
}
```

::: warning

提供的示例资源 ID 值仅供参考，不可使用 `0x7f`，当前宿主可能存在多个 Xposed 模块，所以建议自定义你自己的资源 ID。

:::

## 使用模块资源

如果你只需要读取模块中的资源，你可以直接在 `PackageParam` 中使用 `module.resources`。

> 示例如下

```kotlin
loadApp("io.github.myapp") {
    val appName = module.resources.getString(R.string.app_name)
}
```

## 注入模块资源

在 Hook 宿主之后，我们可以直接在 Hooker 中得到的 `Context` 向宿主注入当前模块资源。

> 示例如下

```kotlin
// 得到 `Activity` 的 `onCreate` 方法
val onCreate = Activity::class.java.getDeclaredMethod(
    "onCreate", Bundle::class.java
)

// 进行 Hook
onCreate.hook().after {
    val activity = instance<Activity>()
    // 方案 1：通过 Context 注入模块资源
    activity.injectModuleResources()
    // 方案 2：直接得到宿主 Resources 注入模块资源
    activity.resources.injectModuleResources()
    // 注入后，可以直接使用模块的资源 ID
    val appName = activity.getString(R.string.app_name)
}
```

你还可以直接在生命周期事件中注入当前模块资源，详情请参考 [宿主生命周期](./host-lifecycle.md)。

> 示例如下

```kotlin
registerAppLifecycle {
    onCreate {
        // 全局注入模块资源，但仅限于全局生命周期
        // 类似 `ImageView.setImageResource` 这样的方法在 `Activity` 中需要单独注入
        injectModuleResources()
        // 注入后，可以直接使用模块的资源 ID
        val appName = getString(R.string.app_name)
    }
}
```

::: warning

注入的资源仅对当前的 `Context` 或 `Resources` 生效，你需要对每个需要使用模块资源的宿主 `Context` 分别进行注入。

在 Android 11 及以上系统中，YukiHook 使用 `ResourcesLoader` 来动态装载模块资源，请在主线程中调用此方法。

低于 Android 11 的系统中注入模块资源后将不再支持 [模块热重载](./hot-reload.md) 功能，因为模块资源已经通过 `addAssetPath` 的方式加载到宿主中。

请只向宿主自身的 `Context` 或 `Resources` 注入模块资源，不要向其他应用的 `Context` 注入，例如通过 `createPackageContext` 得到的 `Context`
或通知 `RemoteViews` 中的 `Context`。这些应用更新后，它们原有的 APK 路径将不再存在，模块热重载时系统无法重建这些
`Resources`，会导致模块资源重载失败。如果你只需要读取模块中的资源，请参考 [使用模块资源](#使用模块资源)。

:::

## 注册模块 Activity

在 Android 系统中所有应用的 `Activity` 启动时，都需要在 `AndroidManifest.xml` 中进行注册，下面的方案介绍了如何通过宿主来直接启动模块中未注册的 `Activity`。

::: danger

此功能至少需要 Android 7.0 及以上系统版本。

:::

> 示例如下

```kotlin
loadApp("io.github.myapp") {
    // 在宿主的 `Application` 可用时注册模块 `Activity`
    withApplicationReady { application ->
        application.registerModuleActivities()
    }
}
```

你还可以直接在得到的 `Context` 中注册当前模块的 `Activity` 代理。

> 示例如下

```kotlin
// 得到 `Activity` 的 `onCreate` 方法
val onCreate = Activity::class.java.getDeclaredMethod(
    "onCreate", Bundle::class.java
)

// 进行 Hook
onCreate.hook().after {
    val activity = instance<Activity>()
    // 通过当前 `Activity` 注册模块 `Activity`
    activity.registerModuleActivities()
}
```

如果你没有填写 `proxyClass` 参数，YukiHook 将会根据当前 `Context` 自动获取当前宿主的启动入口 `Activity` 进行注册。

自动注册模式可能不稳定，例如一些 `Activity` 会在注册清单上加入具有干扰性的启动参数，此时我们就需要使用下面这种解决方案。

若未注册的 `Activity` 不能被正确启动，我们可以对宿主的 `AndroidManifest.xml` 进行 AXML 反编译分析以得到一个注册过的 `Activity` 标签，获取其中的 `name`。

你需要选择一个当前宿主可能用不到的 `Activity` 作为一个 “傀儡” 将其注册为代理。

> 示例如下

```xml
<activity
    android:name="io.github.myapp.FooActivity"
    ...>
```

根据其中的 `name`，我们只需要在方法中加入这个参数进行注册即可。

> 示例如下

```kotlin
registerModuleActivities("io.github.myapp.FooActivity")
```

注册完成后，请将你需要使用宿主启动的模块中的 `Activity` 实现 `ModuleActivity` 接口。

我们推荐你创建 `BaseActivity` 作为所有模块 `Activity` 的基类。

> 示例如下

```kotlin
abstract class BaseActivity : AppCompatActivity(), ModuleActivity {

    // 设置 AppCompat 主题 (如果当前是 [AppCompatActivity])
    override val moduleTheme get() = R.style.YourAppTheme

    override fun getClassLoader() = delegate.getClassLoader()

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.onCreate(savedInstanceState)
        super.onCreate(savedInstanceState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        delegate.onConfigurationChanged(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        delegate.onRestoreInstanceState(savedInstanceState)
        super.onRestoreInstanceState(savedInstanceState)
    }
}
```

然后将需要实现的 `Activity` 继承于 `BaseActivity`。

> 示例如下

```kotlin
class MyHostActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 模块资源已被自动注入，你可以直接使用 XML 装载布局
        setContentView(R.layout.activity_main)
    }
}
```

以上步骤全部完成后，你就可以在宿主环境任意存在 `Context` 的地方愉快地调用 `startActivity` 了。

> 示例如下

```kotlin
val context: Context = ... // 假设这就是你的 Context
context.startActivity(Intent(context, MyHostActivity::class.java))
```

上面我们注册的 `proxyClass` 参数为默认的全局代理 `Activity`。

如果你需要指定某个 `Activity` 使用另外的宿主 `Activity` 进行代理，你可以参考如下方法。

> 示例如下

```kotlin
class MyHostActivity : BaseActivity() {

    // 指定一个另外的代理 `Activity` 类名，其也必须存在于宿主的 AndroidManifest 中
    override val proxyClassName = "io.github.myapp.OtherActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
```

## 包装 Context

有时候，我们需要使用 `MaterialAlertDialogBuilder` 来美化自己在宿主中的对话框，但是拿不到 AppCompat 主题就无法创建。

> 直接创建会得到如下异常

```text:no-line-numbers
The style on this component requires your app theme to be Theme.AppCompat (or a descendant).
```

此时，我们想在宿主被 Hook 的当前 `Activity` 中使用 `MaterialAlertDialogBuilder` 来创建对话框，就可以有如下方法。

> 示例如下

```kotlin
// 得到 `Activity` 的 `onCreate` 方法
val onCreate = Activity::class.java.getDeclaredMethod(
    "onCreate", Bundle::class.java
)

// 进行 Hook
onCreate.hook().after {
    val activity = instance<Activity>()
    // 使用 `applyModuleTheme` 创建一个当前模块中的主题资源
    val appCompatContext = activity.applyModuleTheme(R.style.Theme_AppCompat)
    // 直接使用这个包装了模块主题后的 Context 创建对话框
    MaterialAlertDialogBuilder(appCompatContext)
        .setTitle("AppCompat 主题对话框")
        .setMessage("我是一个在宿主中显示的 AppCompat 主题对话框。")
        .setPositiveButton("确定", null)
        .show()
}
```

::: warning

由于一些应用自身使用的 `androidx` 依赖或自定义主题可能会对当前 `MaterialAlertDialog` 实际样式造成干扰，例如对话框的按钮样式，这种情况你可以参考模块 Demo 中 [这里的示例代码](repo://tree/1.x/samples/demo-module-modern/src/main/java/com/highcapable/yukihookapi/demo/module/modern/ui/component/ComponentCompat.kt) 来修复这个问题。

某些应用在创建时可能会发生 `ClassCastException` 异常，请手动指定新的 `Configuration` 实例来进行修复。

:::

## ClassLoader 冲突

直接将模块的资源注入到宿主时，由于模块与宿主不在同一个 APK 中，其可能存在 `ClassLoader` 冲突的问题。

若发生了 `ClassLoader` 冲突，你可能会遇到 `ClassCastException` 异常。

YukiHook 默认已解决了可能冲突的问题，其余情况需要你自行配置排除列表。

排除列表决定了这些 `Class` 需要被模块还是宿主的 `ClassLoader` 进行装载。

> 示例如下

```kotlin
// 排除属于宿主的 `Class` 类名
// 它们将会被宿主的 `ClassLoader` 装载
// 以下内容仅供演示，不要直接使用，请以你的实际情况为准
ModuleClassLoader.excludeHostClasses(
    "androidx.core.app.ActivityCompat",
    "io.github.myapp.Test"
)
// 排除属于模块的 `Class` 类名
// 它们将会被模块 (当前 Hook 进程) 的 `ClassLoader` 装载
// 以下内容仅供演示，不要直接使用，请以你的实际情况为准
ModuleClassLoader.excludeModuleClasses(
    "io.github.mymodule.entry.HookEntry",
    "io.github.mymodule.controller.ModuleController"
)
```

::: warning

你需要在向宿主注入模块资源的方法执行之前进行设置才能生效。

此功能仅为解决宿主与模块中可能存在**同名 `Class`** 的情况，例如共用的 SDK 以及依赖，在大部分情况下你不会用到此功能。

:::