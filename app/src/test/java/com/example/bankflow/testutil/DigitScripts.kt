package com.example.bankflow.testutil

fun String.toArabicIndic(): String =
    map { if (it in '0'..'9') '\u0660' + (it - '0') else it }.joinToString("")

fun String.toPersianDigits(): String =
    map { if (it in '0'..'9') '\u06F0' + (it - '0') else it }.joinToString("")

fun String.toFullWidthDigits(): String =
    map { if (it in '0'..'9') '\uFF10' + (it - '0') else it }.joinToString("")

fun String.withArabicDecimalSeparator(): String = replace('.', '\u066B')