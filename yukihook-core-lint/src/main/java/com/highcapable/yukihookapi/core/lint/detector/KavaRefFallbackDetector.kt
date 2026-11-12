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
 * This file is created by fankes on 2026/10/14.
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
import com.highcapable.yukihookapi.core.lint.detector.extension.analyzeOrNull
import com.highcapable.yukihookapi.core.lint.detector.extension.createExternalOnlyUastHandler
import com.highcapable.yukihookapi.generated.YukiHookProperties
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UCallExpression

class KavaRefFallbackDetector : Detector(), Detector.UastScanner {

    companion object {

        private const val PACKAGE_PARAM_CLASS = "${DeclaredSymbol.CORE_PACKAGE_NAME}.hook.param.PackageParam"
        private const val KAVAREF_EXTENSION_PACKAGE_NAME = "com.highcapable.kavaref.extension"
        private const val VARIOUS_CLASS = "$KAVAREF_EXTENSION_PACKAGE_NAME.VariousClass"

        private const val LOADER_PARAMETER = "loader"

        /**
         * An issue ID registered by the lint checks of `yukihook-extension-kavaref`.
         *
         * Those checks report the same calls with quick fixes, so this check turns itself off when the ID is registered.
         */
        private const val EXTENSION_ISSUE_ID = "ClassLoadingWithoutHostClassLoader"

        private val VARIOUS_CLASS_LOADING_FUNCTIONS = setOf("load", "loadOrNull")
        private val CLASS_LOADING_FUNCTIONS = setOf("toClass", "toClassOrNull", "lazyClass", "lazyClassOrNull")

        private val PACKAGE_PARAM_CLASS_ID = ClassId.topLevel(FqName(PACKAGE_PARAM_CLASS))

        val ISSUE = Issue.create(
            id = "KavaRefWithoutHostClassLoader",
            briefDescription = "KavaRef class loaded without the host class loader",
            explanation = """
                In a `PackageParam` scope, `toClass`, `lazyClass`, `VariousClass.load` and other class loading functions \
                from KavaRef use the module class loader when no loader is passed. Add the \
                `${YukiHookProperties.PROJECT_YUKIHOOK_EXTENSION_KAVAREF_MODULE_NAME}` dependency to load the class from the host \
                by default, its lint checks provide quick fixes for these calls.

                See the documentation for more details:
                - English: https://highcapable.github.io/YukiHook/en/library/yukihook-extension-kavaref
                - 简体中文: https://highcapable.github.io/YukiHook/zh-cn/library/yukihook-extension-kavaref

                The `${YukiHookProperties.PROJECT_YUKIHOOK_EXTENSION_KAVAREF_MODULE_NAME}` provides:
                - `toClass` and `toClassOrNull` for `String` and `VariousClass` that use `hostClassLoader` by default
                - `lazyClass` and `lazyClassOrNull` that use `hostClassLoader` by default

                Examples:
                ```kotlin
                // Before
                import com.highcapable.kavaref.extension.lazyClass
                import com.highcapable.kavaref.extension.toClass

                "com.example.Test".toClass()
                VariousClass("com.example.Test").load()
                lazyClass("com.example.Test")

                // After
                import com.highcapable.yukihookapi.extension.kavaref.lazyClass
                import com.highcapable.yukihookapi.extension.kavaref.toClass

                "com.example.Test".toClass()
                VariousClass("com.example.Test").toClass()
                lazyClass("com.example.Test")
                ```
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KavaRefFallbackDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        if (context.driver.registry.isIssueId(EXTENSION_ISSUE_ID)) return null

        return context.createExternalOnlyUastHandler(object : UElementHandler() {

            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName !in VARIOUS_CLASS_LOADING_FUNCTIONS && methodName !in CLASS_LOADING_FUNCTIONS) return
                val source = node.sourcePsi as? KtCallExpression ?: return
                val callee = source.calleeExpression ?: return
                val arguments = source.valueArgumentList ?: return
                val method = node.resolve() ?: return
                if (!method.isKavaRefClassLoading(methodName)) return
                if (!source.hasPackageParamReceiver()) return
                if (node.hasLoaderArgument(method)) return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    // A trailing lambda is part of the call, the range has to cover it as well.
                    location = context.getRangeLocation(callee, 0, source.lambdaArguments.lastOrNull() ?: arguments, 0),
                    message = "`$methodName` from KavaRef does not use `hostClassLoader`, add the " +
                        "`${YukiHookProperties.PROJECT_YUKIHOOK_EXTENSION_KAVAREF_MODULE_NAME}` dependency to load the class from the host"
                )
            }

            private fun PsiMethod.isKavaRefClassLoading(methodName: String): Boolean {
                val className = containingClass?.qualifiedName ?: return false
                return when (methodName) {
                    in VARIOUS_CLASS_LOADING_FUNCTIONS -> className == VARIOUS_CLASS
                    else -> className.substringBeforeLast('.') == KAVAREF_EXTENSION_PACKAGE_NAME &&
                        parameterList.parameters.any { it.name == LOADER_PARAMETER }
                }
            }

            private fun UCallExpression.hasLoaderArgument(method: PsiMethod) =
                context.evaluator.computeArgumentMapping(this, method).values.any { it.name == LOADER_PARAMETER }

            private fun KtCallExpression.hasPackageParamReceiver() = analyzeOrNull {
                containingKtFile.scopeContext(this@hasPackageParamReceiver).implicitReceivers.any {
                    it.type.isSubtypeOf(PACKAGE_PARAM_CLASS_ID)
                }
            } == true
        })
    }
}