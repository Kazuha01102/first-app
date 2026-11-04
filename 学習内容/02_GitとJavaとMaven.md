# 02. GitHub・Java・Maven・Spring Bootを学ぶ

## 1. Git / GitHub

- **Git**: ソースコードの変更履歴を管理するツール。
- **GitHub**: Gitのリポジトリをオンラインで共有・保管するサービス。
- **リポジトリ**: プロジェクトのファイルと変更履歴をまとめたもの。
- **clone**: リモートのリポジトリをローカルに複製する操作。

### [実施済み] クローン

```bash
git clone https://github.com/Kazuha01102/first-app.git
cd first-app
ls
```

- `git clone <URL>`: GitHubからソースを取得し、`first-app` ディレクトリを作る。
- `cd first-app`: プロジェクトの中へ移動。
- `ls`: `README.md`, `docs`, `pom.xml`, `scripts`, `src` などを確認。

既に `first-app` がある状態で `git clone` を繰り返すと、同名ディレクトリの存在で失敗する。既存の作業ディレクトリを使う。

### [参考] 更新とGitの状態確認

```bash
git status
git branch --show-current
git pull --ff-only
git log --oneline -5
```

- `git status`: 変更済み・未追跡ファイルなどを確認
- `git branch --show-current`: 今使っているブランチ名を確認
- `git pull --ff-only`: リモートの変更を取り込む（fast-forwardできない場合は停止し、勝手にマージしない）
- `git log --oneline -5`: 直近5件のコミットを表示

これらの更新用コマンドは学習用に紹介している。今回のデプロイログで全実行を確認したわけではない。

## 2. Javaのバージョンと実行場所

```bash
java -version
readlink -f "$(command -v java)"
```

- `java`: Java仮想マシン（JVM）を起動するコマンド
- `-version`: 使用するJavaバージョンを表示
- `command -v java`: シェルが見つけた `java` の位置を調べる
- `$( ... )`: 中のコマンドの出力を文字列として埋め込む（コマンド置換）
- `readlink -f`: シンボリックリンクをたどって実際のファイルパスを表示
- `" ... "`: パスに空白が含まれても1つの引数として扱うための引用

### [実施済み] 今回の結果

```text
/usr/lib/jvm/java-26-openjdk-amd64/bin/java
```

これはsystemdの `ExecStart` にも使用した。Java 26と指定していても、**別のPCのJavaの位置と一致するとは限らない**。

## 3. Mavenでビルドする

### [実施済み]

```bash
cd ~/first-app
mvn clean package
```

- **Maven**: Javaプロジェクトの依存関係やコンパイル・テストを管理するビルドツール。
- **pom.xml**: Mavenの設定ファイル。Javaバージョン、依存ライブラリ、Spring Bootなどを定義。
- `mvn`: Mavenを実行する。
- `clean`: 過去のビルド成果物を `target/` から削除。
- `package`: コードをコンパイルし、通常はテストを実行して配布用JARを生成。
- `mvn clean package`: 古い成果物を消してから、一式を新しく作る操作。

今回はビルド成功を確認し、`target/myfirstapp-0.0.1-SNAPSHOT.jar` が生成された。

```bash
ls -lh target/myfirstapp-0.0.1-SNAPSHOT.jar
```

- `target/`: Mavenの生成物ディレクトリ
- `.jar`: Javaのクラスや依存ファイル等をまとめたアーカイブ（このSpring Bootアプリは実行可能JAR）

**注意:** Mavenは `maven` ではなく通常 `mvn` コマンドで実行する。

## 4. Spring Bootを手動起動する

### [実施済み]

```bash
java -Djava.net.preferIPv4Stack=true -jar target/myfirstapp-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8081
```

長いので左から分解する。

| 部分 | 意味 |
| --- | --- |
| `java` | Javaを起動 |
| `-Djava.net.preferIPv4Stack=true` | IPv4スタックを優先するJVMのシステムプロパティ |
| `-jar` | 指定されたJARを実行 |
| `target/myfirstapp-0.0.1-SNAPSHOT.jar` | 実行するJARへのパス |
| `--server.address=127.0.0.1` | Spring Bootをこのサーバー自身からだけアクセス可能なアドレスにバインド |
| `--server.port=8081` | アプリが待ち受けるTCPポート番号 |

**順序が重要:** `-D...` はJavaのオプションなので `-jar` より前。Spring Bootの `--server.*` はJAR名の後。

`127.0.0.1` はループバックアドレス（localhost）。`0.0.0.0` に変更するとアクセス範囲が広がるため、認証なしで変更しない。

**手動起動**はそのターミナルに依存する。恒久運用にはsystemdを使用した。

## 5. HTTPとcurlで動作確認

### [実施済み] トップページ

```bash
curl -I http://127.0.0.1:8081/
```

- `curl`: HTTPなどの通信でサーバーにリクエストするツール
- `-I`: HTTPヘッダーだけを取得（HTTP HEADリクエスト）
- `HTTP/1.1 200`: リクエスト成功を示すステータス
- `/`: ダッシュボードのトップページ

### [実施済み] API

```bash
curl http://127.0.0.1:8081/api/monitor/health
```

`curl` に `-I` を付けなければ、ここではレスポンス本文のJSONを確認できる。

実際の例:

```json
{"status":"UP","checkedAt":"2026-11-04T03:52:57.495202902Z","service":"myfirstapp"}
```

- `status: UP`: アプリのヘルスAPIが正常応答
- `checkedAt`: 確認時刻（`Z` はUTC）
- `service`: サービス名

**HTTP 200とUPだけでサーバー全体の健全性が保証されるわけではない**。まずはアプリが返答できることの確認。

## 6. Spring Bootと関連語句

- **Spring Boot**: JavaでWebサーバーやAPIを比較的簡単に開発・実行するフレームワーク。
- **Tomcat**: Spring Bootに組み込めるWebサーバー（Servletコンテナ）。
- **API**: プログラム同士が決めた形式で情報をやり取りする窓口。
- **REST API**: HTTPのURLとメソッドなどを使いリソースを操作・取得する設計スタイル。
- **JVM**: Javaバイトコードを実行する仮想マシン。
- **localhost**: その操作をしているコンピューター自身を指す名前。

今回の監視画面は、その物理UbuntuとアプリJVMの指標を表示する構成になっている。
