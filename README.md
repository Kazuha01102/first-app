# SERVER CONTROL / Monitoring Dashboard

Spring Boot **4.1.1** / **Java 26** / Maven **3.9.16**。WSL2のLinux環境と、このSpring Bootプロセスを監視するローカル専用ダッシュボードです。HTML・CSS・JavaScriptのみで動作し、フロントエンドのビルドやCDN接続は不要です。

## 起動（WSL / Ubuntu）

WindowsのVS CodeではWSLターミナルを使ってください。このPCのWindows既定Javaは21なので、ビルドはJava 26のあるWSL側で行います。

~~~bash
cd /mnt/c/Users/user/git/first-app
java -version
mvn -version
mvn clean package
bash scripts/run-dev.sh
~~~

- ダッシュボード: **http://127.0.0.1:8081/**
- 既存の計算機: **http://127.0.0.1:8081/calculator.html**
- 終了: 起動したターミナルで **Ctrl+C**

起動スクリプトはIPv4の **127.0.0.1:8081** のみを使います。JavaのIPv6-mapped待受をWSLのlocalhost転送が拾わないケースを避けるため、-Djava.net.preferIPv4Stack=true を指定しています。8081が使用中なら別プロセスを停止せず起動エラーになります。

既存の **8080 / myfirstapp.service / /opt/myfirstapp/app.jar** を変更する操作はありません。生成JARをそこへコピーしたり、サービスを再起動したりしないでください。GitHubへのPushもこの手順には含みません。

## 機能

- OS、Java、Linux起動後の稼働時間、JVM稼働時間、接続状態、最終取得時刻
- Linux CPU・メモリ・Swap、JVMヒープ・非ヒープ・スレッド
- CPU／メモリの実測グラフ、1分／5分／15分の表示切替、平均・最大値
- Linux非ループバックIFの送受信量／速度と、このJVMのHTTP件数・4xx／5xx・監視API応答時間
- INFO／WARN／ERRORログの検索・絞り込み、新しいERRORの通知
- 5秒更新、一時停止、手動更新、ローディング、API失敗時の最終値・再接続
- モバイル表示、キーボード操作、外部ライブラリなしのSVGグラフ
- 元の四則計算API・挨拶APIを維持

取得できない指標はJSONでは **null**、画面では **N/A**。架空データや事前生成のグラフは使いません。CPU・通信速度の最初の1サンプルもN/Aです。

## API

| GET | 内容 |
| --- | --- |
| /api/monitor | 全体の同時刻スナップショット。従来のos／cpuCoresも保持 |
| /api/monitor/overview | 環境・OS・Java・稼働時間 |
| /api/monitor/cpu | Linux CPU、直近最大15分の平均・最大 |
| /api/monitor/memory | Linux RAM・Swap |
| /api/monitor/jvm | このJavaプロセスのメモリ・スレッド |
| /api/monitor/network | Linux IFカウンターとアプリHTTP統計 |
| /api/monitor/health | APIプロセスのヘルス |
| /api/monitor/history?seconds=300 | 1〜900秒の履歴（最大900サンプル） |
| /api/monitor/logs?level=ALL&limit=100 | level=ALL／INFO／WARN／ERROR、limit=1〜200 |

計算機API: /calculate?a=10&b=5&op=add（subtract／multiply／divideも対応）、/add、/multiply。/hello、/hello-world、/test もそのままです。

## テスト

~~~bash
mvn clean package
# 以下のみNode.jsが必要。アプリの実行には不要。
node --test src/test/js/metrics.test.mjs
~~~

任意のChromeブラウザテスト: Playwrightがある環境で、8081版を起動してから **node scripts/browser-check.mjs**。外部ディレクトリにインストール済みの場合は **PLAYWRIGHT_MODULE** にその **playwright/index.mjs** の絶対パスを指定します。テスト用エラー・欠測レスポンスはブラウザ内だけで差し替え、実サーバーにテスト用APIや架空指標を追加しません。

検証画像・ブラウザ結果は **target/browser-check/**、Javaテスト結果は **target/surefire-reports/**。いずれもGit管理外です。

## 監視範囲と制約

WSL2のCPU／メモリはWSLのLinux VMから見える値で、**Windowsホスト全体の使用率ではありません**。LinuxのIF統計は現在のネットワーク名前空間に属し、複数IFでは転送が重複計上される場合があります。JVMとHTTPの値は**8081で起動したこのプロセスだけ**です。既存の8080サービスや他サーバーを遠隔監視する実装ではありません。

サンプラーは約1秒ごと、画面は5秒ごとに更新。履歴・ログ・HTTP集計はメモリ内で、再起動時にリセットします。ログはアプリとSpring Bootの許可したロガーのみ、直近300件、1メッセージ最大1000文字。既知の認証情報をマスクし、スタックトレース・環境変数・リクエスト本文・任意のログファイルは取得しません。将来追加するコードでも秘密情報そのものをログへ出さないでください。

認証・遠隔アクセス・永続保存・外部通知・Actuatorの公開・任意コマンド実行・JMX管理ポート公開は実装していません。認証なしでインターネットに公開しないでください。

詳しい計測方法・構成は [docs/monitoring.md](docs/monitoring.md) を参照してください。