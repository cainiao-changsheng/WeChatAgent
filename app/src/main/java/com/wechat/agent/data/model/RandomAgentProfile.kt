package com.wechat.agent.data.model

/** AI 随机生成的好友角色资料（用于「新增 AI 好友」页的随机生成按钮）。 */
data class RandomAgentProfile(
    val name: String,
    val gender: String,
    val age: String,
    val persona: String,
    val globalSettings: String
)
