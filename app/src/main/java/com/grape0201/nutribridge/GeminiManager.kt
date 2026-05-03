package com.grape0201.nutribridge

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NutritionInfo(
    @SerialName("eaten_at") val eatenAt: String,
    val menu: String,
    val calories: Int,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    @SerialName("source_url") val sourceUrl: String? = null
)

@Serializable
data class MealResponse(
    val meals: List<NutritionInfo>
)

class GeminiManager(apiKey: String) {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = apiKey
    )

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    suspend fun analyzeMeal(text: String, bitmap: Bitmap?, eatenAt: String): MealResponse? {
        val prompt = """
            あなたはプロの栄養士です。提供された食事の画像やテキストから、料理名と正確な栄養素（カロリー、タンパク質、脂質、炭水化物）を推測してください。
            場合によっては複数の料理が含まれている可能性があるため、それぞれの料理を個別に抽出してリスト形式で返してください。

            以下の手順で推論を行ってください：

            1. Vision解析: 画像やテキストから各料理名と推定分量（g単位、または「1人前」等の目安）を特定する。
            2. Grounding & 多角的照合:
              - Google検索を使用し、特定された料理の栄養素情報を検索する。
              - 重要: 検索結果が「低糖質」「ダイエット用」「特定の個人レシピ」等、特殊な材料（代替食品）を使用している場合は、その数値を採用せず、**日本食品標準成分表等の公的データベースに基づく「標準的な数値」**を優先してください。
              - 画像の見た目（油の量、具材の密度）と、検索した数値に大きな矛盾がないか自己検証してください。
            3. 構造化出力: 解析結果を必ず以下の**単一のJSONオブジェクト**形式で出力してください。リスト形式(`[...]`)で囲わず、`{ "meals": [...] }` の形式にしてください。他のテキストは一切含めないでください。フィールド名は必ずこの通りにしてください。

            JSON Structure:
            {
              "meals": [
                {
                  "eaten_at": "$eatenAt",
                  "menu": "料理名",
                  "calories": 整数(kcal),
                  "protein": 小数(g),
                  "fat": 小数(g),
                  "carb": 小数(g),
                  "source_url": "参照したURL(あれば)"
                }
              ]
            }

            共通の時刻設定:
            eaten_at = "$eatenAt"

            ユーザーテキスト入力: $text
        """.trimIndent()

        val response = try {
            if (bitmap != null) {
                generativeModel.generateContent(content {
                    image(bitmap)
                    text(prompt)
                })
            } else {
                generativeModel.generateContent(prompt)
            }
        } catch (e: Exception) {
            return null
        }

        return try {
            val jsonStr = response.text?.let { extractJson(it) }
            if (jsonStr != null) {
                json.decodeFromString<MealResponse>(jsonStr)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractJson(text: String): String? {
        val jsonStart = text.indexOf("{")
        val jsonEnd = text.lastIndexOf("}")
        return if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            text.substring(jsonStart, jsonEnd + 1)
        } else null
    }
}
