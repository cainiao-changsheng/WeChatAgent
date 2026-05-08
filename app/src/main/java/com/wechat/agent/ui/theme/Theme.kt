package com.wechat.agent.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = WeChatGreen,
    onPrimary = White,
    primaryContainer = WeChatGreenLight,
    secondary = WeChatGreenDark,
    background = WeChatBg,
    surface = White,
    onBackground = Black,
    onSurface = Black,
    surfaceVariant = Gray50
)

private val DarkColorScheme = darkColorScheme(
    primary = WeChatGreen,
    onPrimary = White,
    primaryContainer = DarkSelfBubble,
    secondary = WeChatGreenDark,
    background = WeChatBgDark,
    surface = DarkOtherBubble,
    onBackground = White,
    onSurface = White,
    surfaceVariant = Gray900
)

@Composable
fun WeChatAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
