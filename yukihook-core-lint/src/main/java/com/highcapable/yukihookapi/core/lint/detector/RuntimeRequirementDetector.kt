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
 * This file is created by fankes on 2026/9/16.
 */
package com.highcapable.yukihookapi.core.lint.detector

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.highcapable.yukihookapi.core.lint.DeclaredSymbol
import com.highcapable.yukihookapi.core.lint.detector.extension.analyzeOrNull
import com.highcapable.yukihookapi.core.lint.detector.extension.buildReplaceFix
import com.highcapable.yukihookapi.core.lint.detector.extension.createExternalOnlyUastHandler
import com.highcapable.yukihookapi.core.lint.detector.extension.findPropertyField
import com.highcapable.yukihookapi.core.lint.detector.extension.isConstructorReference
import com.highcapable.yukihookapi.core.lint.detector.extension.reportNode
import com.highcapable.yukihookapi.generated.YukiHookProperties
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElementOfType

class RuntimeRequirementDetector : Detector(), Detector.UastScanner {

    companion object {

        private const val REQUIRES_RUNTIME_ANNOTATION = "${DeclaredSymbol.CORE_PACKAGE_NAME}.annotation.internal.RequiresRuntime"

        private const val API_VERSION = "apiVersion"

        private const val YUKIHOOK_CLASS = "${DeclaredSymbol.CORE_PACKAGE_NAME}.YukiHook"
        private const val STATUS_CLASS = "$YUKIHOOK_CLASS.Status"
        private const val STATUS_RUNTIME_GETTER = "getRuntime"
        private const val RUNTIME_CLASS = "${DeclaredSymbol.CORE_PACKAGE_NAME}.runtime.YukiHookRuntime"
        private const val EXECUTOR_CLASS = "$STATUS_CLASS.Executor"
        private const val EXECUTOR_API_VERSION_GETTER = "getApiVersion"
        private const val EXECUTOR_API_VERSION = "YukiHook.Status.Executor.apiVersion"
        private const val STATUS_RUNTIME = "YukiHook.Status.runtime"
        private const val RUNTIME_NAME = "YukiHookRuntime"

        private const val SOURCE_DIR_NAME = "src"
        private const val MAIN_SOURCE_SET = "main"

        private const val KOTLIN_PACKAGE = "kotlin"
        private val PRECONDITION_FUNCTIONS = setOf("require", "check")

        private const val LIBXPOSED_RUNTIME = "LIBXPOSED"
        private const val XPOSED_82_RUNTIME = "XPOSED_82"

        private val RUNTIME_CLASSES = mapOf(
            LIBXPOSED_RUNTIME to "${DeclaredSymbol.CORE_PACKAGE_NAME}.runtime.libxposed.entry.base.LibXposedBaseEntry",
            XPOSED_82_RUNTIME to "${DeclaredSymbol.CORE_PACKAGE_NAME}.runtime.xposed82.entry.Xposed82Entry"
        )

        private val RUNTIME_MODULES = mapOf(
            LIBXPOSED_RUNTIME to YukiHookProperties.PROJECT_YUKIHOOK_RUNTIME_LIBXPOSED_MODULE_NAME,
            XPOSED_82_RUNTIME to YukiHookProperties.PROJECT_YUKIHOOK_RUNTIME_XPOSED82_MODULE_NAME
        )

        val REQUIRES_YUKIHOOK_RUNTIME_ISSUE = Issue.create(
            id = "RequiresYukiHookRuntime",
            briefDescription = "Incompatible YukiHook runtime or Xposed API version",
            explanation = """
                YukiHook APIs require a compatible runtime. Calls checked by `YukiHook.Status.runtime` \
                for the required runtime are not reported. If an API specifies an Xposed API version, \
                the target API version of the module entry must be that version or higher.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                RuntimeRequirementDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val NEW_XPOSED_API_ISSUE = Issue.create(
            id = "NewXposedApi",
            briefDescription = "Using a YukiHook API newer than the module's minimum Xposed API version",
            explanation = """
                The API requires a newer Xposed API version than the minimum API version of the module entry. \
                It has no effect when the module runs on an older framework. Check `$EXECUTOR_API_VERSION` before the call, \
                or raise the minimum API version.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                RuntimeRequirementDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(
        UAnnotation::class.java,
        UCallExpression::class.java,
        USimpleNameReferenceExpression::class.java,
        UTypeReferenceExpression::class.java
    )

    override fun createUastHandler(context: JavaContext) = context.createExternalOnlyUastHandler(object : UElementHandler() {

        private val versionDetector = ModuleEntryDetector.VersionDetector(context)

        override fun visitAnnotation(node: UAnnotation) {
            val declaration = node.resolve() ?: return

            reportRequirement(
                scope = node,
                declaration = declaration,
                apiName = declaration.name ?: node.qualifiedName ?: node.asSourceString(),
                location = context.getNameLocation(node)
            )
        }

        override fun visitCallExpression(node: UCallExpression) {
            val method = node.resolve() ?: return

            reportRequirement(
                scope = node,
                declaration = method,
                apiName = if (method.isConstructor) method.containingClass?.name ?: method.name
                else node.methodName ?: method.name,
                location = context.getCallLocation(node, includeReceiver = false, includeArguments = true)
            )
        }

        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
            val declaration = node.resolve() as? PsiModifierListOwner ?: return
            if (declaration is PsiClass && node.isConstructorReference()) return

            reportRequirement(
                scope = node,
                declaration = declaration,
                apiName = node.identifier,
                location = context.getLocation(node.reportNode())
            )
        }

        override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
            val declaration = context.evaluator.getTypeClass(node.type) ?: return

            reportRequirement(
                scope = node,
                declaration = declaration,
                apiName = declaration.name ?: node.asSourceString(),
                location = context.getLocation(node)
            )
        }

        private fun reportRequirement(scope: UElement, declaration: PsiModifierListOwner, apiName: String, location: Location) {
            val requirement = declaration.runtimeRequirement() ?: return
            val runtimeClass = RUNTIME_CLASSES[requirement.runtime] ?: return
            val source = scope.sourcePsi ?: return

            val psiFacade = JavaPsiFacade.getInstance(source.project)
            if (psiFacade.findClass(runtimeClass, source.resolveScope) == null) {
                // Code shared by variants of different runtimes may call these APIs behind a runtime check.
                if (scope.isGuardedByRuntime(requirement.runtime)) return
                // A chain such as `sharedFiles.list()` is only reported at its first API.
                if (scope.chainedReceiver()?.requiresRuntimeInChain(requirement.runtime) == true) return
                val runtimeModule = RUNTIME_MODULES.getValue(requirement.runtime)
                context.report(
                    issue = REQUIRES_YUKIHOOK_RUNTIME_ISSUE,
                    scope = scope,
                    location = location,
                    message = "`$apiName` requires the `$runtimeModule` runtime",
                    // Only shared code may run on another runtime, so a check is pointless in a single runtime module.
                    quickfixData = if (isSharedByFlavors()) scope.runtimeCheckFix(requirement.runtime) else null
                )
                return
            }

            if (requirement.runtime != LIBXPOSED_RUNTIME || requirement.apiVersion <= 0) return
            val entry = versionDetector.findLibXposedEntry() ?: return

            val isNewApi = entry.minApiVersion < requirement.apiVersion &&
                (entry.targetApiVersion == null || entry.targetApiVersion >= requirement.apiVersion)
            if (isNewApi && scope.isGuardedByApiVersion(requirement.apiVersion)) return

            val (issue, attribute, declaredVersion) = when {
                entry.targetApiVersion != null && entry.targetApiVersion < requirement.apiVersion ->
                    Triple(REQUIRES_YUKIHOOK_RUNTIME_ISSUE, ModuleEntryDetector.TARGET_API_VERSION, entry.targetApiVersion)
                entry.minApiVersion < requirement.apiVersion ->
                    Triple(NEW_XPOSED_API_ISSUE, ModuleEntryDetector.MIN_API_VERSION, entry.minApiVersion)
                else -> return
            }

            context.report(
                issue = issue,
                scope = scope,
                location = location,
                message = "`$apiName` requires Xposed API ${requirement.apiVersion}, but " +
                    "`@${ModuleEntryDetector.LIBXPOSED_ENTRY_ANNOTATION_NAME}` specifies `$attribute = $declaredVersion`",
                quickfixData = if (issue == NEW_XPOSED_API_ISSUE) scope.versionCheckFix(requirement.apiVersion)
                else versionDetector.buildFix(entry, requirement.apiVersion, attribute, outside = true)
            )
        }

        private fun UElement.chainedReceiver() = when (this) {
            is UCallExpression -> receiver
            is USimpleNameReferenceExpression -> (uastParent as? UQualifiedReferenceExpression)?.takeIf { it.selector == this }?.receiver
            else -> null
        }

        /** Checks whether this expression or any receiver before it requires [runtime]. */
        private fun UExpression.requiresRuntimeInChain(runtime: String): Boolean {
            val expression = skipParenthesizedExprDown()
            val declaration = (expression as? UResolvable)?.resolve() as? PsiModifierListOwner
            if (declaration?.runtimeRequirement()?.runtime == runtime) return true

            return expression.chainedReceiver()?.requiresRuntimeInChain(runtime) == true ||
                (expression as? UQualifiedReferenceExpression)?.receiver?.requiresRuntimeInChain(runtime) == true
        }

        /**
         * Checks whether this element only runs after [condition] holds.
         *
         * Supported forms are the then branch of `if`, a `when` branch, and an earlier `if (...)` that exits,
         * `require` or `check` in the same block. [condition] receives `negated = true` for the early exit form.
         */
        private fun UElement.isGuardedBy(
            condition: (expression: UExpression, negated: Boolean) -> Boolean,
            subjectCase: (subject: UExpression, case: UExpression) -> Boolean
        ) = generateSequence(this) { it.uastParent }.zipWithNext().any { (child, parent) ->
            when (parent) {
                is UIfExpression -> parent.thenExpression == child && condition(parent.condition, false)
                is USwitchClauseExpressionWithBody -> parent.body == child && parent.caseValues.isNotEmpty() && run {
                    val subject = (parent.uastParent?.uastParent as? USwitchExpression)?.expression
                    parent.caseValues.all {
                        if (subject != null)
                            subjectCase(subject, it)
                        else condition(it, false)
                    }
                }
                is UBlockExpression -> parent.expressions.takeWhile { it != child }.any {
                    it.exitsWhen { expression -> condition(expression, true) } ||
                        it.isPrecondition { expression -> condition(expression, false) }
                }
                else -> false
            }
        }

        private fun UExpression.exitsWhen(condition: (UExpression) -> Boolean): Boolean {
            val expression = skipParenthesizedExprDown() as? UIfExpression ?: return false
            return expression.elseExpression == null && expression.thenExpression?.isExit() == true && condition(expression.condition)
        }

        private fun UExpression.isPrecondition(condition: (UExpression) -> Boolean): Boolean {
            val call = skipParenthesizedExprDown() as? UCallExpression ?: return false
            if (call.methodName !in PRECONDITION_FUNCTIONS) return false
            val method = call.resolve() ?: return false
            if (context.evaluator.getPackage(method)?.qualifiedName != KOTLIN_PACKAGE) return false

            return call.getArgumentForParameter(0)?.let(condition) == true
        }

        private fun UExpression.isExit(): Boolean = when (val expression = skipParenthesizedExprDown()) {
            is UReturnExpression, is UThrowExpression, is UBreakExpression, is UContinueExpression -> true
            is UBlockExpression -> expression.expressions.lastOrNull()?.isExit() == true
            else -> (expression.sourcePsi as? KtExpression)?.let { source ->
                source.analyzeOrNull { source.expressionType?.isNothingType }
            } == true
        }

        private fun UElement.isGuardedByRuntime(runtime: String) = isGuardedBy(
            condition = { expression, negated -> expression.requiresRuntime(runtime, negated) },
            subjectCase = { subject, case -> isRuntimeEquality(subject, case, runtime) }
        )

        private fun UElement.isGuardedByApiVersion(apiVersion: Int) = isGuardedBy(
            condition = { expression, negated -> expression.requiresApiVersion(apiVersion, negated) },
            subjectCase = { _, _ -> false }
        )

        /** With [negated], checks a condition whose failure means the runtime is [runtime], such as `runtime != X`. */
        private fun UExpression.requiresRuntime(runtime: String, negated: Boolean, getters: Set<KtProperty> = emptySet()): Boolean {
            val expression = skipParenthesizedExprDown()
            if (expression is UPrefixExpression && expression.operator == UastPrefixOperator.LOGICAL_NOT)
                return expression.operand.requiresRuntime(runtime, !negated, getters)
            if (expression is UQualifiedReferenceExpression && (expression.selector as? UCallExpression)?.isBooleanNot() == true)
                return expression.receiver.requiresRuntime(runtime, !negated, getters)
            if (expression !is UBinaryExpression) {
                val method = (expression as? UResolvable)?.resolve() as? PsiMethod ?: return false
                if (!method.hasModifierProperty(PsiModifier.FINAL) && !method.hasModifierProperty(PsiModifier.PRIVATE)) return false
                val property = when (val declaration = method.navigationElement) {
                    is KtProperty -> declaration
                    is KtPropertyAccessor -> declaration.property
                    else -> return false
                }
                if (property.isVar || property in getters) return false
                val getter = property.getter?.takeUnless { it.hasBlockBody() } ?: return false
                return getter.bodyExpression.toUElementOfType<UExpression>()?.requiresRuntime(runtime, negated, getters + property) == true
            }
            if (expression.operator == joinOperator(negated))
                return expression.leftOperand.requiresRuntime(runtime, negated, getters) ||
                    expression.rightOperand.requiresRuntime(runtime, negated, getters)

            val operators = if (negated)
                listOf(UastBinaryOperator.NOT_EQUALS, UastBinaryOperator.IDENTITY_NOT_EQUALS)
            else listOf(UastBinaryOperator.EQUALS, UastBinaryOperator.IDENTITY_EQUALS)

            return expression.operator in operators && isRuntimeEquality(expression.leftOperand, expression.rightOperand, runtime)
        }

        private fun UCallExpression.isBooleanNot(): Boolean {
            if (methodName != "not") return false
            val source = sourcePsi as? KtCallExpression ?: return false
            return source.analyzeOrNull {
                source.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId?.asSingleFqName()?.asString() == "kotlin.Boolean.not"
            } == true
        }

        private fun isRuntimeEquality(first: UExpression, second: UExpression, runtime: String) = when {
            first.isStatusRuntime() -> second.runtimeName() == runtime
            second.isStatusRuntime() -> first.runtimeName() == runtime
            else -> false
        }

        private fun UExpression.isStatusRuntime(): Boolean {
            val getter = (skipParenthesizedExprDown() as? UResolvable)?.resolve() as? PsiMethod ?: return false
            return getter.name == STATUS_RUNTIME_GETTER && getter.containingClass?.qualifiedName == STATUS_CLASS
        }

        private fun UExpression.runtimeName(): String? {
            val constant = (skipParenthesizedExprDown() as? UResolvable)?.resolve() as? PsiField ?: return null
            return constant.name.takeIf { constant.containingClass?.qualifiedName == RUNTIME_CLASS }
        }

        /** With [negated], checks a condition whose failure means the API version is at least [apiVersion], such as `apiVersion < 102`. */
        private fun UExpression.requiresApiVersion(apiVersion: Int, negated: Boolean): Boolean {
            val expression = skipParenthesizedExprDown()
            if (expression is UPrefixExpression && expression.operator == UastPrefixOperator.LOGICAL_NOT)
                return expression.operand.requiresApiVersion(apiVersion, !negated)
            if (expression !is UBinaryExpression) return false
            if (expression.operator == joinOperator(negated))
                return expression.leftOperand.requiresApiVersion(apiVersion, negated) ||
                    expression.rightOperand.requiresApiVersion(apiVersion, negated)

            val left = expression.leftOperand
            val right = expression.rightOperand
            val (version, operator) = when {
                left.isExecutorApiVersion() -> right.constantInt() to expression.operator
                right.isExecutorApiVersion() -> left.constantInt() to when (expression.operator) {
                    UastBinaryOperator.LESS_OR_EQUALS -> UastBinaryOperator.GREATER_OR_EQUALS
                    UastBinaryOperator.LESS -> UastBinaryOperator.GREATER
                    UastBinaryOperator.GREATER_OR_EQUALS -> UastBinaryOperator.LESS_OR_EQUALS
                    UastBinaryOperator.GREATER -> UastBinaryOperator.LESS
                    else -> return false
                }
                else -> return false
            }
            version ?: return false

            return when (operator) {
                UastBinaryOperator.GREATER_OR_EQUALS -> !negated && version >= apiVersion
                UastBinaryOperator.GREATER -> !negated && version + 1 >= apiVersion
                UastBinaryOperator.LESS -> negated && version >= apiVersion
                UastBinaryOperator.LESS_OR_EQUALS -> negated && version + 1 >= apiVersion
                else -> false
            }
        }

        // A guard holds when any part of `&&` holds, and an early exit holds when any part of `||` fails.
        private fun joinOperator(negated: Boolean) = if (negated) UastBinaryOperator.LOGICAL_OR else UastBinaryOperator.LOGICAL_AND

        private fun UExpression.isExecutorApiVersion(): Boolean {
            val getter = (skipParenthesizedExprDown() as? UResolvable)?.resolve() as? PsiMethod ?: return false
            return getter.name == EXECUTOR_API_VERSION_GETTER && getter.containingClass?.qualifiedName == EXECUTOR_CLASS
        }

        private fun UExpression.constantInt() = ConstantEvaluator.evaluate(context, this) as? Int

        private fun UElement.versionCheckFix(apiVersion: Int) = surroundWithIfFix(
            condition = "$EXECUTOR_API_VERSION >= $apiVersion",
            otherwise = "$EXECUTOR_API_VERSION < $apiVersion",
            YUKIHOOK_CLASS
        )

        private fun UElement.runtimeCheckFix(runtime: String) = surroundWithIfFix(
            condition = "$STATUS_RUNTIME == $RUNTIME_NAME.$runtime",
            otherwise = "$STATUS_RUNTIME != $RUNTIME_NAME.$runtime",
            YUKIHOOK_CLASS,
            RUNTIME_CLASS
        )

        /** Surrounds this element with `if ([condition])`, an expression whose value is used gets an else branch for [otherwise]. */
        private fun UElement.surroundWithIfFix(condition: String, otherwise: String, vararg imports: String): LintFix? {
            val target = (sourcePsi as? KtElement)?.versionCheckTarget() ?: return null
            val isUsed = target.analyzeOrNull { target.isUsedAsExpression } ?: return null
            val replacement = "if ($condition) {\n${target.text}\n}" +
                if (isUsed) " else {\nTODO(\"$otherwise\")\n}" else ""

            return buildReplaceFix(
                name = "Surround with if ($condition) { ... }",
                replacement = replacement,
                *imports,
                range = context.getLocation(target)
            )
        }

        /**
         * Checks whether the current file is shared by product flavors, such as the `main` or `debug` source set
         * of a module with `modern` and `legacy` flavors.
         *
         * The lint model only contains the current variant, so the flavors are found from its source set directories,
         * which are named `main`, the flavors, the build type and the variant, a module without flavors only has `main`
         * and the build type named the same as the variant.
         */
        private fun isSharedByFlavors(): Boolean {
            val variant = context.project.buildVariant ?: return false
            val sourceDirs = variant.sourceProviders.flatMap { it.javaDirectories }
                .filter { it.parentFile?.parentFile?.name == SOURCE_DIR_NAME }
            val sourceSet = sourceDirs.firstOrNull { context.file.startsWith(it) }?.parentFile?.name ?: return false
            val buildType = sourceDirs.map { it.parentFile.name }.firstOrNull {
                it != MAIN_SOURCE_SET && it != variant.name && variant.name.endsWith(it.replaceFirstChar(Char::uppercaseChar))
            } ?: return false

            return sourceSet == MAIN_SOURCE_SET || sourceSet == buildType
        }

        private fun KtElement.versionCheckTarget(): KtExpression? {
            var target = this as? KtExpression ?: getStrictParentOfType<KtExpression>() ?: return null
            while (true) {
                val parent = target.parent ?: return target
                if (parent.isInvalidVersionCheckTarget()) return target
                target = target.getStrictParentOfType<KtExpression>() ?: return target
            }
        }

        private fun PsiElement.isInvalidVersionCheckTarget() = this is KtBlockExpression || this is KtContainerNode ||
            this is KtWhenEntry || this is KtFunction || this is KtPropertyAccessor || this is KtProperty ||
            this is KtReturnExpression || this is KtDestructuringDeclaration || this is KtClassInitializer

        private fun PsiModifierListOwner.runtimeRequirement(): RuntimeRequirement? {
            val annotation = findRuntimeAnnotation()
                ?: (this as? PsiMember)?.containingClass?.let {
                    context.evaluator.getAnnotation(it, REQUIRES_RUNTIME_ANNOTATION)
                } ?: return null
            val runtime = annotation.findAttributeValue("value")?.asSourceString()?.substringAfterLast('.') ?: return null
            val versionValue = annotation.javaPsi?.findAttributeValue(API_VERSION)
            val apiVersion = versionValue?.let {
                JavaPsiFacade.getInstance(project).constantEvaluationHelper.computeConstantExpression(it) as? Int
            } ?: 0

            return RuntimeRequirement(runtime, apiVersion)
        }

        private fun PsiModifierListOwner.findRuntimeAnnotation() =
            context.evaluator.getAnnotation(this, REQUIRES_RUNTIME_ANNOTATION)
                ?: (this as? PsiMethod)?.findPropertyField()?.let {
                    context.evaluator.getAnnotation(it, REQUIRES_RUNTIME_ANNOTATION)
                }
    })

    private data class RuntimeRequirement(
        val runtime: String,
        val apiVersion: Int
    )
}