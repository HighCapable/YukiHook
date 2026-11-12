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
 * This file is created by fankes on 2026/10/13.
 */
package com.highcapable.yukihookapi.extension.kavaref.lint.detector

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.highcapable.yukihookapi.extension.kavaref.lint.DeclaredSymbol
import com.highcapable.yukihookapi.extension.kavaref.lint.detector.extension.analyzeOrNull
import com.highcapable.yukihookapi.extension.kavaref.lint.detector.extension.buildReplaceFix
import com.highcapable.yukihookapi.extension.kavaref.lint.detector.extension.createExternalOnlyUastHandler
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.skipParenthesizedExprDown

class PackageParamDetector : Detector(), Detector.UastScanner {

    companion object {

        private const val PACKAGE_PARAM_CLASS = "${DeclaredSymbol.CORE_PACKAGE_NAME}.hook.param.PackageParam"
        private const val KAVAREF_EXTENSION_PACKAGE_NAME = "com.highcapable.kavaref.extension"
        private const val VARIOUS_CLASS = "$KAVAREF_EXTENSION_PACKAGE_NAME.VariousClass"

        private const val LOADER_PARAMETER = "loader"
        private const val HOST_CLASS_LOADER = "hostClassLoader"

        private val HOST_CLASS_LOADER_GETTERS = setOf("getHostClassLoader", "getAppClassLoader")
        private val VARIOUS_CLASS_LOADING_FUNCTIONS = mapOf("load" to "toClass", "loadOrNull" to "toClassOrNull")
        private val TO_CLASS_FUNCTIONS = setOf("toClass", "toClassOrNull")
        private val LAZY_CLASS_FUNCTIONS = setOf("lazyClass", "lazyClassOrNull")

        private val PACKAGE_PARAM_CLASS_ID = ClassId.topLevel(FqName(PACKAGE_PARAM_CLASS))

        val CLASS_LOADING_WITHOUT_HOST_CLASS_LOADER_ISSUE = Issue.create(
            id = "ClassLoadingWithoutHostClassLoader",
            briefDescription = "KavaRef class loaded without the host class loader",
            explanation = """
                In a `PackageParam` scope, `toClass`, `lazyClass`, `VariousClass.load` and other class loading functions \
                from KavaRef use the module class loader when no loader is passed. Use the functions \
                from `${DeclaredSymbol.EXTENSION_PACKAGE_NAME}` to load the class from the host.

                See the documentation for more details:
                - English: https://highcapable.github.io/YukiHook/en/library/yukihook-extension-kavaref
                - 简体中文: https://highcapable.github.io/YukiHook/zh-cn/library/yukihook-extension-kavaref

                The `${DeclaredSymbol.EXTENSION_PACKAGE_NAME}` provides:
                - `toClass` and `toClassOrNull` for `String` and `VariousClass` that use `hostClassLoader` by default
                - `lazyClass` and `lazyClassOrNull` that use `hostClassLoader` by default

                These functions have the same names as the KavaRef functions, importing both versions \
                in the same file causes an overload resolution ambiguity.

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
                PackageParamDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val CLASS_LOADING_WITH_HOST_CLASS_LOADER_ISSUE = Issue.create(
            id = "ClassLoadingWithHostClassLoader",
            briefDescription = "KavaRef class loaded with an explicit host class loader",
            explanation = """
                In a `PackageParam` scope, the class loading functions from `${DeclaredSymbol.EXTENSION_PACKAGE_NAME}` \
                use `hostClassLoader` by default, passing it to the KavaRef functions is unnecessary.

                See the documentation for more details:
                - English: https://highcapable.github.io/YukiHook/en/library/yukihook-extension-kavaref
                - 简体中文: https://highcapable.github.io/YukiHook/zh-cn/library/yukihook-extension-kavaref

                The `${DeclaredSymbol.EXTENSION_PACKAGE_NAME}` provides:
                - `toClass` and `toClassOrNull` for `String` and `VariousClass` that use `hostClassLoader` by default
                - `lazyClass` and `lazyClassOrNull` that use `hostClassLoader` by default

                These functions have the same names as the KavaRef functions, importing both versions \
                in the same file causes an overload resolution ambiguity.

                Examples:
                ```kotlin
                // Before
                import com.highcapable.kavaref.extension.lazyClass
                import com.highcapable.kavaref.extension.toClass

                "com.example.Test".toClass(hostClassLoader)
                VariousClass("com.example.Test").load(hostClassLoader)
                lazyClass("com.example.Test") { hostClassLoader }

                // After
                import com.highcapable.yukihookapi.extension.kavaref.lazyClass
                import com.highcapable.yukihookapi.extension.kavaref.toClass

                "com.example.Test".toClass()
                VariousClass("com.example.Test").toClass()
                lazyClass("com.example.Test")
                ```
            """.trimIndent(),
            category = Category.PRODUCTIVITY,
            priority = 5,
            severity = Severity.INFORMATIONAL,
            implementation = Implementation(
                PackageParamDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = context.createExternalOnlyUastHandler(object : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            val methodName = node.methodName ?: return
            when (methodName) {
                in VARIOUS_CLASS_LOADING_FUNCTIONS -> checkVariousClass(node)
                in TO_CLASS_FUNCTIONS -> checkKavaRefFunction(node) { isHostClassLoader() }
                in LAZY_CLASS_FUNCTIONS -> checkKavaRefFunction(node) { isHostClassLoaderInitializer() }
            }
        }

        private fun checkVariousClass(node: UCallExpression) {
            val source = node.sourcePsi as? KtCallExpression ?: return
            val callee = source.calleeExpression ?: return
            val arguments = source.valueArgumentList ?: return
            val replacementName = VARIOUS_CLASS_LOADING_FUNCTIONS[node.methodName] ?: return
            val method = node.resolve() ?: return
            if (method.containingClass?.qualifiedName != VARIOUS_CLASS) return
            if (!source.hasPackageParamReceiver()) return

            val loader = node.loaderArgument(method)
            val location = context.getRangeLocation(callee, 0, arguments, 0)

            when {
                loader == null -> context.report(
                    issue = CLASS_LOADING_WITHOUT_HOST_CLASS_LOADER_ISSUE,
                    scope = node,
                    location = location,
                    message = "`${node.methodName}` does not use `$HOST_CLASS_LOADER`, use `$replacementName` instead",
                    quickfixData = replaceFix(source, method, location, replacementName)
                )
                loader.isHostClassLoader() -> context.report(
                    issue = CLASS_LOADING_WITH_HOST_CLASS_LOADER_ISSUE,
                    scope = node,
                    location = location,
                    message = "`$replacementName` uses `$HOST_CLASS_LOADER` by default, use it instead",
                    quickfixData = replaceFix(source, method, location, replacementName)
                )
            }
        }

        private fun checkKavaRefFunction(node: UCallExpression, isHostLoader: UExpression.() -> Boolean) {
            val source = node.sourcePsi as? KtCallExpression ?: return
            val callee = source.calleeExpression ?: return
            val arguments = source.valueArgumentList ?: return
            val method = node.resolve() ?: return
            if (method.containingClass?.qualifiedName?.substringBeforeLast('.') != KAVAREF_EXTENSION_PACKAGE_NAME) return
            if (method.parameterList.parameters.none { it.name == LOADER_PARAMETER }) return
            if (!source.hasPackageParamReceiver()) return

            val loader = node.loaderArgument(method)
            // A trailing lambda is part of the call, the range has to cover it as well.
            val location = context.getRangeLocation(callee, 0, source.lambdaArguments.lastOrNull() ?: arguments, 0)
            val replacement = "${DeclaredSymbol.EXTENSION_PACKAGE_NAME}.${node.methodName}"
            val importFix = source.importFix(node.methodName ?: return, replacement)

            when {
                loader == null -> context.report(
                    issue = CLASS_LOADING_WITHOUT_HOST_CLASS_LOADER_ISSUE,
                    scope = node,
                    location = location,
                    message = "`${node.methodName}` from KavaRef does not use `$HOST_CLASS_LOADER`, import `$replacement` instead",
                    quickfixData = importFix
                )
                loader.isHostLoader() -> context.report(
                    issue = CLASS_LOADING_WITH_HOST_CLASS_LOADER_ISSUE,
                    scope = node,
                    location = location,
                    message = "`$replacement` uses `$HOST_CLASS_LOADER` by default, import it instead",
                    quickfixData = importFix?.let { fix ->
                        node.withoutLoaderFix(method, location)?.let {
                            LintFix.create().name("Import '$replacement'").composite(fix, it)
                        } ?: fix
                    }
                )
            }
        }

        /**
         * The replacement has the same name as the `KavaRef` function and both imports cannot coexist in one file,
         * so an explicit `KavaRef` import is replaced, and a star import gets an explicit import that takes priority over it.
         */
        private fun KtCallExpression.importFix(functionName: String, replacement: String): LintFix? {
            val directives = containingKtFile.importDirectives
            // An aliased import changes the name used at the call site, it cannot be replaced safely.
            if (directives.any { it.aliasName == functionName }) return null
            val explicitImport = directives.firstOrNull {
                it.aliasName == null && it.importedFqName?.asString() == "$KAVAREF_EXTENSION_PACKAGE_NAME.$functionName"
            }?.importedReference
            val starImport = directives.firstOrNull {
                it.isAllUnder && it.importedFqName?.asString() == KAVAREF_EXTENSION_PACKAGE_NAME
            }
            val (location, text) = when {
                explicitImport != null -> context.getLocation(explicitImport) to replacement
                // The command line fix performer does not add imports, so the new import is written after the star import.
                starImport != null -> context.getLocation(starImport) to "${starImport.text}\nimport $replacement"
                else -> return null
            }

            return LintFix.create()
                .name("Import '$replacement'")
                .replace()
                .range(location)
                .with(text)
                .build()
        }

        private fun UCallExpression.withoutLoaderFix(method: PsiMethod, location: Location): LintFix? {
            val source = sourcePsi as? KtCallExpression ?: return null
            val mapping = context.evaluator.computeArgumentMapping(this, method)
            // Remaining arguments are named, removing the loader would shift positional ones.
            val remaining = valueArguments.mapNotNull { argument ->
                val parameter = mapping[argument]?.name ?: return null
                val text = argument.sourcePsi?.text ?: return null
                "$parameter = $text".takeIf { parameter != LOADER_PARAMETER }
            }
            val typeArguments = source.typeArgumentList?.text.orEmpty()

            return buildReplaceFix(
                "Delete Loader Argument",
                methodName + typeArguments + remaining.joinToString(prefix = "(", postfix = ")"),
                range = location,
                reformat = false
            )
        }

        private fun UCallExpression.loaderArgument(method: PsiMethod) =
            context.evaluator.computeArgumentMapping(this, method).entries.firstOrNull { it.value.name == LOADER_PARAMETER }?.key

        private fun KtCallExpression.hasPackageParamReceiver() = analyzeOrNull {
            containingKtFile.scopeContext(this@hasPackageParamReceiver).implicitReceivers.any {
                it.type.isSubtypeOf(PACKAGE_PARAM_CLASS_ID)
            }
        } == true

        private fun UExpression.isHostClassLoader(): Boolean {
            val expression = skipParenthesizedExprDown()
            val getter = (expression as? UResolvable)?.resolve() as? PsiMethod ?: return false
            if (getter.name !in HOST_CLASS_LOADER_GETTERS || getter.containingClass?.qualifiedName != PACKAGE_PARAM_CLASS) return false

            // Only the current receiver matches the class loader used by the PackageParam overloads.
            val receiver = (expression as? UQualifiedReferenceExpression)?.receiver?.skipParenthesizedExprDown() ?: return true
            return receiver is UThisExpression && receiver.label == null
        }

        private fun UExpression.isHostClassLoaderInitializer(): Boolean {
            val lambda = skipParenthesizedExprDown() as? ULambdaExpression ?: return false
            val expression = (lambda.body as? UBlockExpression)?.expressions?.singleOrNull() ?: lambda.body
            return ((expression as? UReturnExpression)?.returnExpression ?: expression).isHostClassLoader()
        }

        private fun replaceFix(source: KtCallExpression, method: PsiMethod, location: Location, replacementName: String): LintFix? {
            val parameters = method.parameterList.parameters
            // Remaining arguments are named, removing the loader would shift positional ones.
            val remaining = source.valueArguments.mapIndexedNotNull { index, argument ->
                val expression = argument.getArgumentExpression() ?: return null
                val parameter = argument.getArgumentName()?.asName?.asString() ?: parameters.getOrNull(index)?.name ?: return null
                if (argument.isSpread) return null
                "$parameter = ${expression.text}".takeIf { parameter != LOADER_PARAMETER }
            }
            val typeArguments = source.typeArgumentList?.text.orEmpty()

            return buildReplaceFix(
                "Replace with '$replacementName'",
                replacementName + typeArguments + remaining.joinToString(prefix = "(", postfix = ")"),
                "${DeclaredSymbol.EXTENSION_PACKAGE_NAME}.$replacementName",
                range = location,
                reformat = false
            )
        }
    })
}