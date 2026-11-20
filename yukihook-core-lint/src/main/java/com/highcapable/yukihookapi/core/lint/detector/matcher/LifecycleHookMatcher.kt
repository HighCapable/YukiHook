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
 * This file is created by fankes on 2026/11/20.
 */
package com.highcapable.yukihookapi.core.lint.detector.matcher

import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.JavaContext
import com.highcapable.yukihookapi.core.lint.DeclaredSymbol
import com.intellij.psi.PsiElement
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.toUElementOfType

/**
 * Matches class sources, method selectors, and callback reads for lifecycle hooks.
 */
internal class LifecycleHookMatcher(private val context: JavaContext, private val session: KaSession) {

    companion object {

        private const val APPLICATION = "android.app.Application"
        private const val CONTEXT = "android.content.Context"
        private const val BOOLEAN = "boolean"
        private const val KOTLIN_BOOLEAN = "kotlin.Boolean"
        private const val JAVA_BOOLEAN = "java.lang.Boolean"
        private const val KOTLIN_STRING = "kotlin.String"
        private const val CONTEXT_WRAPPER = "android.content.ContextWrapper"
        private const val INSTRUMENTATION = "android.app.Instrumentation"
        private const val ACTIVITY_THREAD = "android.app.ActivityThread"
        private const val APP_BIND_DATA = "$ACTIVITY_THREAD.AppBindData"
        private const val LOADED_APK = "android.app.LoadedApk"
        private const val ACTIVITY = "android.app.Activity"
        private const val SERVICE = "android.app.Service"
        private const val PROVIDER = "android.content.ContentProvider"
        private const val BUNDLE = "android.os.Bundle"
        private const val PERSISTABLE_BUNDLE = "android.os.PersistableBundle"
        private const val PROVIDER_INFO = "android.content.pm.ProviderInfo"
        private const val APP_GLOBALS = "android.app.AppGlobals"
        private const val ANDROID_APP_HELPER = "android.app.AndroidAppHelper"
        private const val JAVA_CLASS = "java.lang.Class"
        private const val CLASS_LOADER = "java.lang.ClassLoader"
        private const val JAVA_STRING = "java.lang.String"
        private const val JAVA_METHOD = "java.lang.reflect.Method"
        private const val KOTLIN_ALSO = "kotlin.also"
        private const val KOTLIN_APPLY = "kotlin.apply"
        private const val KOTLIN_LET = "kotlin.let"
        private const val KOTLIN_CLASS = "kotlin.jvm.kotlin"
        private const val KOTLIN_JAVA = "kotlin.jvm.java"
        private const val KOTLIN_TEXT_EQUALS = "kotlin.text.equals"
        private const val KOTLIN_JAVA_OBJECT_TYPE = "kotlin.jvm.javaObjectType"
        private const val KOTLIN_JAVA_PRIMITIVE_TYPE = "kotlin.jvm.javaPrimitiveType"
        private const val KOTLIN_ARRAY = "kotlin.arrayOf"
        private const val COLLECTIONS = "kotlin.collections"
        private const val SEQUENCES = "kotlin.sequences"
        private const val REFLECTION_NAME = "java.lang.reflect.Member.getName"
        private const val REFLECTION_COUNT = "java.lang.reflect.Executable.getParameterCount"
        private const val REFLECTION_TYPES = "java.lang.reflect.Executable.getParameterTypes"
        private const val KOTLIN_CONTENT_EQUALS = "kotlin.collections.contentEquals"
        private const val KAVA = "com.highcapable.kavaref"
        private const val VARIOUS_CLASS = "$KAVA.extension.VariousClass"
        private const val KAVA_CLASS_EXTENSION = "$KAVA.extension"
        private const val KAVA_REF = "$KAVA.KavaRef"
        private const val MEMBER_SCOPE = "$KAVA_REF.MemberScope"
        private const val METHOD_RESOLVER = "$KAVA.resolver.MethodResolver"
        private const val METHOD_CONDITION = "$KAVA.condition.MethodCondition"
        private const val MEMBER_CONDITION = "$KAVA.condition.base.MemberCondition"
        private const val CONDITION_CONFIGURATION = "$MEMBER_CONDITION.Configuration"
        private const val CONDITION_MERGE = "$KAVA.condition.extension.mergeWith"
        private const val EXECUTABLE_CONDITION = "$KAVA.condition.base.ExecutableCondition"
        private const val CORE = DeclaredSymbol.CORE_PACKAGE_NAME
        private const val KAVA_EXTENSION = "$CORE.extension.kavaref"
        private const val PACKAGE_PARAM = "$CORE.hook.param.PackageParam"
        private const val APP_LIFECYCLE = "$PACKAGE_PARAM.AppLifecycle"
        private const val CLASSIC_HOOKER = "$CORE.hook.core.YukiHookCreator.ClassicMemberHooker"
        private const val INVOCATION = "$CORE.hook.param.HookInvocation"
        private const val HOOK_PARAM = "$CORE.hook.param.HookParam"
        private const val HOOK_CHAIN = "$CORE.hook.param.HookChain"
        private const val ARGUMENT_EDITOR = "$HOOK_PARAM.ArgumentEditor"

        private val hookNames = setOf("hook", "hookAll", "intercept", "interceptAll")
        private val collectionSelectors = setOf("first", "last", "single", "firstOrNull", "lastOrNull", "singleOrNull", "find", "findLast", "filter", "filterNot")
        private val collectionAdapters = setOf("asList", "toList", "toTypedArray", "asSequence")
        private val selectors = setOf("method", "firstMethod", "firstMethodOrNull", "lastMethod", "lastMethodOrNull")
        private val globalApplicationReads = setOf(
            "$ACTIVITY_THREAD.currentApplication", "$APP_GLOBALS.getInitialApplication", "$ANDROID_APP_HELPER.currentApplication"
        )
        private val methodLookups = setOf("$JAVA_CLASS.getMethod", "$JAVA_CLASS.getDeclaredMethod")
        private val applicationContextReads = setOf(
            "$CONTEXT.applicationContext", "$CONTEXT.getApplicationContext",
            "$CONTEXT_WRAPPER.applicationContext", "$CONTEXT_WRAPPER.getApplicationContext",
            "$ACTIVITY.application", "$ACTIVITY.getApplication", "$SERVICE.application", "$SERVICE.getApplication",
            "$ACTIVITY_THREAD.application", "$ACTIVITY_THREAD.getApplication"
        )
        private val targets = listOf(
            Target(INSTRUMENTATION, "callApplicationOnCreate", listOf(APPLICATION), Source.ARGUMENT),
            Target(APPLICATION, "onCreate", emptyList(), Source.INSTANCE),
            Target(APPLICATION, "attach", listOf(CONTEXT), Source.ARGUMENT_OR_INSTANCE, early = true, public = false),
            Target(APPLICATION, "attachBaseContext", listOf(CONTEXT), Source.ARGUMENT_OR_INSTANCE, early = true, public = false),
            Target(CONTEXT_WRAPPER, "attachBaseContext", listOf(CONTEXT), Source.WRAPPER, early = true, public = false),
            Target(INSTRUMENTATION, "newApplication", listOf(CLASS_LOADER, JAVA_STRING, CONTEXT), Source.RESULT, argument = 2, early = true),
            Target(INSTRUMENTATION, "newApplication", listOf(JAVA_CLASS, CONTEXT), Source.RESULT, argument = 1, early = true),
            Target(LOADED_APK, "makeApplication", listOf(BOOLEAN, INSTRUMENTATION), Source.RESULT, early = true),
            Target(LOADED_APK, "makeApplicationInner", listOf(BOOLEAN, INSTRUMENTATION), Source.RESULT, early = true),
            Target(LOADED_APK, "makeApplicationInner", listOf(BOOLEAN, INSTRUMENTATION, BOOLEAN), Source.RESULT, early = true, public = false),
            Target(ACTIVITY_THREAD, "handleBindApplication", listOf(APP_BIND_DATA), Source.GLOBAL, early = true, public = false),
            Target(INSTRUMENTATION, "callActivityOnCreate", listOf(ACTIVITY, BUNDLE), Source.COMPONENT_ARGUMENT),
            Target(INSTRUMENTATION, "callActivityOnCreate", listOf(ACTIVITY, BUNDLE, PERSISTABLE_BUNDLE), Source.COMPONENT_ARGUMENT),
            Target(INSTRUMENTATION, "callActivityOnStart", listOf(ACTIVITY), Source.COMPONENT_ARGUMENT),
            Target(INSTRUMENTATION, "callActivityOnResume", listOf(ACTIVITY), Source.COMPONENT_ARGUMENT),
            Target(ACTIVITY, "onCreate", listOf(BUNDLE), Source.COMPONENT, public = false),
            Target(ACTIVITY, "onCreate", listOf(BUNDLE, PERSISTABLE_BUNDLE), Source.COMPONENT),
            Target(ACTIVITY, "onStart", emptyList(), Source.COMPONENT, public = false),
            Target(ACTIVITY, "onResume", emptyList(), Source.COMPONENT, public = false),
            Target(SERVICE, "onCreate", emptyList(), Source.COMPONENT),
            Target(PROVIDER, "onCreate", emptyList(), Source.COMPONENT, early = true),
            Target(PROVIDER, "attachInfo", listOf(CONTEXT, PROVIDER_INFO), Source.COMPONENT_ARGUMENT, early = true)
        )

        val callbackNames = setOf("before", "after", "onCreate", "intercept", "interceptAll")
    }

    private enum class CallbackPhase { BEFORE, AFTER, CHAIN }

    private enum class Source {
        INSTANCE,
        ARGUMENT,
        ARGUMENT_OR_INSTANCE,
        RESULT,
        WRAPPER,
        GLOBAL,
        COMPONENT,
        COMPONENT_ARGUMENT,
        LIFECYCLE;

        val isComponent get() = this == COMPONENT || this == COMPONENT_ARGUMENT
    }

    private data class Target(
        val owner: String,
        val name: String,
        val parameters: List<String>,
        val source: Source,
        val argument: Int = 0,
        val early: Boolean = false,
        val public: Boolean = true
    )

    enum class Advice { DIRECT, TIMING_DEPENDENT, COMPONENT_AS_APPLICATION }

    data class Match(val anchor: KtCallExpression, val expression: KtExpression, val advice: Advice)

    private class Conditions {
        var name: String? = null
        var parameters: List<String>? = null
        var count: Int? = null
        var superclass = false
    }

    private data class MethodSet(val owner: String, val publicOnly: Boolean, val conditions: List<MethodKey> = emptyList())

    private data class MethodKey(val name: String? = null, val count: Int? = null, val parameters: List<String>? = null)

    private data class Input(val className: String?, val methods: MethodSet?, val constant: Any?)

    private data class Frame(val function: KtNamedFunction, val inputs: Map<KtParameter, Input>)

    private data class MethodLookup(val owner: String, val name: String, val parameters: List<String>, val publicOnly: Boolean)

    private val frames = mutableListOf<Frame>()
    private val resolvingMethods = mutableSetOf<KtProperty>()

    fun match(call: KtCallExpression): Match? {
        val name = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return null
        val resolved = call.function() ?: return null
        val callback = resolved.valueArgumentMapping.entries.singleOrNull {
            it.value.name.asString() in setOf("callback", "invocation")
        }?.key?.let { KtPsiUtil.safeDeparenthesize(it) } as? KtLambdaExpression ?: return null
        if (resolved.symbol.id() == "$APP_LIFECYCLE.onCreate") {
            val config = resolved.dispatchReceiver.lambda() ?: return null
            val registration = config.enclosingCall() ?: return null
            if (registration.function()?.symbol?.id() !in setOf("$PACKAGE_PARAM.registerAppLifecycle", "$PACKAGE_PARAM.onAppLifecycle")) return null
            return contextAdvice(callback, Source.LIFECYCLE)?.let { Match(call, call.fullExpression(), it) }
        }

        val phase = when (name) {
            "intercept", "interceptAll" -> CallbackPhase.CHAIN
            "before" -> CallbackPhase.BEFORE
            else -> CallbackPhase.AFTER
        }
        val anchor = if (phase == CallbackPhase.CHAIN) call else {
            if (resolved.symbol.id() !in setOf("$CLASSIC_HOOKER.before", "$CLASSIC_HOOKER.after")) return null
            classicRegistration(resolved.dispatchReceiver) ?: return null
        }
        val registration = anchor.function() ?: return null
        if (!registration.symbol.isHook()) return null
        val receiver = registration.extensionReceiver.expression() ?: return null
        val targets = methodTargets(receiver)
        if (targets.isEmpty()) return null
        val advice = targets.maxOf { target ->
            val usesContext = contextAdvice(callback, target.source, target.argument, phase, target.parameters.size) ?: return null
            if (usesContext == Advice.DIRECT && (phase == CallbackPhase.BEFORE || target.early)) Advice.TIMING_DEPENDENT else usesContext
        }
        return Match(anchor, registrationRange(anchor, call), advice)
    }

    private fun registrationRange(anchor: KtCallExpression, callback: KtCallExpression): KtExpression {
        val hook = anchor.fullExpression()
        if (hook.textRange.contains(callback.textRange)) return hook
        val range = callback.fullExpression()
        if (range.textRange.contains(hook.textRange)) return range
        return callback.parents.filterIsInstance<KtCallExpression>()
            .map { it.fullExpression() }.firstOrNull { it.textRange.contains(hook.textRange) } ?: range
    }

    private fun KtCallExpression.fullExpression() =
        (parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == this } ?: this

    private fun classicRegistration(receiver: KaReceiverValue?): KtCallExpression? {
        receiver.expression()?.let { return it.origin().call() }
        val config = receiver.lambda() ?: return null
        val enclosing = config.enclosingCall() ?: return null
        val call = enclosing.function() ?: return null
        if (call.symbol.isHook()) return enclosing
        if (call.symbol.id() != KOTLIN_APPLY) return null
        return call.extensionReceiver.expression()?.origin()?.call()
    }

    private fun methodTargets(expression: KtExpression, use: KtExpression = expression): List<Target> {
        reflectedTargets(expression, use)?.let { return it }
        val origin = expression.origin()
        if (origin is KtPostfixExpression && origin.operationToken == KtTokens.EXCLEXCL)
            return origin.baseExpression?.let { methodTargets(it, use) }.orEmpty()
        if (origin is KtArrayAccessExpression)
            return origin.arrayExpression?.let { methodTargets(it, use) }.orEmpty()
        if (origin is KtNameReferenceExpression) return assignedMethods(origin, use)
        if (origin is KtQualifiedExpression && origin.selectorExpression is KtNameReferenceExpression &&
            origin.selectorExpression?.variable()?.symbol?.id() == "$METHOD_RESOLVER.self")
            return methodTargets(origin.receiverExpression)
        val call = origin.call() ?: return emptyList()
        val resolved = call.function() ?: return emptyList()
        localMethodTargets(call)?.let { return it }
        val id = resolved.symbol.id()
        if (id in setOf("$COLLECTIONS.listOf", KOTLIN_ARRAY))
            return call.arguments()?.flatMap { methodTargets(it).ifEmpty { return emptyList() } }.orEmpty()
        if (id in methodLookups) {
            val lookup = methodLookup(call) ?: return emptyList()
            return findTargets(lookup.owner, lookup.name, lookup.parameters, null).filter { !lookup.publicOnly || it.public }
        }
        val name = resolved.symbol.callableId?.callableName?.asString()
        val owner = resolved.symbol.callableId?.classId?.asSingleFqName()?.asString()
        if ((name in selectors && owner == MEMBER_SCOPE) || (name == "build" && owner in setOf(METHOD_CONDITION, MEMBER_CONDITION)))
            return kavaTargets(call)
        val collection = resolved.extensionReceiver.expression() ?: return emptyList()
        if (resolved.symbol.isCollectionCall(collectionSelectors + collectionAdapters) &&
            (call.valueArguments.isEmpty() || call.arguments()?.singleOrNull() is KtLambdaExpression))
            return methodTargets(collection, use)
        if (resolved.symbol.isCollectionCall(setOf("getOrNull", "elementAt", "elementAtOrNull")) && call.valueArguments.size == 1)
            return methodTargets(collection, use)
        if (resolved.symbol.isCollectionCall(setOf("map", "mapNotNull"))) {
            val lambda = call.arguments()?.singleOrNull() as? KtLambdaExpression ?: return emptyList()
            val self = lambda.bodyExpression?.statements?.singleOrNull()?.variable() ?: return emptyList()
            if (self.symbol.id() == "$METHOD_RESOLVER.self" && self.dispatchReceiver.expression()?.isParameterOf(lambda) == true)
                return methodTargets(collection, use)
        }
        return emptyList()
    }

    private fun assignedMethods(reference: KtNameReferenceExpression, use: KtExpression): List<Target> {
        val property = reference.targetSymbol()?.psi as? KtProperty ?: return emptyList()
        if (!property.isLocal || !property.isVar || property in resolvingMethods) return emptyList()
        val block = property.parent as? KtBlockExpression ?: return emptyList()
        if (!block.textRange.contains(use.textRange)) return emptyList()
        val writes = block.collectDescendantsOfType<KtBinaryExpression> {
            it.textOffset < use.textOffset && it.operationToken in KtTokens.ALL_ASSIGNMENTS &&
                (it.left as? KtNameReferenceExpression)?.targetSymbol()?.psi == property
        }
        val values = listOfNotNull(property.initializer) + writes.map {
            if (it.operationToken != KtTokens.EQ) return emptyList()
            it.right ?: return emptyList()
        }
        if (!resolvingMethods.add(property)) return emptyList()
        return try {
            values.filterNot { it.isNullLiteral() }.flatMap { methodTargets(it).ifEmpty { return emptyList() } }
        } finally {
            resolvingMethods.remove(property)
        }
    }

    private fun localMethodTargets(call: KtCallExpression): List<Target>? {
        val resolved = call.function() ?: return null
        val function = resolved.symbol.psi as? KtNamedFunction ?: return null
        if (function.containingFile != call.containingFile || resolved.extensionReceiver != null ||
            (!function.isLocal && !function.hasModifier(KtTokens.PRIVATE_KEYWORD))) return null
        val inputs = resolved.valueArgumentMapping.entries.associate { (expression, parameter) ->
            val declaration = parameter.symbol.psi as? KtParameter ?: return null
            declaration to Input(className(expression), methodSet(expression), expression.constant())
        }

        // Numeric cursor changes do not change method provenance in a recursive array walk.
        fun Map<KtParameter, Input>.withoutIndices() = mapValues { (_, input) ->
            if (input.constant is Int) input.copy(constant = null) else input
        }
        if (frames.any { it.function == function && it.inputs.withoutIndices() == inputs.withoutIndices() }) return emptyList()
        if (frames.size >= 12) return null
        frames += Frame(function, inputs)
        return try {
            val body = function.bodyExpression ?: return null
            if (!function.hasBlockBody()) return returnTargets(body)
            val returns = body.collectDescendantsOfType<KtReturnExpression> {
                it.getLabelName() == null && it.parents.filterIsInstance<KtNamedFunction>().firstOrNull() == function
            }
            if (returns.isEmpty()) return null
            returns.flatMap { returnTargets(it.returnedExpression ?: return null) ?: return null }
        } finally {
            frames.removeAt(frames.lastIndex)
        }
    }

    private fun returnTargets(expression: KtExpression): List<Target>? {
        val value = KtPsiUtil.safeDeparenthesize(expression)
        if (value.isNullLiteral()) return emptyList()
        if (value is KtIfExpression) {
            val yes = value.then?.let(::returnTargets) ?: return null
            val no = value.`else`?.let(::returnTargets) ?: return null
            return yes + no
        }
        if (value is KtBinaryExpression && value.operationToken == KtTokens.ELVIS) {
            val left = value.left?.let(::returnTargets) ?: return null
            val right = value.right?.let(::returnTargets) ?: return null
            return left + right
        }
        val helper = value.call()?.let(::localMethodTargets)
        return helper ?: methodTargets(value).takeIf { it.isNotEmpty() }
    }

    private fun KtExpression.isNullLiteral() = node.elementType == KtNodeTypes.NULL

    private fun input(expression: KtExpression): Input? {
        val param = (expression as? KtNameReferenceExpression)?.targetSymbol()?.psi as? KtParameter ?: return null
        return frames.asReversed().firstNotNullOfOrNull { it.inputs[param] }
    }

    private fun reflectedTargets(expression: KtExpression, use: KtExpression): List<Target>? {
        val set = methodSet(expression) ?: return null
        val conditions = set.conditions + guards(expression, use)
        val name = conditions.mapNotNull { it.name }.distinct().singleOrNull() ?: return null
        val counts = conditions.mapNotNull { it.count }.distinct()
        val signatures = conditions.mapNotNull { it.parameters }.distinct()
        if (counts.size > 1 || signatures.size > 1) return null
        return findTargets(set.owner, name, signatures.singleOrNull(), counts.singleOrNull()).filter { !set.publicOnly || it.public }
    }

    private fun methodSet(expression: KtExpression): MethodSet? {
        val origin = expression.origin()
        input(origin)?.methods?.let { return it }
        if (origin is KtPostfixExpression && origin.operationToken == KtTokens.EXCLEXCL)
            return origin.baseExpression?.let(::methodSet)
        if (origin is KtBinaryExpression && origin.operationToken == KtTokens.ELVIS && origin.right.isExit())
            return origin.left?.let(::methodSet)
        if (origin is KtArrayAccessExpression) return origin.arrayExpression?.let(::methodSet)
        val variable = origin.variable()
        if (variable?.symbol?.javaId() in setOf("$JAVA_CLASS.getDeclaredMethods", "$JAVA_CLASS.getMethods")) {
            val owner = variable?.dispatchReceiver.expression()?.let(::className) ?: return null
            return MethodSet(owner, variable?.symbol?.javaId() == "$JAVA_CLASS.getMethods")
        }
        if (origin is KtNameReferenceExpression) {
            val declaration = origin.targetSymbol() ?: return null
            val parameter = declaration.psi as? KtParameter
            val loop = parameter?.parents?.filterIsInstance<KtForExpression>()?.firstOrNull { it.loopParameter == parameter }
            if (loop != null) return loop.loopRange?.let(::methodSet)
            val symbol = declaration as? KaValueParameterSymbol ?: return null
            val lambda = with(session) { symbol.containingSymbol }?.psi?.parent as? KtLambdaExpression ?: return null
            val call = lambda.enclosingCall() ?: return null
            val resolved = call.function() ?: return null
            if (!resolved.symbol.isCollectionCall(setOf("forEach"))) return null
            return resolved.extensionReceiver.expression()?.let(::methodSet)
        }
        val call = origin.call() ?: return null
        val resolved = call.function() ?: return null
        val id = resolved.symbol.id()
        if (id in setOf("$JAVA_CLASS.getDeclaredMethods", "$JAVA_CLASS.getMethods")) {
            val owner = resolved.dispatchReceiver.expression()?.let(::className) ?: return null
            return MethodSet(owner, id == "$JAVA_CLASS.getMethods")
        }
        val name = resolved.symbol.callableId?.callableName?.asString()
        val collection = resolved.extensionReceiver.expression() ?: resolved.dispatchReceiver.expression() ?: return null
        if (resolved.symbol.isCollectionCall(collectionAdapters) && call.valueArguments.isEmpty()) return methodSet(collection)
        if (resolved.symbol.isCollectionCall(setOf("getOrNull", "elementAt", "elementAtOrNull")) && call.valueArguments.size == 1)
            return methodSet(collection)
        if (!resolved.symbol.isCollectionCall(collectionSelectors)) return null
        val methods = methodSet(collection) ?: return null
        if (call.valueArguments.isEmpty()) return methods
        val lambda = call.arguments()?.singleOrNull() as? KtLambdaExpression ?: return null
        val last = lambda.bodyExpression?.statements?.singleOrNull() ?: return null
        val keys = conditionKeys(last, truth = name != "filterNot") { it.isParameterOf(lambda) }
        return methods.copy(conditions = methods.conditions + keys)
    }

    private fun guards(method: KtExpression, use: KtExpression) = buildList {
        fun addCondition(condition: KtExpression?, truth: Boolean) {
            if (condition != null) addAll(conditionKeys(condition, truth) { sameMethod(it, method) })
        }
        var child: PsiElement = use
        for (parent in use.parents) {
            when (parent) {
                is KtIfExpression -> when {
                    parent.then?.textRange?.contains(child.textRange) == true -> addCondition(parent.condition, true)
                    parent.`else`?.textRange?.contains(child.textRange) == true -> addCondition(parent.condition, false)
                }
                is KtWhenEntry -> {
                    val subject = (parent.parent as? KtWhenExpression)?.subjectExpression
                    val value = (parent.conditions.singleOrNull() as? KtWhenConditionWithExpression)?.expression
                    if (subject != null && value != null) methodKey(subject, value) { sameMethod(it, method) }?.let(::add)
                }
                is KtWhileExpression -> if (parent.body?.textRange?.contains(child.textRange) == true) addCondition(parent.condition, true)
                is KtBlockExpression -> for (statement in parent.statements.takeWhile { !it.textRange.contains(child.textRange) }) {
                    if (statement !is KtIfExpression) continue
                    if (statement.then.isExit()) addCondition(statement.condition, false)
                    if (statement.`else`.isExit()) addCondition(statement.condition, true)
                }
                is KtNamedFunction -> break
            }
            child = parent
        }
    }

    private fun conditionKeys(expression: KtExpression, truth: Boolean, isMethod: (KtExpression) -> Boolean): List<MethodKey> {
        val condition = KtPsiUtil.safeDeparenthesize(expression)
        val binary = condition as? KtBinaryExpression
        if (binary?.operationToken == if (truth) KtTokens.ANDAND else KtTokens.OROR)
            return listOfNotNull(binary.left, binary.right).flatMap { conditionKeys(it, truth, isMethod) }
        if (binary?.operationToken == if (truth) KtTokens.EQEQ else KtTokens.EXCLEQ) {
            val left = binary.left ?: return emptyList()
            val right = binary.right ?: return emptyList()
            return listOfNotNull(methodKey(left, right, isMethod) ?: methodKey(right, left, isMethod))
        }
        val resolved = condition.call()?.function() ?: return emptyList()
        if (!truth) return emptyList()
        if (resolved.symbol.id() in setOf("$KOTLIN_STRING.equals", KOTLIN_TEXT_EQUALS)) {
            val receiver = resolved.extensionReceiver.expression() ?: resolved.dispatchReceiver.expression() ?: return emptyList()
            val argument = resolved.valueArgumentMapping.entries.singleOrNull { it.value.name.asString() == "other" }?.key ?: return emptyList()
            if (resolved.valueArgumentMapping.entries.any { it.value.name.asString() == "ignoreCase" && it.key.constant() != false }) return emptyList()
            return listOfNotNull(methodKey(receiver, argument, isMethod) ?: methodKey(argument, receiver, isMethod))
        }
        if (resolved.symbol.id() != KOTLIN_CONTENT_EQUALS) return emptyList()
        val receiver = resolved.extensionReceiver.expression() ?: return emptyList()
        val parameterTypes = resolved.valueArgumentMapping.keys.singleOrNull()?.origin()?.call() ?: return emptyList()
        if (parameterTypes.function()?.symbol?.id() != KOTLIN_ARRAY || !isMethodProperty(receiver, "getParameterTypes", isMethod))
            return emptyList()
        val types = parameterTypes.arguments()?.map { className(it) ?: return emptyList() } ?: return emptyList()
        return listOf(MethodKey(parameters = types))
    }

    private fun methodKey(property: KtExpression, value: KtExpression, isMethod: (KtExpression) -> Boolean): MethodKey? = when {
        isMethodProperty(property, "getName", isMethod) -> (value.constant() as? String)?.let { MethodKey(name = it) }
        isMethodProperty(property, "getParameterCount", isMethod) -> (value.constant() as? Int)?.let { MethodKey(count = it) }
        else -> null
    }

    private fun isMethodProperty(expression: KtExpression, name: String, isMethod: (KtExpression) -> Boolean): Boolean {
        val origin = expression.origin()
        val access = origin.variable()
        val call = origin.call()?.function()
        val id = (access?.symbol ?: call?.symbol)?.javaId() ?: return false
        val reflectionId = when (name) {
            "getName" -> REFLECTION_NAME
            "getParameterCount" -> REFLECTION_COUNT
            else -> REFLECTION_TYPES
        }
        if (id != reflectionId && id != "$JAVA_METHOD.$name") return false
        val receiver = (access?.dispatchReceiver ?: call?.dispatchReceiver).expression() ?: return false
        return isMethod(receiver)
    }

    private fun sameMethod(first: KtExpression, second: KtExpression): Boolean {
        val left = first.origin()
        val right = second.origin()
        if (left == right) return true
        val symbol = left.lastName()?.targetSymbol()
        if (symbol != null && symbol == right.lastName()?.targetSymbol() && left is KtNameReferenceExpression && right is KtNameReferenceExpression)
            return true
        if (left is KtArrayAccessExpression && right is KtArrayAccessExpression) {
            val array = left.arrayExpression?.origin() ?: return false
            val other = right.arrayExpression?.origin() ?: return false
            val index = left.indexExpressions.singleOrNull()?.constant() ?: return false
            return index == right.indexExpressions.singleOrNull()?.constant() && sameMethod(array, other)
        }
        return false
    }

    private tailrec fun KtExpression?.isExit(): Boolean = when (this) {
        is KtReturnExpression -> true
        is KtContinueExpression -> getLabelName() == null
        is KtBreakExpression -> getLabelName() == null
        is KtBlockExpression -> statements.singleOrNull().isExit()
        else -> false
    }

    private fun KaCallableSymbol.javaId() = (this as? KaSyntheticJavaPropertySymbol)?.javaGetterSymbol?.id() ?: id()

    private fun kavaTargets(call: KtCallExpression): List<Target> {
        val resolved = call.function() ?: return emptyList()
        val condition = Conditions()
        val argument = call.arguments()?.singleOrNull() ?: if (call.valueArguments.isEmpty()) null else return emptyList()
        val owner = if (resolved.symbol.id() in setOf("$METHOD_CONDITION.build", "$MEMBER_CONDITION.build")) {
            val receiver = resolved.dispatchReceiver.expression() ?: return emptyList()
            val builderOwner = conditionBuilder(receiver, condition) ?: return emptyList()
            if (argument != null && !argument.isNullLiteral()) configurationOwner(argument) ?: return emptyList() else builderOwner
        } else {
            val owner = memberScopeOwner(resolved.dispatchReceiver) ?: return emptyList()
            if (argument == null) return emptyList()
            if (argument is KtLambdaExpression) {
                if (!readConditionBlock(argument, condition)) return emptyList()
            } else if (conditionBuilder(argument, condition) == null) return emptyList()
            owner
        }
        val name = condition.name ?: return emptyList()
        return findTargets(owner, name, condition.parameters, condition.count).ifEmpty {
            if (!condition.superclass) return emptyList()
            val parent = context.evaluator.findClass(owner)?.superClass?.qualifiedName ?: return emptyList()
            findTargets(parent, name, condition.parameters, condition.count)
        }
    }

    private fun conditionBuilder(expression: KtExpression, condition: Conditions): String? {
        val reference = KtPsiUtil.safeDeparenthesize(expression) as? KtNameReferenceExpression
        val property = reference?.targetSymbol()?.psi as? KtProperty
        if (property != null && property.isLocal && !property.isVar) {
            val owner = property.initializer?.let { conditionBuilder(it, condition) } ?: return null
            return owner.takeIf { readConditionUpdates(property, expression, condition) }
        }
        val origin = expression.origin()
        val call = origin.call() ?: return null
        val resolved = call.function() ?: return null
        if ((resolved.symbol as? KaConstructorSymbol)?.containingClassId?.asSingleFqName()?.asString() == METHOD_CONDITION)
            return ""
        return when (resolved.symbol.id()) {
            "$MEMBER_SCOPE.method" -> if (call.valueArguments.isEmpty()) memberScopeOwner(resolved.dispatchReceiver) else null
            "$METHOD_CONDITION.copy" -> resolved.dispatchReceiver.expression()?.let { conditionBuilder(it, condition) }
            KOTLIN_APPLY -> {
                val base = resolved.extensionReceiver.expression() ?: return null
                val owner = conditionBuilder(base, condition) ?: return null
                val block = call.arguments()?.singleOrNull() as? KtLambdaExpression ?: return null
                owner.takeIf { readConditionBlock(block, condition) }
            }
            else -> {
                val base = resolved.dispatchReceiver.expression() ?: return null
                val owner = conditionBuilder(base, condition) ?: return null
                owner.takeIf { readConditionCall(call, condition) }
            }
        }
    }

    private fun configurationOwner(expression: KtExpression): String? {
        val call = expression.origin().call() ?: return null
        val resolved = call.function() ?: return null
        if (resolved.valueArgumentMapping.entries.any { it.value.name.asString() in setOf("superclass", "processorResolver") }) return null
        if (resolved.symbol.id() == "$CONDITION_CONFIGURATION.Companion.createConfiguration")
            return resolved.extensionReceiver.expression()?.let(::className)
        if ((resolved.symbol as? KaConstructorSymbol)?.containingClassId?.asSingleFqName()?.asString() != CONDITION_CONFIGURATION) return null
        return resolved.valueArgumentMapping.entries.singleOrNull { it.value.name.asString() == "declaringClass" }?.key?.let(::className)
    }

    private fun readConditionUpdates(property: KtProperty, use: KtExpression, condition: Conditions): Boolean {
        val block = property.parent as? KtBlockExpression ?: return false
        if (!block.textRange.contains(use.textRange)) return false
        for (statement in block.statements.dropWhile { it != property }.drop(1).takeWhile { !it.textRange.contains(use.textRange) }) {
            if (!statement.anyDescendantOfType<KtNameReferenceExpression> { it.targetSymbol()?.psi == property }) continue
            if (statement is KtBinaryExpression && readConditionAssignment(statement, condition) {
                    it.expression()?.lastName()?.targetSymbol()?.psi == property
                }) continue
            val call = statement.call() ?: return false
            val resolved = call.function() ?: return false
            val receiver = resolved.dispatchReceiver.expression() ?: resolved.extensionReceiver.expression()
            if (receiver?.lastName()?.targetSymbol()?.psi == property) {
                if (resolved.symbol.id() == CONDITION_MERGE) {
                    val other = call.arguments()?.singleOrNull() ?: return false
                    val merged = Conditions()
                    if (conditionBuilder(other, merged) == null) return false
                    merged.name?.let { condition.name = it }
                    merged.parameters?.let { condition.parameters = it }
                    merged.count?.let { condition.count = it }
                    condition.superclass = condition.superclass || merged.superclass
                } else if (!readConditionCall(call, condition)) return false
            } else if (resolved.symbol.callableId?.classId?.asSingleFqName()?.asString() != PACKAGE_PARAM && !resolved.symbol.isHook() &&
                !statement.anyDescendantOfType<KtCallExpression> { it.function()?.symbol?.isHook() == true }) return false
        }
        return true
    }

    private fun readConditionBlock(block: KtLambdaExpression, condition: Conditions): Boolean {
        for (statement in block.bodyExpression?.statements.orEmpty()) {
            if (statement is KtBinaryExpression && statement.operationToken in KtTokens.ALL_ASSIGNMENTS) {
                if (!readConditionAssignment(statement, condition) { it.isReceiverOf(block) }) return false
            } else {
                val steps = statement.selectorSteps()
                if (!steps.first().call()?.function()?.dispatchReceiver.isReceiverOf(block)) return false
                if (steps.any { it.call()?.let { call -> readConditionCall(call, condition) } != true }) return false
            }
        }
        return true
    }

    private fun readConditionAssignment(
        assignment: KtBinaryExpression,
        condition: Conditions,
        isBuilder: (KaReceiverValue?) -> Boolean
    ): Boolean {
        val access = assignment.left?.variable() ?: return false
        if (!isBuilder(access.dispatchReceiver)) return false
        val rhs = assignment.right ?: return false
        when (access.symbol.id()) {
            "$MEMBER_CONDITION.name" -> {
                if (assignment.operationToken != KtTokens.EQ) return false
                condition.name = rhs.constant() as? String ?: return false
            }
            "$EXECUTABLE_CONDITION.parameterCount" -> {
                if (assignment.operationToken != KtTokens.EQ) return false
                condition.count = rhs.constant() as? Int ?: return false
            }
            "$EXECUTABLE_CONDITION.parameters" -> {
                if (assignment.operationToken != KtTokens.PLUSEQ) return false
                val types = rhs.origin().call()?.takeIf { it.function()?.symbol?.id() in setOf(KOTLIN_ARRAY, "$COLLECTIONS.listOf") }
                val params = if (types != null) types.arguments(expandArrays = true) ?: return false else listOf(rhs)
                condition.parameters = condition.parameters.orEmpty() + params.map {
                    className(it) ?: (it.constant() as? String)?.javaName() ?: return false
                }
            }
            "$METHOD_CONDITION.returnType", "$METHOD_CONDITION.genericReturnType", "$METHOD_CONDITION.isBridge",
            "$METHOD_CONDITION.isBridgeNot", "$METHOD_CONDITION.isDefault", "$METHOD_CONDITION.isDefaultNot",
            "$METHOD_CONDITION.defaultValue", "$MEMBER_CONDITION.genericString", "$MEMBER_CONDITION.isSynthetic",
            "$MEMBER_CONDITION.isSyntheticNot", "$EXECUTABLE_CONDITION.isVarArgs", "$EXECUTABLE_CONDITION.isVarArgsNot" ->
                if (assignment.operationToken != KtTokens.EQ) return false
            else -> return false
        }
        return true
    }

    private fun readConditionCall(call: KtCallExpression, condition: Conditions): Boolean {
        val symbol = call.function()?.symbol ?: return false
        val id = symbol.callableId ?: return false
        val owner = id.classId?.asSingleFqName()?.asString()
        if (owner !in setOf(METHOD_CONDITION, MEMBER_CONDITION, EXECUTABLE_CONDITION)) return false
        val args = call.arguments(expandArrays = true) ?: return false
        when (id.callableName.asString()) {
            "name" -> condition.name = args.singleOrNull()?.constant() as? String ?: return false
            "parameters" -> {
                val parameters = args.map { className(it) ?: (it.constant() as? String)?.javaName() ?: return false }
                condition.parameters = condition.parameters.orEmpty() + parameters
                if (condition.parameters?.isEmpty() == true) condition.parameters = null
            }
            "emptyParameters" -> condition.count = 0
            "parameterCount" -> condition.count = args.singleOrNull()?.constant() as? Int ?: return false
            "superclass" -> condition.superclass = true
            // These selectors can only narrow the set identified by the literal name and signature.
            "returnType", "genericReturnType", "defaultValue", "modifiers", "modifiersNot", "isBridge", "isBridgeNot",
            "isDefault", "isDefaultNot", "isSynthetic", "isSyntheticNot", "annotations", "annotationsNot", "emptyAnnotations",
            "isVarArgs", "isVarArgsNot", "parametersNot", "exceptionTypes", "exceptionTypesNot", "genericString", "typeParameters",
            "typeParametersNot", "genericParameters", "genericParametersNot", "emptyExceptionTypes", "emptyParametersNot",
            "emptyTypeParameters", "emptyTypeParametersNot", "genericExceptionTypes", "genericExceptionTypesNot", "parameterAnnotations",
            "parameterAnnotationsNot", "annotatedParameterTypes", "annotatedParameterTypesNot", "annotatedReturnType",
            "annotatedReturnTypeNot", "annotatedReceiverType", "annotatedReceiverTypeNot" -> if (args.any { it is KtLambdaExpression }) return false
            else -> return false
        }
        return true
    }

    private fun KtExpression.selectorSteps(): List<KtExpression> {
        if (this !is KtQualifiedExpression) return listOf(this)
        val receiver = receiverExpression
        val selector = selectorExpression ?: return listOf(this)
        if (receiver.call() == null || selector !is KtCallExpression) return listOf(this)
        return receiver.selectorSteps() + selector
    }

    private fun methodLookup(call: KtCallExpression): MethodLookup? {
        val resolved = call.function() ?: return null
        val id = resolved.symbol.id()
        if (id !in methodLookups) return null
        val args = call.arguments(expandArrays = true) ?: return null
        val owner = resolved.dispatchReceiver.expression()?.let(::className) ?: return null
        val name = args.firstOrNull()?.constant() as? String ?: return null
        val parameters = args.drop(1).map { className(it) ?: return null }
        return MethodLookup(owner, name, parameters, id == "$JAVA_CLASS.getMethod")
    }

    private fun memberScopeOwner(receiver: KaReceiverValue?): String? {
        val expression = receiver.expression() ?: receiver.lambda()?.enclosingCall()?.function()
            ?.takeIf { it.symbol.id() == KOTLIN_APPLY }?.extensionReceiver.expression() ?: return null
        val call = expression.origin().call()?.function() ?: return null
        return when (call.symbol.id()) {
            "$KAVA_REF.Companion.resolve" -> call.extensionReceiver.expression()?.let(::className)
            "$KAVA_REF.Companion.asResolver" -> {
                val constructor = call.extensionReceiver.expression()?.origin()?.call()?.function()?.symbol as? KaConstructorSymbol
                constructor?.containingClassId?.asSingleFqName()?.asString()
            }
            "$MEMBER_SCOPE.optional", "$MEMBER_SCOPE.processor" -> memberScopeOwner(call.dispatchReceiver)
            else -> null
        }
    }

    private fun findTargets(owner: String, name: String, signature: List<String>?, count: Int?): List<Target> {
        val matches = targets.filter {
            it.name == name && (signature == null || it.parameters == signature) &&
                (count == null || it.parameters.size == count)
        }
        val exact = matches.filter { it.owner == owner }
        // Check the callback against every matching framework overload rather than guessing which one was selected.
        if (exact.isNotEmpty()) return exact
        val inherited = matches.filter { it.owner in setOf(APPLICATION, ACTIVITY, SERVICE, PROVIDER, INSTRUMENTATION) && extends(owner, it.owner) }
        if (inherited.isEmpty()) return emptyList()
        // An app class can add unrelated overloads. Check its declarations when the selector does not give an exact signature.
        val candidates = if (signature != null) inherited else {
            val type = context.evaluator.findClass(owner) ?: return emptyList()
            val methods = type.findMethodsByName(name, false).filter { count == null || it.parameterList.parametersCount == count }
            methods.flatMap { method ->
                val parameters = method.parameterList.parameters.map { TypeConversionUtil.erasure(it.type).canonicalText }
                inherited.filter { it.parameters == parameters }.ifEmpty { return emptyList() }
            }
        }
        return candidates.map { it.copy(early = true) }
    }

    private fun extends(owner: String, base: String) = owner == base || context.evaluator.findClass(owner)?.let {
        context.evaluator.extendsClass(it, base, false)
    } == true

    private fun className(expression: KtExpression): String? {
        val origin = expression.origin(includeProperties = true)
        input(origin)?.className?.let { return it }
        if (origin is KtPostfixExpression && origin.operationToken == KtTokens.EXCLEXCL)
            return origin.baseExpression?.let(::className)
        if (origin is KtBinaryExpressionWithTypeRHS && origin.operationReference.text in setOf("as", "as?"))
            return className(origin.left)
        if (origin is KtClassLiteralExpression) {
            val receiver = origin.receiverExpression ?: return null
            return (receiver.lastName()?.targetSymbol() as? KaClassLikeSymbol)?.classId?.asSingleFqName()?.asString()?.javaName()
        }
        val property = (origin as? KtNameReferenceExpression)?.targetSymbol()?.psi as? KtProperty
        val delegate = property?.takeIf { !it.isVar && it.getter == null }?.delegateExpression
        if (delegate != null) return delegate.call()?.let { classFactory(it, delegated = true) }
        val access = origin.variable()
        if (origin is KtQualifiedExpression) {
            if (access?.symbol?.id() in setOf(KOTLIN_JAVA, KOTLIN_CLASS, KOTLIN_JAVA_OBJECT_TYPE, KOTLIN_JAVA_PRIMITIVE_TYPE)) {
                val owner = className(origin.receiverExpression)
                return if (owner == BOOLEAN && access?.symbol?.id() == KOTLIN_JAVA_OBJECT_TYPE) JAVA_BOOLEAN else owner
            }
            if (access?.symbol?.javaId() == "$JAVA_CLASS.getSuperclass")
                return className(origin.receiverExpression)?.let { context.evaluator.findClass(it)?.superClass?.qualifiedName }
        }
        return origin.call()?.let(::classFactory)
    }

    private fun classFactory(call: KtCallExpression, delegated: Boolean = false): String? {
        val resolved = call.function() ?: return null
        val id = resolved.symbol.id()
        val name = resolved.symbol.callableId?.callableName?.asString()
        val pkg = resolved.symbol.callableId?.packageName?.asString()
        if (delegated) {
            if (pkg !in setOf(KAVA_CLASS_EXTENSION, KAVA_EXTENSION) || name !in setOf("lazyClass", "lazyClassOrNull")) return null
            val argument = resolved.valueArgumentMapping.entries.singleOrNull { it.value.name.asString() in setOf("name", "variousClass") } ?: return null
            return if (argument.value.name.asString() == "name") (argument.key.constant() as? String)?.javaName() else variousClassName(argument.key)
        }
        if (id == "$KAVA_CLASS_EXTENSION.classOf") {
            val type = call.typeArguments.singleOrNull()?.typeReference ?: return null
            val owner = with(session) { type.type }.className() ?: return null
            return if (owner == KOTLIN_BOOLEAN && call.arguments()?.singleOrNull()?.constant() == false) JAVA_BOOLEAN else owner.javaName()
        }
        if (id == "$JAVA_CLASS.forName" || id == "$CLASS_LOADER.loadClass")
            return (call.arguments()?.firstOrNull()?.constant() as? String)?.javaName()
        if (pkg in setOf(KAVA_CLASS_EXTENSION, KAVA_EXTENSION) && name in setOf("toClass", "toClassOrNull")) {
            val receiver = resolved.extensionReceiver.expression() ?: return null
            return (receiver.constant() as? String)?.javaName() ?: variousClassName(receiver)
        }
        if (id in setOf("$VARIOUS_CLASS.load", "$VARIOUS_CLASS.loadOrNull"))
            return resolved.dispatchReceiver.expression()?.let(::variousClassName)
        return null
    }

    private fun variousClassName(expression: KtExpression): String? {
        val call = expression.origin().call() ?: return null
        val constructor = call.function()?.symbol as? KaConstructorSymbol ?: return null
        if (constructor.containingClassId?.asSingleFqName()?.asString() != VARIOUS_CLASS) return null
        return call.arguments()?.map { (it.constant() as? String)?.javaName() ?: return null }?.distinct()?.singleOrNull()
    }

    private fun String.javaName() = when (this) {
        KOTLIN_BOOLEAN -> BOOLEAN
        KOTLIN_STRING -> JAVA_STRING
        else -> replace('$', '.')
    }

    private fun contextAdvice(
        callback: KtLambdaExpression,
        source: Source,
        argument: Int = 0,
        phase: CallbackPhase = CallbackPhase.AFTER,
        parameterCount: Int = 0
    ): Advice? {
        val body = callback.bodyExpression ?: return null
        var proceedResult: KtProperty? = null
        val proceedCall = if (phase == CallbackPhase.CHAIN) {
            val first = body.statements.firstOrNull() ?: return null
            val call = if (first is KtProperty) {
                if (first.isVar || body.statements.lastOrNull()?.lastName()?.targetSymbol()?.psi != first) return null
                proceedResult = first
                first.initializer?.call()
            } else {
                if (body.statements.size != 1) return null
                val also = first.call()?.function() ?: return null
                if (also.symbol.id() != KOTLIN_ALSO) return null
                also.extensionReceiver.expression()?.call()
            } ?: return null
            val proceed = call.function() ?: return null
            if (proceed.symbol.id() != "$HOOK_CHAIN.proceed" || proceed.valueArgumentMapping.isNotEmpty() ||
                !proceed.dispatchReceiver.isReceiverOf(callback) || body.anyDescendantOfType<KtReturnExpression>()) return null
            call
        } else null

        fun isArgumentArray(expression: KtExpression): Boolean {
            val access = expression.origin().variable() ?: return false
            return access.symbol.id() in setOf("$HOOK_PARAM.args", "$HOOK_CHAIN.args") && access.dispatchReceiver.isReceiverOf(callback)
        }

        fun read(expression: KtExpression, argumentEditor: Boolean = false): Source? {
            val origin = expression.origin()
            val array = origin as? KtArrayAccessExpression
            if (array != null && array.indexExpressions.singleOrNull()?.constant() == argument) {
                if (array.arrayExpression?.let(::isArgumentArray) == true) return Source.ARGUMENT
            }
            val result = origin.lastName()?.targetSymbol()?.psi
            if (proceedResult != null && result == proceedResult) return Source.RESULT
            val access = origin.variable()
            if (access?.dispatchReceiver.isReceiverOf(callback)) when (access?.symbol?.id()) {
                "$INVOCATION.instance", "$INVOCATION.instanceOrNull" -> return Source.INSTANCE
                "$HOOK_PARAM.result" -> return Source.RESULT
            }
            val call = origin.call() ?: return null
            val resolved = call.function() ?: return null
            val id = resolved.symbol.id()
            val sequence = (resolved.extensionReceiver ?: resolved.dispatchReceiver).expression()
            if (sequence?.let(::isArgumentArray) == true &&
                resolved.symbol
                    .isCollectionCall(setOf("first", "firstOrNull", "last", "lastOrNull", "getOrNull", "elementAt", "elementAtOrNull"))
            ) {
                val index = when (resolved.symbol.callableId?.callableName?.asString()) {
                    "first", "firstOrNull" -> 0.takeIf { call.valueArguments.isEmpty() }
                    "last", "lastOrNull" -> (parameterCount - 1).takeIf { call.valueArguments.isEmpty() }
                    else -> call.arguments()?.singleOrNull()?.constant() as? Int
                }
                return Source.ARGUMENT.takeIf { index == argument }
            }
            if (id in setOf("$ARGUMENT_EDITOR.get", "$ARGUMENT_EDITOR.getOrNull"))
                return resolved.dispatchReceiver.expression()?.let { read(it, argumentEditor = true) }
            if (!resolved.dispatchReceiver.isReceiverOf(callback)) return null
            return when (id) {
                "$INVOCATION.instance", "$INVOCATION.instanceOrNull" -> Source.INSTANCE
                "$HOOK_PARAM.result" -> Source.RESULT
                "$HOOK_PARAM.firstArg", "$HOOK_CHAIN.firstArg", "$HOOK_CHAIN.firstArgOrNull" ->
                    Source.ARGUMENT.takeIf { argument == 0 && (id != "$HOOK_PARAM.firstArg" || argumentEditor) }
                "$HOOK_PARAM.lastArg", "$HOOK_CHAIN.lastArg", "$HOOK_CHAIN.lastArgOrNull" ->
                    Source.ARGUMENT.takeIf { argument == parameterCount - 1 && (id != "$HOOK_PARAM.lastArg" || argumentEditor) }
                "$HOOK_PARAM.arg", "$HOOK_CHAIN.arg", "$HOOK_CHAIN.argOrNull" ->
                    Source.ARGUMENT.takeIf { (id != "$HOOK_PARAM.arg" || argumentEditor) && call.arguments()?.singleOrNull()?.constant() == argument }
                else -> null
            }
        }

        if (body.anyDescendantOfType<KtBinaryExpression> {
                if (it.operationToken !in KtTokens.ALL_ASSIGNMENTS) return@anyDescendantOfType false
                val lhs = it.left ?: return@anyDescendantOfType false
                val affected = if (lhs is KtArrayAccessExpression) lhs.arrayExpression?.origin() else lhs
                affected?.variable()?.let { access ->
                    access.symbol.id() in setOf("$HOOK_PARAM.args", "$HOOK_PARAM.result", "$HOOK_PARAM.throwable") &&
                        access.dispatchReceiver.isReceiverOf(callback)
                } == true
            } || body.anyDescendantOfType<KtCallExpression> {
                val id = it.function()?.symbol?.id()
                id == "$ARGUMENT_EDITOR.set" || id in setOf(
                    "$HOOK_PARAM.throwToHost", "$HOOK_PARAM.throwToApp", "$HOOK_PARAM.callOriginal", "$HOOK_PARAM.invokeOriginal",
                    "$HOOK_PARAM.resultTrue", "$HOOK_PARAM.resultFalse", "$HOOK_PARAM.resultNull"
                ) ||
                    (phase == CallbackPhase.CHAIN && id in setOf("$HOOK_CHAIN.proceed", "$HOOK_CHAIN.proceedWith") && it != proceedCall)
            }) return null

        if (source == Source.GLOBAL && phase == CallbackPhase.BEFORE) return null
        if (source == Source.LIFECYCLE) return Advice.DIRECT.takeIf {
            body.anyDescendantOfType<KtThisExpression> { it.isThisOf(callback) } ||
                body.anyDescendantOfType<KtNameReferenceExpression> { it.variable()?.dispatchReceiver.isReceiverOf(callback) } ||
                body.anyDescendantOfType<KtCallExpression> { it.function()?.dispatchReceiver.isReceiverOf(callback) }
        }

        fun accepts(read: Source?) = when (source) {
            Source.INSTANCE -> read == Source.INSTANCE
            Source.ARGUMENT -> read == Source.ARGUMENT
            Source.ARGUMENT_OR_INSTANCE -> read == Source.ARGUMENT || read == Source.INSTANCE
            Source.RESULT -> (phase != CallbackPhase.BEFORE && read == Source.RESULT) || (argument != 0 && read == Source.ARGUMENT)
            else -> false
        }

        fun readId(symbol: KaCallableSymbol?) = (symbol as? KaSyntheticJavaPropertySymbol)?.javaGetterSymbol?.id() ?: symbol?.id()

        // Access to invocation state is a reason to keep the hook, except when it yields the Application itself.
        if (body.anyDescendantOfType<KtNameReferenceExpression> {
                val access = it.variable() ?: return@anyDescendantOfType false
                access.dispatchReceiver.isReceiverOf(callback) && when (access.symbol.id()) {
                    "$HOOK_PARAM.throwable", "$HOOK_PARAM.hasThrowable" -> true
                    "$HOOK_PARAM.result" -> source != Source.RESULT
                    else -> false
                }
            }) return null

        fun componentRead(expression: KtExpression): Boolean {
            val origin = expression.origin()
            if (origin is KtPostfixExpression && origin.operationToken == KtTokens.EXCLEXCL)
                return origin.baseExpression?.let(::componentRead) == true
            if (origin is KtBinaryExpressionWithTypeRHS) return componentRead(origin.left)
            val letReceiver = origin.letReceiver()
            if (letReceiver != null && body.textRange.contains(letReceiver.textRange)) return componentRead(letReceiver)
            return read(origin) == if (source == Source.COMPONENT) Source.INSTANCE else Source.ARGUMENT
        }

        // An explicit Application type shows intent, but does not turn a component into its application.
        fun readsAsApplication(expression: KtExpression): Boolean {
            val origin = expression.origin()
            val type = if (origin is KtBinaryExpressionWithTypeRHS) origin.right else origin.call()?.typeArguments?.singleOrNull()?.typeReference
            val owner = with(session) { type?.type }.className() ?: return false
            return extends(owner, APPLICATION) && componentRead(origin)
        }

        if (source.isComponent && (body.anyDescendantOfType<KtQualifiedExpression> {
                if (!componentRead(it.receiverExpression) || readsAsApplication(it.receiverExpression)) return@anyDescendantOfType false
                val selector = it.selectorExpression
                val call = selector?.call()
                if (call?.letBlock() != null) return@anyDescendantOfType false
                val id = readId(call?.function()?.symbol ?: selector?.variable()?.symbol)
                id !in applicationContextReads && (source != Source.COMPONENT || id != "$PROVIDER.getContext")
            } || body.anyDescendantOfType<KtCallExpression> { call ->
                call.arguments()?.any { componentRead(it) && !readsAsApplication(it) } == true
            })) return null

        fun providerContext(expression: KtExpression): Boolean {
            val origin = expression.origin()
            val access = origin.variable()
            val call = origin.call()?.function()
            if (readId(access?.symbol ?: call?.symbol) != "$PROVIDER.getContext") return false
            val receiver = (access?.dispatchReceiver ?: call?.dispatchReceiver).expression() ?: return false
            return componentRead(receiver)
        }

        val readsContext = body.anyDescendantOfType<KtExpression> { expression ->
            val access = (expression as? KtNameReferenceExpression)?.takeUnless { it.parent is KtCallExpression }?.variable()
            val call = (expression as? KtCallExpression)?.function()
            val id = readId(access?.symbol ?: call?.symbol)
            if (source == Source.WRAPPER) {
                val (value, type) = when (expression) {
                    is KtBinaryExpressionWithTypeRHS -> expression.left to expression.right
                    is KtIsExpression -> expression.leftHandSide to expression.typeReference
                    else -> return@anyDescendantOfType false
                }
                val owner = with(session) { type?.type }.className() ?: return@anyDescendantOfType false
                return@anyDescendantOfType extends(owner, APPLICATION) && read(value) == Source.INSTANCE
            }
            if (id in globalApplicationReads || id == "$PACKAGE_PARAM.hostApplication") return@anyDescendantOfType true
            if (id == "$JAVA_METHOD.invoke" && expression is KtCallExpression && expression.arguments()?.size == 1) {
                val lookup = call?.dispatchReceiver.expression()?.origin()?.call()?.let { methodLookup(it) }
                if (lookup != null && lookup.parameters.isEmpty() && "${lookup.owner}.${lookup.name}" in globalApplicationReads)
                    return@anyDescendantOfType true
            }
            if (source.isComponent || source == Source.GLOBAL) {
                if (id !in applicationContextReads) return@anyDescendantOfType false
                if (source == Source.GLOBAL) return@anyDescendantOfType id?.startsWith("$ACTIVITY_THREAD.") == true
                val receiver = (access?.dispatchReceiver ?: call?.dispatchReceiver).expression() ?: return@anyDescendantOfType false
                return@anyDescendantOfType componentRead(receiver) ||
                    (source == Source.COMPONENT && providerContext(receiver))
            }
            accepts(read(expression))
        }
        if (readsContext) return if (source.isComponent) Advice.TIMING_DEPENDENT else Advice.DIRECT
        if (!source.isComponent) return null

        return Advice.COMPONENT_AS_APPLICATION.takeIf { body.anyDescendantOfType<KtExpression> { readsAsApplication(it) } }
    }

    private fun KtExpression.origin(includeProperties: Boolean = false): KtExpression {
        var expression = KtPsiUtil.safeDeparenthesize(this)
        val seen = mutableSetOf<KtProperty>()
        while (expression is KtNameReferenceExpression) {
            val property = expression.targetSymbol()?.psi as? KtProperty ?: break
            if (property.isVar || property.getter != null || !seen.add(property)) break
            if (!property.isLocal && (!includeProperties || property.containingFile != containingFile ||
                    property.hasModifier(KtTokens.OPEN_KEYWORD) || property.hasModifier(KtTokens.OVERRIDE_KEYWORD))) break
            expression = property.initializer?.let(KtPsiUtil::safeDeparenthesize) ?: break
        }
        return expression
    }

    private fun KtLambdaExpression.enclosingCall() = parents.filterIsInstance<KtCallExpression>().firstOrNull()?.takeIf {
        it.arguments()?.any { argument -> KtPsiUtil.safeDeparenthesize(argument) == this } == true
    }

    private fun KtCallExpression.letBlock() = function()?.takeIf { it.symbol.id() == KOTLIN_LET }
        ?.valueArgumentMapping?.keys?.singleOrNull()?.let(KtPsiUtil::safeDeparenthesize) as? KtLambdaExpression

    private fun KtExpression.letReceiver(): KtExpression? {
        val symbol = (this as? KtNameReferenceExpression)?.targetSymbol() as? KaValueParameterSymbol ?: return null
        val lambda = with(session) { symbol.containingSymbol }?.psi?.parent as? KtLambdaExpression ?: return null
        val call = lambda.enclosingCall() ?: return null
        if (call.letBlock() != lambda) return null
        return call.function()?.extensionReceiver.expression()
    }

    private fun KtExpression.isParameterOf(lambda: KtLambdaExpression): Boolean {
        val symbol = (origin() as? KtNameReferenceExpression)?.targetSymbol() as? KaValueParameterSymbol ?: return false
        return with(session) { symbol.containingSymbol }?.psi == lambda.functionLiteral
    }

    private fun KtThisExpression.isThisOf(lambda: KtLambdaExpression) =
        instanceReference.targetSymbol().receiverDeclaration() == lambda.functionLiteral

    private fun KaReceiverValue?.isReceiverOf(lambda: KtLambdaExpression) = when (this) {
        is KaExplicitReceiverValue -> (expression.origin() as? KtThisExpression)?.isThisOf(lambda) == true
        else -> lambda() == lambda
    }

    private fun KaReceiverValue?.lambda(): KtLambdaExpression? = when (this) {
        is KaImplicitReceiverValue -> symbol.receiverDeclaration()?.parent as? KtLambdaExpression
        is KaSmartCastedReceiverValue -> original.lambda()
        else -> null
    }

    private fun KaReceiverValue?.expression(): KtExpression? = when (this) {
        is KaExplicitReceiverValue -> expression
        is KaSmartCastedReceiverValue -> original.expression()
        else -> null
    }

    private fun KaSymbol?.receiverDeclaration() =
        (if (this is KaReceiverParameterSymbol) owningCallableSymbol else this)?.psi

    private fun KtReferenceExpression.targetSymbol() = with(session) {
        references.filterIsInstance<KtReference>().firstNotNullOfOrNull { it.resolveToSymbol() }
    }

    private fun KtCallExpression.function() = with(session) { resolveToCall()?.singleFunctionCallOrNull() }

    private fun KtExpression.variable() = with(session) { lastName()?.resolveToCall()?.singleVariableAccessCall() }

    private fun KtExpression.constant(): Any? {
        val source = origin()
        return input(source)?.constant ?: source.toUElementOfType<UExpression>()?.let {
            ConstantEvaluator.evaluate(this@LifecycleHookMatcher.context, it)
        }
    }

    private fun KtExpression.lastName() = (if (this is KtQualifiedExpression) selectorExpression else this) as? KtNameReferenceExpression

    private fun KtExpression.call() = (if (this is KtQualifiedExpression) selectorExpression else this) as? KtCallExpression

    private fun KtCallExpression.arguments(expandArrays: Boolean = false): List<KtExpression>? = valueArguments.flatMap { argument ->
        val expression = argument.getArgumentExpression() ?: return null
        if (argument.getSpreadElement() == null) listOf(expression) else {
            if (!expandArrays) return null
            val array = expression.origin().call() ?: return null
            if (array.function()?.symbol?.id() != KOTLIN_ARRAY) return null
            array.arguments() ?: return null
        }
    }

    private fun KaType?.className() = (with(session) { this@className?.fullyExpandedType } as? KaClassType)?.classId?.asSingleFqName()?.asString()

    private fun KaCallableSymbol.id() = callableId?.asSingleFqName()?.asString()

    private fun KaCallableSymbol.isCollectionCall(names: Set<String>) = callableId?.let {
        it.packageName.asString() in setOf(COLLECTIONS, SEQUENCES) && it.callableName.asString() in names
    } == true

    private fun KaCallableSymbol.isHook() = callableId?.let {
        it.callableName.asString() in hookNames && (it.classId?.asSingleFqName()?.asString() == PACKAGE_PARAM ||
            (it.classId == null && it.packageName.asString() == KAVA_EXTENSION))
    } == true
}