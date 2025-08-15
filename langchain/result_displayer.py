"""
結果表示
"""
from recipe_types import RecipeExtractionResult
import json


class ResultDisplayer:
    """結果表示クラス"""

    def display_results(self, result: RecipeExtractionResult):
        """抽出結果を表示"""
        print("\n" + "="*50)
        print("📊 === レシピ抽出結果 ===")
        print("="*50)

        if result.extracted_recipe:
            recipe = result.extracted_recipe
            print(f"\n🍽️ レシピ名: {recipe.name}")
            print(f"📝 材料数: {len(recipe.ingredients)}個")

            print("\n📋 材料リスト:")
            for i, ingredient in enumerate(recipe.ingredients, 1):
                print(f"  {i:2d}. {ingredient.name} - {ingredient.quantity}{ingredient.unit}")
        else:
            print("\n❌ レシピ情報の抽出に失敗しました")

        if result.total_cooking_minutes:
            print(f"\n🕐 総調理時間: {result.total_cooking_minutes}分")
        else:
            print("\n⏰ 調理時間情報なし")

        print("\n" + "="*50)

        # JSON出力
        json_result = {
            "recipe": {
                "name": result.extracted_recipe.name if result.extracted_recipe else None,
                "ingredients": [
                    {
                        "name": ing.name,
                        "quantity": ing.quantity,
                        "unit": ing.unit
                    }
                    for ing in result.extracted_recipe.ingredients
                ] if result.extracted_recipe else [],
            },
            "total_cooking_minutes": result.total_cooking_minutes
        }

        print("\n📄 JSON出力:")
        print(json.dumps(json_result, ensure_ascii=False, indent=2))
