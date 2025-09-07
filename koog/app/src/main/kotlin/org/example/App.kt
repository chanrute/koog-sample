package org.example

// Koog Agent API - エージェントとストラテジー
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent

// Koog Structured Data API
import ai.koog.prompt.structure.json.JsonStructuredData
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.StructureFixingParser

// Koog Embedding API
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLMCapability
import ai.koog.embeddings.base.Vector

// Kotlin standard library
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * KoogライブラリによるPDFレシピ抽出アプリケーション
 * 
 * Koogの主要機能：
 * 1. AI Strategy Graph - 複雑なワークフローの構築
 * 2. Structured Output - JSON構造化データ抽出
 * 3. LLM Embedder - 高精度なベクトル類似度検索
 * 4. Tool Registry - カスタムツールの実行
 * 5. 並列実行 - 複数ノードの同時処理
 */
class PdfRagApp {
    private val dotenv = dotenv {
        directory = "../../"
        ignoreIfMalformed = true
        ignoreIfMissing = false
    }
    private val pdfService = PdfService()

    internal fun getOpenAiApiKey(): String {
        return dotenv["OPENAI_API_KEY"]
            ?: throw IllegalStateException("OPENAI_API_KEY環境変数が設定されていません")
    }


    // ==========================================
    // Koog Embedding API - 高精度なベクトル検索
    // ==========================================
    
    private suspend fun createLLMEmbedder(): LLMEmbedder {
        val openAiApiKey = getOpenAiApiKey()
        val client = OpenAILLMClient(openAiApiKey)
        val lModel = LLModel(
            LLMProvider.OpenAI,
            "text-embedding-ada-002",
            capabilities = listOf(LLMCapability.Embed),
            contextLength = 8192L
        )
        return LLMEmbedder(client, lModel)
    }

    suspend fun createOpenAIEmbeddings(chunks: List<String>): List<ChunkEmbedding> {
        println("🧠 KoogのLLMEmbedderを使ったOpenAI Embeddingを作成中...")
        return try {
            val embedder = createLLMEmbedder()
            val embeddings = chunks.map { chunk ->
                val embedding = embedder.embed(chunk) as Vector
                ChunkEmbedding(chunk, embedding)
            }
            println("✅ ${embeddings.size}個のKoog LLMEmbeddingを作成しました")
            embeddings
        } catch (e: Exception) {
            println("❌ Koog LLMEmbedder作成でエラーが発生: ${e.message}")
            emptyList()
        }
    }

    suspend fun createQueryEmbedding(query: String): Vector? {
        return try {
            val embedder = createLLMEmbedder()
            println("  クエリのEmbedding生成中...")
            embedder.embed(query)
        } catch (e: Exception) {
            println("❌ クエリEmbedding作成でエラーが発生: ${e.message}")
            null
        }
    }

    suspend fun findRelevantChunks(
        queryEmbedding: Vector,
        embeddings: List<ChunkEmbedding>,
        topK: Int = 3
    ): List<String> {
        val embedder = createLLMEmbedder()
        val similarities = embeddings.map { chunkEmbedding ->
            val diff = embedder.diff(queryEmbedding, chunkEmbedding.vectorEmbedding)
            chunkEmbedding.chunkText to diff.toDouble()
        }
        val topChunks = similarities.sortedBy { it.second }.take(topK).map { it.first }
        
        println("⏱️ チャンクの内容:")
        topChunks.forEachIndexed { index, chunk ->
            println("  ${index + 1}. ${chunk.take(100)}...")
        }
        return topChunks
    }

    // ==========================================
    // Koog Structured Output - JSON構造化データ抽出
    // ==========================================
    
    suspend fun generateAnswerWithAgent(relevantChunks: List<String>): RecipeEntity? {
        println("🤖 AIAgentのStructured Outputを使ってRecipeEntityを抽出中...")
        
        return try {
            val apiKey = getOpenAiApiKey()
            val context = relevantChunks.joinToString("\n\n") { "【文書内容】\n$it" }
            val exampleRecipes = RecipeEntity.getExampleRecipes()

            // Koogの構造化出力設定
            val recipeStructure = JsonStructuredData.createJsonStructure<RecipeEntity>(
                examples = exampleRecipes
            )

            // Strategy内でstructured outputを使用
            val recipeExtractionStrategy = strategy<String, RecipeEntity>("recipe-extraction-strategy") {
                val extractRecipe by node<String, RecipeEntity>("extract-recipe") { input ->
                    llm.writeSession {
                        updatePrompt {
                            user("以下の文書からレシピ情報を抽出してください：\n\n$input")
                        }

                        val result = requestLLMStructured(
                            config = StructuredOutputConfig(
                                default = StructuredOutput.Manual(recipeStructure),
                                fixingParser = StructureFixingParser(
                                    fixingModel = OpenAIModels.Chat.GPT4o,
                                    retries = 3
                                )
                            )
                        )
                        result.getOrThrow().structure
                    }
                }

                edge(nodeStart forwardTo extractRecipe)
                edge(extractRecipe forwardTo nodeFinish)
            }

            val agentConfig = AIAgentConfig(
                prompt = prompt("recipe-extraction") {
                    system("""
                        あなたは料理レシピの専門家です。
                        提供された文書からレシピ情報を正確に抽出してください。

                        抽出する情報：
                        - レシピ名（料理の名前）
                        - 材料リスト（材料名、数量、単位）

                        注意事項：
                        - 数量は数値として正確に抽出してください
                        - 単位は日本語で記載してください（グラム、個、本、カップ、大さじ、小さじなど）
                        - 文書に記載されている情報のみを抽出してください
                    """.trimIndent())
                },
                model = OpenAIModels.Chat.GPT4o,
                maxAgentIterations = 10
            )

            val agent = AIAgent(
                promptExecutor = simpleOpenAIExecutor(apiKey),
                strategy = recipeExtractionStrategy,
                agentConfig = agentConfig
            )

            val result = agent.run(context)

            println("✅ AIAgent Structured Output RecipeEntity抽出完了")
            println("抽出結果: $result")
            
            result
        } catch (e: Exception) {
            println("❌ AIAgent RecipeEntity抽出でエラーが発生: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // ==========================================
    // Koog AI Strategy Graph - 複雑なワークフロー構築
    // ==========================================
    
    fun createRecipeExtractionStrategy() = strategy<PdfUrl, RecipeExtractionResult>("recipe-extraction") {
        // 1. ストレージキーの定義
        val recipeDataKey = createStorageKey<RecipeWorkflowData>("recipeData")
        val validationKey = createStorageKey<PdfValidationResult>("validation")

        // 2. 終了ノード：レシピでない場合の処理
        val notRecipeFinish by node<ValidatedPdfContent, RecipeExtractionResult>("not-recipe-finish") { _ ->
            println("❌ このPDFは料理のレシピではありません。処理を終了します。")
            RecipeExtractionResult(null, null)
        }

        // 3. ノード定義：レシピ判定（KoogのStructured Output使用）
        val validateRecipePdf by node<PdfUrl, ValidatedPdfContent>("validate-recipe-pdf") { pdfUrl ->
            println("\n📋 PDFの内容を判定中: ${pdfUrl.url}")

            try {
                val pdfBytes = pdfService.downloadPdf(pdfUrl.url)

                // Koogの構造化出力でPDF判定（Strategy内でrequestLLMStructuredを使用）
                val validationStructure = JsonStructuredData.createJsonStructure<PdfValidationResult>(
                    examples = PdfValidationResult.getExampleValidations()
                )

                val validation = try {
                    val result = llm.writeSession {
                        updatePrompt {
                            user(
                                content = "添付されたPDFファイルから、料理のレシピに関する情報が含まれているかを判定してください。",
                                attachments = listOf(
                                    Attachment.File(
                                        content = AttachmentContent.Binary.Bytes(pdfBytes),
                                        format = "pdf",
                                        mimeType = "application/pdf",
                                        fileName = "recipe.pdf"
                                    )
                                )
                            )
                        }
                        
                        requestLLMStructured(
                            config = StructuredOutputConfig(
                                default = StructuredOutput.Manual(validationStructure),
                                fixingParser = StructureFixingParser(
                                    fixingModel = OpenAIModels.Chat.GPT4o,
                                    retries = 3
                                )
                            )
                        )
                    }
                    result.getOrThrow().structure
                } catch (e: Exception) {
                    println("❌ Strategy内PDF判定でエラーが発生: ${e.message}")
                    PdfValidationResult(true, "PDF判定に失敗したため、処理を続行します")
                }

                println("🔍 判定結果: ${if (validation.isRecipe) "✅ レシピPDF" else "❌ レシピ以外"}")
                println("📝 理由: ${validation.reason}")

                storage.set(validationKey, validation)
                ValidatedPdfContent(pdfUrl.url, pdfBytes, validation.isRecipe)
                
            } catch (e: Exception) {
                println("❌ PDF内容判定でエラーが発生: ${e.message}")
                throw e
            }
        }

        // 4. 基本的なノード群：データ処理パイプライン
        val extractTextFromPdf by node<ValidatedPdfContent, PdfContent>("extract-text-from-pdf") { validatedPdf ->
            println("\n📄 PDFからテキストを抽出中...")
            val extractedText = pdfService.extractTextFromPdf(validatedPdf.pdfBytes)
            PdfContent(validatedPdf.pdfBytes, extractedText)
        }

        val splitIntoChunks by node<PdfContent, DocumentChunks>("split-chunks") { pdfContent ->
            val chunks = pdfService.splitTextIntoChunks(pdfContent.extractedText)
            DocumentChunks(pdfContent.extractedText, chunks)
        }

        val createEmbeddings by node<DocumentChunks, EmbeddedChunks>("create-embeddings") { documentChunks ->
            val embeddings = createOpenAIEmbeddings(documentChunks.textChunks)
            EmbeddedChunks(documentChunks.textChunks, embeddings)
        }


        // 5. ベクトル類似度検索ノード（Koog LLMEmbedder使用）
        val findRelevantChunks by node<EmbeddedChunks, RecipeSearchResult>("find-relevant-chunks") { embeddedChunks ->
            val recipeQuery = "レシピ名 材料 分量"
            println("🔍 KoogのLLMEmbedderを使ってクエリに関連するチャンクを検索中: '$recipeQuery'")
            val queryEmbedding = createQueryEmbedding(recipeQuery)
            val matchedChunks = if (queryEmbedding != null) {
                findRelevantChunks(queryEmbedding, embeddedChunks.chunkEmbeddings)
            } else {
                emptyList()
            }
            val result = RecipeSearchResult(recipeQuery, matchedChunks, embeddedChunks.chunkEmbeddings)
            storage.set(recipeDataKey, RecipeWorkflowData(result, null))
            result
        }


        // 6. 並列実行ノード群（Koogの並列処理）
        val extractRecipeEntity by node<RecipeSearchResult, ParallelExtractionData>("extract-recipe-entity") { searchResult ->
            val recipeEntity = generateAnswerWithAgent(searchResult.matchedChunks)
            ParallelExtractionData(recipeEntity, null)
        }

        val extractCookingTime by node<RecipeSearchResult, ParallelExtractionData>("extract-cooking-time") { searchResult ->
            println("\n🔍 料理時間関連のチャンクを検索中...")

            val timeQuery = "時間 分 調理時間 作業時間 合計"
            val timeQueryEmbedding = createQueryEmbedding(timeQuery)
            val timeRelatedChunks = if (timeQueryEmbedding != null) {
                findRelevantChunks(timeQueryEmbedding, searchResult.allChunkEmbeddings, topK = 3)
            } else {
                emptyList()
            }

            // Koogのツール実行機能を使用
            val response = llm.writeSession {
                updatePrompt {
                    system("""
                        あなたは料理レシピの調理時間を分析する専門家です。
                        提供された文書から料理にかかる時間（分）を抽出してください。
                        必ずsumMinutesツールを使って合計時間を計算してください。

                        注意事項：
                        - 「約10分」のような表現は10として扱ってください
                        - 「5〜10分」のような範囲は最大値（10）を使用してください
                        - 時間が明記されていない場合は、一般的な調理時間を推定してください
                    """.trimIndent())

                    user("""
                        以下の文書から調理時間を抽出し、sumMinutesツールを使って合計時間を計算してください：

                        ${timeRelatedChunks.joinToString("\n\n") { "【文書内容】\n$it" }}

                        必ずsumMinutesツールを呼び出して時間を合計してください。
                    """.trimIndent())
                }
                requestLLM()
            }

            // ツール実行結果から調理時間を抽出
            val cookingMinutes = try {
                if (response is Message.Tool.Call && response.tool == "sumMinutes") {
                    // SumMinutesArgsをKotlinxシリアライゼーションでデシリアライズ
                    val sumArgs = Json.decodeFromString<SumMinutesArgs>(response.content)
                    
                    // CalculateToolを実際に実行して結果を取得
                    val sumTool = CalculateTool()
                    val toolResult = sumTool.sum(sumArgs)
                    
                    println("🕐 ツール実行結果: ${sumArgs.minutes.joinToString(" + ")} = 分")
                    toolResult
                } else null
            } catch (e: Exception) {
                println("⚠️ 調理時間の取得中にエラー: ${e.message}")
                null
            }

            ParallelExtractionData(null, cookingMinutes)
        }

        // 7. 並列実行コントローラー（Koogの並列処理機能）
        val extractInParallel by parallel(
            extractRecipeEntity, extractCookingTime
        ) {
            // foldによる並列結果の統合
            fold(ParallelExtractionData(null, null)) { acc, extractionData ->
                ParallelExtractionData(
                    extractionData.extractedRecipe ?: acc.extractedRecipe,
                    extractionData.totalCookingMinutes ?: acc.totalCookingMinutes
                )
            }
        }

        // 8. 最終結果作成ノード
        val createFinalResult by node<ParallelExtractionData, RecipeExtractionResult>("create-final-result") { extractionData ->
            RecipeExtractionResult(extractionData.extractedRecipe, extractionData.totalCookingMinutes)
        }

        // 9. ワークフローエッジ定義（Koogの条件分岐機能）
        edge(nodeStart forwardTo validateRecipePdf)

        // 条件分岐：レシピ判定による処理分岐
        edge(
            (validateRecipePdf forwardTo extractTextFromPdf)
                onCondition { validatedPdf ->
                    validatedPdf.isValidated
                }
        )
        edge(
            (validateRecipePdf forwardTo notRecipeFinish)
                onCondition { validatedPdf ->
                    !validatedPdf.isValidated
                }
        )

        // メインワークフローチェーン
        edge(extractTextFromPdf forwardTo splitIntoChunks)
        edge(splitIntoChunks forwardTo createEmbeddings)
        edge(createEmbeddings forwardTo findRelevantChunks)
        edge(findRelevantChunks forwardTo extractInParallel)
        edge(extractInParallel forwardTo createFinalResult)
        edge(createFinalResult forwardTo nodeFinish)
        edge(notRecipeFinish forwardTo nodeFinish)
    }
}

// ==========================================
// メイン実行部：Koogライブラリの統合実行
// ==========================================

fun main() {
    runBlocking {
        val app = PdfRagApp()
        val apiKey = app.getOpenAiApiKey()
        // PDFファイルをURLで指定
        val pdfUrl = "https://kyushucgc.co.jp/recipe_pdf/202112/recipe05.pdf"

        try {
            // Koogエージェント設定
            val agentConfig = AIAgentConfig(
                prompt = prompt("recipe-extraction-prompt") {
                    system("あなたは料理レシピ分析の専門家です。PDFドキュメントから料理レシピ情報を正確に抽出し、構造化されたデータとして整理する能力を持っています。")
                },
                model = OpenAIModels.Chat.GPT4o,
                maxAgentIterations = 25
            )

            // KoogのAI Strategy Graphとツール登録
            val recipeExtractionStrategy = app.createRecipeExtractionStrategy()
            val toolRegistry = ToolRegistry { tool(CalculateTool()) }

            // メインエージェントの作成と実行
            val agent = AIAgent(
                promptExecutor = simpleOpenAIExecutor(apiKey),
                toolRegistry = toolRegistry,
                strategy = recipeExtractionStrategy,
                agentConfig = agentConfig
            )

            println("\n🔄 === Koogワークフロー実行開始 ===")
            val result = agent.run(PdfUrl(pdfUrl))

            // 結果表示
            ResultDisplayer().displayResults(result)

        } catch (e: Exception) {
            println("\n❌ エラーが発生しました: ${e.message}")
            e.printStackTrace()
        }

        println("\n🎉 === KoogのAIStrategy Graphテスト完了！ ===")
    }
}

