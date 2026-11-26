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
 * This file is created by fankes on 2026/9/27.
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
import com.highcapable.yukihookapi.compiler.subprocessor.spec.LibXposedGenerateSpec
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy

/**
 * Exercises the processor's annotation-symbol input and generated resource contract.
 */
class ModuleReloadGenerationTest {

    private companion object {
        const val MODERN_ANNOTATION = LibXposedGenerateSpec.ANNOTATION_NAME
        const val HOT_RELOAD_CLASS = LibXposedGenerateSpec.HOT_RELOAD_CLASS_NAME
    }

    private data class EntryConfig(
        val minApiVersion: Int = 101,
        val targetApiVersion: Int = 102,
        val hotReload: LibXposedGenerateSpec.HotReload = LibXposedGenerateSpec.HotReload.NONE,
        val hasJavaEntry: Boolean = false,
        val nativeEntries: List<String> = emptyList()
    )

    @get:Rule
    val project = TemporaryFolder()

    @Test
    fun automaticReloadIsOnlyRequestedByAuto() {
        val disabled = generate(EntryConfig())
        val manual = generate(EntryConfig(hotReload = LibXposedGenerateSpec.HotReload.MANUAL))
        val automatic = generate(EntryConfig(hotReload = LibXposedGenerateSpec.HotReload.AUTO))

        Assert.assertTrue(disabled.getValue("META-INF/xposed/module.prop").contains("minApiVersion=101"))
        Assert.assertTrue(disabled.getValue("META-INF/xposed/module.prop").contains("autoHotReload=false"))
        Assert.assertTrue(manual.getValue("META-INF/xposed/module.prop").contains("autoHotReload=false"))
        Assert.assertTrue(automatic.getValue("META-INF/xposed/module.prop").contains("autoHotReload=true"))
        Assert.assertEquals("example.module.HookEntry_YukiHookXposedInit", automatic["META-INF/xposed/java_init.list"])
    }

    @Test
    fun modernHotReloadIsDisabledAndVersionsAreIndependent() {
        val api102 = generate(EntryConfig())
        val api103 = generate(EntryConfig(targetApiVersion = 103))

        Assert.assertTrue(api102.getValue("META-INF/xposed/module.prop").contains("minApiVersion=101"))
        Assert.assertTrue(api102.getValue("META-INF/xposed/module.prop").contains("targetApiVersion=102"))
        Assert.assertTrue(api102.getValue("META-INF/xposed/module.prop").contains("autoHotReload=false"))
        Assert.assertTrue(api103.getValue("META-INF/xposed/module.prop").contains("targetApiVersion=103"))
        Assert.assertTrue(api103.getValue("META-INF/xposed/module.prop").contains("minApiVersion=101"))
    }

    @Test
    fun modernNativeEntriesTrackConfiguredFilesWithoutChangingOtherMetadata() {
        val configured = generate(EntryConfig(nativeEntries = listOf("libexample.so", "libsecond.so")))
        val omitted = generate(EntryConfig())

        Assert.assertEquals("libexample.so\nlibsecond.so", configured["META-INF/xposed/native_init.list"])
        Assert.assertEquals("example.module.HookEntry_YukiHookXposedInit", configured["META-INF/xposed/java_init.list"])
        Assert.assertEquals(
            setOf(
                "META-INF/xposed/java_init.list", "META-INF/xposed/module.prop",
                "META-INF/xposed/scope.list", "META-INF/xposed/native_init.list"
            ),
            configured.keys.filter { it.startsWith("META-INF/") }.toSet()
        )
        Assert.assertFalse("META-INF/xposed/native_init.list" in omitted)
        Assert.assertEquals("", configured["META-INF/xposed/scope.list"])
        Assert.assertEquals(omitted["META-INF/xposed/module.prop"], configured["META-INF/xposed/module.prop"])
    }

    @Test
    fun targetApiVersionSelectsTheSupportedEntry() {
        val api101 = generate(EntryConfig(targetApiVersion = 101)).getValue("example.module.HookEntry_YukiHookXposedInit.kt")
        val api102 = generate(EntryConfig()).getValue("example.module.HookEntry_YukiHookXposedInit.kt")

        Assert.assertTrue(api101.contains("LibXposed101Entry()"))
        Assert.assertFalse(api101.contains("ModuleReloadPolicy"))
        Assert.assertTrue(api102.contains("LibXposed102Entry()"))
        Assert.assertTrue(api102.contains("ModuleReloadPolicy.NONE"))
    }

    @Test
    fun reloadCannotBeAdvertisedToTargetApi101() {
        listOf(LibXposedGenerateSpec.HotReload.MANUAL, LibXposedGenerateSpec.HotReload.AUTO).forEach { policy ->
            val failure = Assert.assertThrows(RuntimeException::class.java) {
                generate(EntryConfig(targetApiVersion = 101, hotReload = policy))
            }
            Assert.assertTrue(failure.message.orEmpty().contains("targetApiVersion >= 102"))
        }
    }

    @Test
    fun reloadRejectsAdditionalJavaEntriesBeforeWritingMetadata() {
        val failure = Assert.assertThrows(RuntimeException::class.java) {
            generate(
                EntryConfig(hotReload = LibXposedGenerateSpec.HotReload.MANUAL, hasJavaEntry = true)
            )
        }
        Assert.assertTrue(failure.message.orEmpty().contains("exactly one Java entry"))
    }

    @Test
    fun noneKeepsAdditionalEntriesAndApi101Available() {
        val output = generate(EntryConfig(hasJavaEntry = true))
        Assert.assertEquals(
            "example.module.HookEntry_YukiHookXposedInit\nexample.module.NativeEntry",
            output["META-INF/xposed/java_init.list"]
        )
    }

    private fun generate(value: EntryConfig): Map<String, String> {
        val root = project.newFolder()
        File(root, "build.gradle.kts").writeText("")
        val source = File(root, "src/main/kotlin/HookEntry.kt").apply {
            parentFile.mkdirs()
            writeText("package example.module")
        }
        File(root, "src/main/AndroidManifest.xml").writeText("<manifest/>")
        val sourceFile = proxy<KSFile> { name, _ ->
            when (name) {
                "getFilePath" -> source.path
                "getFileName" -> source.name
                "getLocation" -> FileLocation(source.path, 1)
                "getPackageName" -> ksName("example.module")
                else -> error("Unexpected source request: $name")
            }
        }
        val moduleInterface = declaration("com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule")
        val nativeInterface = declaration("io.github.libxposed.api.XposedModule")
        val nativeEntry = declaration("example.module.NativeEntry", sequenceOf(typeReference(nativeInterface)))
        val annotationClass = declaration(MODERN_ANNOTATION)
        val arguments = linkedMapOf(
            "entryClassName" to "",
            "minApiVersion" to value.minApiVersion,
            "targetApiVersion" to value.targetApiVersion,
            "scope" to emptyList<String>(),
            "staticScope" to false,
            "hotReload" to type(declaration("$HOT_RELOAD_CLASS.${value.hotReload.name}")),
            "javaEntries" to if (value.hasJavaEntry) listOf(type(nativeEntry)) else emptyList(),
            "nativeEntries" to value.nativeEntries
        )
        val annotation = proxy<KSAnnotation> { name, _ ->
            when (name) {
                "getShortName" -> ksName("YukiHookLibXposedEntry")
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
            "example.module.HookEntry", sequenceOf(typeReference(moduleInterface)),
            annotations = sequenceOf(annotation), file = sourceFile
        )
        val resolver = proxy<Resolver> { name, args ->
            when (name) {
                "getSymbolsWithAnnotation" ->
                    if (args[0] == MODERN_ANNOTATION) sequenceOf<KSAnnotated>(entry) else emptySequence()
                "getKSNameFromString" -> ksName(args[0] as String)
                "getClassDeclarationByName" ->
                    if ((args[0] as KSName).asString() == LibXposedGenerateSpec.BASE_ENTRY_CLASS_NAME)
                        declaration(LibXposedGenerateSpec.BASE_ENTRY_CLASS_NAME)
                    else null
                else -> error("Unexpected resolver request: $name")
            }
        }
        val output = linkedMapOf<String, ByteArrayOutputStream>()
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
                logger = proxy<KSPLogger> { _, _ -> }
            )
        )
        runCatching { processor.process(resolver) }.onFailure {
            Assert.assertTrue("Invalid entry wrote files: ${output.keys}", output.isEmpty())
        }.getOrThrow()
        return output.mapValues { it.value.toString(Charsets.UTF_8.name()) }
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
        type(declaration)
    }

    private fun type(declaration: KSClassDeclaration) = proxy<KSType> { name, _ ->
        check(name == "getDeclaration")
        declaration
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