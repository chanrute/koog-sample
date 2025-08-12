package org.example

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.UserMessage
import dev.langchain4j.data.message.Content
import dev.langchain4j.data.document.Document
import dev.langchain4j.data.message.UserMessage as UserMessageData
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import org.bsc.langgraph4j.CompileConfig
import org.bsc.langgraph4j.StateGraph
import org.bsc.langgraph4j.state.AgentState
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.concurrent.CompletableFuture

/**
 * LangChain4j + LangGraph4jを使用したPDFレシピ抽出アプリケーション
 * （KoogのAI Strategy Graphに相当するワークフロー）
 * 
 * 主な機能：
 * 1. LangGraph4j StateGraph - 複雑なワークフローの構築
 * 2. LangChain4j Structured Output - JSON構造化データ抽出  
 * 3. LangChain4j Embeddings - 高精度なベクトル類似度検索
 * 4. AiServices - カスタムAIサービスの実行
 * 5. 条件分岐 - 複数ノードの分岐処理
 */

/**
 * PDF判定用のAIサービスインターフェース（PDFファイル直接送信対応）
 */
interface PdfValidator {
    fun validateRecipe(pdfUrl: String): PdfValidationResult
}

/**
 * レシピ抽出用のAIサービスインターフェース
 */
interface RecipeExtractor {
    @UserMessage("""
        あなたは料理レシピの専門家です。
        提供された文書からレシピ情報を正確に抽出してください。
        
        抽出する情報：
        - レシピ名（料理の名前）
        - 材料リスト（材料名、数量、単位）
        
        注意事項：
        - 数量は数値として正確に抽出してください
        - 単位は日本語で記載してください（グラム、個、本、カップ、大さじ、小さじなど）
        - 文書に記載されている情報のみを抽出してください
        
        以下の文書からレシピ情報を抽出してください：
        
        {{it}}
        
        必ずJSON形式のみで回答し、マークダウンは使わないでください。
    """)
    fun extractRecipe(context: String): RecipeEntity
}

/**
 * 調理時間抽出用のAIサービスインターフェース（Function Calling対応）
 */
interface CookingTimeExtractor {
    @UserMessage("""
        あなたは料理レシピの調理時間を分析する専門家です。
        提供された文書から調理時間を抽出し、sumMinutesツールを使って合計時間を計算してください。
        
        手順：
        1. 文書から調理時間を抽出（「約10分」「5〜10分」「2時間」など）
        2. 抽出した時間を分単位の数値のリストに変換
        3. sumMinutesツールを呼び出して合計時間を計算
        4. 結果をJSON形式で返す
        
        以下の文書から調理時間を抽出してください：
        
        {{it}}
        
        必ずJSON形式のみで回答し、マークダウンは使わないでください。
    """)
    fun extractCookingTime(context: String): CookingTimeResult
}

/**
 * LangChain4j + LangGraph4jによるPDFレシピ抽出アプリケーション
 * 
 * LangChain4jの主要機能：
 * 1. LangGraph4j StateGraph - 複雑なワークフローの構築
 * 2. LangChain4j Structured Output - JSON構造化データ抽出  
 * 3. LangChain4j Embeddings - 高精度なベクトル類似度検索
 * 4. AiServices - カスタムAIサービスの実行
 * 5. Function Calling - ツールの実行
 */
class PdfRagApp {
    private val dotenv = dotenv {
        directory = "../"
        ignoreIfMalformed = true
        ignoreIfMissing = false
    }
    private val pdfService = PdfService()
    
    internal fun getOpenAiApiKey(): String {
        return dotenv["OPENAI_API_KEY"]
            ?: throw IllegalStateException("OPENAI_API_KEY環境変数が設定されていません")
    }

    // ==========================================
    // LangChain4j Model API - ChatModel & EmbeddingModel初期化
    // ==========================================
    
    private fun createChatModel(): ChatModel {
        return OpenAiChatModel.builder()
            .apiKey(getOpenAiApiKey())
            .modelName("gpt-4o")
            .temperature(0.1)
            .build()
    }
    
    private fun createEmbeddingModel(): EmbeddingModel {
        return OpenAiEmbeddingModel.builder()
            .apiKey(getOpenAiApiKey())
            .modelName("text-embedding-ada-002")
            .build()
    }
    
    /**
     * PDFファイル直接送信にPdfValidatorの実装を作成
     */
    private fun createPdfValidator(chatModel: ChatModel): PdfValidator {
        return object : PdfValidator {
            override fun validateRecipe(pdfUrl: String): PdfValidationResult {
                println("📝 PDFファイルを直接LLMに送信中: $pdfUrl")
                
                try {
                    // PDFファイルをダウンロードしてDocumentとして読み込み
                    val pdfBytes = runBlocking { pdfService.downloadPdf(pdfUrl) }
                    val document = pdfService.parseDocument(pdfBytes)
                    val extractedText = document.text().take(2000)  // 初期の2000文字を使用
                    
                    val prompt = """
                        あなたは料理レシピの専門家です。
                        提供されたPDFファイルの内容が料理のレシピに関する内容かどうかを判断してください。
                        
                        判断基準:
                        - 料理名、材料、作り方、調理時間などが含まれているか
                        - 料理に関する情報が主な内容となっているか
                        
                        reasonフィールドには必ず日本語で詳しい理由を記載してください。
                        必ずJSON形式のみで回答し、マークダウンは使わないでください。
                        
                        以下のシステムプロンプトの例を従い、正確にJSON形式で出力してください：
                        {
                          "isRecipe": true,
                          "reason": "このPDFには料理名'カレー'、材料リスト、調理手順が含まれており、明らかに料理レシピの内容です。"
                        }
                        
                        以下のPDFの内容から判定してください：
                        
                        $extractedText
                    """.trimIndent()
                    
                    val response = chatModel.chat(prompt)

                    // JSONレスポンスをPdfValidationResultにパーシング
                    return try {
                        com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readValue(response, PdfValidationResult::class.java)
                    } catch (e: Exception) {
                        println("⚠️ JSONパーシングエラー: ${e.message}")
                        PdfValidationResult(true, "JSONパーシングに失敗したため、処理を続行します")
                    }
                } catch (e: Exception) {
                    println("❌ PDFファイル送信エラー: ${e.message}")
                    return PdfValidationResult(true, "エラーが発生したため、処理を続行します")
                }
            }
        }
    }
    
    // ==========================================
    // LangGraph4j AI Strategy Graph - 複雑なワークフロー構築
    // ==========================================
    
    /**
     * ワークフローの状態管理（Koogのストレージキーに相当）
     */
    class WorkflowState(state: MutableMap<String, Any> = mutableMapOf()) : AgentState(state) {
        
        // 状態アクセサー - data()が返すMutableMapを直接操作
        var pdfUrl: PdfUrl?
            get() = data()["pdfUrl"] as? PdfUrl
            set(value) { 
                if (value != null) data()["pdfUrl"] = value else data().remove("pdfUrl")
            }
            
        var validationResult: PdfValidationResult?
            get() = data()["validation"] as? PdfValidationResult
            set(value) { 
                if (value != null) data()["validation"] = value else data().remove("validation")
            }
            
        var pdfContent: PdfContent?
            get() = data()["pdfContent"] as? PdfContent
            set(value) { 
                if (value != null) data()["pdfContent"] = value else data().remove("pdfContent")
            }
            
        var documentChunks: DocumentChunks?
            get() = data()["documentChunks"] as? DocumentChunks
            set(value) { 
                if (value != null) data()["documentChunks"] = value else data().remove("documentChunks")
            }
            
        var embeddedChunks: EmbeddedChunks?
            get() = data()["embeddedChunks"] as? EmbeddedChunks
            set(value) { 
                if (value != null) data()["embeddedChunks"] = value else data().remove("embeddedChunks")
            }
            
        var recipeSearchResult: RecipeSearchResult?
            get() = data()["recipeSearchResult"] as? RecipeSearchResult
            set(value) { 
                if (value != null) data()["recipeSearchResult"] = value else data().remove("recipeSearchResult")
            }
            
        var extractedRecipe: RecipeEntity?
            get() = data()["extractedRecipe"] as? RecipeEntity
            set(value) { 
                if (value != null) data()["extractedRecipe"] = value else data().remove("extractedRecipe")
            }
            
        var totalCookingMinutes: Float?
            get() = data()["totalCookingMinutes"] as? Float
            set(value) { 
                if (value != null) data()["totalCookingMinutes"] = value else data().remove("totalCookingMinutes")
            }
            
        var finalResult: RecipeExtractionResult?
            get() = data()["finalResult"] as? RecipeExtractionResult
            set(value) { 
                if (value != null) data()["finalResult"] = value else data().remove("finalResult")
            }
    }
    
    /**
     * ワークフローグラフを構築（Koogの createRecipeExtractionStrategy に相当）
     */
    fun createRecipeExtractionStrategy(
        embeddingModel: EmbeddingModel,
        validator: PdfValidator,
        recipeExtractor: RecipeExtractor,
        cookingTimeExtractor: CookingTimeExtractor
    ): StateGraph<WorkflowState> {
        val workflow = StateGraph { initialData -> 
            WorkflowState(initialData.toMutableMap()) 
        }
        
        // 1. レシピ判定ノード（KoogのvalidateRecipePdfに相当）- PDFファイル直接送信
        workflow.addNode("validate_recipe_pdf") { state ->
            println("\n📋 PDFファイルを直接送信して判定中: ${state.pdfUrl?.url}")
            
            if (state.pdfUrl == null) {
                println("❌ エラー: pdfUrlが設定されていません")
                val errorValidation = PdfValidationResult(false, "pdfUrlが設定されていないため処理できません")
                return@addNode CompletableFuture.completedFuture(mapOf<String, Any>("validation" to errorValidation))
            }
            
            try {
                // PDFファイルを直接LLMに送信して判定（Koogの方式と同じ）
                val validation = validator.validateRecipe(state.pdfUrl!!.url)
                
                println("🔍 判定結果: ${if (validation.isRecipe) "✅ レシピPDF" else "❌ レシピ以外"}")
                println("📝 理由: ${validation.reason}")
                
                // 状態更新をノードの戻り値として返す
                CompletableFuture.completedFuture(mapOf<String, Any>("validation" to validation))
            } catch (e: Exception) {
                println("❌ PDFファイル判定でエラーが発生: ${e.message}")
                val errorValidation = PdfValidationResult(true, "判定エラーが発生したため、処理を続行します")
                CompletableFuture.completedFuture(mapOf<String, Any>("validation" to errorValidation))
            }
        }
        
        // 2. レシピでない場合の終了ノード（KoogのnotRecipeFinishに相当）
        workflow.addNode("not_recipe_finish") { state ->
            println("❌ このPDFは料理のレシピではありません。処理を終了します。")
            val result = RecipeExtractionResult(null, null)
            CompletableFuture.completedFuture(mapOf<String, Any>("finalResult" to result))
        }
        
        // 3. PDFダウンロード・抽出ノード（KoogのdownloadAndExtractPdfに相当）
        workflow.addNode("download_extract_pdf") { state ->
            val pdfBytes = runBlocking { pdfService.downloadPdf(state.pdfUrl!!.url) }
            val document = pdfService.parseDocument(pdfBytes)
            val extractedText = document.text()
            
            val pdfContent = PdfContent(pdfBytes, extractedText)
            
            CompletableFuture.completedFuture(mapOf<String, Any>("pdfContent" to pdfContent))
        }
        
        // 4. チャンク分割ノード（KoogのsplitIntoChunksに相当）
        workflow.addNode("split_chunks") { state ->
            println("🔪 ドキュメントをチャンクに分割中...")
            
            val document = pdfService.parseDocument(state.pdfContent!!.pdfBytes)
            val textSegments = pdfService.splitDocument(document)
            
            val documentChunks = DocumentChunks.fromTextSegments(state.pdfContent!!.extractedText, textSegments)
            
            CompletableFuture.completedFuture(mapOf<String, Any>("documentChunks" to documentChunks))
        }
        
        // 5. Embedding作成ノード（KoogのcreateEmbeddingsに相当）
        workflow.addNode("create_embeddings") { state ->
            println("🧠 エンベディングを作成中...")
            
            val textSegments = state.documentChunks!!.toTextSegments()
            val chunkEmbeddings = textSegments.map { segment ->
                ChunkEmbedding.fromEmbedding(segment.text(), embeddingModel.embed(segment.text()).content())
            }
            
            val embeddedChunks = EmbeddedChunks.fromTextSegments(textSegments, chunkEmbeddings)
            
            println("✅ ${chunkEmbeddings.size}個のエンベディングを作成しました")
            CompletableFuture.completedFuture(mapOf<String, Any>("embeddedChunks" to embeddedChunks))
        }
        
        // 6. 関連チャンク検索ノード（KoogのfindRelevantChunksに相当）
        workflow.addNode("find_relevant_chunks") { state ->
            println("🔍 レシピ関連のチャンクを検索中...")
            
            val recipeQuery = "レシピ名 材料 分量"
            val allSegments = state.embeddedChunks!!.toTextSegments()
            val relevantSegments = performRagSearch(allSegments, embeddingModel, recipeQuery, 5)
            
            val searchResult = RecipeSearchResult.fromTextSegments(
                recipeQuery,
                relevantSegments,
                state.embeddedChunks!!.chunkEmbeddings
            )
            
            CompletableFuture.completedFuture(mapOf<String, Any>("recipeSearchResult" to searchResult))
        }
        
        // 7. レシピ抽出ノード（KoogのextractRecipeEntityに相当）- 並列ブランチ1
        workflow.addNode("extract_recipe_entity") { state ->
            println("🤖 レシピ情報を抽出中...")
            
            try {
                val context = state.recipeSearchResult!!.toMatchedSegments()
                    .joinToString("\n\n") { "【文書内容】\n${it.text()}" }
                
                val recipe = recipeExtractor.extractRecipe(context)
                
                println("✅ レシピ抽出完了: ${recipe.name}")
                println("材料数: ${recipe.ingredients.size}個")
                
                CompletableFuture.completedFuture(mapOf<String, Any>("extractedRecipe" to recipe))
            } catch (e: Exception) {
                println("❌ レシピ抽出でエラーが発生: ${e.message}")
                // エラー時は状態を更新しない（デフォルトでnullのまま）
                CompletableFuture.completedFuture(mapOf<String, Any>())
            }
        }
        
        // 8. 調理時間抽出ノード（KoogのextractCookingTimeに相当）- 並列ブランチ2
        workflow.addNode("extract_cooking_time") { state ->
            println("🕐 調理時間を抽出中...")
            
            try {
                val timeQuery = "時間 分 調理時間 作業時間 合計"
                val allSegments = state.embeddedChunks!!.toTextSegments()
                val timeRelatedSegments = performRagSearch(allSegments, embeddingModel, timeQuery, 3)
                
                if (timeRelatedSegments.isNotEmpty()) {
                    val context = timeRelatedSegments.joinToString("\n\n") { "【文書内容】\n${it.text()}" }
                    val result = cookingTimeExtractor.extractCookingTime(context)
                    
                    println("🕐 調理時間抽出結果: ${result.totalMinutes}分")
                    CompletableFuture.completedFuture(mapOf<String, Any>("totalCookingMinutes" to result.totalMinutes))
                } else {
                    println("⚠️ 時間関連の情報が見つかりませんでした")
                    // 情報がない場合は状態を更新しない
                    CompletableFuture.completedFuture(mapOf<String, Any>())
                }
            } catch (e: Exception) {
                println("❌ 調理時間抽出でエラーが発生: ${e.message}")
                // エラー時は状態を更新しない
                CompletableFuture.completedFuture(mapOf<String, Any>())
            }
        }
        
        // 9. 並列処理後の結合ノード（LangGraph4jの並列処理パターン）
        workflow.addNode("parallel_join") { state ->
            println("🔄 並列処理結果を統合中...")
            println("⚡ 並列実行完了: レシピ抽出=${state.extractedRecipe != null}, 調理時間=${state.totalCookingMinutes}分")
            CompletableFuture.completedFuture(mapOf<String, Any>())
        }
        
        // 10. 最終結果作成ノード（KoogのcreateFinalResultに相当）
        workflow.addNode("create_final_result") { state ->
            val result = RecipeExtractionResult(
                state.extractedRecipe,
                state.totalCookingMinutes
            )
            CompletableFuture.completedFuture(mapOf<String, Any>("finalResult" to result))
        }
        
        // 11. エッジ定義（Koogのワークフローエッジ + LangGraph4j並列処理パターン）
        // エントリーポイントを設定 - STARTから最初のノードへ
        workflow.addEdge(StateGraph.START, "validate_recipe_pdf")
        
        // 条件分岐：レシピ判定による処理分岐
        workflow.addConditionalEdges(
            "validate_recipe_pdf",
            { state -> 
                CompletableFuture.completedFuture(
                    if (state.validationResult?.isRecipe == true) "download_extract_pdf" else "not_recipe_finish"
                )
            },
            mapOf(
                "download_extract_pdf" to "download_extract_pdf",
                "not_recipe_finish" to "not_recipe_finish"
            )
        )
        
        // メインワークフローチェーン
        workflow.addEdge("download_extract_pdf", "split_chunks")
        workflow.addEdge("split_chunks", "create_embeddings")
        workflow.addEdge("create_embeddings", "find_relevant_chunks")
        
        // 並列実行（LangGraph4j fork-joinパターン）
        // find_relevant_chunks から 2つの並列ブランチに分岐
        workflow.addEdge("find_relevant_chunks", "extract_recipe_entity")
        workflow.addEdge("find_relevant_chunks", "extract_cooking_time")
        
        // 並列ブランチをparallel_joinで合流
        workflow.addEdge("extract_recipe_entity", "parallel_join")
        workflow.addEdge("extract_cooking_time", "parallel_join")
        
        // 最終処理
        workflow.addEdge("parallel_join", "create_final_result")
        
        // 終了ポイントを設定 - 最終ノードからENDへ
        workflow.addEdge("create_final_result", StateGraph.END)
        workflow.addEdge("not_recipe_finish", StateGraph.END)
        
        return workflow
    }
    
    /**
     * RAG検索の実装（元のperformRagSearchメソッド）
     */
    private fun performRagSearch(
        textSegments: List<TextSegment>,
        embeddingModel: EmbeddingModel,
        query: String,
        maxResults: Int = 3
    ): List<TextSegment> {
        println("🔍 クエリ「$query」に関連するコンテンツを検索中...")
        
        // インメモリエンベディングストア作成
        val embeddingStore: EmbeddingStore<TextSegment> = InMemoryEmbeddingStore()
        
        // セグメントのエンベディングを作成してストアに追加
        textSegments.forEach { segment ->
            val embedding = embeddingModel.embed(segment.text()).content()
            embeddingStore.add(embedding, segment)
        }
        
        // ContentRetrieverを作成
        val contentRetriever: ContentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(maxResults)
            .minScore(0.5)
            .build()
        
        // 関連コンテンツを検索
        val relevantContents = contentRetriever.retrieve(dev.langchain4j.rag.query.Query.from(query))
        val relevantSegments = relevantContents.map { it.textSegment() }
        
        println("✅ ${relevantSegments.size}個の関連セグメントを取得しました")
        relevantSegments.forEachIndexed { index, segment ->
            println("  ${index + 1}. ${segment.text().take(100)}...")
        }
        
        return relevantSegments
    }
    
    /**
     * LangGraph4jワークフローを使ったレシピ抽出（KoogのAI Strategy Graph相当）
     */
    fun executeRecipeExtraction(pdfUrl: String): RecipeExtractionResult {
        println("\n🔄 === LangGraph4j AI Strategy Graphワークフロー実行開始 ===")
        
        try {
            // モデル初期化
            val chatModel = createChatModel()
            val embeddingModel = createEmbeddingModel()
            
            // ツールサービス初期化（KoogのCalculateToolに相当）
            val calculateTool = CalculateTool()
            
            // AiServices初期化（Function Calling対応）
            // PDF ValidatorはAiServicesを使わず直接ChatModelで実装
            val validator = createPdfValidator(chatModel)
            val recipeExtractor = AiServices.create(RecipeExtractor::class.java, chatModel)
            val cookingTimeExtractor = AiServices.builder(CookingTimeExtractor::class.java)
                .chatModel(chatModel)
                .tools(calculateTool)  // sumMinutesツールを登録
                .build()
            
            // 初期状態設定 - まず初期データを準備
            val initialInputs = mapOf<String, Any>("pdfUrl" to PdfUrl(pdfUrl))
            
            // LangGraph4jワークフロー作成（KoogのcreateRecipeExtractionStrategy相当）
            val workflow = createRecipeExtractionStrategy(
                embeddingModel,
                validator,
                recipeExtractor,
                cookingTimeExtractor
            )
            
            // ワークフローコンパイル
            val app = workflow.compile(CompileConfig.builder().build())
            
            // ワークフロー実行（KoogのagentのrunWithStrategy相当）
            println("🚀 LangGraph4j StateGraphを実行中...")
            val result = app.invoke(initialInputs)
            
            // 結果を取得 - OptionalからStateを取り出し、finalResultを取得
            return result.orElse(null)?.finalResult ?: RecipeExtractionResult(null, null)
            
        } catch (e: Exception) {
            println("❌ LangGraph4jワークフロー実行中にエラーが発生: ${e.message}")
            e.printStackTrace()
            return RecipeExtractionResult(null, null)
        }
    }
}


/**
 * メイン実行部：LangGraph4j AI Strategy Graphの統合実行
 */
fun main() {
    val app = PdfRagApp()
    
    // PDFファイルをURLで指定（KoogプロジェクトのApp.ktと同じURL）
    val pdfUrl = "https://kyushucgc.co.jp/recipe_pdf/202112/recipe05.pdf"
    
    try {
        // LangGraph4jワークフロー実行（KoogのAI Strategy Graph相当）
        val result = app.executeRecipeExtraction(pdfUrl)
        
        // 結果表示
        ResultDisplayer().displayResults(result)
        
    } catch (e: Exception) {
        println("\n❌ エラーが発生しました: ${e.message}")
        e.printStackTrace()
    }
    
    println("\n🎉 === LangGraph4j AI Strategy Graphテスト完了！ ===")
}