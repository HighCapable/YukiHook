---
name: yukihook
description: Use YukiHook to build Android Xposed modules in Kotlin. Find documentation for integrating YukiHook into a module project, choosing a base module, writing hooks, using module features, and migrating from other frameworks or earlier YukiHook versions. This skill covers using YukiHook, not developing the YukiHook library itself.
---

# Using YukiHook

Use this skill when building modules with YukiHook. The [documentation website](https://highcapable.github.io/YukiHook/en) is available for browsing.

Read the English Markdown sources in `docs-source/src/en/` on the `1.x` branch of `HighCapable/YukiHook`.
The links below serve the raw `.md` files directly; fetch the relevant chapters without converting HTML.
Resolve relative documentation links against the source file's directory on the same branch.

## Setup

- [Quick Start: requirements, dependencies, and choosing a base module](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/guide/quick-start.md)
- [BOM and dependency versions](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-bom.md)
- [Core library](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core.md)
- [Compiler and generated configuration](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-compiler.md)
- [R8 and ProGuard](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/config/r8-proguard.md)

## Base Modules

- [libxposed](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-runtime-libxposed.md)
- [Xposed 82](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-runtime-xposed82.md)

## Hook Development

- [Hook entries](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/hook-entry.md)
- [Multiple entry variants](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/hook-entry/multi-entry-variants.md)
- [Hook DSL and usage](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/hook-usage.md)
- [KavaRef extension](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-extension-kavaref.md)

## Module Features

- [Host lifecycle](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/host-lifecycle.md)
- [Module status](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/module-status.md)
- [Preferences](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/preferences.md)
- [Shared files](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/shared-files.md)
- [Data channels](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/data-channel.md)
- [Resource injection](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/resource-injection.md)
- [Logging](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/logger.md)
- [Hot reload](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/library/yukihook-core/hot-reload.md)

## Migration from Other Frameworks

For projects moving from another Hook framework to YukiHook, read
the [migration overview](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/config/migration.md)
and the guide for the source framework:

- [Xposed Framework API](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/config/migration/xposed-framework-api.md)
- [libxposed API](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/config/migration/libxposed-api.md)
- [EzXHelper](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/config/migration/ezxhelper.md)

## Migration from Earlier YukiHook Versions

For projects upgrading from YukiHook 1.3.x to 1.5.x,
read [Migration to 1.5.x](https://raw.githubusercontent.com/HighCapable/YukiHook/1.x/docs-source/src/en/config/migration-1-5-x.md).