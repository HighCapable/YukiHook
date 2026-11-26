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
package com.highcapable.yukihookapi.core.lint.detector

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.highcapable.yukihookapi.core.lint.DeclaredSymbol
import com.highcapable.yukihookapi.core.lint.detector.extension.branches
import com.highcapable.yukihookapi.core.lint.detector.extension.buildDeleteFix
import com.highcapable.yukihookapi.core.lint.detector.extension.createExternalOnlyUastHandler
import com.highcapable.yukihookapi.core.lint.detector.extension.enclosingCall
import com.highcapable.yukihookapi.core.lint.detector.extension.enclosingFunction
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor

class MemberHookerDetector : Detector(), Detector.UastScanner {

    companion object {

        private const val CLASSIC_HOOKER = "${DeclaredSymbol.CORE_PACKAGE_NAME}.hook.core.YukiHookCreator.ClassicMemberHooker"

        private const val BEFORE_METHOD_NAME = "before"
        private const val AFTER_METHOD_NAME = "after"
        private const val INTERCEPT_METHOD_NAME = "intercept"
        private const val REPLACE_ANY_METHOD_NAME = "replaceAny"
        private const val REPLACE_UNIT_METHOD_NAME = "replaceUnit"
        private const val REPLACE_TO_METHOD_NAME = "replaceTo"
        private const val REPLACE_TO_TRUE_METHOD_NAME = "replaceToTrue"
        private const val REPLACE_TO_FALSE_METHOD_NAME = "replaceToFalse"
        private const val HOOK_METHOD_NAME = "hook"
        private const val HOOK_ALL_METHOD_NAME = "hookAll"
        private const val INTERCEPT_ALL_METHOD_NAME = "interceptAll"

        private val INTERCEPT_METHOD_NAMES = setOf(
            INTERCEPT_METHOD_NAME, REPLACE_ANY_METHOD_NAME, REPLACE_UNIT_METHOD_NAME, REPLACE_TO_METHOD_NAME,
            REPLACE_TO_TRUE_METHOD_NAME, REPLACE_TO_FALSE_METHOD_NAME
        )

        private const val PACKAGE_PARAM_CLASS = "${DeclaredSymbol.CORE_PACKAGE_NAME}.hook.param.PackageParam"
        private const val MEMBER_CLASS = "java.lang.reflect.Member"
        private const val FIELD_CLASS = "java.lang.reflect.Field"
        private const val COLLECTION_CLASS = "java.util.Collection"

        private val MEMBER_HOOK_METHOD_NAMES = setOf(HOOK_METHOD_NAME, INTERCEPT_METHOD_NAME)
        private val MEMBERS_HOOK_METHOD_NAMES = setOf(HOOK_ALL_METHOD_NAME, INTERCEPT_ALL_METHOD_NAME)

        private val COLLECTION_FACTORY_NAMES = setOf(
            "arrayOf", "listOf", "listOfNotNull", "mutableListOf", "arrayListOf",
            "setOf", "setOfNotNull", "mutableSetOf", "hashSetOf", "linkedSetOf"
        )

        val CONFLICTING_CLASSIC_HOOK_CALLBACKS_ISSUE = Issue.create(
            id = "ConflictingClassicHookCallbacks",
            briefDescription = "Conflicting classic hook callbacks",
            explanation = """
                A classic hooker may use `before` and `after` together, but `intercept` replaces their callbacks. \
                Do not configure them with `intercept` on the same hooker.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                MemberHookerDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val INVALID_FIELD_HOOK_TARGET_ISSUE = Issue.create(
            id = "InvalidFieldHookTarget",
            briefDescription = "Field used as a hook target",
            explanation = """
                Only methods and constructors can be hooked. Passing a `Field` to `hook`, `intercept`, \
                `hookAll` or `interceptAll` throws an `IllegalArgumentException` at runtime.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(
                MemberHookerDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    private enum class Callback { BEFORE, AFTER, INTERCEPT }

    private data class HookerGroup(val owner: PsiElement?, val declaration: PsiElement)

    private data class CallbackCall(
        val node: UCallExpression,
        val callback: Callback,
        val branches: Map<PsiElement, PsiElement>
    ) {

        fun conflictsWith(other: CallbackCall) = (callback == Callback.INTERCEPT) != (other.callback == Callback.INTERCEPT) &&
            branches.none { (condition, branch) -> other.branches[condition]?.let { it != branch } == true }
    }

    override fun getApplicableUastTypes() = listOf(UFile::class.java, UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = context.createExternalOnlyUastHandler(object : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            val method = node.resolve() ?: return
            if (method.containingClass?.qualifiedName != PACKAGE_PARAM_CLASS) return
            // Use the source name, some overloads have a different JVM name such as "hookAllByMember".
            val methodName = node.methodName ?: return
            val isMultiple = when (methodName) {
                in MEMBER_HOOK_METHOD_NAMES -> false
                in MEMBERS_HOOK_METHOD_NAMES -> true
                else -> return
            }
            // The extension receiver is the first parameter, only the Member overloads accept a field.
            val receiverType = method.parameterList.parameters.firstOrNull()?.type ?: return
            if ((if (isMultiple) receiverType.elementType() else receiverType).qualifiedName() != MEMBER_CLASS) return
            val receiver = node.receiver?.skipParenthesizedExprDown() ?: return

            fun report(target: UElement, message: String) = context.report(
                issue = INVALID_FIELD_HOOK_TARGET_ISSUE,
                scope = target,
                location = context.getLocation(target),
                message = message
            )

            val hookName = "`$methodName`"
            if (!isMultiple) {
                if (receiver.getExpressionType().qualifiedName() == FIELD_CLASS)
                    report(receiver, "$hookName cannot be used with a `Field`, only methods and constructors can be hooked")
                return
            }
            if (receiver.getExpressionType().elementType().qualifiedName() == FIELD_CLASS) {
                report(receiver, "$hookName cannot be used with `Field` elements, only methods and constructors can be hooked")
                return
            }
            // A factory call such as "arrayOf<Member>(field)" widens the element type, so check its arguments.
            val factory = receiver as? UCallExpression ?: return
            if (factory.methodName !in COLLECTION_FACTORY_NAMES) return
            factory.valueArguments.forEach { argument ->
                if (argument.getExpressionType().qualifiedName() == FIELD_CLASS)
                    report(argument, "$hookName cannot be used with a `Field`, only methods and constructors can be hooked")
            }
        }

        override fun visitFile(node: UFile) {
            val callbacks = mutableMapOf<HookerGroup, MutableList<CallbackCall>>()

            node.accept(object : AbstractUastVisitor() {

                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val method = node.resolve() ?: return false
                    if (method.containingClass?.qualifiedName != CLASSIC_HOOKER) return false

                    val callback = when (method.name) {
                        BEFORE_METHOD_NAME -> Callback.BEFORE
                        AFTER_METHOD_NAME -> Callback.AFTER
                        in INTERCEPT_METHOD_NAMES -> Callback.INTERCEPT
                        else -> return false
                    }
                    val group = node.hookerGroup() ?: return false
                    callbacks.getOrPut(group) { mutableListOf() }.add(CallbackCall(node, callback, node.branches()))

                    return false
                }
            })

            callbacks.values.forEach { group ->
                val previous = mutableListOf<CallbackCall>()

                group.sortedBy { it.node.sourcePsi?.textRange?.startOffset ?: Int.MAX_VALUE }.forEach { call ->
                    if (previous.any(call::conflictsWith)) {
                        val interceptions = if (call.callback == Callback.INTERCEPT) listOf(call)
                        else group.filter(call::conflictsWith)
                        val callbacks = listOf(Callback.BEFORE, Callback.AFTER).filter { callback ->
                            group.any { it.callback == callback && interceptions.any(it::conflictsWith) }
                        }.joinToString(" and ") { "`${it.name.lowercase()}`" }
                        val expression = generateSequence(call.node as UElement) { it.uastParent as? UQualifiedReferenceExpression }.last()
                        val location = context.getLocation(expression)
                        val parent = expression.uastParent
                        val isStandalone = parent is UBlockExpression || (parent is UReturnExpression &&
                            parent.sourcePsi == null && expression.sourcePsi?.parent is KtBlockExpression)

                        context.report(
                            issue = CONFLICTING_CLASSIC_HOOK_CALLBACKS_ISSUE,
                            scope = call.node,
                            location = location,
                            message = "`intercept` cannot be used with $callbacks on the same classic hooker",
                            quickfixData = if (isStandalone) buildDeleteFix("Delete Call Expression", location) else null
                        )
                    }

                    previous.add(call)
                }
            }
        }

        private fun UCallExpression.hookerGroup(): HookerGroup? {
            val receiver = receiver
            if (receiver != null && receiver !is UThisExpression) {
                val property = receiver.sourcePsi?.references?.firstNotNullOfOrNull { it.resolve() as? KtProperty }
                if (property != null) return HookerGroup(enclosingFunction(), property).takeIf { property.isLocal && !property.isVar }

                val target = (receiver as? USimpleNameReferenceExpression)?.resolve() ?: return null
                if (target !is PsiLocalVariable && target !is PsiParameter) return null
                if (target is PsiLocalVariable && !target.hasModifierProperty(PsiModifier.FINAL)) return null
                return HookerGroup(enclosingFunction(), target.navigationElement)
            }

            val lambdas = generateSequence(uastParent) { it.uastParent }.filterIsInstance<ULambdaExpression>().toList()
            val scope = lambdas.firstOrNull { it.isClassicScope() }
                ?: lambdas.firstOrNull { it.enclosingCall()?.resolve()?.containingClass?.qualifiedName != CLASSIC_HOOKER }
            return scope?.sourcePsi?.let { HookerGroup(null, it) }
        }

        private fun ULambdaExpression.isClassicScope(): Boolean {
            val call = enclosingCall() ?: return false
            return call.resolve()?.containingClass?.qualifiedName != CLASSIC_HOOKER &&
                (call.resolve()?.parameterList?.parameters?.any { it.type.containsClassicHooker() } == true ||
                    call.receiver?.getExpressionType().containsClassicHooker())
        }

        private fun PsiType?.containsClassicHooker(): Boolean = when (this) {
            is PsiArrayType -> componentType.containsClassicHooker()
            is PsiClassType -> resolve()?.qualifiedName == CLASSIC_HOOKER || parameters.any { it.containsClassicHooker() }
            is PsiWildcardType -> bound.containsClassicHooker()
            else -> false
        }

        private fun PsiType?.qualifiedName() = (this as? PsiClassType)?.resolve()?.qualifiedName
        private fun PsiType?.elementType() = when (this) {
            is PsiArrayType -> componentType
            is PsiClassType -> parameters.singleOrNull()?.takeIf { InheritanceUtil.isInheritor(this, COLLECTION_CLASS) }
            else -> null
        }.let { if (it is PsiWildcardType) it.bound else it }
    })
}