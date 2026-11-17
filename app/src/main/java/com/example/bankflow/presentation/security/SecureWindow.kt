package com.example.bankflow.presentation.security

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.example.bankflow.presentation.common.findActivity

@Composable
fun SecureWindowEffect(secure: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, secure) {
        val window = view.context.findActivity()?.window
        if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}