# LangChain4jでのワークフロー
## 環境変数
プロジェクトのルートディレクトリに `OPENAI_API_KEY` を指定した `.env`ファイルを格納すると、ワークフローが実行可能になります

```shell
$ cat .env.sample
OPENAI_API_KEY=sk-xxx...

$ cp .env.sample .env
```

## コマンド
`langchain-4j`ディレクトリで以下のコマンドを実行すると、ワークフローが実行されます

```shell
# Gradleが未インストールの場合(Mac OS)
$ brew install gradle

# 実行
$ ./gradlew run
```