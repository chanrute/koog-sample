"""
LangChainライブラリによるPDFレシピ抽出アプリケーション

LangChainの主要機能：
1. LangGraph StateGraph - 複雑なワークフロー構築（LangChain4jのLangGraph4j相当）
2. PydanticOutputParser - 構造化データ抽出
3. ChromaDB VectorStore - 高精度なベクトル類似度検索
4. LangChain Tools - 関数呼び出し機能（調理時間計算）
5. 条件分岐 - 複数ノードの分岐処理
"""

import os
import base64
from typing import List, Optional
from dotenv import load_dotenv

# LangChain Core
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain_core.documents import Document
from langchain_core.output_parsers import PydanticOutputParser
from langchain.output_parsers import OutputFixingParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.exceptions import OutputParserException
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_chroma import Chroma
from langchain.agents import create_tool_calling_agent, AgentExecutor

# Local imports
from recipe_types import (
    PdfValidationResult, PdfContent, DocumentChunks,
    ChunkEmbedding, EmbeddedChunks, RecipeEntity,
    RecipeExtractionResult
)
from pdf_service import PdfService
from result_displayer import ResultDisplayer
from cooking_tools import cooking_tools, sum_minutes
# LangGraph
from typing import TypedDict
from langgraph.graph import StateGraph, END


class PdfRagApp:
    """LangChainによるPDFレシピ抽出アプリケーション（KoogのApp.kt構造に準拠）"""

    def __init__(self):
        # 環境変数をルートディレクトリの.envから読み込み（Koogと同じ構造）
        load_dotenv(dotenv_path="../.env")

        self.pdf_service = PdfService()

        # OpenAI API Key取得（KoogのgetOpenAiApiKey()と同じ）
        self.openai_api_key = os.getenv("OPENAI_API_KEY")
        if not self.openai_api_key:
            raise ValueError("OPENAI_API_KEY環境変数が設定されていません")

    def get_openai_api_key(self) -> str:
        """KoogのgetOpenAiApiKey()メソッドに対応"""
        return self.openai_api_key

    # ==========================================
    # LangChain LLM & Embedding API - 高精度なベクトル検索
    # ==========================================

    def create_chat_model(self) -> ChatOpenAI:
        """ChatModelの初期化"""
        return ChatOpenAI(
            model="gpt-4o",
            temperature=0.1,
            openai_api_key=self.openai_api_key
        )

    def create_embedding_model(self) -> OpenAIEmbeddings:
        """EmbeddingModelの初期化"""
        return OpenAIEmbeddings(
            model="text-embedding-ada-002",
            openai_api_key=self.openai_api_key
        )

    def create_openai_embeddings(self, chunks: List[str]) -> List[ChunkEmbedding]:
        """OpenAI Embeddingを作成"""
        print("🧠 LangChainのOpenAI Embeddingを作成中...")
        try:
            embedding_model = self.create_embedding_model()
            embeddings = []
            for chunk in chunks:
                embedding = embedding_model.embed_query(chunk)
                chunk_embedding = ChunkEmbedding.from_embedding(chunk, embedding)
                embeddings.append(chunk_embedding)

            print(f"✅ {len(embeddings)}個のLangChain Embeddingを作成しました")
            return embeddings
        except Exception as e:
            print(f"❌ LangChain Embedding作成でエラーが発生: {e}")
            return []

    def _extract_sum_minutes_from_agent_result(self, result: dict) -> Optional[float]:
        """AgentExecutorの結果からsum_minutesツールの引数を抽出して実行"""
        intermediate_steps = result.get("intermediate_steps", [])

        for step in intermediate_steps:
            action, observation = step
            if hasattr(action, 'tool') and action.tool == 'sum_minutes':
                if hasattr(action, 'message_log') and action.message_log:
                    message = action.message_log[0]
                    if hasattr(message, 'tool_calls') and message.tool_calls:
                        for tool_call in message.tool_calls:
                            if tool_call['name'] == 'sum_minutes':
                                # tool_callのargsを使ってsum_minutesを直接実行
                                tool_args = tool_call['args']
                                return float(sum_minutes.invoke(tool_args))
        return None

    def perform_rag_search(self, documents: List[Document], query: str, max_results: int = 3) -> List[Document]:
        """RAG検索の実装（ChromaDBを使用）"""
        print(f"🔍 LangChain ChromaDB VectorStoreを使ってクエリ「{query}」に関連するコンテンツを検索中...")

        try:
            # ChromaDBベクトルストアを作成
            embedding_model = self.create_embedding_model()
            vectorstore = Chroma.from_documents(documents, embedding_model)

            # 関連ドキュメントを検索
            relevant_docs = vectorstore.similarity_search(query, k=max_results)

            print(f"✅ {len(relevant_docs)}個の関連セグメントを取得しました")
            for i, doc in enumerate(relevant_docs, 1):
                print(f"  {i}. {doc.page_content[:100]}...")

            # ChromaDBインスタンスを削除してリソースを解放
            vectorstore.delete_collection()

            return relevant_docs

        except Exception as e:
            print(f"❌ RAG検索でエラーが発生: {e}")
            return documents[:max_results]  # エラー時は最初の数個を返す

    # ==========================================
    # LangChain Structured Output - JSON構造化データ抽出
    # ==========================================

    def generate_answer_with_agent(self, relevant_chunks: List[Document]) -> Optional[RecipeEntity]:
        """RecipeEntityを抽出"""
        print("🤖 LangChainのPydanticOutputParserを使ってRecipeEntityを抽出中...")

        try:
            context = "\n\n".join([
                f"【文書内容】\n{doc.page_content}"
                for doc in relevant_chunks
            ])

            # PydanticOutputParserを作成
            output_parser = PydanticOutputParser(pydantic_object=RecipeEntity)

            # OutputFixingParserでエラー修正機能を追加
            chat_model = self.create_chat_model()
            fixing_parser = OutputFixingParser.from_llm(
                parser=output_parser,
                llm=chat_model
            )

            # フォーマット指示を取得
            format_instructions = output_parser.get_format_instructions()

            # プロンプトテンプレートを作成
            prompt_template = ChatPromptTemplate.from_messages([
                (
                    "human",
                    """
                    あなたは料理レシピの専門家です。
                    提供された文書からレシピ情報を正確に抽出してください。

                    抽出する情報：
                    - レシピ名（料理の名前）
                    - 材料リスト（材料名、数量、単位）

                    注意事項：
                    - 数量は数値として正確に抽出してください
                    - 単位は日本語で記載してください（グラム、個、本、カップ、大さじ、小さじなど）
                    - 文書に記載されている情報のみを抽出してください

                    {format_instructions}

                    以下の文書からレシピ情報を抽出してください：

                    {context}
                    """
                )
            ])

            # チェーンを作成（リトライ機能付き）
            chain = (
                prompt_template.partial(format_instructions=format_instructions)
                | chat_model
                | fixing_parser
            ).with_retry(
                retry_if_exception_type=(OutputParserException,),
                wait_exponential_jitter=True,
                stop_after_attempt=2,
            )

            # チェーンを実行
            recipe = chain.invoke({"context": context})

            print(f"✅ LangChain RecipeEntity抽出完了: {recipe.name}")
            print(f"材料数: {len(recipe.ingredients)}個")
            return recipe

        except Exception as e:
            print(f"❌ LangChain RecipeEntity抽出でエラーが発生: {e}")
            return None

    def extract_cooking_time_with_tools(self, time_related_segments: List[Document]) -> Optional[float]:
        """調理時間抽出（KoogのApp.ktパターンに合わせたLangChain Tools使用）"""
        print("🕐 LangChain Toolsを使って調理時間を抽出中...")

        try:
            context = "\n\n".join([
                f"【文書内容】\n{segment.page_content}"
                for segment in time_related_segments
            ])

            # ChatModelを作成
            chat_model = self.create_chat_model()

            # ツール実行
            prompt = ChatPromptTemplate.from_messages([
                (
                    "system",
                    """
                    あなたは料理レシピの調理時間を分析する専門家です。
                    提供された文書から料理にかかる時間（分）を抽出してください。
                    必ずsumMinutesツールを使って合計時間を計算してください。

                    注意事項：
                    - 「約10分」のような表現は10として扱ってください
                    - 「5〜10分」のような範囲は最大値（10）を使用してください
                    - 時間が明記されていない場合は、一般的な調理時間を推定してください
                    """
                ),
                ("human", "{input}"),
                ("placeholder", "{agent_scratchpad}")
            ])

            # Tool Calling Agentを作成
            agent = create_tool_calling_agent(chat_model, cooking_tools, prompt)
            agent_executor = AgentExecutor(
                agent=agent,
                tools=cooking_tools,
                verbose=True,
                return_intermediate_steps=True
            )

            # 文書分析とツール実行を行う
            result = agent_executor.invoke({
                "input":
                f"""以下の文書から調理時間を抽出し、sumMinutesツールを使って合計時間を計算してください：

                {context}

                必ずsumMinutesツールを呼び出して時間を合計してください。"""
            })

            # sum_minutesツールの実行結果を取得
            total_minutes = self._extract_sum_minutes_from_agent_result(result)

            if total_minutes is not None:
                print(f"🕐 LangChain Tools調理時間抽出結果: {total_minutes}分")
                return total_minutes
            else:
                print("⚠️ sum_minutesツールのtool_callsが見つかりませんでした")
                return None

        except Exception as e:
            print(f"❌ LangChain Tools調理時間抽出でエラーが発生: {e}")
            return None

    # ==========================================
    # LangGraph StateGraph - 複雑なワークフロー構築
    # ==========================================

    class WorkflowState(TypedDict):
        """
        LangGraph用のワークフロー状態管理
        """
        # 入力データ
        pdf_url: str

        # 中間データ
        validation_result: Optional[PdfValidationResult]
        pdf_content: Optional[PdfContent]
        document_chunks: Optional[DocumentChunks]
        embedded_chunks: Optional[EmbeddedChunks]
        recipe_relevant_segments: Optional[List[Document]]

        # 抽出結果
        extracted_recipe: Optional[RecipeEntity]
        total_cooking_minutes: Optional[float]

        # 最終結果
        final_result: Optional[RecipeExtractionResult]

        # エラーフラグ
        error: Optional[str]

    def create_langgraph_workflow(self) -> StateGraph:
        """
        LangGraphワークフローを構築
        """
        workflow = StateGraph(self.WorkflowState)

        # ノードを追加
        workflow.add_node("validate_recipe_pdf", self._validate_recipe_pdf_node)
        workflow.add_node("not_recipe_finish", self._not_recipe_finish_node)
        workflow.add_node("download_extract_pdf", self._download_extract_pdf_node)
        workflow.add_node("split_chunks", self._split_chunks_node)
        workflow.add_node("create_embeddings", self._create_embeddings_node)
        workflow.add_node("find_relevant_chunks", self._find_relevant_chunks_node)
        workflow.add_node("extract_recipe_entity", self._extract_recipe_entity_node)
        workflow.add_node("extract_cooking_time", self._extract_cooking_time_node)
        workflow.add_node("create_final_result", self._create_final_result_node)

        # エントリーポイントを設定
        workflow.set_entry_point("validate_recipe_pdf")

        # 条件分岐エッジを追加
        workflow.add_conditional_edges(
            "validate_recipe_pdf",
            self._should_continue_processing,
            {
                "continue": "download_extract_pdf",
                "stop": "not_recipe_finish"
            }
        )

        # 逐次エッジを追加
        workflow.add_edge("download_extract_pdf", "split_chunks")
        workflow.add_edge("split_chunks", "create_embeddings")
        workflow.add_edge("create_embeddings", "find_relevant_chunks")

        # 逐次処理に変更
        workflow.add_edge("find_relevant_chunks", "extract_recipe_entity")
        workflow.add_edge("extract_recipe_entity", "extract_cooking_time")
        workflow.add_edge("extract_cooking_time", "create_final_result")

        # 終了ポイント
        workflow.add_edge("create_final_result", END)
        workflow.add_edge("not_recipe_finish", END)

        return workflow

    def _validate_recipe_pdf_node(self, state: WorkflowState) -> WorkflowState:
        """PDFファイル判定ノード"""
        print(f"\n📋 PDFファイルを直接送信して判定中: {state['pdf_url']}")

        try:
            # PDFをダウンロード
            pdf_bytes = self.pdf_service.download_pdf_sync(state['pdf_url'])

            # PDFをBase64エンコード
            pdf_base64 = base64.b64encode(pdf_bytes).decode("utf-8")
            filename = state['pdf_url'].split('/')[-1] or 'recipe.pdf'

            # ChatModelを作成
            chat_model = self.create_chat_model()

            # PydanticOutputParserを作成
            output_parser = PydanticOutputParser(pydantic_object=PdfValidationResult)
            fixing_parser = OutputFixingParser.from_llm(
                parser=output_parser,
                llm=chat_model
            )

            # プロンプトテンプレートを作成
            prompt_template = ChatPromptTemplate.from_messages([
                ("system", "あなたは料理レシピの専門家です。回答には必ず日本語で答えてください。"),
                ("human", [
                    {
                        "type": "text",
                        "text": """
                        提供されたPDFファイルの内容が料理のレシピに関する内容かどうかを判断してください。

                        判断基準:
                        - 料理名、材料、作り方、調理時間などが含まれているか
                        - 料理に関する情報が主な内容となっているか

                        reasonフィールドには必ず日本語で詳しい理由を記載してください。

                        {format_instructions}
                        """
                    },
                    {
                        "type": "file",
                        "file": {
                            "filename": filename,
                            "file_data": f"data:application/pdf;base64,{pdf_base64}"
                        }
                    }
                ])
            ])

            # チェーンを作成
            chain = (
                prompt_template.partial(format_instructions=output_parser.get_format_instructions())
                | chat_model
                | fixing_parser
            )

            # チェーンを実行
            validation_result = chain.invoke({})

            print(f"🔍 判定結果: {'✅ レシピPDF' if validation_result.is_recipe else '❌ レシピ以外'}")
            print(f"📝 理由: {validation_result.reason}")

            return {**state, "validation_result": validation_result}

        except Exception as e:
            print(f"❌ PDFファイル判定でエラーが発生: {e}")
            # エラー時はデフォルトで処理を続行
            validation_result = PdfValidationResult(is_recipe=True, reason="エラーが発生したため、処理を続行します")
            return {**state, "validation_result": validation_result}

    def _not_recipe_finish_node(self, state: WorkflowState) -> WorkflowState:
        """レシピでない場合の終了ノード"""
        print("❌ このPDFは料理のレシピではありません。処理を終了します。")
        final_result = RecipeExtractionResult(None, None)
        return {**state, "final_result": final_result}

    def _download_extract_pdf_node(self, state: WorkflowState) -> WorkflowState:
        """PDFダウンロード・抽出ノード"""
        print("📥 PDFをダウンロードして解析中...")

        try:
            pdf_bytes = self.pdf_service.download_pdf_sync(state['pdf_url'])
            document = self.pdf_service.parse_document(pdf_bytes)
            pdf_content = PdfContent(pdf_bytes, document.page_content)

            return {**state, "pdf_content": pdf_content}

        except Exception as e:
            print(f"❌ PDF処理でエラーが発生: {e}")
            return {**state, "error": str(e)}

    def _split_chunks_node(self, state: WorkflowState) -> WorkflowState:
        """チャンク分割ノード"""
        print("🔪 ドキュメントをチャンクに分割中...")

        try:
            document = self.pdf_service.parse_document(state['pdf_content'].pdf_bytes)
            text_segments = self.pdf_service.split_document(document)
            document_chunks = DocumentChunks(
                original_text=state['pdf_content'].extracted_text,
                text_segments=text_segments
            )

            return {**state, "document_chunks": document_chunks}

        except Exception as e:
            print(f"❌ チャンク分割でエラーが発生: {e}")
            return {**state, "error": str(e)}

    def _create_embeddings_node(self, state: WorkflowState) -> WorkflowState:
        """Embedding作成ノード"""
        print("🧠 エンベディングを作成中...")

        try:
            text_segments = state['document_chunks'].text_segments
            chunk_texts = [seg.page_content for seg in text_segments]
            chunk_embeddings = self.create_openai_embeddings(chunk_texts)

            embedded_chunks = EmbeddedChunks(
                text_segments=text_segments,
                chunk_embeddings=chunk_embeddings
            )

            return {**state, "embedded_chunks": embedded_chunks}

        except Exception as e:
            print(f"❌ Embedding作成でエラーが発生: {e}")
            return {**state, "error": str(e)}

    def _find_relevant_chunks_node(self, state: WorkflowState) -> WorkflowState:
        """関連チャンク検索ノード"""
        print("🔍 レシピ関連のチャンクを検索中...")

        try:
            recipe_query = "レシピ名 材料 分量"
            text_segments = state['embedded_chunks'].text_segments
            relevant_segments = self.perform_rag_search(text_segments, recipe_query, 5)

            # 検索結果をstateに保存
            return {**state, "recipe_relevant_segments": relevant_segments}

        except Exception as e:
            print(f"❌ 関連チャンク検索でエラーが発生: {e}")
            return {**state, "error": str(e)}

    def _extract_recipe_entity_node(self, state: WorkflowState) -> WorkflowState:
        """レシピ抽出ノード"""
        print("🤖 レシピ情報を抽出中...")

        try:
            relevant_segments = state.get('recipe_relevant_segments', [])
            if relevant_segments:
                extracted_recipe = self.generate_answer_with_agent(relevant_segments)
                return {**state, "extracted_recipe": extracted_recipe}
            else:
                return {**state, "extracted_recipe": None}

        except Exception as e:
            print(f"❌ レシピ抽出でエラーが発生: {e}")
            return {**state, "extracted_recipe": None}

    def _extract_cooking_time_node(self, state: WorkflowState) -> WorkflowState:
        """調理時間抽出ノード"""
        print("🕐 調理時間を抽出中...")

        try:
            time_query = "時間 分 調理時間 作業時間 合計"
            text_segments = state['embedded_chunks'].text_segments
            time_related_segments = self.perform_rag_search(text_segments, time_query, 3)

            if time_related_segments:
                total_cooking_minutes = self.extract_cooking_time_with_tools(time_related_segments)
                return {**state, "total_cooking_minutes": total_cooking_minutes}
            else:
                print("⚠️ 時間関連の情報が見つかりませんでした")
                return {**state, "total_cooking_minutes": None}

        except Exception as e:
            print(f"❌ 調理時間抽出でエラーが発生: {e}")
            return {**state, "total_cooking_minutes": None}

    def _create_final_result_node(self, state: WorkflowState) -> WorkflowState:
        """最終結果作成ノード"""
        print("🔄 処理結果を統合中...")

        extracted_recipe = state.get('extracted_recipe')
        total_cooking_minutes = state.get('total_cooking_minutes')

        print(f"⚡ 処理完了: レシピ抽出={extracted_recipe is not None}, 調理時間={total_cooking_minutes}分")

        final_result = RecipeExtractionResult(
            extracted_recipe=extracted_recipe,
            total_cooking_minutes=total_cooking_minutes
        )

        return {**state, "final_result": final_result}

    def _should_continue_processing(self, state: WorkflowState) -> str:
        """
        処理を続行するかの条件分岐判定
        """
        validation_result = state.get('validation_result')
        if validation_result and validation_result.is_recipe:
            return "continue"
        else:
            return "stop"

    def execute_langgraph_workflow(self, pdf_url: str) -> RecipeExtractionResult:
        """
        LangGraphワークフローを実行
        """
        print("\n🔄 === LangGraph Workflowワークフロー実行開始 ===")

        try:
            # ワークフローを作成してコンパイル
            workflow = self.create_langgraph_workflow()
            app = workflow.compile()

            # 初期状態を設定
            initial_state = {
                "pdf_url": pdf_url,
                "validation_result": None,
                "pdf_content": None,
                "document_chunks": None,
                "embedded_chunks": None,
                "recipe_relevant_segments": None,
                "extracted_recipe": None,
                "total_cooking_minutes": None,
                "final_result": None,
                "error": None
            }

            # ワークフローを実行
            final_state = app.invoke(initial_state)

            # 最終結果を返す
            return final_state.get('final_result', RecipeExtractionResult(None, None))

        except Exception as e:
            print(f"❌ LangGraphワークフロー実行中にエラーが発生: {e}")
            import traceback
            traceback.print_exc()
            return RecipeExtractionResult(None, None)


# ==========================================
# メイン実行部：LangChainライブラリの統合実行
# ==========================================

def main():
    """
    LangChain/LangGraphを使用したワークフロー実行
    """

    app = PdfRagApp()

    # PDFファイルをURLで指定
    pdf_url = "https://kyushucgc.co.jp/recipe_pdf/202112/recipe05.pdf"

    try:
        # LangGraphワークフロー実行
        result = app.execute_langgraph_workflow(pdf_url)

        # 結果表示
        displayer = ResultDisplayer()
        displayer.display_results(result)

    except Exception as e:
        print(f"\n❌ エラーが発生しました: {e}")
        import traceback
        traceback.print_exc()

    print("\n🎉 === LangGraphのワークフローテスト完了！ ===")


if __name__ == "__main__":
    main()
