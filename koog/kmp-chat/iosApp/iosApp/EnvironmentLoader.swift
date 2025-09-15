import Foundation

class EnvironmentLoader {
    private static var envVariables: [String: String] = [:]

    static func loadEnvironment() {
        guard let envPath = findEnvFile() else {
            print("⚠️ .env file not found")
            return
        }

        do {
            let envContent = try String(contentsOfFile: envPath, encoding: .utf8)
            parseEnvContent(envContent)
            print("✅ Environment variables loaded from: \(envPath)")
        } catch {
            print("❌ Failed to read .env file: \(error)")
        }
    }

    private static func findEnvFile() -> String? {
        // プロジェクトルートの.envファイルを探す
        let projectRoot = "/Users/chanrute/project/koog-sample"
        let envPath = "\(projectRoot)/.env"

        if FileManager.default.fileExists(atPath: envPath) {
            return envPath
        }

        return nil
    }

    private static func parseEnvContent(_ content: String) {
        let lines = content.components(separatedBy: .newlines)

        for line in lines {
            let trimmedLine = line.trimmingCharacters(in: .whitespacesAndNewlines)

            // 空行やコメント行をスキップ
            if trimmedLine.isEmpty || trimmedLine.hasPrefix("#") {
                continue
            }

            // KEY=VALUE形式をパース
            let components = trimmedLine.components(separatedBy: "=")
            if components.count >= 2 {
                let key = components[0].trimmingCharacters(in: .whitespacesAndNewlines)
                let value = components.dropFirst().joined(separator: "=").trimmingCharacters(in: .whitespacesAndNewlines)
                envVariables[key] = value
            }
        }
    }

    static func getVariable(_ key: String) -> String? {
        return envVariables[key]
    }

    static func printLoadedVariables() {
        print("📋 Loaded environment variables:")
        for (key, value) in envVariables {
            // APIキーは一部のみ表示
            if key.contains("API_KEY") {
                let maskedValue = String(value.prefix(10)) + "..." + String(value.suffix(4))
                print("  \(key) = \(maskedValue)")
            } else {
                print("  \(key) = \(value)")
            }
        }
    }
}