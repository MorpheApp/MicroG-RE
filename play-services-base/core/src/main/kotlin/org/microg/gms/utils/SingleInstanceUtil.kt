/*
 * SPDX-FileCopyrightText: 2024 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.utils

private val singleInstanceLock = Any()
private val singleInstanceMap: MutableMap<Class<*>, Any> = hashMapOf()

fun <T : Any> singleInstanceOf(tClass: Class<T>, tCreator: () -> T): T {
    val volatileItem = singleInstanceMap[tClass]
    @Suppress("UNCHECKED_CAST")
    if (volatileItem != null && tClass.isAssignableFrom(volatileItem.javaClass)) return volatileItem as T
    val itemLock = synchronized(singleInstanceLock) {
        val item = singleInstanceMap[tClass]
        if (item != null) {
            @Suppress("UNCHECKED_CAST")
            if (tClass.isAssignableFrom(item.javaClass)) return item as T
            item
        } else {
            val lock = Any()
            singleInstanceMap[tClass] = lock
            lock
        }
    }
    synchronized(itemLock) {
        val item = synchronized(singleInstanceMap) { singleInstanceMap[tClass] }
        if (item == null) throw IllegalStateException()
        @Suppress("UNCHECKED_CAST")
        if (tClass.isAssignableFrom(item.javaClass)) return item as T
        if (item != itemLock) throw IllegalStateException()

        val newItem = tCreator()
        synchronized(singleInstanceMap) {
            singleInstanceMap[tClass] = newItem
        }
        return newItem
    }
}

inline fun <reified T : Any> singleInstanceOf(noinline creator: () -> T): T =
    singleInstanceOf(T::class.java, creator)
