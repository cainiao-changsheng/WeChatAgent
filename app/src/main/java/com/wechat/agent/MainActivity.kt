package com.wechat.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wechat.agent.data.AutoBackupConfig
import com.wechat.agent.ui.components.WeChatBottomBar
import com.wechat.agent.ui.screens.AgentDetailScreen
import com.wechat.agent.ui.screens.AgentSetupScreen
import com.wechat.agent.ui.screens.ChangelogScreen
import com.wechat.agent.ui.screens.ChatListScreen
import com.wechat.agent.ui.screens.ChatScreen
import com.wechat.agent.ui.screens.ComposeMomentScreen
import com.wechat.agent.ui.screens.ContactsScreen
import com.wechat.agent.ui.screens.EditProfileScreen
import com.wechat.agent.ui.screens.LabScreen
import com.wechat.agent.ui.screens.ModelConfigScreen
import com.wechat.agent.ui.screens.MomentsScreen
import com.wechat.agent.ui.screens.MyProfileScreen
import com.wechat.agent.ui.screens.SettingsScreen
import com.wechat.agent.ui.screens.UpdateCheckScreen
import com.wechat.agent.ui.theme.WeChatAgentTheme
import com.wechat.agent.viewmodel.ChatViewModel
import com.wechat.agent.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeChatAgentTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "chatList"
    val chatViewModel: ChatViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val chats by chatViewModel.chats.collectAsState()
    val currentMessages by chatViewModel.currentMessages.collectAsState()
    val streamingContent by chatViewModel.streamingContent.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val moodText by chatViewModel.moodText.collectAsState()
    val agentAvatar by settingsViewModel.agentAvatar.collectAsState()
    val userAvatar by settingsViewModel.userAvatar.collectAsState()
    val agentAvatarUri by settingsViewModel.agentAvatarUri.collectAsState()
    val userAvatarUri by settingsViewModel.userAvatarUri.collectAsState()
    val agentName by settingsViewModel.agentName.collectAsState()
    val userNickname by settingsViewModel.userNickname.collectAsState()
    val agentGender by settingsViewModel.agentGender.collectAsState()
    val agentAge by settingsViewModel.agentAge.collectAsState()
    val agentPersona by settingsViewModel.agentPersona.collectAsState()
    val agentGlobalSettings by settingsViewModel.agentGlobalSettings.collectAsState()
    val nowPlaying by chatViewModel.nowPlaying.collectAsState()
    val momentPosts by chatViewModel.momentsPosts.collectAsState()
    val agentProfiles by settingsViewModel.agentProfiles.collectAsState()
    val currentAgentId by settingsViewModel.currentAgentId.collectAsState()
    val backupConfig by settingsViewModel.backupConfig.collectAsState()

    // 当前 AI 角色变化时，聊天数据、记忆库、朋友圈等一并切换
    LaunchedEffect(currentAgentId) {
        chatViewModel.switchAgent(currentAgentId)
    }

    NavHost(navController = navController, startDestination = "chatList") {
        composable("chatList") {
            ChatListScreen(
                chats = chats, agentAvatar = agentAvatar, agentAvatarUri = agentAvatarUri,
                onChatClick = { chatId -> chatViewModel.selectChat(chatId); navController.navigate("chat/$chatId") },
                onNewChat = { navController.navigate("chat/${chatViewModel.createNewChat()}") },
                onDeleteChat = { chatViewModel.deleteChat(it) },
                bottomBar = {
                    WeChatBottomBar(
                        currentRoute = currentRoute,
                        onTabSelected = { route -> navigateToTab(navController, route) }
                    )
                }
            )
        }

        composable("contacts") {
            ContactsScreen(
                chats = chats,
                profiles = agentProfiles,
                onOpenAgentDetail = { agentId -> navController.navigate("agentDetail/$agentId") },
                onNewFriendClick = { navController.navigate("agentSetup") },
                bottomBar = {
                    WeChatBottomBar(
                        currentRoute = currentRoute,
                        onTabSelected = { route -> navigateToTab(navController, route) }
                    )
                }
            )
        }

        composable("agentSetup") {
            AgentSetupScreen(
                isEdit = false,
                initialName = agentName,
                initialGender = agentGender,
                initialAge = agentAge,
                initialPersona = agentPersona,
                initialGlobalSettings = agentGlobalSettings,
                initialAvatar = agentAvatar,
                initialAvatarUri = agentAvatarUri,
                onBack = { navController.popBackStack() },
                onSave = { name, gender, age, persona, global, avatar, avatarUri ->
                    // 新增角色：追加到角色列表，不覆盖默认角色，并自动切换为新角色
                    settingsViewModel.addAgentProfile(name, gender, age, persona, global, avatar, avatarUri)
                    navController.popBackStack()
                }
            )
        }

        composable("agentSetupEdit") {
            AgentSetupScreen(
                isEdit = true,
                initialName = agentName,
                initialGender = agentGender,
                initialAge = agentAge,
                initialPersona = agentPersona,
                initialGlobalSettings = agentGlobalSettings,
                initialAvatar = agentAvatar,
                initialAvatarUri = agentAvatarUri,
                onBack = { navController.popBackStack() },
                onSave = { name, gender, age, persona, global, avatar, avatarUri ->
                    settingsViewModel.updateCurrentAgentProfile(name, gender, age, persona, global, avatar, avatarUri)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "agentDetail/{agentId}",
            arguments = listOf(navArgument("agentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getString("agentId") ?: return@composable
            val profile = agentProfiles.find { it.id == agentId }
            if (profile == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            // 进入角色详情即切换为该角色：记忆库、聊天、朋友圈全部使用该角色独立数据
            LaunchedEffect(agentId) {
                settingsViewModel.switchAgent(agentId)
            }
            AgentDetailScreen(
                agentId = profile.id,
                agentName = profile.name,
                agentGender = profile.gender,
                agentAge = profile.age,
                agentPersona = profile.persona,
                agentGlobalSettings = profile.globalSettings,
                agentAvatar = profile.avatar,
                agentAvatarUri = profile.avatarUri,
                backupIntervalMinutes = backupConfig.intervalMinutes,
                backupOverwrite = backupConfig.overwriteOld,
                backupOnExit = backupConfig.backupOnExit,
                onBackupConfigChange = { interval, overwrite, onExit ->
                    settingsViewModel.saveBackupConfig(
                        AutoBackupConfig(
                            enabled = interval > 0,
                            intervalMinutes = interval,
                            overwriteOld = overwrite,
                            backupOnExit = onExit
                        )
                    )
                },
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("agentSetupEdit") },
                onSendMessage = {
                    val chatId = chatViewModel.openOrCreateChatWithAgent(profile.id)
                    navController.navigate("chat/$chatId")
                },
                onClearMemory = {
                    // 记忆清除在 AgentDetailScreen 内直接处理（弹 Snackbar 反馈）
                },
                onDeleteAgent = {
                    chatViewModel.deleteChatsByAgent(profile.id)
                    settingsViewModel.deleteAgentProfile(profile.id)
                    navController.popBackStack()
                }
            )
        }

        composable("chat/{chatId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("chatId") ?: return@composable
            val chat = chats.find { it.id == id }
            val chatAgent = chat?.agentId?.let { aid -> agentProfiles.find { it.id == aid } }
            ChatScreen(
                chatTitle = chatAgent?.name ?: chat?.title ?: "对话", messages = currentMessages,
                streamingContent = streamingContent, isLoading = isLoading,
                agentAvatar = agentAvatar, userAvatar = userAvatar,
                agentAvatarUri = agentAvatarUri, userAvatarUri = userAvatarUri,
                moodText = moodText, nowPlaying = nowPlaying,
                onBack = {
                    chatViewModel.backupOnExit()
                    navController.popBackStack()
                },
                onSendMessage = { chatViewModel.sendMessage(it) },
                onSendImage = { chatViewModel.sendImageMessage(it) },
                onPlayMusic = { chatViewModel.playMusic() },
                onPauseMusic = { chatViewModel.pauseMusic() },
                onSkipNext = { chatViewModel.skipNextMusic() },
                onSkipPrev = { chatViewModel.skipPrevMusic() },
                onOpenMusicApp = { chatViewModel.openMusicApp() },
                onAvatarClick = {
                    val aid = chat?.agentId
                    if (!aid.isNullOrBlank()) navController.navigate("agentDetail/$aid")
                },
                onTypingChange = { chatViewModel.setUserTyping(it) }
            )
        }

        composable("moments") {
            MomentsScreen(
                agentAvatar = agentAvatar, agentAvatarUri = agentAvatarUri,
                agentName = agentName,
                userAvatar = userAvatar, userAvatarUri = userAvatarUri,
                posts = momentPosts, isLoading = false,
                showBack = false,
                onBack = { navController.popBackStack() },
                onComposeMoment = { navController.navigate("composeMoment") },
                onToggleLike = { chatViewModel.toggleLike(it) },
                onAddComment = { postId, comment -> chatViewModel.addComment(postId, comment) },
                onDeletePost = { chatViewModel.deleteMomentPost(it) },
                bottomBar = {
                    WeChatBottomBar(
                        currentRoute = currentRoute,
                        onTabSelected = { route -> navigateToTab(navController, route) }
                    )
                }
            )
        }

        composable("composeMoment") {
            ComposeMomentScreen(
                onBack = { navController.popBackStack() },
                onPublish = { content, imageUri ->
                    chatViewModel.postUserMoment(content, imageUri)
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            MyProfileScreen(
                userAvatar = userAvatar, userAvatarUri = userAvatarUri,
                userNickname = userNickname,
                showBack = false,
                onBack = { navController.popBackStack() },
                onEditProfile = { navController.navigate("editProfile") },
                onOpenMoments = { navigateToTab(navController, "moments") },
                onOpenSettings = { navController.navigate("settingsDetail") },
                bottomBar = {
                    WeChatBottomBar(
                        currentRoute = currentRoute,
                        onTabSelected = { route -> navigateToTab(navController, route) }
                    )
                }
            )
        }

        composable("settingsDetail") {
            SettingsScreen(
                showBack = true,
                onBack = { navController.popBackStack() },
                onOpenModelConfig = { navController.navigate("modelConfig") },
                onOpenUpdateCheck = { navController.navigate("updateCheck") },
                onOpenChangelog = { navController.navigate("changelog") },
                onOpenLab = { navController.navigate("lab") }
            )
        }

        composable("lab") {
            LabScreen(
                onBack = { navController.popBackStack() },
                onOpenModelConfig = { navController.navigate("modelConfig") }
            )
        }

        composable("modelConfig") {
            ModelConfigScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("updateCheck") {
            UpdateCheckScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("changelog") {
            ChangelogScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("editProfile") {
            EditProfileScreen(
                userAvatar = userAvatar,
                userAvatarUri = userAvatarUri,
                userNickname = userNickname,
                onBack = { navController.popBackStack() },
                onSave = { avatar, avatarUri, nickname ->
                    if (avatar.isNotEmpty() || avatarUri.isNotEmpty()) {
                        settingsViewModel.saveUserAvatar(avatar, avatarUri)
                    }
                    if (nickname.isNotBlank()) settingsViewModel.saveUserNickname(nickname)
                    navController.popBackStack()
                }
            )
        }
    }
}

private fun navigateToTab(navController: androidx.navigation.NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
