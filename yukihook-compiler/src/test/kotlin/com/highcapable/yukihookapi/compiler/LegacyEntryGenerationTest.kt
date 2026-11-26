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
 * This file is created by fankes on 2026/9/29.
 */
package com.highcapable.yukihookapi.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.highcapable.yukihookapi.compiler.subprocessor.spec.Xposed82GenerateSpec
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy

/**
 * Checks legacy entry generation and migration warnings through the processor's public contract.
 */
class LegacyEntryGenerationTest {

    @get:Rule
    val project = TemporaryFolder()

    @Test
    fun deprecatedEntryPreservesMetadataAndWarnsAboutBothMigrations() {
        val (output, warnings) = generate()

        Assert.assertEquals("example.module.CompatEntry", output["assets/xposed_init"])
        Assert.assertTrue(output.getValue("example.module.CompatEntry.kt").contains("\"example.application\""))
        Assert.assertTrue("com.highcapable.yukihookapi.hook.xposed.bridge.status.YukiHookLegacyModuleStatus_Impl.kt" in output)
        Assert.assertTrue(output.getValue("com.highcapable.yukihookapi.YukiHook_Impl.kt").contains(
            "com.highcapable.yukihookapi.hook.xposed.bridge.status.YukiHookLegacyModuleStatus_Impl_example_application"
        ))
        Assert.assertTrue(warnings.any {
            "@InjectYukiHookWithXposed" in it && "will be removed" in it && "@YukiHookXposed82Entry" in it
        })
        Assert.assertTrue(warnings.any {
            "IYukiHookXposedInit" in it && "will be removed" in it && "YukiHookXposedModule" in it
        })
    }

    @Test
    fun deprecatedStatusFlagCanDisableSelfHookGeneration() {
        val (output, _) = generate(generateModuleStatus = false)

        Assert.assertEquals("example.module.CompatEntry", output["assets/xposed_init"])
        Assert.assertFalse("com.highcapable.yukihookapi.hook.xposed.bridge.status.YukiHookLegacyModuleStatus_Impl.kt" in output)
    }

    @Test
    fun annotationCanBeMigratedBeforeTheInterface() {
        val (output, warnings) = generate(deprecatedAnnotation = false)

        Assert.assertEquals(
            mapOf("assets/xposed_init" to "example.module.CompatEntry"),
            output.filterKeys { !it.endsWith(".kt") }
        )
        Assert.assertTrue(warnings.any { "IYukiHookXposedInit" in it && "will be removed" in it })
        Assert.assertFalse(warnings.any { "InjectYukiHookWithXposed" in it })
    }

    @Test
    fun interfaceCanBeMigratedBeforeTheAnnotation() {
        val (output, warnings) = generate(deprecatedInterface = false)

        Assert.assertEquals("example.module.CompatEntry", output["assets/xposed_init"])
        Assert.assertTrue(warnings.any { "InjectYukiHookWithXposed" in it && "will be removed" in it })
        Assert.assertFalse(warnings.any { "IYukiHookXposedInit" in it })
    }

    private fun generate(
        deprecatedAnnotation: Boolean = true,
        deprecatedInterface: Boolean = true,
        generateModuleStatus: Boolean = true
    ): Pair<Map<String, String>, List<String>> {
        val root = project.newFolder()
        File(root, "build.gradle.kts").writeText("")
        val source = File(root, "src/main/kotlin/HookEntry.kt").apply {
            parentFile.mkdirs()
            writeText("package example.module")
        }
        File(root, "src/main/AndroidManifest.xml").writeText("<manifest/>")
        val sourceFile = proxy<KSFile> { name, _ ->
            when (name) {
                "getLocation" -> FileLocation(source.path, 1)
                else -> error("Unexpected source request: $name")
            }
        }
        val moduleInterface = declaration(DeclaredSymbol.HOOK_ENTRY_CLASS)
        val entryInterface = if (deprecatedInterface)
            declaration(DeclaredSymbol.LEGACY_HOOK_ENTRY_CLASS, sequenceOf(typeReference(moduleInterface)))
        else moduleInterface
        val annotationName = if (deprecatedAnnotation)
            Xposed82GenerateSpec.LEGACY_ANNOTATION_NAME else Xposed82GenerateSpec.ANNOTATION_NAME
        val annotationClass = declaration(annotationName)
        val arguments = buildMap {
            put("modulePackageName", "example.application")
            put("entryClassName", "CompatEntry")
            put(if (deprecatedAnnotation) "isUsingXposedModuleStatus" else "generateModuleStatus", generateModuleStatus)
            if (deprecatedAnnotation) {
                put("sourcePath", "not/a/source/directory")
                put("isUsingResourcesHook", true)
            }
        }
        val annotation = proxy<KSAnnotation> { name, _ ->
            when (name) {
                "getAnnotationType" -> typeReference(annotationClass)
                "getArguments" -> arguments.map { (key, argument) ->
                    proxy<KSValueArgument> { method, _ ->
                        when (method) {
                            "getName" -> ksName(key)
                            "getValue" -> argument
                            else -> error("Unexpected argument request: $method")
                        }
                    }
                }
                else -> error("Unexpected annotation request: $name")
            }
        }
        val entry = declaration(
            "example.module.HookEntry", sequenceOf(typeReference(entryInterface)),
            annotations = sequenceOf(annotation), file = sourceFile
        )
        val resolver = proxy<Resolver> { name, args ->
            when (name) {
                "getSymbolsWithAnnotation" ->
                    if (args[0] == annotationName) sequenceOf<KSAnnotated>(entry) else emptySequence()
                "getKSNameFromString" -> ksName(args[0] as String)
                "getClassDeclarationByName" ->
                    if ((args[0] as KSName).asString() == Xposed82GenerateSpec.ENTRY_CLASS_NAME)
                        declaration(Xposed82GenerateSpec.ENTRY_CLASS_NAME)
                    else null
                else -> error("Unexpected resolver request: $name")
            }
        }
        val output = linkedMapOf<String, ByteArrayOutputStream>()
        val warnings = mutableListOf<String>()
        val processor = YukiHookProcessor().create(
            SymbolProcessorEnvironment(
                options = emptyMap(), kotlinVersion = KotlinVersion.CURRENT,
                codeGenerator = proxy<CodeGenerator> { name, args ->
                    when (name) {
                        "createNewFileByPath" -> ByteArrayOutputStream().also { output[args[1] as String] = it }
                        "createNewFile" -> ByteArrayOutputStream().also {
                            output["${args[1]}.${args[2]}.${args[3]}"] = it
                        }
                        "associate", "associateByPath", "associateWithClasses" -> Unit
                        else -> error("Unexpected generator request: $name")
                    }
                },
                logger = proxy<KSPLogger> { name, args ->
                    if (name == "warn") warnings.add(args[0] as String)
                    Unit
                }
            )
        )
        processor.process(resolver)
        return output.mapValues { it.value.toString(Charsets.UTF_8.name()) } to warnings
    }

    private fun declaration(
        qualifiedName: String,
        supers: Sequence<KSTypeReference> = emptySequence(),
        annotations: Sequence<KSAnnotation> = emptySequence(),
        file: KSFile? = null
    ) = proxy<KSClassDeclaration> { name, _ ->
        when (name) {
            "getSimpleName" -> ksName(qualifiedName.substringAfterLast('.'))
            "getQualifiedName" -> ksName(qualifiedName)
            "getPackageName" -> ksName(qualifiedName.substringBeforeLast('.'))
            "getParentDeclaration" -> null
            "getSuperTypes" -> supers
            "getAnnotations" -> annotations
            "getContainingFile" -> file
            "getLocation" -> file?.location
            "getPrimaryConstructor" -> null
            "getClassKind" -> ClassKind.OBJECT
            else -> error("Unexpected declaration request: $name")
        }
    }

    private fun typeReference(declaration: KSClassDeclaration) = proxy<KSTypeReference> { name, _ ->
        check(name == "resolve")
        proxy<KSType> { method, _ ->
            check(method == "getDeclaration")
            declaration
        }
    }

    private fun ksName(value: String) = proxy<KSName> { name, _ ->
        when (name) {
            "asString" -> value
            "getShortName" -> value.substringAfterLast('.')
            "getQualifier" -> value.substringBeforeLast('.', "")
            else -> error("Unexpected name request: $name")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?) =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(self)
                "toString" -> T::class.java.simpleName
                else -> call(method.name, args.orEmpty())
            }
        } as T
}