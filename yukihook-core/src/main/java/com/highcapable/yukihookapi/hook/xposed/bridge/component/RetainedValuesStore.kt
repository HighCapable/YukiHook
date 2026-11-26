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
package com.highcapable.yukihookapi.hook.xposed.bridge.component

import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.context.PackageContext
import com.highcapable.yukihookapi.hook.xposed.bridge.event.ModuleEventDispatcher
import com.highcapable.yukihookapi.hook.xposed.bridge.session.ModuleLifecycleManager
import com.highcapable.yukihookapi.hook.xposed.bridge.session.ModuleReloadable
import com.highcapable.yukihookapi.runtime.state.ModuleStateSpec
import java.lang.ref.Reference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Keeps classloader-neutral values across module instances without retaining module-owned objects.
 *
 * Keys are scoped by the hook entry that declared them, so each package scope owns its own values.
 * Without a managed module session, values are computed and validated but not retained.
 *
 * Collection and map results, and values declared as an Iterable, are frozen into read-only snapshots before they are retained.
 * Arrays, bundles and reference holders are rejected because they cannot be frozen and could receive module-owned objects later.
 */
internal class RetainedValuesStore : ModuleReloadable {

    companion object {

        const val RELOAD_ID = "retained-values-store"

        private val snapshotTypes = setOf(
            classOf<Iterable<*>>(), classOf<Collection<*>>(), classOf<List<*>>(),
            classOf<Set<*>>(), classOf<Map<*, *>>()
        )
    }

    private data class Slot(
        val scope: String,
        val key: String
    )

    private data class SavedResult(
        val slot: Slot,
        val type: String,
        val value: Any?
    ) {

        companion object : ModuleStateSpec<SavedResult> {

            override fun encode(state: SavedResult) = arrayOf(state.slot.scope, state.slot.key, state.type, state.value)

            override fun decode(encoded: Any?): SavedResult {
                val values = encoded as? Array<*> ?: error("Invalid retained value state.")
                check(values.size == 4 && values[0] is String && values[1] is String && values[2] is String) {
                    "Invalid retained value state."
                }

                return SavedResult(Slot(values[0] as String, values[1] as String), values[2] as String, values[3])
            }
        }
    }

    private var events = ModuleEventDispatcher()

    private val usedSlots = mutableSetOf<Slot>()
    private val results = linkedMapOf<Slot, SavedResult>()
    private var restored = emptyMap<Slot, SavedResult>()

    override fun onAttach(events: ModuleEventDispatcher) {
        this.events = events
    }

    override fun onSaveState(recovery: Boolean) = synchronized(this) {
        results.values.map { SavedResult.encode(it) }.toTypedArray()
    }

    override fun onPrepareRestore(appInfo: ApplicationInfo, state: Any?) {
        val values = state as? Array<*> ?: error("Missing retained value state.")

        restored = buildMap {
            values.forEach {
                val result = SavedResult.decode(it)
                check(result.slot !in this) {
                    "Duplicate retained value key '${result.slot.key}' in scope '${result.slot.scope}'"
                }

                put(result.slot, result)
            }
        }
    }

    /**
     * Retains a saved value with the same declared type or computes a new value for this module instance.
     * @param context the hook entry that owns [key].
     * @param type the declared result type, including type arguments and nullability.
     * @param key the versioned key of the result value.
     * @param block computes the value when no saved result has the same type.
     * @return [T]
     * @throws IllegalArgumentException if [key] is blank or already used in the same package scope.
     * @throws IllegalStateException if the result cannot be retained or the module instance is retiring.
     */
    fun <T> retain(context: PackageContext, type: KType, key: String, block: () -> T): T {
        val scope = context.id

        return events.use {
            require(key.isNotBlank()) {
                "Retained result key must not be blank."
            }

            val slot = Slot(scope, key)
            synchronized(this) {
                require(usedSlots.add(slot)) { "Retained result key '$key' is already used in scope '$scope' of this module instance." }
            }

            runCatching {
                val typeSignature = type.toCacheType()
                val saved = restored[slot]
                // Saved values are snapshots frozen by the previous module instance.
                val value = if (typeSignature != null && saved?.type == typeSignature) saved.value else freeze(block(), type)

                ModuleLifecycleManager.checkValue(value)
                if (events.isManaged) when (typeSignature) {
                    null -> YLog.warn(
                        "Retained result key '$key' in scope '$scope' has no stable declared type \"$type\", " +
                            "the value will be computed again after module reload. " +
                            "Declare a named type instead of local, anonymous or generic types"
                    )
                    else -> synchronized(this) { results[slot] = SavedResult(slot, typeSignature, value) }
                }

                @Suppress("UNCHECKED_CAST")
                value as T
            }.getOrElse {
                synchronized(this) { usedSlots.remove(slot) }
                throw it
            }
        }
    }

    private fun freeze(value: Any?, type: KType?): Any? {
        val snapshots = IdentityHashMap<Any, Any>()

        fun KType.elementType(index: Int) = arguments.getOrNull(index)?.type

        fun freezeValue(current: Any?, declared: KType?): Any? {
            if (current == null) return null

            check(!current.javaClass.isArray) {
                "Retained results must not contain arrays, use List instead of ${current.javaClass.simpleName}"
            }
            check(current !is Bundle) {
                "Retained results must not contain Bundle, retain its values instead."
            }
            // Reference holders cannot be frozen and could receive module-owned objects later.
            check(current !is Reference<*> && current !is AtomicReference<*> && current !is AtomicReferenceArray<*> && current !is ThreadLocal<*>) {
                "Retained results must not contain ${current.javaClass.simpleName}, retain the referenced value instead."
            }

            // Other iterable types, such as paths, are values unless they are declared as an Iterable.
            val declaredClass = (declared?.classifier as? KClass<*>)?.javaObjectType
            val isSnapshot = current is Collection<*> || current is Map<*, *> || (current is Iterable<*> && declaredClass == classOf<Iterable<*>>())
            if (!isSnapshot) return current
            snapshots[current]?.let { return it }

            // Only read-only container types describe their elements, other declared types stop at this snapshot.
            val elements = declared?.takeIf { declaredClass in snapshotTypes }
            val snapshot: Any
            when (current) {
                is Map<*, *> -> {
                    val backing = LinkedHashMap<Any?, Any?>(current.size)

                    snapshot = Collections.unmodifiableMap(backing)
                    snapshots[current] = snapshot
                    current.forEach { (key, item) ->
                        backing[freezeValue(key, elements?.elementType(0))] =
                            freezeValue(item, elements?.elementType(1))
                    }
                }
                is Set<*> -> {
                    val backing = LinkedHashSet<Any?>(current.size)

                    snapshot = Collections.unmodifiableSet(backing)
                    snapshots[current] = snapshot
                    current.forEach { backing.add(freezeValue(it, elements?.elementType(0))) }
                }
                else -> {
                    val backing = ArrayList<Any?>()

                    snapshot = Collections.unmodifiableList(backing)
                    snapshots[current] = snapshot
                    (current as Iterable<*>).forEach { backing.add(freezeValue(it, elements?.elementType(0))) }
                }
            }

            check(declaredClass == null || declaredClass.isInstance(snapshot)) {
                "Retained ${current.javaClass.simpleName} is kept as a read-only snapshot, " +
                    "declare it as List, Set, Map, Collection or Iterable instead of ${declaredClass?.simpleName}"
            }
            return snapshot
        }

        return freezeValue(value, type)
    }

    private fun KType.toCacheType(): String? {
        val name = (classifier as? KClass<*>)?.qualifiedName ?: return null
        val parameters = arguments.map { argument ->
            val nestedType = argument.type ?: return@map "*"
            val nested = nestedType.toCacheType() ?: return null
            "${argument.variance}:${nested.length}:$nested"
        }

        return buildString {
            append("1:").append(name.length).append(':').append(name)
            append(if (isMarkedNullable) '?' else '!')
            append('<').append(parameters.size).append(':')
            parameters.forEach { append(it.length).append(':').append(it) }
            append('>')
        }
    }
}