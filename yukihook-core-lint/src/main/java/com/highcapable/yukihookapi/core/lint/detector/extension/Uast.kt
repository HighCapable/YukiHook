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
package com.highcapable.yukihookapi.core.lint.detector.extension

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.io.File
import java.util.concurrent.CancellationException

private const val YUKIHOOK_MODULE_NAME_PREFIX = "yukihook-"

internal fun JavaContext.createExternalOnlyUastHandler(handler: UElementHandler) = handler.takeUnless {
    project.name.split('.', ':').any { it.startsWith(YUKIHOOK_MODULE_NAME_PREFIX) }
}

internal fun USimpleNameReferenceExpression.reportNode(): UElement {
    val parent = uastParent
    val grandparent = parent?.uastParent

    return when {
        parent is UQualifiedReferenceExpression && parent.selector == this -> when (grandparent) {
            is UBinaryExpression -> grandparent
            else -> parent.outermostQualifiedReference()
        }
        parent is UQualifiedReferenceExpression && parent.receiver == this -> parent.outermostQualifiedReference()
        parent is UBinaryExpression -> parent
        grandparent is UBinaryExpression -> grandparent
        else -> this
    }
}

internal fun UElement.outermostQualifiedReference(): UElement {
    var current = this
    while (true) {
        val next = current.uastParent as? UQualifiedReferenceExpression ?: return current
        if (next.receiver != current) return current
        current = next
    }
}

internal fun USimpleNameReferenceExpression.isConstructorReference(): Boolean {
    var parent = uastParent
    while (parent is UQualifiedReferenceExpression) parent = parent.uastParent
    return (parent as? UCallExpression)?.kind == UastCallKind.CONSTRUCTOR_CALL
}

internal fun UCallExpression.enclosingFunction() =
    generateSequence(sourcePsi) { it.parent }.filterIsInstance<KtNamedFunction>().firstOrNull()
        ?: generateSequence(uastParent) { it.uastParent }.filterIsInstance<UMethod>().firstOrNull()
            ?.let { it.sourcePsi ?: it.javaPsi.navigationElement }

internal fun UCallExpression.functionTarget(): PsiElement? {
    // UAST may resolve a local Kotlin function to a synthetic method instead of its declaration.
    return (sourcePsi as? KtCallExpression)?.calleeExpression?.references
        ?.firstNotNullOfOrNull { it.resolve().sourceFunction() }
        ?: resolve()?.navigationElement.sourceFunction()
}

internal fun UCallableReferenceExpression.functionTarget() =
    sourcePsi?.references?.firstNotNullOfOrNull { it.resolve().sourceFunction() }
        ?: resolve()?.navigationElement.sourceFunction()

private fun PsiElement?.sourceFunction(): PsiElement? {
    if (this !is KtNamedFunction && this !is PsiMethod) return null
    val extension = containingFile?.virtualFile?.extension
    return takeIf { extension == "kt" || extension == "java" }
}

internal fun JavaContext.toUFile(source: File): UFile? {
    if (source == file) return uastFile
    val sourceContext = JavaContext(driver, project, null, source).also {
        it.uastParser = uastParser
        it.sourceSetType = sourceSetType
    }

    return uastParser.parse(sourceContext)
}

internal fun UCallExpression.branches(): Map<PsiElement, PsiElement> {
    val source = sourcePsi ?: return emptyMap()
    val branches = mutableMapOf<PsiElement, PsiElement>()

    generateSequence(source.parent) { it.parent }.forEach { parent ->
        when (parent) {
            is KtIfExpression -> listOfNotNull(parent.then, parent.`else`)
                .firstOrNull { it.textRange.contains(source.textRange) }
                ?.let { branches[parent] = it }
            is PsiIfStatement -> listOfNotNull(parent.thenBranch, parent.elseBranch)
                .firstOrNull { it.textRange.contains(source.textRange) }
                ?.let { branches[parent] = it }
            is KtWhenEntry -> (parent.parent as? KtWhenExpression)?.let { branches[it] = parent }
        }
    }

    return branches
}

internal fun PsiClass.displayName() = name ?: (this as? PsiAnonymousClass)?.baseClassType?.className?.let { "anonymous $it" } ?: "anonymous"

internal fun <R> KtElement.analyzeOrNull(block: KaSession.() -> R): R? = try {
    analyze(this, block)
} catch (failure: Throwable) {
    if (failure is ControlFlowException || failure is CancellationException) throw failure
    null
}

internal fun ULambdaExpression.enclosingCall() = generateSequence(uastParent) { it.uastParent }
    .filterIsInstance<UCallExpression>().firstOrNull()

internal fun UCallExpression.hasImplicitReceiver() = receiver?.skipParenthesizedExprDown()?.sourcePsi == null

internal fun UMethod.isLocal() = generateSequence(uastParent) { it.uastParent }
    .takeWhile { it !is UFile }
    .any { it is UMethod || it is ULambdaExpression || (it is UClass && it.javaPsi is PsiAnonymousClass) }

internal fun UFile.allClasses() = classes.flatMap { clazz ->
    generateSequence(listOf(clazz)) { level ->
        level.flatMap {
            it.innerClasses.toList()
        }.ifEmpty { null }
    }.flatten()
}

internal fun UNamedExpression.isEmptyArray() = when (val source = expression.sourcePsi) {
    is KtCollectionLiteralExpression -> source.innerExpressions.isEmpty()
    is PsiArrayInitializerMemberValue -> source.initializers.isEmpty()
    else -> false
}

internal fun UNamedExpression.argumentSource() = generateSequence(sourcePsi ?: expression.sourcePsi) { it.parent }
    .firstOrNull { it is KtValueArgument || it is PsiNameValuePair }

internal fun PsiMethod.findPropertyField(): PsiField? {
    val fields = containingClass?.fields ?: return null
    fields.firstOrNull { it.navigationElement == navigationElement }?.let { return it }

    val nameStem = when (parameterList.parametersCount) {
        0 if name.startsWith("get") -> name.removePrefix("get")
        0 if name.startsWith("is") -> return fields.firstOrNull { it.name == name }
        1 if name.startsWith("set") -> name.removePrefix("set")
        else -> return null
    }
    val propertyName = nameStem.replaceFirstChar { it.lowercase() }

    return fields.firstOrNull { it.name == propertyName || it.name == "is$nameStem" }
}

internal fun JavaContext.annotationNameLocation(node: UAnnotation): Location {
    // UAST's annotation name location can cover the full Kotlin annotation, including arguments.
    val name = when (val source = node.sourcePsi) {
        is KtAnnotationEntry -> (source.typeReference?.typeElement as? KtUserType)?.referenceExpression
        is PsiAnnotation -> source.nameReferenceElement?.referenceNameElement
        else -> null
    }
    return name?.let { getLocation(it) } ?: getNameLocation(node)
}

internal fun UExpression.isStringLiteral() = when (val source = skipParenthesizedExprDown().sourcePsi) {
    is KtStringTemplateExpression -> !source.hasInterpolation()
    is PsiLiteralExpression -> true
    else -> false
}

internal fun JavaContext.deletionLocation(source: PsiElement): Location {
    val nextComma = generateSequence(source.nextSibling) { it.nextSibling }
        .firstOrNull { it !is PsiWhiteSpace }
        ?.takeIf { it.text == "," }
    if (nextComma != null) {
        // Also remove the space after the comma, but keep line breaks so the following arguments stay in place.
        val end = (nextComma.nextSibling as? PsiWhiteSpace)?.takeUnless { '\n' in it.text }?.textRange?.endOffset
            ?: nextComma.textRange.endOffset
        return Location.create(file, getContents(), source.textRange.startOffset, end)
    }

    val previousComma = generateSequence(source.prevSibling) { it.prevSibling }
        .firstOrNull { it !is PsiWhiteSpace }
        ?.takeIf { it.text == "," }
    val start = previousComma?.textRange?.startOffset ?: source.textRange.startOffset

    return Location.create(file, getContents(), start, source.textRange.endOffset)
}

internal fun ULambdaExpression.returnedExpressions(): List<UExpression> {
    val results = mutableListOf<UExpression>()
    body.accept(object : AbstractUastVisitor() {

        override fun visitClass(node: UClass) = true

        override fun visitReturnExpression(node: UReturnExpression): Boolean {
            if (node.jumpTarget == this@returnedExpressions) node.returnExpression?.let(results::add)
            return super.visitReturnExpression(node)
        }
    })

    return results
}