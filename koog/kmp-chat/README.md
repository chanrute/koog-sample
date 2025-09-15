## 🚀 アプリ起動手順

### 1. 前提条件

- macOS (Apple Silicon推奨)
- Xcode 16.4
- Java 17以降
- `.env`ファイルに`OPENAI_API_KEY`が設定されていること

### 2. フレームワークビルド

```bash
# iOS Simulator ARM64用フレームワークをビルド
./gradlew linkDebugFrameworkIosSimulatorArm64

# その他のターゲット（必要に応じて）
./gradlew linkDebugFrameworkIosX64          # Intel Mac Simulator
./gradlew linkDebugFrameworkIosArm64        # 実機用
```

### 3. iOSアプリの起動

```bash
# Xcodeプロジェクトを開く
cd iosApp
open iosApp.xcodeproj
```

Xcodeで：
1. **デバイス選択**: iPhone 15 ProなどのSimulatorを選択
2. **▶️ボタン**: ビルド＆実行

