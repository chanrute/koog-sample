import dotenv from 'dotenv';
import { z } from 'zod';
import { embedMany, generateObject, generateText } from 'ai';
import { openai } from '@ai-sdk/openai';
import { createWorkflow, createStep } from '@mastra/core';
import { PdfService } from './pdf-service.js';
import { ResultDisplayer } from './result-displayer.js';
import {
  ChunkEmbedding,
  RecipeExtractionResult
} from './types.js';

// 環境変数の読み込み（プロジェクトルートの.envを使用）
dotenv.config({ path: '../.env' });

// Mastraテレメトリの警告を無効化
(globalThis as any).___MASTRA_TELEMETRY___ = true;

/**
 * MastraによるPDFレシピ抽出アプリケーション（Parallel実装版）
 *
 * KoogのAI Strategy GraphとLangChain4j + LangGraph4jワークフローに対応する
 * Mastra 0.13.0での実装版（.parallel()メソッド使用）
 *
 * 主な機能：
 * 1. Mastra Workflow - 複雑なワークフローの構築
 * 2. AI SDK structured output - JSON構造化データ抽出
 * 3. AI SDK embeddings - 高精度なベクトル類似度検索
 * 4. 条件分岐と並列実行（.parallel()メソッド使用）
 */
export class PdfRagApp {
  private pdfService: PdfService;

  constructor() {
    this.pdfService = new PdfService();
  }

  /**
   * AI SDK Embeddingsを使用してベクトル埋め込みを作成
   */
  async createOpenAIEmbeddings(chunks: string[]): Promise<ChunkEmbedding[]> {
    console.log("🧠 AI SDK Embeddingを作成中...");

    try {
      const { embeddings } = await embedMany({
        values: chunks,
        model: openai.embedding('text-embedding-ada-002'),
      });

      const chunkEmbeddings: ChunkEmbedding[] = chunks.map((chunk, index) => ({
        chunkText: chunk,
        vectorEmbedding: embeddings[index]
      }));

      console.log(`✅ ${chunkEmbeddings.length}個のAI SDK Embeddingを作成しました`);
      return chunkEmbeddings;
    } catch (error) {
      console.error("❌ AI SDK Embedding作成でエラーが発生:", error);
      return [];
    }
  }

  /**
   * ベクトル類似度検索
   */
  findRelevantChunks(
    queryEmbedding: number[],
    embeddings: ChunkEmbedding[],
    topK: number = 3
  ): string[] {
    console.log("🔍 ベクトル類似度検索中...");

    const similarities = embeddings.map(chunkEmbedding => {
      // コサイン類似度を計算
      const similarity = this.cosineSimilarity(queryEmbedding, chunkEmbedding.vectorEmbedding);
      return { chunk: chunkEmbedding.chunkText, similarity };
    });

    const topChunks = similarities
      .sort((a, b) => b.similarity - a.similarity)
      .slice(0, topK)
      .map(item => item.chunk);

    console.log("⏱️ チャンクの内容:");
    topChunks.forEach((chunk, index) => {
      console.log(`  ${index + 1}. ${chunk.substring(0, 100)}...`);
    });

    return topChunks;
  }

  /**
   * コサイン類似度計算
   */
  private cosineSimilarity(vecA: number[], vecB: number[]): number {
    let dotProduct = 0;
    let normA = 0;
    let normB = 0;

    for (let i = 0; i < vecA.length; i++) {
      dotProduct += vecA[i] * vecB[i];
      normA += vecA[i] * vecA[i];
      normB += vecB[i] * vecB[i];
    }

    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  /**
   * Mastraワークフローを作成（.parallel()メソッド使用版）
   * KoogのcreateRecipeExtractionStrategyとLangGraph4jのcreateRecipeExtractionStrategyに対応
   */
  createRecipeExtractionWorkflow() {
    // Step 1: PDF判定ステップ
    const validatePdfStep = createStep({
      id: 'validate-pdf',
      description: 'PDFが料理レシピかどうかを判定',
      inputSchema: z.object({
        url: z.string()
      }),
      outputSchema: z.object({
        validation: z.object({
          isRecipe: z.boolean(),
          reason: z.string()
        }),
        pdfContent: z.object({
          pdfBytes: z.any().optional(),
          extractedText: z.string()
        }).optional()
      }),
      execute: async ({ inputData }) => {
        const { url } = inputData;
        console.log("\n📋 PDFの内容を判定中:", url);

        try {
          const pdfContent = await this.pdfService.processPdf(url);

          // PDFを直接LLMに送信
          const textResult = await generateText({
            model: openai('gpt-4o'),
            messages: [
              {
                role: "user",
                content: [
                  {
                    type: "text",
                    text: `あなたは料理レシピの専門家です。
                      提供されたPDFファイルの内容が料理のレシピに関する内容かどうかを判断してください。

                      判断基準:
                      - 料理名、材料、作り方、調理時間などが含まれているか
                      - 料理に関する情報が主な内容となっているか

                      以下のJSON形式で回答してください：
                      {
                        "isRecipe": boolean,
                        "reason": "日本語で詳しい理由"
                      }`
                  },
                  {
                    type: "file",
                    mimeType: "application/pdf",
                    data: pdfContent.pdfBytes,
                    filename: url.split('/').pop() || 'recipe.pdf'
                  }
                ] as any
              }
            ]
          });

          // JSONレスポンスをパース
          const result = await generateObject({
            model: openai('gpt-4o'),
            prompt: `以下のテキストからJSON形式のデータを抽出してください：\n\n${textResult.text}`,
            schema: z.object({
              isRecipe: z.boolean(),
              reason: z.string()
            })
          });

          const validation = result.object;
          console.log(`🔍 判定結果: ${validation.isRecipe ? "✅ レシピPDF" : "❌ レシピ以外"}`);
          console.log(`📝 理由: ${validation.reason}`);

          return { validation, pdfContent };
        } catch (error) {
          console.error("❌ PDF内容判定でエラーが発生:", error);
          const errorValidation = {
            isRecipe: true,
            reason: "判定エラーが発生したため、処理を続行します"
          };
          return { validation: errorValidation };
        }
      }
    });

    // Step 2: データ前処理ステップ（レシピの場合のみ）
    const preprocessDataStep = createStep({
      id: 'preprocess-data',
      description: 'PDFデータの前処理',
      inputSchema: validatePdfStep.outputSchema,
      outputSchema: z.object({
        searchResult: z.object({
          searchQuery: z.string(),
          matchedChunks: z.array(z.string()),
          allChunkEmbeddings: z.array(z.object({
            chunkText: z.string(),
            vectorEmbedding: z.array(z.number())
          }))
        })
      }),
      execute: async ({ inputData }) => {
        // レシピでない場合はエラーを投げる
        if (!inputData.validation.isRecipe) {
          console.log("❌ このPDFは料理のレシピではありません。処理を終了します。");
          throw new Error('レシピではないPDFです');
        }

        // レシピの場合の処理
        if (!inputData.pdfContent) {
          throw new Error('PDFコンテンツが見つかりません');
        }

        // チャンク分割
        const documentChunks = this.pdfService.createDocumentChunks(inputData.pdfContent);

        // エンベディング作成
        const chunkEmbeddings = await this.createOpenAIEmbeddings(documentChunks.textChunks);
        const embeddedChunks = {
          textChunks: documentChunks.textChunks,
          chunkEmbeddings
        };

        // 関連チャンク検索
        const recipeQuery = "レシピ名 材料 分量";
        const { embeddings: queryEmbeddings } = await embedMany({
          values: [recipeQuery],
          model: openai.embedding('text-embedding-ada-002'),
        });
        const queryEmbedding = queryEmbeddings[0];
        const matchedChunks = this.findRelevantChunks(
          queryEmbedding,
          embeddedChunks.chunkEmbeddings,
          5
        );
        const searchResult = {
          searchQuery: recipeQuery,
          matchedChunks,
          allChunkEmbeddings: embeddedChunks.chunkEmbeddings
        };

        return { searchResult };
      }
    });

    // Step 3: レシピ抽出ステップ（並列実行1）
    const extractRecipeStep = createStep({
      id: 'extract-recipe',
      description: 'レシピ情報を抽出',
      inputSchema: z.object({
        searchResult: z.object({
          searchQuery: z.string(),
          matchedChunks: z.array(z.string()),
          allChunkEmbeddings: z.array(z.object({
            chunkText: z.string(),
            vectorEmbedding: z.array(z.number())
          }))
        })
      }),
      outputSchema: z.object({
        extractedRecipe: z.object({
          name: z.string(),
          ingredients: z.array(z.object({
            name: z.string(),
            unit: z.string(),
            quantity: z.number()
          }))
        }).nullable()
      }),
      execute: async ({ inputData }) => {
        const { searchResult } = inputData;
        console.log("🤖 レシピ情報を抽出中...");

        try {
          const contextText = searchResult.matchedChunks
            .map(chunk => `【文書内容】\n${chunk}`)
            .join('\n\n');

          const result = await generateObject({
            model: openai('gpt-4o'),
            prompt: `
              あなたは料理レシピの専門家です。
              提供された文書からレシピ情報を正確に抽出してください。

              抽出する情報：
              - レシピ名（料理の名前）
              - 材料リスト（材料名、数量、単位）

              注意事項：
              - 数量は数値として正確に抽出してください
              - 単位は日本語で記載してください（グラム、個、本、カップ、大さじ、小さじなど）
              - 文書に記載されている情報のみを抽出してください

              以下の文書からレシピ情報を抽出してください：

              ${contextText}`,
            schema: z.object({
              name: z.string(),
              ingredients: z.array(z.object({
                name: z.string(),
                unit: z.string(),
                quantity: z.number()
              }))
            })
          });

          const recipe = result.object;
          console.log(`✅ レシピ抽出完了: ${recipe.name}`);
          console.log(`材料数: ${recipe.ingredients.length}個`);

          return { extractedRecipe: recipe };
        } catch (error) {
          console.error("❌ レシピ抽出でエラーが発生:", error);
          return { extractedRecipe: null };
        }
      }
    });

    // Step 4: 調理時間抽出ステップ（並列実行2）
    const extractCookingTimeStep = createStep({
      id: 'extract-cooking-time',
      description: '調理時間を抽出',
      inputSchema: z.object({
        searchResult: z.object({
          searchQuery: z.string(),
          matchedChunks: z.array(z.string()),
          allChunkEmbeddings: z.array(z.object({
            chunkText: z.string(),
            vectorEmbedding: z.array(z.number())
          }))
        })
      }),
      outputSchema: z.object({
        totalCookingMinutes: z.number().nullable()
      }),
      execute: async ({ inputData }) => {
        const { searchResult } = inputData;
        console.log("🕐 調理時間を抽出中...");

        try {
          const timeQuery = "時間 分 調理時間 作業時間 合計";

          // 時間関連クエリのembeddingを作成
          const { embeddings: timeQueryEmbeddings } = await embedMany({
            values: [timeQuery],
            model: openai.embedding('text-embedding-ada-002'),
          });

          const timeQueryEmbedding = timeQueryEmbeddings[0];
          const timeRelatedChunks = this.findRelevantChunks(
            timeQueryEmbedding,
            searchResult.allChunkEmbeddings,
            3
          );

          if (timeRelatedChunks.length > 0) {
            const contextText = timeRelatedChunks
              .map(chunk => `【文書内容】\n${chunk}`)
              .join('\n\n');

            const result = await generateObject({
              model: openai('gpt-4o'),
              prompt: `
                あなたは料理レシピの調理時間を分析する専門家です。
                提供された文書から調理時間を抽出し、totalMinutesフィールドに合計時間を設定してください。

                手順：
                1. 文書から調理時間を抽出（「約10分」「5〜10分」「2時間」など）
                2. 抽出した時間を分単位の数値に変換
                3. 合計時間を計算

                注意事項：
                - 「約10分」のような表現は10として扱ってください
                - 「5〜10分」のような範囲は最大値（10）を使用してください
                - 時間が明記されていない場合は、一般的な調理時間を推定してください

                以下の文書から調理時間を抽出してください：

                ${contextText}`,
              schema: z.object({
                totalMinutes: z.number(),
                breakdown: z.array(z.string()).optional()
              })
            });

            const cookingResult = result.object;
            console.log(`🕐 調理時間抽出結果: ${cookingResult.totalMinutes}分`);
            return { totalCookingMinutes: cookingResult.totalMinutes };
          } else {
            console.log("⚠️ 時間関連の情報が見つかりませんでした");
            return { totalCookingMinutes: null };
          }
        } catch (error) {
          console.error("❌ 調理時間抽出でエラーが発生:", error);
          return { totalCookingMinutes: null };
        }
      }
    });

    // Step 5: 結果統合ステップ（並列実行結果を受け取る）
    const combineResultsStep = createStep({
      id: 'combine-results',
      description: '並列抽出結果を統合',
      inputSchema: z.object({
        'extract-recipe': z.object({
          extractedRecipe: z.object({
            name: z.string(),
            ingredients: z.array(z.object({
              name: z.string(),
              unit: z.string(),
              quantity: z.number()
            }))
          }).nullable()
        }),
        'extract-cooking-time': z.object({
          totalCookingMinutes: z.number().nullable()
        })
      }),
      outputSchema: z.object({
        extractedRecipe: z.object({
          name: z.string(),
          ingredients: z.array(z.object({
            name: z.string(),
            unit: z.string(),
            quantity: z.number()
          }))
        }).nullable(),
        totalCookingMinutes: z.number().nullable()
      }),
      execute: async ({ inputData }) => {
        const extractedRecipe = inputData['extract-recipe'].extractedRecipe;
        const totalCookingMinutes = inputData['extract-cooking-time'].totalCookingMinutes;

        console.log("🔄 並列抽出結果を統合中...");
        console.log(`⚡ 統合完了: レシピ抽出=${extractedRecipe !== null}, 調理時間=${totalCookingMinutes}分`);

        return { extractedRecipe, totalCookingMinutes };
      }
    });

    // ワークフローを作成
    const workflow = createWorkflow({
      id: 'recipe-extraction-workflow',
      description: 'PDFからレシピ情報を抽出するワークフロー（並列実行版）',
      inputSchema: z.object({
        url: z.string()
      }),
      outputSchema: z.object({
        extractedRecipe: z.object({
          name: z.string(),
          ingredients: z.array(z.object({
            name: z.string(),
            unit: z.string(),
            quantity: z.number()
          }))
        }).nullable(),
        totalCookingMinutes: z.number().nullable()
      })
    });

    // Mastraの.parallel()メソッドを使用したワークフロー
    return workflow
      .then(validatePdfStep)
      .then(preprocessDataStep)
      .parallel([ // 並列実行
        extractRecipeStep,
        extractCookingTimeStep
      ])
      .then(combineResultsStep)
      .commit();
  }

  /**
   * Mastraワークフローを使ったレシピ抽出の実行
   */
  async executeRecipeExtraction(pdfUrl: string): Promise<RecipeExtractionResult> {
    console.log("\n🔄 === Mastraワークフロー実行開始（並列実行版） ===");

    try {
      const workflow = this.createRecipeExtractionWorkflow();
      const run = workflow.createRun();

      const result = await run.start({
        inputData: { url: pdfUrl }
      });

      if (result.status === 'success') {
        return result.result;
      } else {
        console.error("❌ ワークフロー実行失敗:", result);
        return { extractedRecipe: null, totalCookingMinutes: null };
      }
    } catch (error) {
      console.error("❌ Mastraワークフロー実行中にエラーが発生:", error);
      return { extractedRecipe: null, totalCookingMinutes: null };
    }
  }
}

/**
 * メイン実行部：Mastraワークフローの統合実行（並列実行版）
 */
async function main() {
  const app = new PdfRagApp();

  // PDFファイルをURLで指定（KoogとLangChain4jプロジェクトと同じURL）
  const pdfUrl = "https://kyushucgc.co.jp/recipe_pdf/202112/recipe05.pdf";

  try {
    // Mastraワークフロー実行（KoogのAI Strategy GraphとLangGraph4j相当）
    const result = await app.executeRecipeExtraction(pdfUrl);

    // 結果表示
    const resultDisplayer = new ResultDisplayer();
    resultDisplayer.displayResults(result);

  } catch (error) {
    console.error("\n❌ エラーが発生しました:", error instanceof Error ? error.message : String(error));
  }

  console.log("\n🎉 === Mastraワークフローテスト完了！（並列実行版） ===");
}

// メイン関数の実行
if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch(console.error);
}
