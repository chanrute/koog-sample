import { RecipeExtractionResult } from './types.js';

/**
 * レシピ抽出結果の表示を担当するクラス
 * KoogとLangChain4jのResultDisplayerに対応するTypeScript版
 */
export class ResultDisplayer {

  /**
   * レシピ抽出結果を表示する
   */
  displayResults(result: RecipeExtractionResult): void {
    console.log("\n✅ === ワークフロー完了！ ===");
    
    let output = "📊 実行結果:\n";

    if (result.extractedRecipe) {
      output += "\n📋 RecipeEntityオブジェクト:\n";
      output += "{\n";
      output += `  name: "${result.extractedRecipe.name}",\n`;
      output += "  ingredients: [\n";
      
      result.extractedRecipe.ingredients.forEach(ingredient => {
        output += `    { name: "${ingredient.name}", unit: "${ingredient.unit}", quantity: ${ingredient.quantity} },\n`;
      });
      
      output += "  ]\n";
      output += "}";

      if (result.totalCookingMinutes !== null) {
        output += `\n\n⌚️ 調理時間: ${result.totalCookingMinutes}分`;
      }
    } else {
      output += "\n❌ RecipeEntityの抽出に失敗しました";
    }

    console.log(output);
  }
}