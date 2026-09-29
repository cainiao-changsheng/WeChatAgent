package com.wechat.agent.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wechat.agent.ui.theme.WeChatGreen

data class WeChatTab(val route: String, val label: String)

private val DefaultTabs = listOf(
    WeChatTab("chatList", "微信"),
    WeChatTab("contacts", "通讯录"),
    WeChatTab("moments", "发现"),
    WeChatTab("settings", "我")
)

@Composable
fun WeChatBottomBar(
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    tabs: List<WeChatTab> = DefaultTabs
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab.route) },
                icon = {
                    Icon(
                        imageVector = iconFor(tab.route, selected),
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = WeChatGreen,
                    selectedTextColor = WeChatGreen,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

private fun iconFor(route: String, selected: Boolean): ImageVector = when (route) {
    "chatList" -> if (selected) Icons.Filled.Chat else Icons.Outlined.Chat
    "contacts" -> if (selected) Icons.Filled.Contacts else Icons.Outlined.Contacts
    "moments" -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
    "settings" -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    else -> if (selected) Icons.Filled.Chat else Icons.Outlined.Chat
}
