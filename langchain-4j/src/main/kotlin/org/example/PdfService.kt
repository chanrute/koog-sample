package org.example

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentSplitter
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.data.segment.TextSegment
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

/**
 * PDF処理サービス（独自PDFBox実装＋LangChain4j DocumentSplitter使用）
 */
class PdfService {
    
    /**
     * URLからPDFファイルをダウンロード
     */
    suspend fun downloadPdf(url: String): ByteArray {
        val httpClient = HttpClient(CIO)
        try {
            println("📥 PDFダウンロード中: $url")
            return httpClient.get(url).body()
        } catch (e: Exception) {
            throw RuntimeException("PDFのダウンロードに失敗しました: ${e.message}", e)
        } finally {
            httpClient.close()
        }
    }
    
    /**
     * PDFファイルからテキストを抽出
     */
    fun extractTextFromPdf(pdfBytes: ByteArray): String {
        println("📖 PDFからテキストを抽出中...")
        
        val document = Loader.loadPDF(pdfBytes)
        val stripper = PDFTextStripper()
        val extractedText = stripper.getText(document)
        document.close()
        
        println("抽出されたテキスト長: ${extractedText.length}文字")
        return extractedText
    }
    
    /**
     * PDFファイルをLangChain4jのDocumentオブジェクトに変換
     */
    fun parseDocument(pdfBytes: ByteArray): Document {
        println("📖 PDFをLangChain4jドキュメントに変換中...")
        val text = extractTextFromPdf(pdfBytes)
        return Document.from(text)
    }
    
    /**
     * ドキュメントをテキストセグメントに分割（LangChain4jのDocumentSplitters使用）
     */
    fun splitDocument(document: Document, maxChunkSize: Int = 500, maxOverlapSize: Int = 50): List<TextSegment> {
        println("🔪 LangChain4jのDocumentSplittersでドキュメントを分割中...")
        
        val splitter = DocumentSplitters.recursive(maxChunkSize, maxOverlapSize)
        val segments = splitter.split(document)
        
        println("✅ ${segments.size}個のテキストセグメントに分割しました")
        return segments
    }
    
    /**
     * PDFのテキスト内容を取得（従来のAPI互換のため）
     */
    fun extractTextFromDocument(document: Document): String {
        return document.text()
    }
    
    /**
     * URLからPDFをダウンロードしてドキュメントとして解析
     */
    suspend fun downloadAndParseDocument(url: String): Document {
        val pdfBytes = downloadPdf(url)
        return parseDocument(pdfBytes)
    }
    
    /**
     * URLからPDFをダウンロードして分割されたテキストセグメントを取得
     */
    suspend fun downloadAndSplitDocument(
        url: String, 
        maxChunkSize: Int = 500, 
        maxOverlapSize: Int = 50
    ): List<TextSegment> {
        val document = downloadAndParseDocument(url)
        return splitDocument(document, maxChunkSize, maxOverlapSize)
    }
}