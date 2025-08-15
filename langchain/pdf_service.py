"""
PDFサービス（LangChain4jのKotlinクラスをPythonに移植）
"""
import requests
from pypdf import PdfReader
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_core.documents import Document
from typing import List, Tuple
import io


class PdfService:
    """PDFダウンロード・解析・分割のサービスクラス"""

    def __init__(self):
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000,
            chunk_overlap=200
        )

    async def download_pdf(self, url: str) -> bytes:
        """PDFファイルをダウンロード（非同期対応）"""
        try:
            response = requests.get(url, timeout=30)
            response.raise_for_status()
            return response.content
        except Exception as e:
            raise Exception(f"PDFダウンロードエラー: {e}")

    def download_pdf_sync(self, url: str) -> bytes:
        """PDFファイルを同期的にダウンロード"""
        try:
            response = requests.get(url, timeout=30)
            response.raise_for_status()
            return response.content
        except Exception as e:
            raise Exception(f"PDFダウンロードエラー: {e}")

    def parse_document(self, pdf_bytes: bytes) -> Document:
        """PDFバイト列からDocumentを作成"""
        try:
            pdf_file = io.BytesIO(pdf_bytes)
            reader = PdfReader(pdf_file)
            text = ""
            for page in reader.pages:
                text += page.extract_text() + "\n"
            
            return Document(page_content=text.strip())
        except Exception as e:
            raise Exception(f"PDF解析エラー: {e}")

    def split_document(self, document: Document) -> List[Document]:
        """ドキュメントをチャンクに分割"""
        try:
            chunks = self.text_splitter.split_documents([document])
            return chunks
        except Exception as e:
            raise Exception(f"ドキュメント分割エラー: {e}")