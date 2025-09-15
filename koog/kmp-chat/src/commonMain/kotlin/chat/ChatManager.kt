package chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatManager(apiKey: String) {
    private val client = ChatClient(apiKey)
    private val session = ChatSession()
    
    val messages: List<ChatMessage>
        get() = session.messages.toList()
    
    suspend fun sendMessage(message: String): String {
        // ユーザーメッセージを追加
        session.addUserMessage(message)
        
        // DeepSeekに送信して応答を取得
        val response = client.sendMessage(message, session.messages)
        
        // ボットの応答を追加
        session.addBotMessage(response)
        
        return response
    }
    
    fun sendMessageStream(message: String): Flow<String> {
        // ユーザーメッセージを追加
        session.addUserMessage(message)

        var fullResponse = ""

        return client.sendMessageStream(message, session.messages).map { chunk ->
            fullResponse += chunk

            // ストリームが完了したらボットの応答を追加
            if (chunk.isEmpty()) {
                session.addBotMessage(fullResponse)
            }

            chunk
        }
    }

    fun clearHistory() {
        session.messages.clear()
    }

    fun getConversationSummary(): String {
        val messageCount = session.messages.size
        val userMessages = session.messages.count { it.isUser }
        val botMessages = session.messages.count { !it.isUser }
        
        return "メッセージ数: $messageCount (ユーザー: $userMessages, AI: $botMessages)"
    }
}