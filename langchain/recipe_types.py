"""
データ型定義（LangChain4jのKotlinクラスをPythonに移植）
"""
from dataclasses import dataclass
from typing import List, Optional
from langchain_core.documents import Document
from pydantic import BaseModel, Field


class Ingredient(BaseModel):
    """材料を表すPydanticモデル（OutputParser用）"""
    name: str = Field(description="材料名")
    unit: str = Field(description="単位（グラム、個、本、カップ、大さじ、小さじなど）")
    quantity: float = Field(description="数量")


class RecipeEntity(BaseModel):
    """料理のレシピを表すPydanticモデル（OutputParser用）"""
    name: str = Field(description="レシピ名・料理名")
    ingredients: List[Ingredient] = Field(description="材料リスト")

    @classmethod
    def get_example_recipes(cls) -> List['RecipeEntity']:
        return [
            RecipeEntity(
                name="カレーライス",
                ingredients=[
                    Ingredient(name="玉ねぎ", unit="個", quantity=2.0),
                    Ingredient(name="にんじん", unit="本", quantity=1.0),
                    Ingredient(name="牛肉", unit="グラム", quantity=300.0),
                    Ingredient(name="カレールー", unit="箱", quantity=1.0)
                ]
            ),
            RecipeEntity(
                name="パスタボロネーゼ",
                ingredients=[
                    Ingredient(name="パスタ", unit="グラム", quantity=200.0),
                    Ingredient(name="挽き肉", unit="グラム", quantity=150.0),
                    Ingredient(name="トマト缶", unit="缶", quantity=1.0),
                    Ingredient(name="オリーブオイル", unit="大さじ", quantity=2.0)
                ]
            )
        ]


class PdfValidationResult(BaseModel):
    """PDF判定結果（PydanticOutputParser用）"""
    is_recipe: bool = Field(description="レシピ内容かどうかの判定")
    reason: str = Field(description="判定理由の詳細説明")


@dataclass
class PdfContent:
    """PDFコンテンツ"""
    pdf_bytes: bytes
    extracted_text: str


@dataclass
class DocumentChunks:
    """ドキュメントチャンク"""
    original_text: str
    text_segments: List[Document]


@dataclass
class ChunkEmbedding:
    """テキストチャンクとそのベクトル表現"""
    chunk_text: str
    vector_embedding: List[float]

    @classmethod
    def from_embedding(cls, text: str, embedding: List[float]) -> 'ChunkEmbedding':
        return cls(text, embedding)

    def __str__(self) -> str:
        return f"ChunkEmbedding(chunk_text='{self.chunk_text[:50]}...', vector_embedding=<embedding>)"


@dataclass
class EmbeddedChunks:
    """エンベディング済みチャンク"""
    text_segments: List[Document]
    chunk_embeddings: List[ChunkEmbedding]


@dataclass
class RecipeExtractionResult:
    """レシピ抽出結果"""
    extracted_recipe: Optional[RecipeEntity]
    total_cooking_minutes: Optional[float] = None