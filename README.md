# Yuki Hook

[![GitHub license](https://img.shields.io/github/license/HighCapable/YukiHook?color=blue&style=flat-square)](https://github.com/HighCapable/YukiHook/blob/1.x/LICENSE)
[![Telegram](https://img.shields.io/badge/discussion-Telegram-blue.svg?logo=telegram&style=flat-square)](https://t.me/YukiHook)
[![Telegram](https://img.shields.io/badge/discussion%20dev-Telegram-blue.svg?logo=telegram&style=flat-square)](https://t.me/HighCapable_Dev)
[![Telegram](https://img.shields.io/badge/channel-Telegram-blue.svg?logo=telegram&style=flat-square)](https://t.me/YukiHook_Channel)
[![QQ](https://img.shields.io/badge/discussion%20dev-QQ-blue.svg?logo=tencent-qq&logoColor=red&style=flat-square)](https://qm.qq.com/cgi-bin/qm/qr?k=Pnsc5RY6N2mBKFjOLPiYldbAbprAU3V7&jump_from=webapi&authKey=X5EsOVzLXt1dRunge8ryTxDRrh9/IiW1Pua75eDLh9RE3KXE+bwXIYF5cWri/9lf)

<img src="img-src/icon.svg" width = "100" height = "100" alt="LOGO"/>

A modern hook API and Xposed module solution.

English | [简体中文](README-zh-CN.md)

| <img src="https://github.com/HighCapable/.github/blob/main/img-src/logo.jpg?raw=true" width = "30" height = "30" alt="LOGO"/> | [HighCapable](https://github.com/HighCapable) |
|-------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|

This project belongs to the organization above. **Click the link to follow us** and discover more awesome projects.

## What's this

This project was originally created to provide a standard and well-defined hook API to replace `XposedHelpers`, which was defined by Rovo89 on Android
and is no longer very modern.
Now, it aims to be a hook API and to bring a unified development experience to Xposed modules, no matter which underlying hook solution you are using.

The project icon is redrawn by [MaiTungTM](https://github.com/Lagrio) and the name is taken from Yuki Kurihara, the heroine of the
anime [「ももくり」](https://momokuri-anime.jp), and it was formerly the Innocent Xposed API
used in [this project](https://github.com/fankes/TMore).

**YukiHook** has been renamed from **YukiHookAPI** with a modern rewrite of its underlying implementation, and it will continue to evolve and be
maintained as a general hook API and Xposed module solution for Android.

## Why YukiHook?

Previously, when developing an Xposed module, we first had to create an `xposed_init` file under `assets`, then fill in our entry class name manually
and use `XposedHelpers` to implement our hook logic.
Since Kotlin became the main language for Android development, this API is no longer very modern to use.

Is there a **useful, lightweight and modern** solution? With this idea in mind, YukiHook was born. Now, we only need to write one set of hook code,
and YukiHook takes care of the entry files and module configuration for different base modules.

With Kotlin's modern DSL syntax and the syntactic sugar provided by YukiHook, your hook logic can be cleaner and clearer.

> The following example

```kotlin
object HookEntry : YukiHookXposedModule {

    override fun onHook() = encase {
        loadApp("io.github.myapp") {
            // Use Java native reflection to get.
            val onCreate = Activity::class.java.getDeclaredMethod(
                "onCreate", Bundle::class.java
            )
            // Use KavaRef to get.
            val onCreate = Activity::class.resolve().firstMethod {
                name = "onCreate"
                parameters(Bundle::class)
            }

            onCreate.hook {
                before {
                    // Your code here.
                }
                after {
                    // Your code here.
                }
            }
        }
    }
}
```

| <img src="https://github.com/HighCapable/KavaRef/blob/main/img-src/icon.svg?raw=true" width = "30" height = "30" alt="LOGO"/> | [KavaRef](https://github.com/HighCapable/KavaRef) |
|-------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|

🚀 Not a fan of the native Java reflection API? YukiHook now uses KavaRef as a modern reflection API. **Click the link above to get started**.

## Get Started

| <img src="img-src/icon.svg" width = "30" height = "30" alt="LOGO"/> | [YukiHook Documentation](https://highcapable.github.io/YukiHook/en) |
|---------------------------------------------------------------------|---------------------------------------------------------------------|

You can go to the documentation page for more detailed tutorials and content.

### What's next?

1. **Add dependencies**: Add `yukihook-core`, `yukihook-compiler` and one base module to your project.
2. **Create the entry class**: Implement `YukiHookXposedModule` and add the entry annotation that matches your base module.
3. **Build the project**: The entry class and configuration files of the module are generated at compile time, and you can start hooking.

In the opened page, select the **Quick Start** section in the sidebar to continue reading.

Migrating from other Hook APIs? Please read [Migration to YukiHook](https://highcapable.github.io/YukiHook/en/config/migration).

### Agent Skills

For AI-assisted development with Claude Code, Codex, and other agents, install the [YukiHook skill](skills/yukihook/SKILL.md).

> The following example

```bash
npx skills add HighCapable/YukiHook --skill yukihook
```

## Exploring New Version

YukiHook `2.x` is being rebuilt on the `2.x` branch, you can click [here](https://github.com/HighCapable/YukiHook/tree/2.x) to follow its development
progress.

## Adopters

You can go to [here](https://highcapable.github.io/YukiHook/en/about/adopters) to see the projects that are currently using YukiHook.

## More Projects

<!--suppress HtmlDeprecatedAttribute -->
<div align="center">
    <h2>Hey, wait a second! 👋</h2>
    <h3>If this project was helpful, why not stick around and check out more of my work below?</h3>
    <h3>Feel free to leave a follow or a star ⭐️ if they bring you value!</h3>
    <h1><a href="https://github.com/fankes/fankes/blob/main/project-promote/README.md">→ Click here to discover more of my projects ←</a></h1>
</div>

## Star History

[![Star History Chart](https://api.star-history.com/chart?repos=HighCapable/YukiHook&type=date&legend=top-left&sealed_token=cH7W7hRbOUCtuZy08alMZDm3XT4rAGJk-gWnsAEiainLb9d0pvJTYIUCLnhkVyVHMbE0RweCCTM_gn0Mr2UBwjhmbisZCRRxaF_NprdiB44vhtI-5bjaug)](https://www.star-history.com/?repos=HighCapable%2FYukiHook&type=date&legend=top-left)

## Third-Party Open Source Usage Statement

- [KavaRef](https://github.com/HighCapable/KavaRef)
- [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)
- [Xposed Framework API](https://api.xposed.info)
- [libxposed API](https://github.com/libxposed)

## License

- [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)

```
Apache License Version 2.0

Copyright (C) 2019 HighCapable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Copyright © 2019 HighCapable