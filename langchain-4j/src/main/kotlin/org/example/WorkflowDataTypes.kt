package org.example

import dev.langchain4j.data.segment.TextSegment
import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable

/**
 * ワークフローで使用するデータ型定義（Koog版に合わせた）
 */

/**
 * TextSegmentのシリアライゼーション可能な代替クラス
 */
@Serializable
data class SerializableTextSegment(
    val text: String,
    val metadata: Map<String, String> = emptyMap()
) : JavaSerializable {
    companion object {
        fun fromTextSegment(segment: TextSegment): SerializableTextSegment {
            // metadataをString型のMapに変換
            val stringMetadata = segment.metadata().toMap().mapValues { it.value.toString() }
            return SerializableTextSegment(
                text = segment.text(),
                metadata = stringMetadata
            )
        }
    }
    
    fun toTextSegment(): TextSegment {
        // シンプルにテキストのみでTextSegmentを作成
        return TextSegment.from(text)
    }
}

@Serializable
data class PdfUrl(val url: String) : JavaSerializable

/**
 * テキストチャンクとそのベクトル表現（簡略化版）
 */
data class ChunkEmbedding(
    val chunkText: String,
    val vectorEmbedding: String // シリアライゼーション可能なString型に変更
) : JavaSerializable {
    companion object {
        fun fromEmbedding(text: String, embedding: Any): ChunkEmbedding {
            return ChunkEmbedding(text, embedding.toString())
        }
    }
    
    override fun toString(): String {
        return "ChunkEmbedding(chunkText='${chunkText.take(50)}...', vectorEmbedding=<embedding>)"
    }
}

/**
 * レシピ検索の中間データと抽出結果の保持
 */
data class RecipeWorkflowData(
    val searchResult: RecipeSearchResult?,
    val extractedRecipe: RecipeEntity?
) : JavaSerializable

data class PdfContent(
    val pdfBytes: ByteArray,
    val extractedText: String
) : JavaSerializable {
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
    
    override fun toString(): String {
        return "PdfContent(pdfBytes=<${pdfBytes.size} bytes>, extractedText=<${extractedText.length} chars>)"
    }
}

data class DocumentChunks(
    val originalText: String,
    val textSegments: List<SerializableTextSegment> // シリアライゼーション可能な代替クラスを使用
) : JavaSerializable {
    companion object {
        fun fromTextSegments(originalText: String, segments: List<TextSegment>): DocumentChunks {
            return DocumentChunks(
                originalText,
                segments.map { SerializableTextSegment.fromTextSegment(it) }
            )
        }
    }
    
    fun toTextSegments(): List<TextSegment> {
        return textSegments.map { it.toTextSegment() }
    }
    
    override fun toString(): String {
        return "DocumentChunks(originalText=<${originalText.length} chars>, textSegments=${textSegments.size} segments)"
    }
}

data class EmbeddedChunks(
    val textSegments: List<SerializableTextSegment>,
    val chunkEmbeddings: List<ChunkEmbedding>
) : JavaSerializable {
    companion object {
        fun fromTextSegments(segments: List<TextSegment>, embeddings: List<ChunkEmbedding>): EmbeddedChunks {
            return EmbeddedChunks(
                segments.map { SerializableTextSegment.fromTextSegment(it) },
                embeddings
            )
        }
    }
    
    fun toTextSegments(): List<TextSegment> {
        return textSegments.map { it.toTextSegment() }
    }
    
    override fun toString(): String {
        return "EmbeddedChunks(textSegments=${textSegments.size} segments, chunkEmbeddings=${chunkEmbeddings.size} embeddings)"
    }
}

data class RecipeSearchResult(
    val searchQuery: String,
    val matchedSegments: List<SerializableTextSegment>, // シリアライゼーション可能な代替クラスを使用
    val allChunkEmbeddings: List<ChunkEmbedding>
) : JavaSerializable {
    companion object {
        fun fromTextSegments(
            searchQuery: String,
            segments: List<TextSegment>,
            embeddings: List<ChunkEmbedding>
        ): RecipeSearchResult {
            return RecipeSearchResult(
                searchQuery,
                segments.map { SerializableTextSegment.fromTextSegment(it) },
                embeddings
            )
        }
    }
    
    fun toMatchedSegments(): List<TextSegment> {
        return matchedSegments.map { it.toTextSegment() }
    }
}

/**
 * 並列実行の結果を統一するための抽出データ
 */
data class ParallelExtractionData(
    val extractedRecipe: RecipeEntity?,
    val totalCookingMinutes: Float?
) : JavaSerializable