package chat

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock

@Serializable
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

@Serializable
data class ChatSession(
    val messages: MutableList<ChatMessage> = mutableListOf()
) {
    fun addUserMessage(content: String) {
        messages.add(ChatMessage(content, isUser = true))
    }
    
    fun addBotMessage(content: String) {
        messages.add(ChatMessage(content, isUser = false))
    }
}