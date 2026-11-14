# Module Preferences

> This is a solution for sharing the private preferences of the module with hosts.

We need to store the preferences of the module for hosts to use, and the native `SharedPreferences` cannot share the stored data between them.

YukiHook provides `YukiHookPreferences`, which automatically chooses a suitable storage solution based on the base module you use.

## Availability

This feature heavily depends on the solution provided by the current hook framework, and is affected by the system environment the module runs in.

YukiHook uses the `XSharedPreferences` solution in modules built with the [yukihook-runtime-xposed82](../yukihook-runtime-xposed82.md) base module, and the remote `SharedPreferences` solution in modules built with the
[yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) base module.

::: warning

In modules using the [yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) base module, it is recommended to observe framework service availability before use.
See [Module Status → Hook Framework Service Status](./module-status.md#hook-framework-service-status).

Starting with `1.5.1`, when the storage solution provided by the hook framework is unavailable, preferences no longer fall back to private storage for reads and writes.
Instead, data is written to memory and a warning is logged. This data is lost when the module restarts and is not written back to storage when the solution becomes available again.
It is recommended to explicitly check `isAvailable` to determine whether preferences are currently available.

:::

### LSPosed

In 1.9.0 and above, please build your module with the [yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) base module directly, which is currently the most stable solution.

If you are still building your module with the [yukihook-runtime-xposed82](../yukihook-runtime-xposed82.md) base module,
you need to raise the module's `xposedminversion` to `93` and add the `xposedsharedprefs` metadata in the `AndroidManifest.xml` to enable New XSharedPreferences.

However, this feature has been marked as deprecated since 2.1.0, and its selinux compatibility issues, which have existed since 1.9.0+, have never been solved, so we no longer recommend using
New XSharedPreferences. According to the official announcement of LSPosed, it will be removed in version 2.3.0.

### Vector

This is a third-party fork of LSPosed with features similar to LSPosed for now, but we have not tested it, so we make no guarantee of its compatibility.

### EdXposed

The project is no longer updated. If you are using it on old devices, YukiHook still uses the `XSharedPreferences` solution, and we make no guarantee of its compatibility.

### Not Listed

For hook frameworks that are not listed, as long as they are implemented according to the API specification provided by the base module, YukiHook still chooses a suitable storage solution based on the base module type.

## Basic Usage

Before use, check `isAvailable` to determine whether preferences are currently available, and `isWritable` to determine whether they are writable in the current environment.

When storing preferences in the module, if you are in a place with a `Context` such as an `Activity`, you can use the following.

> The following example

```kotlin
preferences().edit {
    putString("foo", "bar")
}
```

When reading preferences in a host, you can use the following in `PackageParam`.

> The following example

```kotlin
val value = preferences.getString("foo", "default")
```

To split preferences into areas, you can specify the name of each storage file.

Use it like this in the module.

> The following example

```kotlin
// Recommended usage.
preferences("challenge").edit {
    putString("foo", "bar")
}
// It can also be used like this.
preferences().name("challenge").edit {
    putString("foo", "bar")
}
```

Read it like this in a host.

> The following example

```kotlin
// Recommended usage.
val value = preferences("challenge").getString("foo", "default")
// It can also be used like this.
val value = preferences.name("challenge").getString("foo", "default")
```

You can call `edit` in the following two ways to store preferences in batches.

> The following example

```kotlin
// Option 1
preferences().edit {
    putString("foo-1", "bar-1")
    putString("foo-2", "bar-2")
}
// Option 2
preferences().edit()
    .putString("foo-1", "bar-1")
    .putString("foo-2", "bar-2")
    .apply()
```

::: danger

Module preferences are read-only in hosts. Attempting to modify them in a host throws an exception.

:::

## Use Templates

If your project has a lot of fixed preferences to store and read, it is recommended to create templates with `PreferenceData`.

> The following example

```kotlin
object Feature {
    val Foo = PreferenceData("foo", "default")
}
```

Then use `get` and `put` to read and store preferences, the default value of the template is used when no default value is given.

> The following example

```kotlin
// Store in the module.
preferences().edit {
    put(Feature.Foo, "bar")
}
// Read in a host.
val value = preferences.get(Feature.Foo)
```

## Use in PreferenceFragment

If your module uses `PreferenceFragmentCompat`, you can move its subclasses to `ModulePreferenceFragment`.

> The following example

```kotlin
class SettingsFragment : ModulePreferenceFragment() {

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)
    }
}
```

::: danger

You must inherit `ModulePreferenceFragment` to store preferences in `YukiHookPreferences`.

Starting with `1.5.1`, `onCreatePreferences` can no longer be overridden. Move your existing initialization code to `onPreferencesReady`.

:::

## Use Native Storage

In the module environment, `YukiHookPreferences` stores preferences in the shared location chosen by the base module by default.

If you want to store preferences directly in the private directory of the current module or host environment, you can use the `native` function.

> The following example

```kotlin
// Store private preferences.
preferences.native().edit {
    putBoolean("isolation", true)
}
// Read private preferences.
val isolation = preferences.native().getBoolean("isolation")
// Read shared preferences.
val shared = preferences.getBoolean("shared")
```

After using `native`, data is stored and read <u>**in the private directory of the corresponding environment**</u> in both the module and hosts,
they are isolated from each other, and hosts can also write preferences in this case.

::: warning

Using `native` in a host requires the `Application` of the host to be created, otherwise an exception is thrown. You can use it in `onCreate` of the [Host Lifecycle](./host-lifecycle.md).

:::