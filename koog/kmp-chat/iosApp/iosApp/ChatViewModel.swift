import Foundation
import KoogChat

@MainActor
class ChatViewModel: ObservableObject {
    @Published var messages: [ChatMessage] = []
    @Published var isLoading = false

    private var chatManager: ChatManager?
    
    init() {
        setupChatManager()
    }
    
    private func setupChatManager() {
        // 環境変数を読み込み
        EnvironmentLoader.loadEnvironment()
        EnvironmentLoader.printLoadedVariables()

        // APIキーを取得
        guard let apiKey = EnvironmentLoader.getVariable("OPENAI_API_KEY"),
              !apiKey.isEmpty else {
            print("⚠️ OPENAI_API_KEY が設定されていません")
            return
        }

        print("✅ OPENAI_API_KEY loaded successfully")
        chatManager = ChatManager(apiKey: apiKey)
    }
    
    func sendMessage(_ text: String) {
        guard let chatManager = chatManager else {
            print("❌ ChatManager が初期化されていません")
            return
        }
        
        isLoading = true
        
        Task {
            do {
                
                let response = try await chatManager.sendMessage(message: text)
                
                // メッセージリストを更新
                messages = chatManager.messages.map { $0 }
                
                print("✅ メッセージ送信完了: \(response)")
            } catch {
                print("❌ メッセージ送信エラー: \(error)")
                
                // エラーメッセージを追加
                let errorMessage = ChatMessage(
                    content: "エラーが発生しました: \(error.localizedDescription)",
                    isUser: false,
                    timestamp: Int64(Date().timeIntervalSince1970 * 1000)
                )
                messages.append(errorMessage)
            }
            
            isLoading = false
        }
    }
    
    func clearHistory() {
        chatManager?.clearHistory()
        messages.removeAll()
    }
    
    func getConversationSummary() -> String {
        return chatManager?.getConversationSummary() ?? "チャット未開始"
    }
}
