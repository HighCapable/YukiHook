/*
 * YukiHook - A modern hook API and Xposed module solution.
 * Copyright (C) 2019 HighCapable
 * https://github.com/HighCapable/YukiHook
 *
 * Apache License Version 2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is created by fankes on 2022/2/5.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.compiler.subprocessor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.highcapable.yukihookapi.compiler.DeclaredSymbol
import com.highcapable.yukihookapi.compiler.subprocessor.base.BaseSymbolProcessor
import com.highcapable.yukihookapi.compiler.subprocessor.spec.GenerateSpec
import com.highcapable.yukihookapi.compiler.subprocessor.spec.LibXposedGenerateSpec
import com.highcapable.yukihookapi.compiler.subprocessor.spec.Xposed82GenerateSpec
import com.highcapable.yukihookapi.generated.YukiHookProperties
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.File

class YukiHookXposedGenerator(environment: SymbolProcessorEnvironment) : BaseSymbolProcessor(environment) {

    private companion object {

        const val TAG = YukiHookProperties.PROJECT_NAME
        const val VERSION = YukiHookProperties.PROJECT_YUKIHOOK_BOM_VERSION
        const val XPOSED_CLASS_SHORT_NAME = "_YukiHookXposedInit"

        val MODULE_PACKAGE_NAME_REGEX = "[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+".toRegex()

        const val IS_ACTIVE_METHOD_NAME = "__--"
        const val GET_EXECUTOR_API_VERSION_METHOD_NAME = "-__-"
        const val GET_EXECUTOR_NAME_METHOD_NAME = "_-_-"
        const val GET_EXECUTOR_VERSION_METHOD_NAME = "-_-_"
        const val GET_EXECUTOR_VERSION_CODE_METHOD_NAME = "___-"

        val ApiImplClass = ClassName(DeclaredSymbol.API_PACKAGE_NAME, DeclaredSymbol.API_IMPL_CLASS_NAME)
        val CompiledTimestampClass = ClassName(DeclaredSymbol.API_PACKAGE_NAME, DeclaredSymbol.COMPILED_TIMESTAMP_CLASS_NAME)
        val ModuleApplicationImplClass = ClassName(DeclaredSymbol.MODULE_APPLICATION_PACKAGE_NAME, DeclaredSymbol.MODULE_APPLICATION_IMPL_CLASS_NAME)
        val LegacyModuleStatusImplClass = ClassName(DeclaredSymbol.MODULE_STATUS_PACKAGE_NAME, DeclaredSymbol.LEGACY_MODULE_STATUS_IMPL_CLASS_NAME)

        val HookEntryClass = ClassName(DeclaredSymbol.HOOK_ENTRY_PACKAGE_NAME, DeclaredSymbol.HOOK_ENTRY_CLASS_NAME)
        val ModuleCallerClass = ClassName(DeclaredSymbol.MODULE_CALLER_PACKAGE_NAME, DeclaredSymbol.MODULE_CALLER_CLASS_NAME)
        val Xposed82EntryClass = ClassName.bestGuess(Xposed82GenerateSpec.ENTRY_CLASS_NAME)
        val ReloadPolicyClass = ClassName.bestGuess(LibXposedGenerateSpec.RELOAD_POLICY_CLASS_NAME)

        val KeepClass = ClassName(DeclaredSymbol.ANDROIDX_ANNOTATION_PACKAGE_NAME, DeclaredSymbol.KEEP_ANNOTATION_CLASS_NAME)
        val LogClass = ClassName(DeclaredSymbol.ANDROID_UTIL_PACKAGE_NAME, DeclaredSymbol.LOG_CLASS_NAME)
    }

    private var isGenerated = false

    override fun startProcess(resolver: Resolver) {
        if (isGenerated) return

        val deprecatedLegacySymbols = resolver.getSymbolsWithAnnotation(Xposed82GenerateSpec.LEGACY_ANNOTATION_NAME).toList()
        val legacySymbols = resolver.getSymbolsWithAnnotation(Xposed82GenerateSpec.ANNOTATION_NAME).toList() + deprecatedLegacySymbols
        val modernSymbols = resolver.getSymbolsWithAnnotation(LibXposedGenerateSpec.ANNOTATION_NAME).toList()

        if (legacySymbols.isEmpty() && modernSymbols.isEmpty()) return
        if (legacySymbols.size + modernSymbols.size != 1) problem(
            "Declare exactly one hook entry with either " +
                "`@${LibXposedGenerateSpec.ANNOTATION_NAME.substringAfterLast('.')}` or " +
                "`@${Xposed82GenerateSpec.ANNOTATION_NAME.substringAfterLast('.')}` and remove the others."
        )

        val hasLibXposedRuntime = resolver.getClassDeclarationByName(resolver.getKSNameFromString(LibXposedGenerateSpec.BASE_ENTRY_CLASS_NAME)) != null
        val hasXposed82Runtime = resolver.getClassDeclarationByName(resolver.getKSNameFromString(Xposed82GenerateSpec.ENTRY_CLASS_NAME)) != null

        if (hasLibXposedRuntime == hasXposed82Runtime) problem(
            "Select exactly one runtime: `${LibXposedGenerateSpec.RUNTIME_MODULE_NAME}` " +
                "or `${Xposed82GenerateSpec.RUNTIME_MODULE_NAME}`."
        )

        val runtimeModuleName = if (hasLibXposedRuntime)
            LibXposedGenerateSpec.RUNTIME_MODULE_NAME
        else Xposed82GenerateSpec.RUNTIME_MODULE_NAME
        val annotationRuntimeModuleName = if (modernSymbols.isNotEmpty())
            LibXposedGenerateSpec.RUNTIME_MODULE_NAME
        else Xposed82GenerateSpec.RUNTIME_MODULE_NAME
        val annotationName = when {
            modernSymbols.isNotEmpty() -> LibXposedGenerateSpec.ANNOTATION_NAME
            deprecatedLegacySymbols.isNotEmpty() -> Xposed82GenerateSpec.LEGACY_ANNOTATION_NAME
            else -> Xposed82GenerateSpec.ANNOTATION_NAME
        }

        if (annotationRuntimeModuleName != runtimeModuleName) problem(
            "`@${annotationName.substringAfterLast('.')}` requires $annotationRuntimeModuleName, " +
                "but $runtimeModuleName is on the compile classpath."
        )

        if (annotationName == Xposed82GenerateSpec.LEGACY_ANNOTATION_NAME) warn(
            "`@${annotationName.substringAfterLast('.')}` will be removed in a future release. " +
                "Migrate it to `@${Xposed82GenerateSpec.ANNOTATION_NAME.substringAfterLast('.')}`"
        )

        val entry = (legacySymbols + modernSymbols).single() as? KSClassDeclaration ?: problem(
            "The hook entry annotation must target a class or object implementing `YukiHookXposedModule`."
        )

        val entrySuperTypes = entry.getAllSuperTypes().mapNotNull { it.declaration.qualifiedName?.asString() }.toSet()
        val usesLegacyEntry = DeclaredSymbol.LEGACY_HOOK_ENTRY_CLASS in entrySuperTypes
        if (usesLegacyEntry && hasLibXposedRuntime) problem(
            "`IYukiHookXposedInit` requires ${Xposed82GenerateSpec.RUNTIME_MODULE_NAME}. " +
                "Use `YukiHookXposedModule` for ${LibXposedGenerateSpec.RUNTIME_MODULE_NAME}"
        )
        if (usesLegacyEntry) warn(
            "The hook entry class \"${entry.simpleName.asString()}\" still implements the deprecated " +
                "`IYukiHookXposedInit`, which will be removed in a future release. Migrate it to `YukiHookXposedModule`"
        )
        if (DeclaredSymbol.HOOK_ENTRY_CLASS !in entrySuperTypes) problem(
            "The hook entry class \"${entry.simpleName.asString()}\" must implement `YukiHookXposedModule`."
        )

        val annotation = entry.annotations.single {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName
        }
        val arguments = annotation.arguments.associate { it.name?.asString() to it.value }
        val spec = if (hasLibXposedRuntime) LibXposedGenerateSpec(
            entryClassName = entry.toClassName(),
            minApiVersion = arguments["minApiVersion"] as? Int ?: LibXposedGenerateSpec.RUNTIME_MIN_API_VERSION,
            targetApiVersion = arguments["targetApiVersion"] as? Int ?: problem("The libxposed entry must declare targetApiVersion."),
            scope = (arguments["scope"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            staticScope = arguments["staticScope"] as? Boolean ?: false,
            hotReload = resolveHotReload(arguments["hotReload"]),
            nativeEntries = (arguments["nativeEntries"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        ) else Xposed82GenerateSpec(
            entryClassName = entry.toClassName(),
            modulePackageName = (arguments["modulePackageName"] as? String).orEmpty().trim(),
            generateModuleStatus = arguments["generateModuleStatus"] as? Boolean ?: arguments["isUsingXposedModuleStatus"] as? Boolean ?: true
        )
        val entrySource = entry.containingFile ?: problem("The hook entry must be declared in a source file.")

        spec.initClassName = (arguments["entryClassName"] as? String).orEmpty().trim()
        if (spec is Xposed82GenerateSpec && spec.modulePackageName.isNotEmpty() &&
            !MODULE_PACKAGE_NAME_REGEX.matches(spec.modulePackageName)
        ) problem("Invalid module package name \"${spec.modulePackageName}\"")

        if (spec.initClassName.isNotEmpty() &&
            ("[^\\p{L}\\p{N}_]".toRegex().containsMatchIn(spec.initClassName) || spec.initClassName.first().isDigit())
        ) problem("Invalid hook entry class name \"${spec.initClassName}\"")

        if ((entry.primaryConstructor?.parameters?.size ?: 0) > 0)
            problem("The hook entry class \"${entry.simpleName.asString()}\" must not have constructor parameters.")
        if (spec.initClassName == entry.simpleName.asString())
            problem("Duplicate hook entry class name \"${spec.initClassName}\"")

        spec.entryPackageName = entry.packageName.asString()
        spec.initClassName = spec.initClassName.ifBlank { "${spec.entryClassName.simpleName}$XPOSED_CLASS_SHORT_NAME" }
        spec.isObjectEntry = when (entry.classKind) {
            ClassKind.CLASS -> false
            ClassKind.OBJECT -> true
            else -> problem("Unsupported class kind `${entry.classKind}` for hook entry class \"${spec.entryClassName.simpleName}\"")
        }
        if (spec is LibXposedGenerateSpec) spec.javaEntries = resolveLibXposedJavaEntries(arguments["javaEntries"], spec)

        generateResources(
            codePath = (entry.location as? FileLocation)?.filePath ?: "",
            spec = spec,
            resolver = resolver,
            entrySource = entrySource
        )
    }

    private fun resolveHotReload(value: Any?) = when (value) {
        null -> LibXposedGenerateSpec.HotReload.NONE
        is KSType -> value.declaration.simpleName.asString()
        is KSClassDeclaration -> value.simpleName.asString()
        else -> value.toString().substringAfterLast('.')
    }.let { name ->
        LibXposedGenerateSpec.HotReload.entries.firstOrNull { it.name == name }
            ?: problem("Unknown hotReload policy \"$name\"")
    }

    private fun resolveLibXposedJavaEntries(value: Any?, spec: LibXposedGenerateSpec): List<String> {
        val entries = when (value) {
            null -> emptyList()
            is List<*> -> value
            is Array<*> -> value.toList()
            else -> problem("Invalid `javaEntries` annotation argument.")
        }

        val generatedEntryName = "${spec.entryPackageName}.${spec.initClassName}"
        val names = entries.map { value ->
            val declaration = (value as? KSType)?.declaration as? KSClassDeclaration
                ?: problem("Each item in `javaEntries` must reference a libxposed API entry class.")
            val name = declaration.toClassName().reflectionName()
            if (name == generatedEntryName) problem(
                "Do not include the generated entry class \"$generatedEntryName\" in `javaEntries`."
            )

            val superTypes = declaration.getAllSuperTypes()
                .mapNotNull { it.declaration.qualifiedName?.asString() }
                .toSet()

            if (name == LibXposedGenerateSpec.BASE_ENTRY_CLASS_NAME || LibXposedGenerateSpec.BASE_ENTRY_CLASS_NAME in superTypes)
                problem("Do not include the YukiHook internal entry class \"$name\" in `javaEntries`.")
            if (LibXposedGenerateSpec.XPOSED_MODULE_CLASS_NAME !in superTypes)
                problem("\"$name\" must extend libxposed API `XposedModule`.")

            name
        }

        if (names.size != names.toSet().size) problem("`javaEntries` contains duplicate libxposed API entry classes.")
        return names
    }

    /**
     * Generates the framework entry and metadata resources.
     * @param codePath the full source path of the annotated class.
     * @param spec the template generation data.
     * @param resolver the current KSP resolver.
     * @param entrySource the annotated entry source file.
     */
    private fun generateResources(codePath: String, spec: GenerateSpec, resolver: Resolver, entrySource: KSFile) {
        val projectDir = findProjectDir(codePath)
        checkSourceEntryFiles(projectDir)

        if (spec is LibXposedGenerateSpec &&
            (spec.minApiVersion < LibXposedGenerateSpec.RUNTIME_MIN_API_VERSION || spec.targetApiVersion < spec.minApiVersion)
        ) problem(
            "`YukiHookLibXposedEntry` requires minApiVersion >= ${LibXposedGenerateSpec.RUNTIME_MIN_API_VERSION} " +
                "and targetApiVersion >= minApiVersion. API 100 is unsupported."
        )

        val modulePackageSource = if (spec is Xposed82GenerateSpec) {
            if (spec.modulePackageName.isNotBlank()) {
                warn(
                    "The module package name is explicitly set to \"${spec.modulePackageName}\". " +
                        "Please verify that it matches the application ID"
                )
                null
            } else parseModulePackageName(resolver).let { (packageName, source) ->
                spec.modulePackageName = packageName
                source
            }
        } else null
        if (spec is LibXposedGenerateSpec) {
            if (spec.hotReload != LibXposedGenerateSpec.HotReload.NONE) {
                if (spec.targetApiVersion < LibXposedGenerateSpec.HOT_RELOAD_API_VERSION)
                    problem("Hot reload requires targetApiVersion >= ${LibXposedGenerateSpec.HOT_RELOAD_API_VERSION}")
                if (spec.javaEntries.isNotEmpty()) problem("Hot reload requires exactly one Java entry. Remove additional javaEntries.")
            }

            if (spec.scope.any { it.isBlank() || it.any(Char::isWhitespace) })
                problem("Each `scope` entry must be a package name or `system`, without whitespace.")
            if (spec.nativeEntries.any {
                    it.isBlank() || it.any(Char::isWhitespace) || '/' in it || '\\' in it || !it.endsWith(".so")
                }) problem(
                "Each nativeEntries entry must be a .so filename without paths or whitespace, for example libexample.so"
            )
        }

        val entryDependencies = Dependencies(aggregating = false, entrySource)
        val modulePackageDependencies = modulePackageSource?.let {
            Dependencies(aggregating = false, entrySource, it)
        } ?: entryDependencies

        spec.createResources().forEach { (path, content) ->
            codeGenerator.createNewFileByPath(entryDependencies, path, extensionName = "")
                .bufferedWriter().use { it.write(content) }
        }

        generateCodeFiles(spec, entryDependencies, modulePackageDependencies)
        isGenerated = true
    }

    /**
     * Generates the required class files.
     * @param spec the template generation spec.
     * @param entryDependencies dependencies originating from the annotated entry.
     * @param modulePackageDependencies dependencies that also originate from `BuildConfig.java` when available.
     */
    private fun generateCodeFiles(spec: GenerateSpec, entryDependencies: Dependencies, modulePackageDependencies: Dependencies) {
        spec.createSources(entryDependencies, modulePackageDependencies).forEach { (source, dependencies) ->
            source.writeTo(codeGenerator, dependencies)
        }
    }

    private fun Xposed82GenerateSpec.withModuleSuffix(type: ClassName) =
        ClassName(type.packageName, type.simpleName + "_" + modulePackageName.replace(".", "_"))

    private fun GenerateSpec.createEntryInstance() = CodeBlock.of(
        if (isObjectEntry) "%T" else "%T()",
        entryClassName
    )

    private fun FileSpec.Builder.addCopyrightFileComment() = addFileComment(
        """
          ${YukiHookProperties.PROJECT_NAME} - ${YukiHookProperties.POM_DESCRIPTION}
          Copyright (C) 2019 HighCapable
          ${YukiHookProperties.PROJECT_URL}

          This file is auto generated by ${YukiHookProperties.PROJECT_NAME}.
          **DO NOT EDIT THIS FILE MANUALLY**
        """.trimIndent()
    )

    private fun ClassName.sourceFileBuilder(vararg suppressions: String) = FileSpec.builder(packageName, simpleName).apply {
        addCopyrightFileComment()
        indent("    ")
        addAnnotation(AnnotationSpec.builder(Suppress::class).apply {
            addMember("%S", "ClassName")
            suppressions.forEach { addMember("%S", it) }
        }.build())
    }

    /**
     * Creates the Kotlin source files required by the selected runtime.
     * @return generated source files and their dependencies.
     */
    private fun GenerateSpec.createSources(entryDependencies: Dependencies, modulePackageDependencies: Dependencies) = buildList {
        val runtimeDependencies = if (this@createSources is Xposed82GenerateSpec) modulePackageDependencies else entryDependencies

        add(createApiSource() to runtimeDependencies)
        add(createCompiledTimestampSource() to Dependencies.ALL_FILES)
        add(createApplicationSource() to entryDependencies)
        if (this@createSources is Xposed82GenerateSpec && generateModuleStatus)
            add(createLegacyModuleStatusSource() to modulePackageDependencies)
        add(createXposedEntrySource() to runtimeDependencies)
    }

    /**
     * Creates the entry registration and framework metadata resources for the selected runtime.
     * @return [Map]<[String], [String]>
     */
    private fun GenerateSpec.createResources(): Map<String, String> {
        val entryName = "$entryPackageName.$initClassName"

        return when (this) {
            is LibXposedGenerateSpec -> buildMap {
                put(LibXposedGenerateSpec.JAVA_INIT_FILE_PATH, (listOf(entryName) + javaEntries).joinToString("\n"))
                put(LibXposedGenerateSpec.MODULE_PROPERTIES_FILE_PATH, createModuleProperties())
                put(LibXposedGenerateSpec.SCOPE_FILE_PATH, scope.joinToString("\n"))

                if (nativeEntries.isNotEmpty()) put(LibXposedGenerateSpec.NATIVE_INIT_FILE_PATH, nativeEntries.joinToString("\n"))
            }
            is Xposed82GenerateSpec -> mapOf(Xposed82GenerateSpec.INIT_FILE_PATH to entryName)
        }
    }

    /**
     * Returns modern framework metadata.
     * @return [String]
     */
    private fun LibXposedGenerateSpec.createModuleProperties() =
        """
          # Generated by YukiHook-$VERSION
          minApiVersion=$minApiVersion
          targetApiVersion=$targetApiVersion
          staticScope=$staticScope
          autoHotReload=${hotReload == LibXposedGenerateSpec.HotReload.AUTO}
        """.trimIndent()

    private fun GenerateSpec.createApiSource() = ApiImplClass.sourceFileBuilder().apply {
        addType(TypeSpec.objectBuilder(ApiImplClass).apply {
            addKdoc("%L Class\n", ApiImplClass.simpleName)
            addProperty(PropertySpec.builder("legacyModuleStatusClassName", STRING).apply {
                addKdoc("Name of the generated legacy module-status target, or empty when unavailable.\n")
                getter(FunSpec.getterBuilder().apply {
                    addStatement(
                        "return %S",
                        if (this@createApiSource is Xposed82GenerateSpec && generateModuleStatus)
                            withModuleSuffix(LegacyModuleStatusImplClass).canonicalName else ""
                    )
                }.build())
            }.build())
            addProperty(PropertySpec.builder("compiledTimestamp", LONG).apply {
                addKdoc("Timestamp of this generated module build.\n")
                getter(FunSpec.getterBuilder().apply {
                    addStatement("return %T.get()", CompiledTimestampClass)
                }.build())
            }.build())
        }.build())
    }.build()

    private fun createCompiledTimestampSource() = CompiledTimestampClass.sourceFileBuilder().apply {
        addType(TypeSpec.objectBuilder(CompiledTimestampClass).apply {
            addKdoc("%L Class\n", CompiledTimestampClass.simpleName)
            addFunction(FunSpec.builder("get").apply {
                addKdoc("Timestamp of this generated module build.\n")
                returns(LONG)
                addStatement("return %L", System.currentTimeMillis())
            }.build())
        }.build())
    }.build()

    private fun GenerateSpec.createApplicationSource() = ModuleApplicationImplClass.sourceFileBuilder().apply {
        addType(TypeSpec.objectBuilder(ModuleApplicationImplClass).apply {
            addKdoc("%L Class\n", ModuleApplicationImplClass.simpleName)
            addFunction(FunSpec.builder("callHookEntryInit").apply {
                addKdoc("Initializes the hook entry in the module application.\n")
                addStatement("%T.callOnInit(%L)", ModuleCallerClass, createEntryInstance())
            }.build())
        }.build())
    }.build()

    private fun Xposed82GenerateSpec.createLegacyModuleStatusSource() = LegacyModuleStatusImplClass.sourceFileBuilder().apply {
        fun funSpec(name: String, jvmName: String, resultType: TypeName, defaultValue: CodeBlock) =
            FunSpec.builder(name).apply {
                addKdoc("Returns the fallback value until the legacy framework hooks this function.\n")
                addAnnotation(JvmStatic::class)
                addAnnotation(AnnotationSpec.builder(JvmName::class).apply {
                    addMember("%S", jvmName)
                }.build())
                returns(resultType)
                addStatement("preventOptimization()")
                addStatement("return %L", defaultValue)
            }.build()

        addType(TypeSpec.objectBuilder(withModuleSuffix(LegacyModuleStatusImplClass)).apply {
            addKdoc("%L Class\n", LegacyModuleStatusImplClass.simpleName)
            addAnnotation(KeepClass)
            addFunction(funSpec("isActive", IS_ACTIVE_METHOD_NAME, BOOLEAN, CodeBlock.of("false")))
            addFunction(funSpec("getExecutorApiVersion", GET_EXECUTOR_API_VERSION_METHOD_NAME, INT, CodeBlock.of("-1")))
            addFunction(funSpec("getExecutorName", GET_EXECUTOR_NAME_METHOD_NAME, STRING, CodeBlock.of("%S", "unknown")))
            addFunction(funSpec("getExecutorVersion", GET_EXECUTOR_VERSION_METHOD_NAME, STRING, CodeBlock.of("%S", "unknown")))
            addFunction(funSpec("getExecutorVersionCode", GET_EXECUTOR_VERSION_CODE_METHOD_NAME, LONG, CodeBlock.of("-1L")))
            addFunction(FunSpec.builder("preventOptimization").apply {
                addModifiers(KModifier.PRIVATE)
                addAnnotation(JvmStatic::class)
                addComment("Keeps the hook targets from becoming short method bodies.")
                addStatement(
                    "if (%T.currentTimeMillis() == 0L) %T.d(%S, %S)",
                    System::class, LogClass, TAG,
                    "Generated legacy module status"
                )
            }.build())
        }.build())
    }.build()

    private fun GenerateSpec.createXposedEntrySource() = FileSpec.builder(entryPackageName, initClassName).apply {
        addCopyrightFileComment()
        indent("    ")
        addType(TypeSpec.classBuilder(initClassName).apply {
            addKdoc("Framework entry generated by `YukiHook`.\n")
            addAnnotation(KeepClass)

            superclass(when (this@createXposedEntrySource) {
                is Xposed82GenerateSpec -> Xposed82EntryClass
                is LibXposedGenerateSpec -> ClassName.bestGuess(runtimeEntryClassName)
            })

            if (this@createXposedEntrySource is Xposed82GenerateSpec)
                addProperty(PropertySpec.builder("modulePackageName", STRING).apply {
                    addModifiers(KModifier.OVERRIDE)
                    initializer("%S", modulePackageName)
                }.build())

            if (this@createXposedEntrySource is LibXposedGenerateSpec &&
                targetApiVersion >= LibXposedGenerateSpec.HOT_RELOAD_API_VERSION
            ) addProperty(PropertySpec.builder("hotReload", ReloadPolicyClass).apply {
                addModifiers(KModifier.OVERRIDE)
                addKdoc("Reload policy declared by the module.\n")
                initializer("%T.%L", ReloadPolicyClass, hotReload.name)
            }.build())

            addFunction(FunSpec.builder("createHookEntry").apply {
                addModifiers(KModifier.OVERRIDE)
                returns(HookEntryClass)
                addStatement("return %L", createEntryInstance())
            }.build())
        }.build())
    }.build()

    /**
     * Finds the Gradle module containing the annotated source file.
     * @param codePath the full source path of the annotated class.
     * @return [File] the Gradle module directory.
     */
    private fun findProjectDir(codePath: String): File {
        if (codePath.isBlank()) problem("The project code path is unavailable.")
        return generateSequence(File(codePath).parentFile) { it.parentFile }
            .firstOrNull { it.resolve("build.gradle").isFile || it.resolve("build.gradle.kts").isFile }
            ?: problem("Cannot find a Gradle module containing \"$codePath\"")
    }

    /**
     * Rejects framework entry and metadata files manually declared in source directories.
     * @param projectDir the Gradle module directory.
     */
    private fun checkSourceEntryFiles(projectDir: File) {
        val sourceDirectories = projectDir.walkTopDown()
            .onEnter { it == projectDir || (it.name != "build" && it.name != ".gradle") }
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .mapNotNull { it.parentFile }
            .toList()
        if (sourceDirectories.isEmpty()) problem(
            "Cannot find an AndroidManifest.xml source file in Gradle module \"${projectDir.path}\""
        )

        val sourceFilePaths = listOf(
            Xposed82GenerateSpec.INIT_FILE_PATH,
            Xposed82GenerateSpec.LEGACY_ASSET_INIT_FILE_PATH
        )
        val resourceFilePaths = listOf(
            Xposed82GenerateSpec.LEGACY_RESOURCE_INIT_FILE_PATH,
            LibXposedGenerateSpec.JAVA_INIT_FILE_PATH,
            LibXposedGenerateSpec.MODULE_PROPERTIES_FILE_PATH,
            LibXposedGenerateSpec.SCOPE_FILE_PATH,
            LibXposedGenerateSpec.NATIVE_INIT_FILE_PATH
        )
        val sourceEntryFiles = sourceDirectories.flatMap { sourceDir ->
            sourceFilePaths.map(sourceDir::resolve) +
                resourceFilePaths.map(sourceDir.resolve("resources")::resolve)
        }.filter(File::isFile)

        if (sourceEntryFiles.isNotEmpty()) problem(
            "Xposed entry and metadata files must be generated exclusively by YukiHook.\n" +
                "Remove the following files from your source directory manually before building:\n" +
                sourceEntryFiles.joinToString("\n")
        )
    }

    /**
     * Resolves the module package name.
     * @param resolver the current KSP resolver.
     * @return [Pair] the module application ID and its `BuildConfig.java` source.
     */
    private fun parseModulePackageName(resolver: Resolver): Pair<String, KSFile> {
        val matcher = "APPLICATION_ID\\s*=\\s*\"([^\"]+)\"".toRegex()
        val candidates = resolver.getAllFiles().mapNotNull { source ->
            if (source.fileName != "BuildConfig.java") return@mapNotNull null
            val sourcePath = (source.location as? FileLocation)?.filePath ?: return@mapNotNull null
            val modulePackageName = runCatching {
                matcher.find(File(sourcePath).readText())?.groups?.get(1)?.value
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            modulePackageName to source
        }.toList()

        if (candidates.isEmpty()) problem(
            "Cannot identify the module application ID from the current KSP compilation. " +
                "Enable the BuildConfig feature or set `modulePackageName`."
        )
        if (candidates.size > 1) problem(
            "Multiple BuildConfig.java files with APPLICATION_ID are visible in the current KSP compilation. " +
                "Set `modulePackageName` explicitly."
        )
        return candidates.single()
    }

    /**
     * Reports a fatal error and aborts processing.
     * @param msg the error message.
     * @return [Nothing]
     */
    private fun problem(msg: String): Nothing {
        val helpMsg = """
          Looking for help? Please see the documentation link below.
          English: https://highcapable.github.io/YukiHook/en/library/yukihook-compiler
          简体中文: https://highcapable.github.io/YukiHook/zh-cn/library/yukihook-compiler
        """.trimIndent()

        logger.error(message = "[$TAG] $msg\n\n$helpMsg")
        throw RuntimeException("[$TAG] $msg\n\n$helpMsg")
    }

    /**
     * Reports a warning.
     * @param msg the warning message.
     */
    private fun warn(msg: String) = logger.warn(message = "[$TAG] $msg")
}