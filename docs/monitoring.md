# 実装と指標の定義

## 構成

**MonitorController** が読み取り専用APIを公開し、**MonitorService** が1秒ごとに同時刻のスナップショットを作ります。APIごとにOSを再計測しないため、複数カードの時刻が揃います。

- **LinuxMetricsReader**: 固定のprocfsファイルを読み取る。シェル実行なし。
- **HttpMetrics / MonitoringFilter**: このJVMで完了したHTTPの件数、応答時間、エラーを集計。
- **RecentLogs / DashboardLogAppender**: 件数制限付きアプリログ、機密値のマスク。
- **MonitorModels**: JSONの型を表すJava record。
- **static/index.html / css/dashboard.css / js/dashboard.js**: 表示と更新。
- **js/metrics.mjs**: 数値表示とグラフの計算。Node標準テストでも検証。
- **calculator.html / js/calculator.js**: 既存計算APIを使う画面。HelloControllerは未変更。

## LinuxとJVMの区別

| 指標 | 取得元・意味 |
| --- | --- |
| CPU使用率 | /proc/statの集計cpu行。2回の差分で (総増分−idle増分)/総増分×100 |
| idle | idle + iowait。guest／guest_niceはuser／niceと重複するので総計へ再加算しない |
| CPU数 | /proc/statのcpuN行数。JVM availableProcessorsは別にJVM値として返す |
| 総RAM | /proc/meminfoのMemTotal × 1024（kB表記はKiB） |
| 使用RAM | MemTotal−MemAvailable。MemFreeだけでは計算しない |
| Swap | SwapTotal−SwapFree |
| Linux稼働時間 | /proc/uptimeの先頭値 |
| 通信量 | /proc/net/devの受信bytesと送信bytes。loは除外 |
| 通信速度 | カウンター差分 / System.nanoTimeによる実際の経過秒数 |
| JVM | ManagementFactoryのMemory／Thread／Runtime MXBean |
| HTTP件数 | このプロセスで完了した全リクエスト。静的ファイル・監視ポーリングも含む |
| HTTPエラー | 完了応答の4xx + 5xx。内部の未処理例外は500として計上 |
| API応答時間 | /api/monitorで始まる完了リクエストのサーバー内処理時間。ブラウザ往復時間ではない |

WSL検出はカーネルバージョンのMicrosoft表記で行います。OS名は/etc/os-releaseのPRETTY_NAMEのみ。Linuxファイルが使えない場合、JVM値は継続し、Linux指標はnullになります。WindowsのメモリをLinuxのメモリとして代用しません。

CPU初回・変化のないカウンター・カウンター巻き戻り・不正データはnull。ネットワーク初回・IF構成変更・カウンター減少も速度をnullにします。0は実際に0と測定した場合だけです。

CPU平均・最大は直近15分かつ最大900サンプル。グラフは選択期間の実測履歴だけを描き、収集開始直後は実際に集まった区間へ拡大します。横軸には実時刻を表示し、欠測や3秒超の採取空白を線でつなぎません。

ネットワーク累積値はIF作成後、HTTP統計はJVM起動後です。WSL共有VM・名前空間から見える範囲なので、他WSLディストリビューション／プロセスの影響を含む場合があります。仮想IFの総和はワイヤ上の一意なトラフィック量とは限りません。

## ログと安全な公開範囲

ログの許可範囲はmyfirstapp.*とorg.springframework.boot.*のINFO／WARN／ERROR。ローカルjournal、任意パス、スタックトレース、HTTP入力値は取得しません。ログAPIの検索条件はlevelとlimitだけです。Java側で認証情報の既知パターンを除去し、画面はtextContentで描画します。未知の秘密表記まで保証する仕組みではないため、秘密をロギングしない運用が前提です。

RESTにCache-Control: no-store、全応答にnosniff／Referrer-Policy／Content-Security-Policyを付与。CORSの許可設定、Actuatorの環境情報API、シェル／ファイル操作APIはありません。JMXはSpring設定で無効。起動スクリプトはIPv4 loopbackの8081だけを使用します。

API接続に失敗すると画面にOfflineを表示し、保持した数値を薄くして「最終取得値」と示します。スナップショットが15秒以上更新されていない場合も異常を表示します。一部の履歴／ログAPIだけが失敗した場合は主指標を表示したまま、該当箇所の取得失敗を通知します。

## 参考となる一次資料

- [Linux kernel: /proc Filesystem](https://docs.kernel.org/filesystems/proc.html) — /proc/stat、meminfo、net/dev、uptime。
- [Microsoft: WSL advanced settings](https://learn.microsoft.com/en-us/windows/wsl/wsl-config) — WSL VMのメモリ・CPU設定とlocalhost forwarding。
- [Microsoft: WSL networking](https://learn.microsoft.com/en-us/windows/wsl/networking) — WindowsからWSLアプリへのlocalhost接続。
- [Spring Boot testing](https://docs.spring.io/spring-boot/how-to/testing.html) — Spring MVCの統合テスト。
