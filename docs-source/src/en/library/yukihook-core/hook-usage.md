# Hook Usage

> Here describes the basic hook styles of `YukiHook` and shows common hook cases.

## Structure Diagram

The structure below describes how YukiHook basically works.

```text:no-line-numbers
Host Environment
└─ PackageParam
   └─ Member
      ├─ hook
      │  ├─ before
      │  ├─ after
      │  └─ intercept
      └─ intercept
         └─ proceed
```

YukiHook provides two hook styles.

- `hook`: the classic hook style, get `HookParam` in `before` and `after` callbacks, or replace the whole method with `intercept`
- `intercept`: the chain hook style, get `HookChain` in the callback and decide whether to continue the remaining chain with `proceed`

## Basic Usage

Suppose we want to hook the `onCreate` method of every `Activity` in `io.github.myapp` and show a dialog after it runs.

> The following example

```kotlin
loadApp("io.github.myapp") {
    val activityClass = Activity::class.java
    // Get the onCreate method with Java reflection.
    val onCreate = activityClass.getDeclaredMethod(
        "onCreate", Bundle::class.java
    )

    onCreate.hook {
        after {
            // Get the instance that the current hooked method belongs to.
            val context = instance<Activity>()

            AlertDialog.Builder(context)
                .setTitle("Hooked")
                .setMessage("onCreate has been hooked!")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
```

Now the `onCreate` method is hooked, and this dialog shows when each `Activity` in `io.github.myapp` starts.

When you only need one hook callback, you can shorten the `hook { ... }` block.

> The following example

```kotlin
// Suppose this is the method you need to hook.
val method: Method
// Hook it.
method.hook().before {
    // Your code here.
}
```

If you need to hook multiple methods at once, you can use `hookAll`.

> The following example

```kotlin
// Suppose this is the list of methods you need to hook.
val methods: List<Method>
// Hook them.
methods.hookAll {
    after {
        // Your code here.
    }
}
// Or
methods.hookAll().after {
    // Your code here.
}
```

::: tip

YukiHook supports hooking `Method`, `Constructor` and `Class` members. For a `Class`, it hooks the `<clinit>` static initializer block,
for details please refer to [Hook Static Initializers](#hook-static-initializers) in this page.

Different hook styles have different requirements for the base module. When the style you use does not support the current base module,
you will get an explicit Lint warning or error.

:::

## Classic Hook

This style provides the before/after callback hooks from the Xposed Framework API era.
`before` and `after` are called before and after the method runs, you can set both of them.

In the callback body, you get the `HookParam` object, both callbacks receive this object.

> The following example

```kotlin
hook {
    before {
        // Get the instance of the current hook.
        instance
        // Get and cast the instance of the current hook to type T.
        instance<T>()
        // A static method has no instance, use `instanceOrNull`.
        instanceOrNull
        // Get and cast the instance of the current hook to type T,
        // null when it is absent or has a different type.
        instanceOrNull<T>()
        // Get the hooked method or constructor.
        member
        // Get the hooked method.
        method
        // Get the hooked constructor.
        constructor
        // Get the argument array.
        args
        // Get the first argument as T.
        firstArg().get<T>()
        // Get the last argument as T.
        lastArg().get<T>()
        // Get the argument at any index as T, 2 is used here as an example.
        arg(2).get<T>()
        // Read an argument as T, returning null if it is missing, null, or has a different type.
        arg(2).getOrNull<T>()
        // Set the argument at any index, 2 is used here as an example.
        arg(2).set(...)
        // Get the data storage of the current callback scope.
        dataExtra
    }
    after {
        // Get the result.
        result
        // Get the result and cast it to T.
        result<T>()
        // Modify the result.
        result = ...
    }
}
```

::: danger

Setting `result` to a value that does not match the return type of the method throws an exception and keeps the original result.

:::

### Intercept the Method

If you need to replace the logic of the whole method, you can use `intercept`.

> The following example

```kotlin
hook {
    intercept {
        // Implement the replaced logic here.
        // Return the value for the method, no need to write return, just put the value at the last line.
        0
    }
}
```

If you only need to replace the method with a fixed result, you can pass the value directly.

> The following example

```kotlin
hook {
    // Replace with the result you need.
    intercept(true)
}
```

Without any argument, the method is intercepted and returns `null`. For methods without a return value and constructors, the result of `intercept` is ignored.

::: danger

`intercept` replaces the `before` and `after` callbacks, do not use them in the same `hook` block.

Returning `null` for a method whose return type is a primitive type such as `Int` or `Boolean` may cause the host to throw an exception.

:::

### Call the Original Method

In the callback body, you can call the original method without hooks.

> The following example

```kotlin
hook {
    intercept {
        // Call the original method with the original arguments, the generic type can be omitted.
        callOriginal<Any?>()
        // Call the original method with custom arguments, the generic type can be omitted.
        invokeOriginal<Any?>(...)
    }
}
```

Outside hook callbacks, you can also use `invokeOriginal` to call the original method of a `Method` without hooks.

> The following example

```kotlin
// Suppose this is the method of the current class.
val method: Method
// Suppose this is the instance of the current class.
val instance: Any
// Call the original implementation of the current method.
method.invokeOriginal(instance)
```

::: tip

You can also use `invokeSpecial`, `newInstanceOriginal`, `newInstanceSpecial` and so on to call methods non-virtually or call original constructors without hooks.

Some of these features require support from the corresponding [base module](../../guide/quick-start.md#choose-a-base-module).

:::

## Chain Hook

This style provides a chain interception style of hooking, the `HookChain` object in the callback can continue the remaining chain with `proceed`.

Without calling `proceed`, the original method does not run, and the value at the last line of the `intercept` block becomes the result of the method.

> The following example

```kotlin
// Suppose this is the method you need to hook.
val method = TextView::class.java.getDeclaredMethod(
    "setText", CharSequence::class.java, TextView.BufferType::class.java
)
// Hook it.
method.intercept {
    // Continue the chain after changing the first argument.
    if (firstArg<CharSequence>() == "Hello")
        proceed(arrayOf("Hello YukiHook", lastArg()))
    else proceed()
}
```

In `HookChain`, you can get the following.

> The following example

```kotlin
intercept {
    // Get the instance of the current hook.
    instance
    // Get the read-only argument array.
    args
    // Get the argument at any index as T, 2 is used here as an example.
    arg<T>(2)
    // Read an argument as T, returning null if it is missing, null, or has a different type.
    argOrNull<T>(2)
    // Continue the chain with the current arguments.
    proceed()
    // Continue the chain with new arguments.
    proceed(arrayOf(...))
    // Continue the chain with a new instance.
    proceedWith(instance = ...)
}
```

The arguments in `HookChain` are read-only, to change them, pass a new argument array to `proceed`.

If you need to intercept multiple methods at once, you can use `interceptAll`.

::: warning

In the [yukihook-runtime-xposed82](../yukihook-runtime-xposed82.md) base module, chain hooks are simulated with a low-priority hook.
`proceed` only runs the remaining chain hooks of the current module and then the original method, other modules and classic hooks are not part of this chain.

:::

## Hook Static Initializers

You can use `hook` or `intercept` directly on a `Class` to hook its static initializer.

> The following example

```kotlin
// Suppose this is the class you need to hook.
val clazz: Class<*>
// Hook <clinit>.
clazz.hook {
    after {
        // Your code here.
    }
}
```

::: warning

Make sure the `Class` is not initialized when you get it. If the `Class` is already initialized, this hook is never called.

Intercepting the static initializer skips it, and static fields keep their JVM default values.

Earlier versions of YukiHook once provided a `hook` function on `Class`, which was an ambiguous API design. It was later deprecated
and officially removed in `1.5.0`, and it is unrelated to the current feature.

:::

::: danger

This feature requires the [yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) base module.

:::

## Hook Priority

You can set `YukiHookPriority` in `hook` and `intercept` to decide the priority of the callback.

> The following example

```kotlin
hook(priority = YukiHookPriority.HIGHEST) {
    // Your code here.
}
```

| Priority  | Description                                 |
| --------- | ------------------------------------------- |
| `DEFAULT` | The default priority                        |
| `HIGHEST` | Called before callbacks with lower priority |
| `LOWEST`  | Called after callbacks with higher priority |

## Remove Hooks

YukiHook provides two ways to remove hooks.

The first way is to keep the `Handle` instance returned by `hook` or `intercept`, and call `remove` at the right time to remove the hook.

> The following example

```kotlin
// Keep the current instance in a variable.
val handle = method.hook {
    after {
        // ...
    }
}
// Call it at the right time.
handle.remove()
```

The second way is to call `removeSelf` in the hook callback to remove itself.

> The following example

```kotlin
method.hook {
    after {
        // Call it directly.
        removeSelf()
    }
}
```

For multiple methods hooked together with `hookAll` and similar functions, `remove` and `removeSelf` remove the whole hook group.

## Replace Hook Callbacks

You can use the `replace` function of `Handle` to replace the registered callbacks without hooking again.

> The following example

```kotlin
val handle = method.hook {
    after {
        // ...
    }
}
// Replace with new callbacks.
handle.replace {
    before {
        // ...
    }
}
```

::: warning

Invocations already in progress continue with the previous callbacks.

:::

::: danger

This feature requires the [yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) base module.

:::

## Deoptimize

ART may inline some short methods into the methods that call them, and hooks on the inlined methods then do not take effect.

You can use `deoptimize` on the **caller** so that it no longer inlines the callee.

> The following example

```kotlin
// Suppose this is the calling method.
val callingMethod: Method
// Deoptimize it.
callingMethod.deoptimize()
```

::: warning

The method to deoptimize is the one containing the inlined call, not the hooked method.

:::

::: danger

This feature requires the [yukihook-runtime-libxposed](../yukihook-runtime-libxposed.md) base module.

:::

## Exception Handling

YukiHook redesigned exception handling. Exceptions in hook callbacks are not thrown to the host by default to keep the host process from crashing.

### Listen for Exceptions

You can handle exceptions that occur during hook callbacks.

> The following example

```kotlin
hook {
    // Your code here.
}.apply {
    // Handle exceptions in hook callbacks.
    onCallbackFailure { param, throwable -> }
    // Handle all exceptions in hook invocations.
    onInvocationFailure { throwable -> }
}
```

Without a listener, the exception is printed to the log. You can also use `ignoreCallbackFailure` and `ignoreInvocationFailure` to skip these logs.

When an exception occurs in the `intercept` callback of a classic hook, YukiHook calls the original method without hooks to keep the host running normally,
if the original method throws an exception itself, the exception is thrown to the caller.

### Throw Exceptions

If you want to throw an exception to the host directly in the hook callback, you can do it as follows.

> The following example

```kotlin
hook {
    after {
        RuntimeException("Exception Test").throwToHost()
    }
}
```

You can also throw the exception directly in the hook callback and mark it to be thrown to the host.

> The following example

```kotlin
hook {
    after {
        throw RuntimeException("Exception Test")
    }.throwToHostOnFailure()
}
```

Both ways make the host receive the exception and crash the host process.

Both ways also work in the `intercept` callback, after using `throwToHost`, the return value of the callback is ignored,
and after using `throwToHostOnFailure`, YukiHook no longer calls the original method when an exception occurs.

For how to throw exceptions in chain hooks, please refer to [Exceptions in Chain Hooks](#exceptions-in-chain-hooks) below.

::: warning

`throwToHostOnFailure` only applies to the callback it is set on, for example, when it is only set on `after`,
exceptions that occur in `before` are still not thrown to the host.

:::

### Exceptions in Chain Hooks

When an exception occurs in a chain hook callback, YukiHook continues the invocation as if this hook did not exist, and the remaining chain is never run twice.

- When the exception occurs before `proceed` is called, YukiHook continues the remaining chain.
- When the exception occurs after `proceed` is called, YukiHook returns the result of the last `proceed` or throws its exception.

When the callback returns a value that does not match the return type of the method, it is handled in the same way.

Exceptions thrown by `proceed` come from the remaining chain or the original method, they are thrown to the caller as they are and do not trigger
`onCallbackFailure` and `onInvocationFailure`, if you need to handle these exceptions, please handle them in the callback yourself.

> The following example

```kotlin
intercept {
    try {
        proceed()
    } catch (e: IllegalStateException) {
        // Handle the exception and return another result.
        "fallback"
    }
}
```

If you want to throw exceptions in the callback to the caller directly, you can do the following.

> The following example

```kotlin
intercept {
    throw RuntimeException("Exception Test")
}.throwToHostOnFailure()
```

::: warning

After setting `throwToHostOnFailure`, because the remaining chain may already have run, YukiHook does not retry it.

:::