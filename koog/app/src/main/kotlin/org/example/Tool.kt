package org.example

// Koog Tools API
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer

/**
 * 調理時間計算ツール
 */
@Serializable
@LLMDescription("調理時間の合計を計算するための引数")
data class SumMinutesArgs(
    @property:LLMDescription("調理時間の値の配列（分単位）")
    val minutes: List<Float>
) : ToolArgs

class CalculateTool : SimpleTool<SumMinutesArgs>() {
    companion object {
        const val NAME = "sumMinutes"
    }

    suspend fun sum(args: SumMinutesArgs): Float {
        println("\n🛠️ === sumMinutesツールが呼び出されました！ ===")
        return args.minutes.sum()
    }

    // NOTE: SimpleToolを継承しているため必要
    override suspend fun doExecute(args: SumMinutesArgs): String {
        return sum(args).toString()
    }

    override val argsSerializer: KSerializer<SumMinutesArgs>
        get() = SumMinutesArgs.serializer()

    override val descriptor: ToolDescriptor
        get() = ToolDescriptor(
            name = NAME,
            description = "複数の調理時間の値を分単位で合計し、総調理時間を計算する",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "minutes",
                    type = ToolParameterType.List(ToolParameterType.Float),
                    description = "調理時間の値の配列（分単位）"
                )
            ),
            optionalParameters = listOf()
        )
}
