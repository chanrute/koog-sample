package chat

import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.dsl.prompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ChatClient(private val apiKey: String) {
    private val baseClient = OpenAILLMClient(apiKey = apiKey)
    private val client = RetryingLLMClient(
        delegate = baseClient,
        config = RetryConfig.PRODUCTION
    )
    
    suspend fun sendMessage(
        message: String,
        chatHistory: List<ChatMessage> = emptyList()
    ): String {
        return try {
            val response = client.execute(
                prompt = buildPrompt(message, chatHistory),
                model = OpenAIModels.Chat.GPT4o
            )
            
            response.firstOrNull()?.content ?: "応答を取得できませんでした"
        } catch (e: Exception) {
            "エラーが発生しました: ${e.message}"
        }
    }
    
    fun sendMessageStream(
        message: String,
        chatHistory: List<ChatMessage> = emptyList()
    ): Flow<String> = flow {
        try {
            val response = client.executeStreaming(
                prompt = buildPrompt(message, chatHistory),
                model = OpenAIModels.Chat.GPT4o
            )
            
            response.collect { chunk ->
                chunk?.let { emit(it) }
            }
        } catch (e: Exception) {
            emit("エラーが発生しました: ${e.message}")
        }
    }
    
    private fun buildPrompt(message: String, chatHistory: List<ChatMessage>) = prompt("chat") {
        system("""
            あなたは親切で知識豊富なAIアシスタントです。
            ユーザーの質問に対して、正確で役立つ回答を提供してください。
            日本語で自然な会話を心がけてください。
        """.trimIndent())
        
        // チャット履歴を追加
        chatHistory.takeLast(10).forEach { msg ->
            if (msg.isUser) {
                user(msg.content)
            } else {
                assistant(msg.content)
            }
        }
        
        // 現在のメッセージを追加
        user(message)
    }
}