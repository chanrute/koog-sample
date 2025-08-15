"""
調理時間計算用のLangChain Tools
KoogのCalculateToolに相当する機能をLangChain Toolsで実装
"""
from langchain_core.tools import tool
from typing import List
import re


@tool
def sum_minutes(minutes_list: List[float]) -> float:
    """
    調理時間の合計を計算するツール
    KoogのCalculateToolのsumMinutesメソッドに相当

    Args:
        minutes_list: 調理時間（分）のリスト

    Returns:
        合計調理時間（分）
    """
    if not minutes_list:
        return 0.0

    total = sum(minutes_list)
    print(f"🔢 調理時間計算: {minutes_list} → {total}分")
    return total

# ツールのリストを定義（AgentExecutorで使用）
# KoogのApp.ktパターンに合わせてsum_minutesのみを使用
cooking_tools = [sum_minutes]
