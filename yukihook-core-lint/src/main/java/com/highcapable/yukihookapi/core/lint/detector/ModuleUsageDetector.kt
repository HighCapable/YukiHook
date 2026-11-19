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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.uast.UBinaryExpressionWithType
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

        private const val INSTANCE_GETTER = "getInstance"
        private const val INSTANCE_OR_NULL_GETTER = "getInstanceOrNull"
        private const val MEMBER_GETTER = "getMember"
        private const val RESULT_GETTER = "getResult"

        private const val METHOD_CLASS = "java.lang.reflect.Method"
        private const val STAR_CONSTRUCTOR_TYPE = "java.lang.reflect.Constructor<?>"

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
            briefDescription = "Use HookInvocation's API instead of casting",
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

                `HookParam.result<T>()` returns null when the result is absent or has a different type.
                Use it in place of a regular cast when you want to handle those cases as null.

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

                // After
                instance<Activity>()
                instance<Activity>()
                instanceOrNull<Activity>()
                instanceOrNull<Activity>()
                instanceOrNull<Activity>()
                method
                constructor
                result<String>()
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

    override fun getApplicableUastTypes() = listOf(UClass::class.java, UBinaryExpressionWithType::class.java)

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

        override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType) {
            val source = node.sourcePsi as? KtBinaryExpressionWithTypeRHS ?: return
            val typeReference = source.right ?: return
            val getter = (node.operand.skipParenthesizedExprDown() as? UResolvable)?.resolve() as? PsiMethod ?: return
            val isResult = getter.name == RESULT_GETTER
            val declaringClass = if (isResult) HOOK_PARAM else HOOK_INVOCATION
            if (getter.containingClass?.qualifiedName != declaringClass) return

            val isSafeCast = source.operationReference.getReferencedNameElementType() == KtTokens.AS_SAFE
            val castType = node.type.canonicalText
            val propertyName = getter.name.removePrefix("get").replaceFirstChar { it.lowercase() }
            val operand = KtPsiUtil.safeDeparenthesize(source.left)
            val selector = if (operand is KtQualifiedExpression) operand.selectorExpression else operand
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
            val typeText = if (isResult) nullableType?.innerType?.text ?: typeReference.text else typeReference.text
            val replacement = if (getter.name == MEMBER_GETTER) replacementName else "$replacementName<$typeText>()"

            // Keep the explicit receiver, e.g. "param.instance as T" becomes "param.instance<T>()".
            val receiverPrefix = (operand as? KtQualifiedExpression)?.selectorExpression?.let {
                operand.text.substring(0, it.textRange.startOffset - operand.textRange.startOffset)
            } ?: ""
            val fixedText = receiverPrefix + replacement
            // A local declaration can shadow an unqualified replacement.
            val isShadowed = receiverPrefix.isEmpty() && (isResult || replacementName != propertyName) &&
                source.hasLocalDeclaration(replacementName, includeFunctions = isResult)
            // A regular cast can throw, while result<T>() returns null.
            val isThrowingResultCast = isResult && !isSafeCast
            // The replacement is a postfix expression, so parentheses that only wrap the cast are no longer needed.
            val replaceTarget = generateSequence(source.parent) { it.parent }
                .takeWhile { it is KtParenthesizedExpression }
                .lastOrNull() ?: source

            context.report(
                issue = USE_HOOK_INVOCATION_API_ISSUE,
                scope = node as UElement,
                location = context.getLocation(source),
                message = if (isThrowingResultCast) "Use `$fixedText` to return null for a missing or mismatched result"
                else "Can be replaced with `$fixedText`",
                quickfixData = if (isShadowed || isThrowingResultCast) null else buildReplaceFix(
                    name = "Replace with '$replacementName'",
                    replacement = fixedText,
                    range = context.getLocation(replaceTarget),
                    reformat = false
                )
            )
        }
    })

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