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
 * This file is created by fankes on 2026/9/17.
 */
package com.highcapable.yukihookapi.core.lint.detector

import com.android.SdkConstants.TAG_APPLICATION
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.resolveManifestName
import com.android.utils.XmlUtils
import com.highcapable.yukihookapi.core.lint.DeclaredSymbol
import com.highcapable.yukihookapi.core.lint.detector.extension.analyzeOrNull
import com.highcapable.yukihookapi.core.lint.detector.extension.buildReplaceFix
import com.highcapable.yukihookapi.core.lint.detector.extension.createExternalOnlyUastHandler
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionWithLabel
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.skipParenthesizedExprDown

class ModuleUsageDetector : Detector(), Detector.UastScanner {

    companion object {

        private const val APPLICATION = "android.app.Application"
        private const val MODULE_APPLICATION = "${DeclaredSymbol.CORE_PACKAGE_NAME}.hook.xposed.application.ModuleApplication"
        private const val HOOK_INVOCATION = "${DeclaredSymbol.CORE_PACKAGE_NAME}.hook.param.HookInvocation"
        private const val HOOK_PARAM = "${DeclaredSymbol.CORE_PACKAGE_NAME}.hook.param.HookParam"
        private const val ARGUMENT_EDITOR = "$HOOK_PARAM.ArgumentEditor"
        private const val PACKAGE_PARAM = "${DeclaredSymbol.CORE_PACKAGE_NAME}.hook.param.PackageParam"
        private const val KAVAREF_EXTENSION_PACKAGE = "${DeclaredSymbol.CORE_PACKAGE_NAME}.extension.kavaref"

        private const val INSTANCE_GETTER = "getInstance"
        private const val INSTANCE_OR_NULL_GETTER = "getInstanceOrNull"
        private const val MEMBER_GETTER = "getMember"
        private const val RESULT_GETTER = "getResult"

        private const val METHOD_CLASS = "java.lang.reflect.Method"
        private const val CONSTRUCTOR_CLASS = "java.lang.reflect.Constructor"
        private const val EXECUTABLE_CLASS = "java.lang.reflect.Executable"
        private const val STAR_CONSTRUCTOR_TYPE = "$CONSTRUCTOR_CLASS<?>"

        private const val HOOK_PARAM_ARG = "$HOOK_PARAM.arg"
        private const val HOOK_PARAM_ARGS = "$HOOK_PARAM.args"
        private const val HOOK_INVOCATION_METHOD = "$HOOK_INVOCATION.method"
        private const val HOOK_INVOCATION_CONSTRUCTOR = "$HOOK_INVOCATION.constructor"
        private const val METHOD_PARAMETER_TYPES_GETTER = "$METHOD_CLASS.getParameterTypes"
        private const val CONSTRUCTOR_PARAMETER_TYPES_GETTER = "$CONSTRUCTOR_CLASS.getParameterTypes"
        private const val EXECUTABLE_PARAMETER_TYPES_GETTER = "$EXECUTABLE_CLASS.getParameterTypes"
        private const val KOTLIN_ARRAY_SIZE = "kotlin.Array.size"
        private const val KOTLIN_LAST_INDEX = "kotlin.collections.lastIndex"
        private const val KOTLIN_APPLY = "kotlin.apply"

        val USE_MODULE_APPLICATION_ISSUE = Issue.create(
            id = "UseModuleApplication",
            briefDescription = "Inherit ModuleApplication to enable full functionality",
            explanation = """
                YukiHook module apps should make their `Application` inherit `ModuleApplication` to initialize \
                the features of the base module and enable full functionality.

                See the documentation for more details:
                - English: https://highcapable.github.io/YukiHook/en/library/yukihook-core
                - 简体中文: https://highcapable.github.io/YukiHook/zh-cn/library/yukihook-core

                The `ModuleApplication` provides:
                - Initialization of the features of the base module in the module process
                - Globally available `ModuleApplication.current` and `ModuleApplication.requireContext()`

                Examples:
                ```kotlin
                // Before
                class YourApplication : Application()

                // After
                class YourApplication : ModuleApplication()
                ```
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ModuleUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val USE_HOOK_INVOCATION_API_ISSUE = Issue.create(
            id = "UseHookInvocationApi",
            briefDescription = "Use hook invocation APIs for casts and argument access",
            explanation = """
                Casting the instance, member, or result in a hook callback can be simplified by using the APIs \
                provided by `HookInvocation` and `HookParam`.

                See the documentation for more details:
                - English: https://highcapable.github.io/YukiHook/en/library/yukihook-core
                - 简体中文: https://highcapable.github.io/YukiHook/zh-cn/library/yukihook-core

                The `HookInvocation` provides:
                - `instance<T>()` to get the instance as `T`
                - `instanceOrNull<T>()` to get the instance as `T`, or null when it is absent or has a different type
                - `method` to get the hooked method
                - `constructor` to get the hooked constructor

                The `HookParam` provides:
                - `result<T>()` to get the result as `T`, or null when it is absent or has a different type
                - `arg(index).get<T>()` to get an argument as `T`, or null when the argument is null or the index is out of bounds
                - `arg(index).getOrNull<T>()` to get an argument as `T`, also returning null when the argument has a different type
                - `arg(index).set(value)` to set an argument
                - `firstArg()` and `lastArg()` to get the editor for the first or last argument

                A replacement for a cast to a non-null type uses `!!` when the replacement API returns a nullable value.

                Examples:
                ```kotlin
                // Before
                instance as Activity
                instanceOrNull as Activity
                instance as? Activity
                instanceOrNull as? Activity
                instanceOrNull as Activity?
                member as Method
                member as Constructor<*>
                result as? String
                args[1] as String?
                args[1] as? String
                args[1] = "value"
                arg(0)
                arg(method.parameterTypes.size - 1)
                firstArg().get() as String?
                arg(1).get() as? String
                lastArg().getOrNull<CharSequence>() as? String

                // After
                instance<Activity>()
                instance<Activity>()
                instanceOrNull<Activity>()
                instanceOrNull<Activity>()
                instanceOrNull<Activity>()
                method
                constructor
                result<String>()
                arg(1).get<String>()
                arg(1).getOrNull<String>()
                arg(1).set("value")
                firstArg()
                lastArg()
                firstArg().get<String>()
                arg(1).getOrNull<String>()
                lastArg().getOrNull<String>()
                ```
            """.trimIndent(),
            category = Category.PRODUCTIVITY,
            priority = 5,
            severity = Severity.INFORMATIONAL,
            implementation = Implementation(
                ModuleUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val USE_HOOK_BLOCK_ISSUE = Issue.create(
            id = "UseHookBlock",
            briefDescription = "Use a hook configuration block instead of apply",
            explanation = """
                Use `hook { ... }` or `hookAll { ... }` to configure callbacks before registering the hook.
                The overload with a configuration block returns a handle, while `apply` returns the hooker.

                See the documentation for more details:
                - English: https://highcapable.github.io/YukiHook/en/library/yukihook-core
                - 简体中文: https://highcapable.github.io/YukiHook/zh-cn/library/yukihook-core

                Examples:
                ```kotlin
                // Before
                member.hook().apply { before { /* ... */ } }
                members.hookAll().apply { after { /* ... */ } }

                // After
                member.hook { before { /* ... */ } }
                members.hookAll { after { /* ... */ } }
                ```
            """.trimIndent(),
            category = Category.PRODUCTIVITY,
            priority = 5,
            severity = Severity.INFORMATIONAL,
            implementation = Implementation(
                ModuleUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    private class ArgumentEditorCall(
        val callPrefix: String,
        val labelPrefix: String,
        val isShadowed: Boolean,
        val keptExpressions: List<PsiElement>
    )

    override fun getApplicableUastTypes() = listOf(
        UClass::class.java,
        UBinaryExpression::class.java,
        UBinaryExpressionWithType::class.java,
        UCallExpression::class.java
    )

    override fun createUastHandler(context: JavaContext) = context.createExternalOnlyUastHandler(object : UElementHandler() {

        private val applicationName = context.findManifestApplicationName()

        override fun visitClass(node: UClass) {
            if (node.qualifiedName != applicationName) return
            if (!context.evaluator.extendsClass(node.javaPsi, APPLICATION, false)) return
            if (context.evaluator.extendsClass(node.javaPsi, MODULE_APPLICATION, false)) return

            context.report(
                issue = USE_MODULE_APPLICATION_ISSUE,
                scope = node as UElement,
                location = context.getNameLocation(node),
                message = "`${node.name}` should inherit `ModuleApplication` to enable full YukiHook functionality"
            )
        }

        override fun visitCallExpression(node: UCallExpression) {
            val call = node.sourcePsi as? KtCallExpression ?: return
            when (call.calleeExpression?.text) {
                "apply" -> checkHookApply(node, call)
                "arg" -> checkArgumentPosition(node, call)
            }
        }

        private fun checkHookApply(node: UCallExpression, applyCall: KtCallExpression) {
            val expression = applyCall.parent as? KtQualifiedExpression ?: return
            if (expression.selectorExpression != applyCall) return
            val receiver = KtPsiUtil.safeDeparenthesize(expression.receiverExpression)
            val hookCall = (if (receiver is KtQualifiedExpression) receiver.selectorExpression else receiver) as? KtCallExpression ?: return
            val hookName = applyCall.analyzeOrNull {
                if (applyCall.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName()?.asString() != KOTLIN_APPLY)
                    return@analyzeOrNull null
                hookCall.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.hookBlockName()
            } ?: return

            val location = context.getLocation(expression)
            context.report(
                issue = USE_HOOK_BLOCK_ISSUE,
                scope = node as UElement,
                location = context.getNameLocation(applyCall),
                message = "Use `$hookName { ... }` to configure callbacks before registering the hook",
                quickfixData = expression.hookBlockReplacement(applyCall, hookCall, receiver)?.let {
                    buildReplaceFix(name = "Use '$hookName' block", replacement = it, range = location, reformat = false)
                }
            )
        }

        private fun checkArgumentPosition(node: UCallExpression, source: KtCallExpression) {
            val index = source.valueArguments.singleOrNull()?.getArgumentExpression() ?: return
            val replacementName = source.analyzeOrNull {
                val call = source.resolveToCall()?.singleFunctionCallOrNull() ?: return@analyzeOrNull null
                if (call.symbol.callableId?.asSingleFqName()?.asString() != HOOK_PARAM_ARG) return@analyzeOrNull null
                when {
                    index.evaluate()?.value == 0 -> "firstArg"
                    isLastArgumentIndex(index, call.dispatchReceiver) -> "lastArg"
                    else -> null
                }
            } ?: return
            val hasReceiver = (source.parent as? KtQualifiedExpression)?.selectorExpression == source
            if (!hasReceiver && source.hasLocalDeclaration(replacementName, includeFunctions = true)) return
            if (source.anyDescendantOfType<PsiComment>()) return
            reportInvocationUsage(node, source, source, replacementName, "$replacementName()")
        }

        override fun visitBinaryExpression(node: UBinaryExpression) {
            val source = node.sourcePsi as? KtBinaryExpression ?: return
            if (source.operationToken != KtTokens.EQ) return
            val access = source.left?.let(KtPsiUtil::safeDeparenthesize) as? KtArrayAccessExpression ?: return
            val value = source.right ?: return
            val editor = access.argumentEditor() ?: return
            if (editor.isShadowed || source.losesComments(editor.keptExpressions + value)) return
            reportInvocationUsage(node, source, source, "${editor.labelPrefix}set", "${editor.callPrefix}set(${value.text})")
        }

        override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType) {
            val source = node.sourcePsi as? KtBinaryExpressionWithTypeRHS ?: return
            val typeReference = source.right ?: return
            val operand = KtPsiUtil.safeDeparenthesize(source.left)
            val isSafeCast = source.operationReference.getReferencedNameElementType() == KtTokens.AS_SAFE
            val editor = if (operand is KtArrayAccessExpression) operand.argumentEditor()
            else operand.argumentEditorRead(typeReference, isSafeCast)
            if (editor != null) {
                if (isSafeCast && typeReference.analyzeOrNull { canReify(typeReference.type) } != true) return
                val name = if (isSafeCast) "getOrNull" else "get"
                val type = (typeReference.typeElement as? KtNullableType)?.innerType ?: typeReference
                val target = source.outermostParenthesizedExpression()
                val call = "${editor.callPrefix}$name<${type.text}>()"
                val replacement = if (isSafeCast) call else typeReference.preserveCastType(call)
                if (editor.isShadowed || target.losesComments(editor.keptExpressions + type)) return
                reportInvocationUsage(node, source, target, "${editor.labelPrefix}$name", replacement)
                return
            }
            val getter = (node.operand.skipParenthesizedExprDown() as? UResolvable)?.resolve() as? PsiMethod ?: return
            val isResult = getter.name == RESULT_GETTER
            val declaringClass = if (isResult) HOOK_PARAM else HOOK_INVOCATION
            if (getter.containingClass?.qualifiedName != declaringClass) return

            val castType = node.type.canonicalText
            val propertyName = getter.name.removePrefix("get").replaceFirstChar { it.lowercase() }
            val selector = operand.selector()
            val nullableType = typeReference.typeElement as? KtNullableType

            // Casting the instance to Any is already redundant.
            if (!isResult && getter.name != MEMBER_GETTER && castType == CommonClassNames.JAVA_LANG_OBJECT) return
            val replacementName = when (getter.name) {
                INSTANCE_GETTER if !isSafeCast -> "instance"
                // A non-null cast already throws when the instance is absent, the same as instance<T>().
                INSTANCE_OR_NULL_GETTER if !isSafeCast && nullableType == null -> "instance"
                INSTANCE_GETTER, INSTANCE_OR_NULL_GETTER -> "instanceOrNull"
                MEMBER_GETTER if isSafeCast -> return
                MEMBER_GETTER if castType == METHOD_CLASS -> "method"
                MEMBER_GETTER if castType == STAR_CONSTRUCTOR_TYPE -> "constructor"
                RESULT_GETTER if selector is KtNameReferenceExpression &&
                    typeReference.analyzeOrNull { canReify(typeReference.type) } == true -> "result"
                else -> return
            }
            val replacementType = when {
                getter.name == MEMBER_GETTER -> null
                isResult -> nullableType?.innerType ?: typeReference
                else -> typeReference
            }
            val replacement = if (replacementType == null) replacementName else "$replacementName<${replacementType.text}>()"

            // Keep the explicit receiver, e.g. "param.instance as T" becomes "param.instance<T>()".
            val receiverPrefix = operand.receiverPrefix()
            val call = receiverPrefix + replacement
            val fixedText = if (isResult && !isSafeCast) typeReference.preserveCastType(call) else call
            if (receiverPrefix.isEmpty() && (isResult || replacementName != propertyName) &&
                source.hasLocalDeclaration(replacementName, includeFunctions = isResult)) return
            val replaceTarget = source.outermostParenthesizedExpression()
            if (replaceTarget.losesComments(listOfNotNull(operand, replacementType))) return
            reportInvocationUsage(node, source, replaceTarget, replacementName, fixedText)
        }

        private fun reportInvocationUsage(
            node: UElement,
            source: KtExpression,
            target: KtExpression,
            name: String,
            replacement: String
        ) {
            context.report(
                issue = USE_HOOK_INVOCATION_API_ISSUE,
                scope = node,
                location = context.getLocation(source),
                message = "Can be replaced with `$replacement`",
                quickfixData = buildReplaceFix(
                    name = "Replace with '$name'",
                    replacement = replacement,
                    range = context.getLocation(target),
                    reformat = false
                )
            )
        }
    })

    private fun KtArrayAccessExpression.argumentEditor(): ArgumentEditorCall? {
        val array = arrayExpression?.let(KtPsiUtil::safeDeparenthesize) ?: return null
        if ((array.selector() as? KtNameReferenceExpression)?.getReferencedName() != "args") return null
        val index = indexExpressions.singleOrNull() ?: return null
        val name = array.analyzeOrNull {
            val property = propertyAccess(array) ?: return@analyzeOrNull null
            if (property.symbol.callableId?.asSingleFqName()?.asString() != HOOK_PARAM_ARGS) return@analyzeOrNull null
            when {
                index.evaluate()?.value == 0 -> "firstArg"
                isLastArgumentIndex(index, property.dispatchReceiver) -> "lastArg"
                else -> "arg"
            }
        } ?: return null
        val prefix = array.receiverPrefix()
        val argument = index.takeIf { name == "arg" }
        return ArgumentEditorCall(
            "$prefix$name(${argument?.text.orEmpty()}).",
            argumentEditorLabelPrefix(name),
            prefix.isEmpty() && hasLocalDeclaration(name, includeFunctions = true),
            listOfNotNull(array, argument)
        )
    }

    private fun KtExpression.argumentEditorRead(typeReference: KtTypeReference, isSafeCast: Boolean): ArgumentEditorCall? {
        val reader = selector() as? KtCallExpression ?: return null
        val name = (reader.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
        if (name != "get" && name != "getOrNull") return null
        if (reader.valueArguments.isNotEmpty()) return null

        val canFold = reader.analyzeOrNull {
            val call = reader.resolveToCall()?.singleFunctionCallOrNull() ?: return@analyzeOrNull false
            if (call.symbol.callableId?.classId?.asSingleFqName()?.asString() != ARGUMENT_EDITOR) return@analyzeOrNull false
            val originalType = call.typeArgumentsMapping.values.singleOrNull()?.fullyExpandedType
            // A safe cast can absorb a wider safe read. A throwing read or cast must keep its own type check.
            when {
                originalType == null -> name == "get" && reader.typeArguments.isEmpty()
                originalType.isAnyType -> true
                name == "getOrNull" && isSafeCast -> typeReference.type.withNullability(true).isSubtypeOf(originalType.withNullability(true))
                else -> false
            }
        }
        if (canFold != true) return null

        val receiver = (this as? KtQualifiedExpression)?.receiverExpression
        val receiverCall = (receiver?.let(KtPsiUtil::safeDeparenthesize)?.selector() as? KtCallExpression)?.calleeExpression?.text
        val replacementName = if (isSafeCast) "getOrNull" else "get"
        return ArgumentEditorCall(
            receiverPrefix(),
            argumentEditorLabelPrefix(receiverCall),
            receiver == null && hasLocalDeclaration(replacementName, includeFunctions = true),
            listOfNotNull(receiver)
        )
    }

    private fun KtTypeReference.preserveCastType(call: String) = if (typeElement is KtNullableType) call else analyzeOrNull {
        val target = type.fullyExpandedType
        when {
            target.isMarkedNullable -> call
            // An unconstrained type parameter can stand for a nullable type.
            target.isNullable -> "($call as $text)"
            else -> "$call!!"
        }
    } ?: "$call!!"

    private fun argumentEditorLabelPrefix(name: String?) = when (name) {
        "arg", "args" -> "$name(index)."
        "firstArg", "lastArg" -> "$name()."
        else -> ""
    }

    private fun KtExpression.selector() = if (this is KtQualifiedExpression) selectorExpression else this

    private fun KtExpression.receiverPrefix() = (this as? KtQualifiedExpression)?.selectorExpression?.let {
        text.substring(0, it.textRange.startOffset - textRange.startOffset)
    }.orEmpty()

    private fun KtExpression.outermostParenthesizedExpression() =
        generateSequence(this) { it.parent as? KtParenthesizedExpression }.last()

    private fun KtExpression.losesComments(keptExpressions: List<PsiElement>) = anyDescendantOfType<PsiComment> { comment ->
        keptExpressions.none { it.textRange.contains(comment.textRange) }
    }

    private fun KaSession.propertyAccess(expression: KtExpression) = expression.selector()?.resolveToCall()?.singleVariableAccessCall()

    private fun KaSession.isLastArgumentIndex(index: KtExpression, receiver: KaReceiverValue?): Boolean {
        val expression = KtPsiUtil.safeDeparenthesize(localInitializer(KtPsiUtil.safeDeparenthesize(index)) ?: index)
        val isSizeMinusOne = expression is KtBinaryExpression && expression.operationToken == KtTokens.MINUS
        val bound = if (isSizeMinusOne) {
            if (expression.right?.evaluate()?.value != 1) return false
            expression.left?.let(KtPsiUtil::safeDeparenthesize)
        } else expression
        val access = bound as? KtQualifiedExpression ?: return false
        val extent = propertyAccess(access) ?: return false
        val expected = if (isSizeMinusOne) KOTLIN_ARRAY_SIZE else KOTLIN_LAST_INDEX
        if (extent.symbol.callableId?.asSingleFqName()?.asString() != expected) return false

        val array = KtPsiUtil.safeDeparenthesize(access.receiverExpression)
        val origin = propertyAccess(localInitializer(array) ?: array) ?: return false
        val invocationAccess = if (origin.symbol.callableId?.asSingleFqName()?.asString() == HOOK_PARAM_ARGS) origin
        else {
            val getter = (origin.symbol as? KaSyntheticJavaPropertySymbol)?.javaGetterSymbol?.callableId?.asSingleFqName()?.asString()
            if (getter != METHOD_PARAMETER_TYPES_GETTER && getter != CONSTRUCTOR_PARAMETER_TYPES_GETTER &&
                getter != EXECUTABLE_PARAMETER_TYPES_GETTER) return false
            val executable = (origin.dispatchReceiver as? KaExplicitReceiverValue)?.expression ?: return false
            val member = propertyAccess(localInitializer(executable) ?: executable) ?: return false
            val id = member.symbol.callableId?.asSingleFqName()?.asString()
            if (id != HOOK_INVOCATION_METHOD && id != HOOK_INVOCATION_CONSTRUCTOR) return false
            member
        }
        val invocation = receiver?.let { stableSymbol(it) } ?: return false
        return invocationAccess.dispatchReceiver?.let { stableSymbol(it) } == invocation
    }

    private fun KaSession.localInitializer(expression: KtExpression): KtExpression? {
        if (expression !is KtNameReferenceExpression) return null
        val variable = propertyAccess(expression)?.symbol as? KaLocalVariableSymbol ?: return null
        return (variable.psi as? KtProperty)?.initializer?.takeIf { variable.isVal }
    }

    private tailrec fun KaSession.stableSymbol(receiver: KaReceiverValue): KaSymbol? = when (receiver) {
        is KaImplicitReceiverValue -> receiver.symbol
        is KaSmartCastedReceiverValue -> stableSymbol(receiver.original)
        is KaExplicitReceiverValue -> when (val expression = KtPsiUtil.safeDeparenthesize(receiver.expression)) {
            is KtNameReferenceExpression -> propertyAccess(expression)?.symbol?.takeIf {
                it is KaValueParameterSymbol || (it is KaLocalVariableSymbol && it.isVal)
            }
            is KtThisExpression -> expression.instanceReference.references.filterIsInstance<KtReference>()
                .firstNotNullOfOrNull { it.resolveToSymbol() }
            else -> null
        }
    }

    private fun KaFunctionSymbol.hookBlockName(): String? {
        val id = callableId ?: return null
        val name = id.callableName.asString()
        if (name != "hook" && name != "hookAll") return null
        if (id.classId?.asSingleFqName()?.asString() != PACKAGE_PARAM &&
            !(id.classId == null && id.packageName.asString() == KAVAREF_EXTENSION_PACKAGE)) return null

        return name.takeIf { valueParameters.size == 1 }
    }

    private fun KtQualifiedExpression.hookBlockReplacement(
        applyCall: KtCallExpression,
        hookCall: KtCallExpression,
        receiver: KtExpression
    ): String? {
        val lambda = applyCall.lambdaArguments.singleOrNull() ?: return null
        val arguments = hookCall.valueArgumentList ?: return null
        if (applyCall.typeArgumentList != null || applyCall.valueArguments.size != 1) return null
        val canReplace = analyzeOrNull {
            // The block overload returns a handle. Dot apply can also run on a null receiver.
            !isUsedAsExpression && (this@hookBlockReplacement is KtSafeQualifiedExpression ||
                receiver.expressionType?.fullyExpandedType?.isMarkedNullable == false)
        }
        if (canReplace != true) return null

        val hookLabel = hookCall.calleeExpression?.text
        // Kotlin's inline apply supports control flow and suspend calls that the hook block cannot carry.
        if (lambda.anyDescendantOfType<KtExpressionWithLabel> {
                val label = it.getLabelName()
                label == "apply" || (label != null && label == hookLabel) || it is KtBreakExpression || it is KtContinueExpression
            }) return null
        if (lambda.analyzeOrNull {
                lambda.anyDescendantOfType<KtReturnExpression> {
                    val target = it.getTargetLabel()?.references?.firstNotNullOfOrNull { reference -> reference.resolve() }
                    target == null || !lambda.textRange.contains(target.textRange)
                } || lambda.anyDescendantOfType<KtCallExpression> {
                    val function = it.resolveToCall()?.singleFunctionCallOrNull()?.symbol as? KaNamedFunctionSymbol
                    function?.isSuspend == true
                }
            } != false) return null

        if (anyDescendantOfType<PsiComment> {
                !receiver.textRange.contains(it.textRange) && !lambda.textRange.contains(it.textRange)
            }) return null

        val hookText = if (arguments.text == "()") receiver.text.dropLast(2) else receiver.text
        return "$hookText ${lambda.text}"
    }

    private fun KaSession.canReify(type: KaType): Boolean = with(type.fullyExpandedType) {
        when {
            this is KaErrorType || this is KaDefinitelyNotNullType || isNothingType -> false
            this is KaTypeParameterType -> symbol.isReified
            this is KaClassType && classId == StandardClassIds.Array -> typeArguments.single().type?.let { canReify(it) } ?: true
            else -> true
        }
    }

    private fun KtElement.hasLocalDeclaration(name: String, includeFunctions: Boolean) =
        generateSequence<PsiElement>(this) { it.parent }.zipWithNext().any { (child, parent) ->
            when (parent) {
                // A local declaration is only visible after itself, not in its own initializer.
                is KtBlockExpression -> parent.statements.takeWhile { it != child }.any { statement ->
                    (statement is KtProperty && statement.name == name) ||
                        (statement is KtDestructuringDeclaration && statement.entries.any { it.name == name }) ||
                        (includeFunctions && statement is KtNamedFunction && statement.name == name)
                }
                is KtFunction -> parent.valueParameters.any { it.name == name }
                is KtForExpression -> parent.loopParameter?.name == name ||
                    parent.destructuringDeclaration?.entries?.any { it.name == name } == true
                is KtCatchClause -> parent.catchParameter?.name == name
                else -> false
            }
        }

    private fun JavaContext.findManifestApplicationName(): String? {
        val manifest = project.mergedManifest?.documentElement ?: return null
        val application = XmlUtils.getFirstSubTagByName(manifest, TAG_APPLICATION) ?: return null

        return resolveManifestName(application, project).takeIf { it.isNotBlank() }
    }
}