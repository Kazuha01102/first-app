# 実装・検証記録（2026-10-30 / JST）

## 実装した機能

SERVER CONTROLを既存first-appに実装。OS／Java／CPU／RAM／Swap／JVM／Linuxネットワーク／アプリHTTP／最近のログを表示します。実測グラフ、5秒更新、表示期間変更、一時停止、手動更新、欠測N/A、API切断と復帰、部分失敗、ERROR通知、レスポンシブ表示に対応しました。

CPU・メモリはWSL2のLinux環境、JVM・HTTPは開発版プロセスだけが対象。任意コマンド、権限昇格、環境変数の取得、外部公開は実装していません。

## 確認した環境

- ブランチ: feature/monitoring-dashboard
- Ubuntu: 26.04 LTS
- Java: Ubuntu OpenJDK 26.0.2
- Maven: 3.9.16
- Spring Boot: pom.xmlの4.1.1を維持（依存追加なし）
- 起動: IPv4 127.0.0.1:8081
- 起動スクリプト: bash scripts/run-dev.sh
- Windows既定Javaは21だったため、ビルド・起動はWSLで実行

## 変更・追加ファイル

- 変更: MyfirstappApplication.java（スケジューリング）、application.properties（ローカル8081、JMX無効、エラー詳細非公開）、static/index.html、README.md。
- 修正: 作業途中で未追跡だったMonitorController.java。Map／HashMapのimport不足、ManegementFactoryのスペル誤り、String.getName()の誤用で失敗していた実装を、型付きREST APIへ置き換えた。
- 追加: monitor/MonitorModels.java、LinuxMetricsReader.java、MonitorService.java、HttpMetrics.java、MonitoringFilter.java、RecentLogs.java、DashboardLogAppender.java。
- 追加: logback-spring.xml、static/css/dashboard.css、static/js/dashboard.js、metrics.mjs、calculator.js、static/calculator.html、favicon.svg。
- 追加: MonitorApiIntegrationTests、monitor配下の3テストクラス、src/test/js/metrics.test.mjs。
- 追加: scripts/run-dev.sh、scripts/browser-check.mjs、.gitattributes、docs/monitoring.md、本記録。
- pom.xmlと既存HelloController.javaは未変更。

作業前のsrc一式とpom.xml、元の未追跡MonitorControllerは **C:/Users/user/.codex/tmp/FirstAppBackup_20261030/** にバックアップした。Gitのreset、stash操作、commit、pushは行っていない。

## API

すべてGET、JSON。/api/monitor と、その配下の /overview、/cpu、/memory、/jvm、/network、/health、/history、/logs の9本。範囲・レスポンス定義はREADMEとmonitoring.md参照。元の計算API・挨拶APIも維持。

## ビルド・テスト

| 検証 | 結果 |
| --- | --- |
| 作業前 mvn clean package | MonitorControllerの5件のコンパイルエラーを再現 |
| 最終 mvn clean package | BUILD SUCCESS / Javaテスト16件、失敗0、エラー0 |
| node --test src/test/js/metrics.test.mjs | 3件成功 |
| JavaScript構文確認 | 成功 |
| Chrome / Playwright | 12項目成功、未捕捉例外0、初期表示のConsole／CSPエラー0 |
| APIの実接続 | 全9本200 / JSON |
| 実測データ | Linux CPU・RAM・Swap・JVM・IF通信量を取得 |
| 自動更新 | 5秒後の再取得と時刻更新を確認 |
| UI異常系 | 切断、復帰、履歴APIのみ失敗、初回Offline、N/Aを確認 |
| ログ | 分類・検索・新しいERROR通知・HTMLを文字列として安全に表示 |
| モバイル | 390px、横スクロールなし、計算機リンクあり |
| 計算機 | 加減乗除とゼロ除算をブラウザ・APIで確認 |

ブラウザの欠測・ERRORログはブラウザ内の検証用レスポンスだけ。アプリ側に架空指標やテスト用エラーAPIは入れていない。納品画像は実データを表示した状態。

Javaテストの実行時にはMockito／Byte Buddyの動的エージェントに関するJDK警告が出る。テストは成功しており、通常起動するアプリにはこのテスト用エージェントは付かない。

結果: target/surefire-reports、target/browser-check/result.json。画像: target/browser-check/desktop.png、mobile.png。ビルドログの控えは外部バックアップ内final-build.log。

## 起動とURL

~~~bash
cd /mnt/c/Users/user/git/first-app
mvn clean package
bash scripts/run-dev.sh
~~~

http://127.0.0.1:8081/ と http://127.0.0.1:8081/calculator.html。
確認後も8081の開発プロセスを起動した状態で残している。停止は起動ターミナルのCtrl+C。JARが読まれている間のcleanは避け、再ビルドする場合は開発プロセスだけを停止する。

## 既存8080サービスへの影響

**既存JAR・systemd設定の上書き、サービス停止・再起動コマンドは実行していない。**

配置済み /opt/myfirstapp/app.jar のSHA-256は作業前後で一致:

~~~text
a6c03b350ad482c8335eb26d8a8e5eb2cd8499f152765abc2b56aae2449e1480
~~~

ただし、WSLの起動状態変化に伴うと考えられるPID・起動時刻の変更を作業中に検出した。記録上、開始時PID165（09:00:43）、途中PID128（09:22:25）、最終確認PID162（09:23:25）。途中の起動直後には8080への接続が一時的に失敗した。NRestarts=0で、WSLのアイドル終了／ディストリビューション再起動が原因と推測されるが、原因の断定まではしていない。**完全無停止だったとは報告できない。**

最終版を8081で動かした状態では、既存サービスはactive/running、8080の計算APIは200相当の正常応答（計算結果:15.0）を確認。サービスへの直接操作はせず、開発プロセスだけをPIDとコマンドラインで照合して停止・再起動した。

## 未実装・未検証と今後

- 既存8080プロセス自体の遠隔監視、複数サーバーの監視、Windowsホスト全体の監視は対象外。
- 認証、外部公開、メトリクスの永続保存、通知配信、長時間負荷試験は未実装・未実施。
- この環境ではChromeで検証。Safari・Firefox・別OSでは未検証。
- ログのマスクは既知パターンで、すべての秘密情報の自動検出を保証しない。ログ出力元に秘密を含めないこと。
- 今後は、認証設計を含めたPrometheus／Micrometer連携、永続履歴、閾値通知、監視対象の明示的な複数化が改善候補。
