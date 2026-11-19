package com.example.bankflow.presentation.common

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection

fun TextStyle.ltr(): TextStyle = copy(textDirection = TextDirection.Ltr)