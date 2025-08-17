package org.example

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * 材料を表すデータクラス
 *
 * @property name 材料名
 * @property unit 単位（例：グラム、カップ、個など）
 * @property quantity 数量
 */
@Serializable
@SerialName("Ingredient")
@LLMDescription("料理の材料")
data class Ingredient(
    @property:LLMDescription("材料名（例：玉ねぎ、にんじん、牛肉など）")
    val name: String,

    @property:LLMDescription("材料の単位（例：グラム、カップ、個、大さじ、小さじなど）")
    val unit: String,

    @property:LLMDescription("材料の数量")
    val quantity: Float
)

/**
 * 料理のレシピを表すデータクラス
 *
 * @property name レシピ名
 * @property ingredients 必要な材料のリスト
 */
@Serializable
@SerialName("RecipeEntity")
@LLMDescription("料理のレシピ情報")
data class RecipeEntity(
    @property:LLMDescription("料理のレシピ名（例：カレーライス、パスタボロネーゼなど）")
    val name: String,

    @property:LLMDescription("レシピに必要な材料のリスト")
    val ingredients: List<Ingredient>
) {
    companion object {
        /**
         * Koogの構造化出力用のサンプルレシピ例を提供する
         */
        fun getExampleRecipes(): List<RecipeEntity> {
            return listOf(
                RecipeEntity(
                    name = "カレーライス",
                    ingredients = listOf(
                        Ingredient("玉ねぎ", "個", 2.0f),
                        Ingredient("にんじん", "本", 1.0f),
                        Ingredient("牛肉", "グラム", 300.0f),
                        Ingredient("カレールー", "箱", 1.0f)
                    )
                ),
                RecipeEntity(
                    name = "パスタボロネーゼ",
                    ingredients = listOf(
                        Ingredient("パスタ", "グラム", 200.0f),
                        Ingredient("挽き肉", "グラム", 150.0f),
                        Ingredient("トマト缶", "缶", 1.0f),
                        Ingredient("オリーブオイル", "大さじ", 2.0f)
                    )
                )
            )
        }
    }
}
