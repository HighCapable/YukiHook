# Yuki Hook

[![GitHub license](https://img.shields.io/github/license/HighCapable/YukiHook?color=blue&style=flat-square)](https://github.com/HighCapable/YukiHook/blob/1.x/LICENSE)
[![Telegram](https://img.shields.io/badge/discussion-Telegram-blue.svg?logo=telegram&style=flat-square)](https://t.me/YukiHook)
[![Telegram](https://img.shields.io/badge/discussion%20dev-Telegram-blue.svg?logo=telegram&style=flat-square)](https://t.me/HighCapable_Dev)
[![Telegram](https://img.shields.io/badge/channel-Telegram-blue.svg?logo=telegram&style=flat-square)](https://t.me/YukiHook_Channel)
[![QQ](https://img.shields.io/badge/discussion%20dev-QQ-blue.svg?logo=tencent-qq&logoColor=red&style=flat-square)](https://qm.qq.com/cgi-bin/qm/qr?k=Pnsc5RY6N2mBKFjOLPiYldbAbprAU3V7&jump_from=webapi&authKey=X5EsOVzLXt1dRunge8ryTxDRrh9/IiW1Pua75eDLh9RE3KXE+bwXIYF5cWri/9lf)

<img src="img-src/icon.svg" width = "100" height = "100" alt="LOGO"/>

一个现代化的 Hook API 与 Xposed 模块解决方案。

[English](README.md) | 简体中文

| <img src="https://github.com/HighCapable/.github/blob/main/img-src/logo.jpg?raw=true" width = "30" height = "30" alt="LOGO"/> | [HighCapable](https://github.com/HighCapable) |
|-------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|

这个项目属于上述组织，**点击上方链接关注这个组织**，发现更多好项目。

## 这是什么

这个项目诞生之初是为了有一套标准、规范的 Hook API 来取代 Android 上由 Rovo89 定义的但是已经不那么现代的 `XposedHelpers`。现在，它的目标是成为一套
Hook API，并为 Xposed 模块带来统一的开发体验，无论你正在使用的是什么底层 Hook 方案。

项目图标由 [MaiTungTM](https://github.com/Lagrio) 重绘，名字取自 [《ももくり》](https://momokuri-anime.jp) 动画中的女主 栗原 雪 (Yuki)
，前身为 [这个项目](https://github.com/fankes/TMore) 使用的 Innocent Xposed API。

**YukiHook** 已完成从 **YukiHookAPI** 改名并完成了现代化的底层重写，未来会作为 Android 通用的 Hook API 和 Xposed 模块方案继续演进和维护。

## 为什么是 YukiHook？

以前，我们在开发 Xposed 模块的时候，首先需要在 `assets` 下创建 `xposed_init` 文件，然后将自己的入口类名手动填入文件中，使用 `XposedHelpers` 去实现我们的
Hook
逻辑。自 Kotlin 作为 Android 主要开发语言以来，这套 API 用起来确实已经不是很现代了。

有没有什么 **好用、轻量、现代** 的解决方案呢？本着这样的想法，YukiHook 诞生了。现在，我们只需要实现同一套 Hook 代码，不同底座的入口文件与模块配置交给
YukiHook 来完成。

借助 Kotlin 现代的 DSL 写法以及 YukiHook 自身提供的语法糖，可以让你的 Hook 逻辑更加美观清晰。

> 示例如下

```kotlin
object HookEntry : YukiHookXposedModule {

    override fun onHook() = encase {
        loadApp("io.github.myapp") {
            // 使用 Java 原生反射获取
            val onCreate = Activity::class.java.getDeclaredMethod(
                "onCreate", Bundle::class.java
            )
            // 使用 KavaRef 获取
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

🚀 不喜欢原生的 Java 反射 API？YukiHook 现正使用 KavaRef 作为现代的反射 API，**点击上方链接立即前往开始使用**。

## 开始使用

| <img src="img-src/icon.svg" width = "30" height = "30" alt="LOGO"/> | [YukiHook 文档](https://highcapable.github.io/YukiHook/zh-cn) |
|---------------------------------------------------------------------|-------------------------------------------------------------|

你可以前往文档页面查看更多详细教程和内容。

### 下一步做什么？

1. **引入依赖**: 将 `yukihook-core`、`yukihook-compiler` 和一个底座模块添加到你的项目中。
2. **创建入口类**: 实现 `YukiHookXposedModule` 接口并加入与底座模块对应的入口注解。
3. **构建项目**: 模块的入口类与配置文件会在编译时自动生成，你就可以开始实现 Hook 代码了。

在打开的页面中，选择侧边栏的 **快速开始** 章节以继续阅读。

从其他 Hook API 迁移而来吗？请阅读 [迁移至 YukiHook](https://highcapable.github.io/YukiHook/zh-cn/config/migration)。

### Agent Skills

使用 Claude Code、Codex 等智能体辅助开发时，可安装 [YukiHook 使用技能](skills/yukihook/SKILL.md)。

> 示例如下

```bash
npx skills add HighCapable/YukiHook --skill yukihook
```

## 探索新版本

YukiHook `2.x` 版本正在 `2.x` 分支中重构，你可以点击 [这里](https://github.com/HighCapable/YukiHook/tree/2.x) 关注它的开发进度。

## 谁在使用

你可以前往 [这里](https://highcapable.github.io/YukiHook/zh-cn/about/adopters) 来查看目前正在使用 YukiHook 的项目。

## 更多项目

<!--suppress HtmlDeprecatedAttribute -->
<div align="center">
    <h2>嘿，还请君留步！👋</h2>
    <h3>如果你觉得这个项目能给你提供帮助，不妨继续往下看看我的更多项目吧！</h3>
    <h3>如果这些项目能为你提供帮助，不妨为我点个关注或者 star ⭐️ 吧！</h3>
    <h1><a href="https://github.com/fankes/fankes/blob/main/project-promote/README-zh-CN.md">→ 查看更多关于我的项目，请点击这里 ←</a></h1>
</div>

## Star History

[![Star History Chart](https://api.star-history.com/chart?repos=HighCapable/YukiHook&type=date&legend=top-left&sealed_token=cH7W7hRbOUCtuZy08alMZDm3XT4rAGJk-gWnsAEiainLb9d0pvJTYIUCLnhkVyVHMbE0RweCCTM_gn0Mr2UBwjhmbisZCRRxaF_NprdiB44vhtI-5bjaug)](https://www.star-history.com/?repos=HighCapable%2FYukiHook&type=date&legend=top-left)

## 第三方开源使用声明

- [KavaRef](https://github.com/HighCapable/KavaRef)
- [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass)
- [Xposed Framework API](https://api.xposed.info)
- [libxposed API](https://github.com/libxposed)

## 许可证

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

版权所有 © 2019 HighCapable