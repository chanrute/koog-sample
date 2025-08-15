# LangChainでのワークフロー
## 環境変数
プロジェクトのルートディレクトリに `OPENAI_API_KEY` を指定した `.env`ファイルを格納すると、ワークフローが実行可能になります

```shell
$ cat .env.sample
OPENAI_API_KEY=sk-xxx...

$ cp .env.sample .env
```

## コマンド
`lanchain`ディレクトリで以下のコマンドを実行すると、ワークフローが実行されます

```shell
# uvが未インストールの場合(Mac OS)
$ curl -LsSf https://astral.sh/uv/install.sh | sh

# 実行
$ uv run python main.py
```
