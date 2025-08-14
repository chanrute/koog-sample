/**
 * ワークフローで使用するデータ型定義
 * KoogとLangChain4jのKotlin実装に対応するTypeScript版
 */

export interface Ingredient {
  /** 材料名（例：玉ねぎ、にんじん、牛肉など） */
  name: string;

  /** 材料の単位（例：グラム、カップ、個、大さじ、小さじなど） */
  unit: string;

  /** 材料の数量 */
  quantity: number;
}

export interface RecipeEntity {
  /** 料理のレシピ名（例：カレーライス、パスタボロネーゼなど） */
  name: string;

  /** レシピに必要な材料のリスト */
  ingredients: Ingredient[];
}

export interface PdfUrl {
  url: string;
}

export interface PdfValidationResult {
  /** PDFが料理レシピに関する内容かどうか（true: レシピ、false: レシピではない） */
  isRecipe: boolean;

  /** 判定の理由や根拠 */
  reason: string;
}

export interface PdfContent {
  pdfBytes?: Buffer;
  extractedText: string;
}

export interface DocumentChunks {
  originalText: string;
  textChunks: string[];
}

export interface ChunkEmbedding {
  chunkText: string;
  vectorEmbedding: number[];
}

export interface EmbeddedChunks {
  textChunks: string[];
  chunkEmbeddings: ChunkEmbedding[];
}

export interface RecipeSearchResult {
  searchQuery: string;
  matchedChunks: string[];
  allChunkEmbeddings: ChunkEmbedding[];
}

export interface RecipeExtractionResult {
  extractedRecipe: RecipeEntity | null;
  totalCookingMinutes: number | null;
}

export interface ParallelExtractionData {
  extractedRecipe: RecipeEntity | null;
  totalCookingMinutes: number | null;
}

export interface CookingTimeResult {
  totalMinutes: number;
  breakdown: string[];
}

export interface RecipeWorkflowData {
  searchResult: RecipeSearchResult | null;
  extractedRecipe: RecipeEntity | null;
}

/**
 * サンプルレシピ例
 */
export const getExampleRecipes = (): RecipeEntity[] => [
  {
    name: "カレーライス",
    ingredients: [
      { name: "玉ねぎ", unit: "個", quantity: 2.0 },
      { name: "にんじん", unit: "本", quantity: 1.0 },
      { name: "牛肉", unit: "グラム", quantity: 300.0 },
      { name: "カレールー", unit: "箱", quantity: 1.0 }
    ]
  },
  {
    name: "パスタボロネーゼ",
    ingredients: [
      { name: "パスタ", unit: "グラム", quantity: 200.0 },
      { name: "挽き肉", unit: "グラム", quantity: 150.0 },
      { name: "トマト缶", unit: "缶", quantity: 1.0 },
      { name: "オリーブオイル", unit: "大さじ", quantity: 2.0 }
    ]
  }
];

export const getExampleValidations = (): PdfValidationResult[] => [
  { isRecipe: true, reason: "料理名、材料、作り方が記載されている料理レシピです" },
  { isRecipe: false, reason: "料理に関する情報が含まれていません" }
];
