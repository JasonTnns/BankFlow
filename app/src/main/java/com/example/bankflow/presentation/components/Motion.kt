package com.example.bankflow.presentation.components

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.example.bankflow.presentation.common.ltr


@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun AnimatedAmount(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(durationMillis = 220, delayMillis = 60)) togetherWith fadeOut(tween(durationMillis = 120))
            }
        },
        label = "amount",
    ) { value -> Text(text = value, style = style.ltr()) }
}