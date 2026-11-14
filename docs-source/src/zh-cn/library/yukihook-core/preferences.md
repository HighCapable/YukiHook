# 模块偏好设置

> 这是一个模块私有偏好设置与宿主互通的解决方案。

我们需要存储模块的偏好设置，以供宿主调用，这个时候会遇到原生 `SharedPreferences` 存储的数据互通阻碍。

YukiHook 提供了 `YukiHookPreferences`，它会根据当前使用的底座模块自动选择合适的存储方案。

## 可用性

此功能极度依赖于当前 Hook 框架提供的解决方案，并受到模块所处系统环境的影响。

YukiHook 会在使用 [yukihook-runtime-xposed82](../yukihook-runtime-xposed82.md) 底座构建的模块中使用 `XSharedPreferences` 方案，在使用
[yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) 底座构建的模块中使用 Remote `SharedPreferences` 方案。

::: warning

在使用 [yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) 底座的模块中，建议监听框架服务的可用状态后使用，详情请参考 [模块状态 → Hook 框架服务状态](./module-status.md#hook-框架服务状态)。

从 `1.5.1` 起，在 Hook 框架提供的解决方案不可用时，偏好设置不会再回退到私有存储允许可读写，而是会写入内存并打印警告日志，在模块重启后会丢失，在解决方案恢复可用时，也不会重新将内存中的数据写回存储，推荐显式使用 `isAvailable` 判断偏好设置当前是否可用。

:::

### LSPosed

在 1.9.0 及以上版本中，请直接使用 [yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) 底座构建模块，这是目前最稳定的使用方案。

如果你依然在使用 [yukihook-runtime-xposed82](../yukihook-runtime-xposed82.md) 底座构建模块，你需要在 `AndroidManifest.xml` 中将模块的 `xposedminversion` 提升至 `93` 并添加 `xposedsharedprefs` 元数据以启用 New XSharedPreferences。

但是在 2.1.0 及以后的版本中这个功能已经被标记弃用，且其从 1.9.0+ 直到现在以来存在的 selinux 兼容问题一直无法解决，所以我们也不再推荐继续使用
New XSharedPreferences，根据 LSPosed 的官方公告，它会在 2.3.0 版本中被移除。

### Vector

这是一个 LSPosed 的第三方 Fork 项目，目前与 LSPosed 功能类似，但是我们没有实测，所以不对其兼容性做出任何保证。

### EdXposed

项目已停止更新，如果你正在老旧设备上使用，YukiHook 仍然会沿用 `XSharedPreferences` 方案，我们对兼容性不做出任何保证。

### 未列出

对于未列出的 Hook 框架，只要它们是按照底座提供的 API 规范实现的，YukiHook 仍然会根据底座类型选择合适的存储方案。

## 基本用法

在使用之前，你需要使用 `isAvailable` 判断偏好设置当前是否可用，使用 `isWritable` 判断偏好设置在当前环境中是否可写。

当你在模块中存储偏好设置的时候，若当前处于 `Activity` 等存在 `Context` 的地方，可以使用如下方法。

> 示例如下

```kotlin
preferences().edit {
    putString("foo", "bar")
}
```

当你在宿主中读取偏好设置时，可以在 `PackageParam` 中使用如下方法。

> 示例如下

```kotlin
val value = preferences.getString("foo", "default")
```

若要实现偏好设置的区域划分，你可以指定每个存储文件的名称。

在模块中这样使用。

> 示例如下

```kotlin
// 推荐用法
preferences("challenge").edit {
    putString("foo", "bar")
}
// 也可以这样用
preferences().name("challenge").edit {
    putString("foo", "bar")
}
```

在宿主中这样读取。

> 示例如下

```kotlin
// 推荐用法
val value = preferences("challenge").getString("foo", "default")
// 也可以这样用
val value = preferences.name("challenge").getString("foo", "default")
```

你可以调用 `edit` 方法使用以下两种方式来批量存储偏好设置。

> 示例如下

```kotlin
// 方案 1
preferences().edit {
    putString("foo-1", "bar-1")
    putString("foo-2", "bar-2")
}
// 方案 2
preferences().edit()
    .putString("foo-1", "bar-1")
    .putString("foo-2", "bar-2")
    .apply()
```

::: danger

模块的偏好设置在宿主中是只读的，在宿主中对其进行修改将会抛出异常。

:::

## 使用模板

若你的项目中有大量的固定偏好设置需要存储和读取，推荐使用 `PreferenceData` 来创建模板。

> 示例如下

```kotlin
object Feature {
    val Foo = PreferenceData("foo", "default")
}
```

然后使用 `get` 与 `put` 读取和存储偏好设置，未设置默认值时将使用模板中的默认值。

> 示例如下

```kotlin
// 在模块中存储
preferences().edit {
    put(Feature.Foo, "bar")
}
// 在宿主中读取
val value = preferences.get(Feature.Foo)
```

## 在 PreferenceFragment 中使用

若你的模块使用了 `PreferenceFragmentCompat`，你可以将其继承类迁移到 `ModulePreferenceFragment`。

> 示例如下

```kotlin
class SettingsFragment : ModulePreferenceFragment() {

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)
    }
}
```

::: danger

你必须继承 `ModulePreferenceFragment` 才能将设置项存储到 `YukiHookPreferences` 中。

从 `1.5.1` 起，`onCreatePreferences` 将不再允许被覆写，你需要将原来的初始化代码迁移至 `onPreferencesReady`。

:::

## 使用原生方式存储

在模块环境中 `YukiHookPreferences` 默认会将偏好设置存储到底座模块选择的共享位置中。

如果你想直接将偏好设置存储到模块或宿主当前环境自身的私有目录，你可以使用 `native` 方法。

> 示例如下

```kotlin
// 存储私有设置
preferences.native().edit {
    putBoolean("isolation", true)
}
// 读取私有设置
val isolation = preferences.native().getBoolean("isolation")
// 读取共享设置
val shared = preferences.getBoolean("shared")
```

使用 `native` 方法后，无论在模块还是宿主中都会将数据<u>**在对应环境的私有目录中**</u>存储、读取，它们相互隔离，此时在宿主中也可以写入偏好设置。

::: warning

在宿主中使用 `native` 方法需要宿主的 `Application` 已经创建完成，否则将会抛出异常，你可以在 [宿主生命周期](./host-lifecycle.md) 的 `onCreate` 中使用它。

:::