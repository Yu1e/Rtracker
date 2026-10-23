package com.readingtracker.service

import java.util.concurrent.ConcurrentHashMap

/**
 * Признаки того, что в читалке что-то происходит (перелистывание, касание).
 * Заполняется службой специальных возможностей, читается службой отслеживания.
 * При ручном таймере учитываются касания в любом приложении: читать могут где угодно.
 */
object ActivitySignal {
    @Volatile
    var accessibilityConnected = false

    /** Идёт ручной таймер: служба спецвозможностей слушает все приложения. */
    @Volatile
    var manual = false

    private val last = ConcurrentHashMap<String, Long>()
    private val counts = ConcurrentHashMap<String, Int>()
    @Volatile private var lastAnyAt = 0L
    @Volatile private var anyCount = 0

    fun touch(pkg: String) {
        last[pkg] = System.currentTimeMillis()
        counts[pkg] = (counts[pkg] ?: 0) + 1
        touchAny()
    }

    fun touchAny() {
        lastAnyAt = System.currentTimeMillis()
        anyCount++
    }

    fun last(pkg: String): Long? = last[pkg]
    fun count(pkg: String): Int = counts[pkg] ?: 0
    fun lastAny(): Long = lastAnyAt
    fun countAny(): Int = anyCount
    fun snapshot(): Map<String, Pair<Int, Long?>> =
        counts.keys.associateWith { (counts[it] ?: 0) to last[it] }
}
