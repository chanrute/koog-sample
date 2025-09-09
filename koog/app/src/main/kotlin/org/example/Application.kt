package org.example

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.freemarker.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

// Koog AI Agents imports
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import io.github.cdimascio.dotenv.dotenv

// Koog Ktor Plugin imports
import ai.koog.ktor.Koog
import ai.koog.ktor.aiAgent
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.prompt.structure.json.JsonStructuredData
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.StructureFixingParser


fun main() {
    println("🚀 Koog PDF Recipe Analyzer サーバーを起動中...")
    println("📡 サーバーURL: http://localhost:8080")

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

@Serializable
data class AnalyzeRequest(val pdfUrl: String)

@Serializable
data class ErrorResponse(val error: String)

fun Application.module() {
    configureKoog()
    configureTemplating()
    configureSerialization()
    configureCallLogging()
    configureStatusPages()
    configureRouting()
}

fun Application.configureKoog() {
    println("⚙️ Koogプラグインを設定中...")

    val dotenv = dotenv {
        directory = "../../"
        ignoreIfMalformed = true
        ignoreIfMissing = false
    }

    val apiKey = dotenv["OPENAI_API_KEY"]
        ?: throw IllegalStateException("OPENAI_API_KEY環境変数が設定されていません")

    println("🔑 OpenAI APIキーを読み込み完了")

    install(Koog) {
        llm {
            openAI(apiKey = apiKey)
        }

        agentConfig {
            maxAgentIterations = 10

            prompt {
                system("あなたは料理レシピの専門家です。PDFドキュメントの内容を分析し、レシピが含まれているかを正確に判定してください。")
            }
        }
    }

    println("✅ Koogプラグイン設定完了")
}


fun Application.configureRouting() {
    routing {
        get("/") {
            call.respond(FreeMarkerContent("index.ftl", mapOf("title" to "Koog PDF Recipe Analyzer")))
        }

        post("/analyze") {
            try {
                val request = call.receive<AnalyzeRequest>()
                println("📄 PDF解析リクエストを受信: ${request.pdfUrl}")
                println("🤖 Koog Structured Outputで解析開始...")

                // KoogのaiAgent関数でカスタムストラテジーを使用してPDF解析を実行
                val validationResult = aiAgent(
                    strategy = pdfValidationReActStrategy(),
                    model = OpenAIModels.Chat.GPT5Mini,
                    input = request.pdfUrl
                )

                println("🧠 構造化された解析結果: $validationResult")
                println("✅ 解析完了 - レシピ判定: ${validationResult.isRecipe}, 理由: ${validationResult.reason}")

                // aiAgentの結果を直接レスポンスとして送信
                call.respond(HttpStatusCode.OK, validationResult)
            } catch (e: Exception) {
                println("❌ PDF解析エラー: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("PDF解析に失敗しました: ${e.message}"))
            }
        }
    }
}

private fun pdfValidationReActStrategy() = strategy<String, PdfValidationResult>("pdf-validation-react") {
    val pdfService = PdfService()

    // PDF解析ノード：PDFをダウンロードして内容を抽出し解析
    val downloadAndAnalyzePdf by node<String, PdfValidationResult>("download-analyze-pdf") { pdfUrl ->
        println("📄 PDFをダウンロードしてテキスト抽出中: $pdfUrl")

        try {
            // PDFをダウンロード
            val pdfBytes = pdfService.downloadPdf(pdfUrl)
            println("✅ PDFダウンロード完了: ${pdfBytes.size} bytes")

            // PDFからテキストを抽出
            val extractedText = pdfService.extractTextFromPdf(pdfBytes)
            println("✅ テキスト抽出完了: ${extractedText.length} 文字")

            // PdfValidationResultの構造化出力設定
            val validationStructure = JsonStructuredData.createJsonStructure<PdfValidationResult>(
                examples = PdfValidationResult.getExampleValidations()
            )

            // llm.writeSessionでStructured Outputを使用してPDF内容を解析
            val validation = llm.writeSession {
                updatePrompt {
                    system("""
                        あなたは料理レシピの専門家です。
                        提供されたPDFの実際のテキスト内容を分析し、料理レシピが含まれているかを正確に判定してください。
                        
                        判定基準：
                        - 材料リスト（例：小麦粉、砂糖、卵など）が含まれている
                        - 調理手順（例：混ぜる、焼く、煮るなど）が含まれている
                        - 料理名が明記されている
                        - 分量や調理時間の記載がある
                        
                        以下の構造で判定結果を回答してください：
                        - isRecipe: true/false (レシピかどうか)
                        - reason: 判定理由（具体的にどの要素が見つかったか、または見つからなかったかを説明）
                    """.trimIndent())

                    user("以下のPDFテキスト内容を分析してください：\n\n${extractedText.take(3000)}")
                }

                requestLLMStructured(
                    config = StructuredOutputConfig(
                        default = StructuredOutput.Manual(validationStructure),
                        fixingParser = StructureFixingParser(
                            fixingModel = OpenAIModels.Chat.GPT5Mini,
                            retries = 3
                        )
                    )
                )
            }
            
            validation.getOrThrow().structure
            
        } catch (e: Exception) {
            println("❌ PDF解析でエラーが発生: ${e.message}")
            PdfValidationResult(false, "PDF解析に失敗しました: ${e.message}")
        }
    }
    
    // ワークフローエッジ定義
    edge(nodeStart forwardTo downloadAndAnalyzePdf)
    edge(downloadAndAnalyzePdf forwardTo nodeFinish)
}