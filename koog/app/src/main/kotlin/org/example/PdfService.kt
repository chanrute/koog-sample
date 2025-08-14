package org.example

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.rendering.ImageType
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.util.logging.Logger
import java.util.logging.Level

/**
 * PDF処理サービス
 * PDFのダウンロード、テキスト抽出、チャンク分割を担当
 */
class PdfService {
    
    init {
        // PDFBoxのフォント警告ログを抑制
        Logger.getLogger("org.apache.fontbox").level = Level.SEVERE
        Logger.getLogger("org.apache.pdfbox").level = Level.SEVERE
    }

    /**
     * URLからPDFファイルをダウンロード
     */
    suspend fun downloadPdf(url: String): ByteArray {
        val httpClient = HttpClient(CIO)
        try {
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
        println("PDFからテキストを抽出中...")

        val document = Loader.loadPDF(pdfBytes)

        val stripper = PDFTextStripper()
        val extractedText = stripper.getText(document)

        document.close()

        println("抽出されたテキスト長: ${extractedText.length}文字")
        return extractedText
    }

    /**
     * PDFの最初のページを画像に変換
     */
    fun convertPdfToImage(pdfBytes: ByteArray): ByteArray {
        println("PDFを画像に変換中...")
        
        // ログレベルを一時的にOFFに設定
        val rootLogger = Logger.getLogger("")
        val originalLevel = rootLogger.level
        val fontboxLogger = Logger.getLogger("org.apache.fontbox")
        val pdfboxLogger = Logger.getLogger("org.apache.pdfbox")
        val originalFontboxLevel = fontboxLogger.level
        val originalPdfboxLevel = pdfboxLogger.level
        
        try {
            rootLogger.level = Level.OFF
            fontboxLogger.level = Level.OFF
            pdfboxLogger.level = Level.OFF
            
            val document = Loader.loadPDF(pdfBytes)
            try {
                val renderer = PDFRenderer(document)
                // 最初のページのみを300DPIで画像に変換
                val image: BufferedImage = renderer.renderImageWithDPI(0, 300.0f, ImageType.RGB)
                
                val outputStream = ByteArrayOutputStream()
                ImageIO.write(image, "png", outputStream)
                val imageBytes = outputStream.toByteArray()
                
                println("PDF画像変換完了: ${imageBytes.size}バイト")
                return imageBytes
            } finally {
                document.close()
            }
        } finally {
            // ログレベルを元に戻す
            rootLogger.level = originalLevel
            fontboxLogger.level = originalFontboxLevel
            pdfboxLogger.level = originalPdfboxLevel
        }
    }

    /**
     * テキストをチャンクに分割
     */
    fun splitTextIntoChunks(text: String, chunkSize: Int = 500): List<String> {
        println("テキストをチャンクに分割中...")
        val chunks = text.chunked(chunkSize)
        println("${chunks.size}個のチャンクに分割しました")
        return chunks
    }
}