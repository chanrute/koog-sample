package org.example

/**
 * レシピ抽出結果の表示を担当するクラス（LangChain4j版）
 */
class ResultDisplayer {

    /**
     * レシピ抽出結果を表示する
     */
    fun displayResults(result: RecipeExtractionResult) {
        println("\n✅ === ワークフロー完了！ ===")
        println(buildString {
            appendLine("📊 実行結果:")

            if (result.extractedRecipe != null) {
                appendLine()
                appendLine("📋 RecipeEntityオブジェクト:")
                appendLine("RecipeEntity(")
                appendLine("  name = \"${result.extractedRecipe.name}\",")
                appendLine("  ingredients = listOf(")
                result.extractedRecipe.ingredients.forEach { ingredient ->
                    appendLine("    Ingredient(\"${ingredient.name}\", \"${ingredient.unit}\", ${ingredient.quantity}f),")
                }
                appendLine("  )")
                appendLine(")")

                if (result.totalCookingMinutes != null) {
                    appendLine()
                    appendLine("⌚️ 調理時間: ${result.totalCookingMinutes}分")
                }
            } else {
                appendLine("❌ RecipeEntityの抽出に失敗しました")
            }
        })
    }
}