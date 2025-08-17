package org.example

import kotlinx.serialization.Serializable
import ai.koog.embeddings.base.Vector
import ai.koog.agents.core.tools.annotations.LLMDescription

/**
 * ワークフローで使用するデータ型定義
 */

@Serializable
data class PdfUrl(val url: String)

/**
 * テキストチャンクとそのベクトル表現
 */
@Serializable
data class ChunkEmbedding(
    val chunkText: String,
    val vectorEmbedding: Vector
)

/**
 * レシピ検索の中間データと抽出結果の保持
 */
@Serializable
data class RecipeWorkflowData(
    val searchResult: RecipeSearchResult,
    val extractedRecipe: RecipeEntity?
)

@Serializable
data class PdfContent(
    val pdfBytes: ByteArray,
    val extractedText: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PdfContent
        if (!pdfBytes.contentEquals(other.pdfBytes)) return false
        if (extractedText != other.extractedText) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pdfBytes.contentHashCode()
        result = 31 * result + extractedText.hashCode()
        return result
    }
}

@Serializable
data class DocumentChunks(
    val originalText: String,
    val textChunks: List<String>
)

@Serializable
data class EmbeddedChunks(
    val textChunks: List<String>,
    val chunkEmbeddings: List<ChunkEmbedding>
)

@Serializable
data class RecipeSearchResult(
    val searchQuery: String,
    val matchedChunks: List<String>,
    val allChunkEmbeddings: List<ChunkEmbedding>
)

@Serializable
data class RecipeExtractionResult(
    val extractedRecipe: RecipeEntity?,
    val totalCookingMinutes: Float? = null
)

/**
 * PDF内容判定結果
 */
@Serializable
@LLMDescription("PDF内容判定の結果")
data class PdfValidationResult(
    @property:LLMDescription("PDFが料理レシピに関する内容かどうか（true: レシピ、false: レシピではない）")
    val isRecipe: Boolean,
    
    @property:LLMDescription("判定の理由や根拠")
    val reason: String
) {
    companion object {
        fun getExampleValidations(): List<PdfValidationResult> {
            return listOf(
                PdfValidationResult(true, "料理名、材料、作り方が記載されている料理レシピです"),
                PdfValidationResult(false, "料理に関する情報が含まれていません")
            )
        }
    }
}

/**
 * 並列実行の結果を統一するための抽出データ
 */
@Serializable
data class ParallelExtractionData(
    val extractedRecipe: RecipeEntity?,
    val totalCookingMinutes: Float?
)