# Mastraでのワークフロー
## 環境変数
プロジェクトのルートディレクトリに `OPENAI_API_KEY` を指定した `.env`ファイルを格納すると、ワークフローが実行可能になります

```shell
$ cat .env.sample
OPENAI_API_KEY=sk-xxx...

$ cp .env.sample .env
```

## コマンド
`mastra`ディレクトリで以下のコマンドを実行すると、ワークフローが実行されます

```shell
# npmが未インストールの場合(Mac OS)
$ brew install npm

# 実行
$ npm run dev
```
