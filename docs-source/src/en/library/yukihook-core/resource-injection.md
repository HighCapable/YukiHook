# Module Resource Injection

> `YukiHook` provides extensions that inject module resources and `Activity` components into hosts and wrap the `Context` of hosts.

## Initial Configuration

Before using this feature, to keep the resource IDs of the module and the host from conflicting,
you need to change the resource ID in `build.gradle.kts` of the current Xposed module project.

> The following example

```kotlin
android {
    androidResources.additionalParameters += listOf(
        "--allow-reserved-package-id", "--package-id", "0x64"
    )
}
```

::: warning

The resource ID value in the example is only for reference, `0x7f` cannot be used. The current host may contain multiple Xposed modules, so it is recommended to use your own resource ID.

:::

## Use Module Resources

If you only need to read the resources of the module, you can use `module.resources` directly in `PackageParam`.

> The following example

```kotlin
loadApp("io.github.myapp") {
    val appName = module.resources.getString(R.string.app_name)
}
```

## Inject Module Resources

After hooking the host, we can inject the resources of the current module into the host directly through the `Context` we get in the hooker.

> The following example

```kotlin
// Get the `onCreate` method of `Activity`.
val onCreate = Activity::class.java.getDeclaredMethod(
    "onCreate", Bundle::class.java
)

// Hook it.
onCreate.hook().after {
    val activity = instance<Activity>()
    // Option 1: Inject module resources through the Context.
    activity.injectModuleResources()
    // Option 2: Inject module resources into the host Resources directly.
    activity.resources.injectModuleResources()
    // After injection, you can use the resource IDs of the module directly.
    val appName = activity.getString(R.string.app_name)
}
```

You can also inject the resources of the current module directly in lifecycle events, for details please refer to [Host Lifecycle](./host-lifecycle.md).

> The following example

```kotlin
registerAppLifecycle {
    onCreate {
        // Inject module resources globally, but only for the global lifecycle.
        // Functions such as `ImageView.setImageResource` need a separate injection in an `Activity`.
        injectModuleResources()
        // After injection, you can use the resource IDs of the module directly.
        val appName = getString(R.string.app_name)
    }
}
```

::: warning

Injected resources only apply to the current `Context` or `Resources`, you need to inject them into each host `Context` that uses module resources.

On Android 11 and above, YukiHook uses `ResourcesLoader` to dynamically load module resources. Please call this method on the main thread.

Below Android 11, after injecting module resources, the [Module Hot Reload](./hot-reload.md) feature will no longer be supported
because the module resources have been loaded into the host through `addAssetPath`.

Only inject module resources into the host's own `Context` or `Resources`, do not inject them into the `Context` of other apps,
such as a `Context` obtained through `createPackageContext` or the `Context` used by notification `RemoteViews`.
After these apps are updated, their original APK paths no longer exist, the system cannot rebuild these `Resources` during module hot reload,
and reloading module resources will fail.
If you only need to read the resources of the module, please refer to [Use Module Resources](#use-module-resources).

:::

## Register Module Activity

On Android, every `Activity` of an app must be registered in `AndroidManifest.xml` before it can start.
The following solution describes how to start an unregistered `Activity` of the module directly through the host.

::: danger

This feature requires Android 7.0 or above.

:::

> The following example

```kotlin
loadApp("io.github.myapp") {
    // Register module `Activity` when the `Application` of the host is available.
    withApplicationReady { application ->
        application.registerModuleActivities()
    }
}
```

You can also register the `Activity` proxy of the current module directly on a `Context` you get.

> The following example

```kotlin
// Get the `onCreate` method of `Activity`.
val onCreate = Activity::class.java.getDeclaredMethod(
    "onCreate", Bundle::class.java
)

// Hook it.
onCreate.hook().after {
    val activity = instance<Activity>()
    // Register module `Activity` through the current `Activity`.
    activity.registerModuleActivities()
}
```

If you do not set the `proxyClass` parameter, YukiHook gets the launch entry `Activity` of the current host based on the current `Context` and registers it automatically.

The automatic registration mode may be unstable, for example some `Activity` entries add disruptive launch parameters in the manifest, then we need the following solution.

If the unregistered `Activity` cannot start correctly, we can decompile and analyze the AXML of the `AndroidManifest.xml` of the host to find a registered `Activity` tag and get its `name`.

You need to choose an `Activity` that the current host may not use as a "puppet" and register it as the proxy.

> The following example

```xml
<activity
    android:name="io.github.myapp.FooActivity"
    ...>
```

With its `name`, we only need to pass it as the parameter to register.

> The following example

```kotlin
registerModuleActivities("io.github.myapp.FooActivity")
```

After registration, make the `Activity` in the module that needs to start in the host implement the `ModuleActivity` interface.

We recommend creating a `BaseActivity` as the base class of all module `Activity` classes.

> The following example

```kotlin
abstract class BaseActivity : AppCompatActivity(), ModuleActivity {

    // Set the AppCompat theme (if this is an [AppCompatActivity]).
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

Then make the `Activity` inherit `BaseActivity`.

> The following example

```kotlin
class MyHostActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Module resources have been injected automatically, you can load the layout from XML directly.
        setContentView(R.layout.activity_main)
    }
}
```

After all the steps above, you can happily call `startActivity` anywhere a `Context` exists in the host environment.

> The following example

```kotlin
val context: Context = ... // Suppose this is your Context.
context.startActivity(Intent(context, MyHostActivity::class.java))
```

The `proxyClass` parameter we registered above is the default global proxy `Activity`.

If you need a specific `Activity` to use another host `Activity` as its proxy, you can refer to the following.

> The following example

```kotlin
class MyHostActivity : BaseActivity() {

    // Specify another proxy `Activity` class name, it must also exist in the AndroidManifest of the host.
    override val proxyClassName = "io.github.myapp.OtherActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
```

## Wrap Context

Sometimes we want to use `MaterialAlertDialogBuilder` to make our dialogs in the host look better, but it cannot be created without an AppCompat theme.

> Creating it directly throws the following exception

```text:no-line-numbers
The style on this component requires your app theme to be Theme.AppCompat (or a descendant).
```

To create a dialog with `MaterialAlertDialogBuilder` in the hooked `Activity` of the host, you can do the following.

> The following example

```kotlin
// Get the `onCreate` method of `Activity`.
val onCreate = Activity::class.java.getDeclaredMethod(
    "onCreate", Bundle::class.java
)

// Hook it.
onCreate.hook().after {
    val activity = instance<Activity>()
    // Use `applyModuleTheme` to create a theme from the current module.
    val appCompatContext = activity.applyModuleTheme(R.style.Theme_AppCompat)
    // Create the dialog with the Context wrapped with the module theme.
    MaterialAlertDialogBuilder(appCompatContext)
        .setTitle("AppCompat Theme Dialog")
        .setMessage("I am an AppCompat theme dialog showing in the host.")
        .setPositiveButton("OK", null)
        .show()
}
```

::: warning

Some apps use their own `androidx` libraries or custom themes that may affect the actual style of the `MaterialAlertDialog`, such as the button style.
In this case, you can refer to [this example code](repo://tree/1.x/samples/demo-module-modern/src/main/java/com/highcapable/yukihookapi/demo/module/modern/ui/component/ComponentCompat.kt) in the module demo to fix it.

Some apps may throw a `ClassCastException` when creating it, please pass a new `Configuration` instance manually to fix it.

:::

## ClassLoader Conflicts

When injecting the resources of the module into the host directly, since the module and the host are not in the same APK, `ClassLoader` conflicts may occur.

When a `ClassLoader` conflict occurs, you may get a `ClassCastException`.

YukiHook resolves possible conflicts by default, for other cases you need to configure the exclusion lists yourself.

The exclusion lists decide whether these `Class` objects are loaded by the `ClassLoader` of the module or the host.

> The following example

```kotlin
// Exclude `Class` names that belong to the host.
// They will be loaded by the `ClassLoader` of the host.
// The following content is only for demonstration, do not use it directly, adjust it to your actual case.
ModuleClassLoader.excludeHostClasses(
    "androidx.core.app.ActivityCompat",
    "io.github.myapp.Test"
)
// Exclude `Class` names that belong to the module.
// They will be loaded by the `ClassLoader` of the module (the current hook process).
// The following content is only for demonstration, do not use it directly, adjust it to your actual case.
ModuleClassLoader.excludeModuleClasses(
    "io.github.mymodule.entry.HookEntry",
    "io.github.mymodule.controller.ModuleController"
)
```

::: warning

You need to set them before the functions that inject module resources into the host run.

This feature only solves the case where the host and the module contain **a `Class` with the same name**, such as shared SDKs and dependencies, and you won't need it in most cases.

:::