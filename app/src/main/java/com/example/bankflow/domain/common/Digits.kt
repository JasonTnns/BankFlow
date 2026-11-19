package com.example.bankflow.domain.common

fun String.toAsciiDigits(): String {
    val source = this
    return buildString(source.length) {
        for (c in source) {
            val digit = Character.digit(c, 10)
            when {
                c in '0'..'9' -> append(c)
                digit >= 0 -> append('0' + digit)
                c == '\u066B' -> append('.')
                else -> append(c)
            }
        }
    }
}