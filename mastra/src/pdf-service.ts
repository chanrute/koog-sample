import axios from 'axios';
import PDFParse from 'pdf-parse';
import { PdfContent, DocumentChunks } from './types.js';

/**
 * PDF処理サービス
 * PDFのダウンロード、テキスト抽出、チャンク分割を担当
 */
export class PdfService {

  /**
   * URLからPDFファイルをダウンロード
   */
  async downloadPdf(url: string): Promise<Buffer> {
    console.log(`📥 PDFダウンロード中: ${url}`);
    try {
      const response = await axios.get(url, {
        responseType: 'arraybuffer'
      });
      const buffer = Buffer.from(response.data);
      console.log(`✅ PDFダウンロード完了: ${buffer.length} bytes`);
      return buffer;
    } catch (error) {
      throw new Error(`PDFのダウンロードに失敗しました: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * PDFファイルからテキストを抽出
   */
  async extractTextFromPdf(pdfBytes: Buffer): Promise<string> {
    console.log("📖 PDFからテキストを抽出中...");
    try {
      const data = await PDFParse(pdfBytes);
      console.log(`✅ 抽出されたテキスト長: ${data.text.length}文字`);
      return data.text;
    } catch (error) {
      throw new Error(`PDFからのテキスト抽出に失敗しました: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  /**
   * テキストをチャンクに分割
   */
  splitTextIntoChunks(text: string, chunkSize: number = 500): string[] {
    console.log("🔪 テキストをチャンクに分割中...");
    const chunks: string[] = [];
    
    for (let i = 0; i < text.length; i += chunkSize) {
      chunks.push(text.slice(i, i + chunkSize));
    }
    
    console.log(`✅ ${chunks.length}個のチャンクに分割しました`);
    return chunks;
  }

  /**
   * PDF処理の統合メソッド
   */
  async processPdf(url: string): Promise<PdfContent> {
    const pdfBytes = await this.downloadPdf(url);
    const extractedText = await this.extractTextFromPdf(pdfBytes);
    return { pdfBytes, extractedText };
  }

  /**
   * PDFコンテンツからDocumentChunksを作成
   */
  createDocumentChunks(pdfContent: PdfContent, chunkSize: number = 500): DocumentChunks {
    const textChunks = this.splitTextIntoChunks(pdfContent.extractedText, chunkSize);
    return {
      originalText: pdfContent.extractedText,
      textChunks
    };
  }
}