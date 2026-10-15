package com.example.tvim

object VersionOrder {
    fun isNewer(candidate: String, current: String): Boolean {
        fun parse(value: String): List<Int>? {
            val version = value.removePrefix("v")
            if (!version.matches(Regex("[0-9]+(\\.[0-9]+)*"))) return null
            return version.split('.').map { it.toIntOrNull() ?: return null }
        }
        val left = parse(candidate) ?: return false
        val right = parse(current) ?: return false
        for (i in 0 until maxOf(left.size, right.size)) {
            val comparison = (left.getOrElse(i) { 0 }).compareTo(right.getOrElse(i) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }
}
