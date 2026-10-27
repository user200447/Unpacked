package com.jassemsendi.unpacked.fixtures

/** Successive instants one minute apart, so a fixture's records are ordered and never collide. */
internal class Ticker(
    private val start: Long,
) {
    private var ticks = 0

    fun next(): Long = start + MINUTE * ticks++

    private companion object {
        const val MINUTE = 60L
    }
}
