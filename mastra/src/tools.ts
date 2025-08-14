/**
 * 調理時間計算ツール
 * KoogのCalculateToolとLangChain4jのFunction Callingに相当する機能を提供
 */

export interface SumMinutesArgs {
  /** 調理時間の値の配列（分単位） */
  minutes: number[];
}

export class CalculateTool {
  static readonly NAME = 'sumMinutes';

  /**
   * 複数の調理時間の値を分単位で合計し、総調理時間を計算する
   */
  sum(args: SumMinutesArgs): number {
    console.log("\n🛠️ === sumMinutesツールが呼び出されました！ ===");
    const total = args.minutes.reduce((acc, curr) => acc + curr, 0);
    console.log(`🕐 調理時間合計: ${args.minutes.join(' + ')} = ${total}分`);
    return total;
  }

  /**
   * Mastra用のツール定義
   */
  getToolDefinition() {
    return {
      name: CalculateTool.NAME,
      description: '複数の調理時間の値を分単位で合計し、総調理時間を計算する',
      parameters: {
        type: 'object',
        properties: {
          minutes: {
            type: 'array',
            items: {
              type: 'number'
            },
            description: '調理時間の値の配列（分単位）'
          }
        },
        required: ['minutes']
      }
    };
  }

  /**
   * ツール実行用のハンドラ
   */
  async execute(args: SumMinutesArgs): Promise<number> {
    return this.sum(args);
  }
}