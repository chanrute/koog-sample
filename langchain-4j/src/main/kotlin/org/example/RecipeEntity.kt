package org.example

import kotlinx.serialization.Serializable
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable as JavaSerializable

/**
 * 材料を表すデータクラス（LangChain4j版）
 */
@Serializable
data class Ingredient(
    @param:JsonProperty("name")
    val name: String,
    
    @param:JsonProperty("unit")
    val unit: String,
    
    @param:JsonProperty("quantity")
    val quantity: Float
) : JavaSerializable

/**
 * 料理のレシピを表すデータクラス（LangChain4j版）
 */
@Serializable
data class RecipeEntity(
    @param:JsonProperty("name")
    val name: String,
    
    @param:JsonProperty("ingredients")
    val ingredients: List<Ingredient>
) : JavaSerializable {
    companion object {
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

/**
 * PDF判定結果
 */
@Serializable
data class PdfValidationResult(
    @param:JsonProperty("isRecipe")
    val isRecipe: Boolean,
    
    @param:JsonProperty("reason")
    val reason: String
) : JavaSerializable

/**
 * レシピ抽出結果
 */
@Serializable
data class RecipeExtractionResult(
    val extractedRecipe: RecipeEntity?,
    val totalCookingMinutes: Float? = null
) : JavaSerializable

/**
 * 調理時間計算用の結果データ
 */
@Serializable
data class CookingTimeResult(
    @param:JsonProperty("totalMinutes")
    val totalMinutes: Float,
    
    @param:JsonProperty("breakdown")
    val breakdown: List<String> = emptyList()
) : JavaSerializable