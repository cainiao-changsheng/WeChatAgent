package com.wechat.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 自定义顶部导航栏：标题绝对居中于整个屏幕宽度，
 * 不受左侧返回按钮 / 右侧 actions 宽度影响。
 * 返回按钮靠左、actions 靠右、标题永远在正中（微信式布局）。
 */
@Composable
fun CenteredTopBar(
    content: @Composable () -> Unit,
    fullWidthContent: (@Composable () -> Unit)? = null,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    actions: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .background(containerColor)
    ) {
        if (fullWidthContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 48.dp)
            ) {
                fullWidthContent()
            }
        } else {
            Box(Modifier.align(Alignment.Center)) {
                content()
            }
        }
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy((-8).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()
        }
    }
}
