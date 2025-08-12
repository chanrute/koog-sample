package org.example

import dev.langchain4j.agent.tool.Tool

/**
 * LangChain4j Tool Functions（Koog版のCalculateToolに相当）
 * 調理時間の分数の合計を計算する
 */
class CalculateTool {
    
    /**
     * 複数の調理時間の値を分単位で合計し、総調理時間を計算する
     * KoogのCalculateToolのsumMinutesに相当
     */
    @Tool("複数の調理時間の値を分単位で合計し、総調理時間を計算する")
    fun sumMinutes(minutes: List<Float>): Float {
        println("\n🛠️ === sumMinutesツールが呼び出されました！ ===")
        println("入力された時間: $minutes")
        val total = minutes.sum()
        println("合計時間: ${total}分")
        return total
    }
}