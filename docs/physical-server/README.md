# 物理Ubuntu Serverへのデプロイ記録（2026-11-04）

> 対象: **物理サーバー**上の Ubuntu Server / Spring Boot 監視ダッシュボード（SERVER CONTROL）。
> 自宅PCの **WSL2環境とは別物** です。既存の [ルートREADME](../../README.md) は主にWSL開発環境向けです。
>
> この資料は実際に実施・確認した操作と、今後の運用手順を整理したものです。
> GitHubは公開リポジトリのため、ログインパスワード・内部ネットワークの実IP・管理画面のURLは掲載しません。

## 0. 完成した構成

```text
手元のPC
  └─ Guacamole等からWindows踏み台サーバーへ接続
       ├─ PowerShell / Tera Term → SSH → 物理Ubuntu Server
       ├─ SSHローカルポート転送（踏み台 127.0.0.1:18081）
       └─ 踏み台のブラウザ → http://127.0.0.1:18081/
                                     │
                      SSHで転送     │
                                     ▼
                        物理Ubuntu Server
                        127.0.0.1:8081
                        systemd: myfirstapp.service
                        /opt/myfirstapp/app.jar
```

- アプリ: Spring Boot 4.1.1、Java 26、Mavenビルド、ポート `8081`。
- 物理サーバー上のソース: `/home/kazuha/first-app`（例: ログインユーザー `kazuha`）。
- systemdで動かすJAR: `/opt/myfirstapp/app.jar`。
- 実行ユーザー: ログイン不可の専用システムユーザー `myfirstapp`。
- 待受先: `127.0.0.1` のみ。**認証なしの監視画面を外部ネットワークに公開しない。**
- 実際に確認: localhostでHTTP 200 / ヘルスAPI `UP`、踏み台ブラウザで画面表示、systemdの `active (running)`。
- **未確認**: OS再起動後の自動起動、長期連続稼働、`enable` 後の `is-enabled` 結果（記録時点）。

## 1. 踏み台ターミナルからUbuntu Serverへ

1. Guacamole等で **Windows踏み台サーバー** に入る。
2. WindowsのPowerShell、またはTera Termを開く。
3. 物理Ubuntuの接続先IPを管理者の割当情報で確認する。以下では `<SERVER_IP>` と書く（実際に使ったのはプライベートIP）。
4. PowerShellから接続する場合:

```powershell
ssh kazuha@<SERVER_IP>
```

- 初回はホスト鍵指紋を照合し、信頼できる接続先と確認できたら受け入れる。
- パスワード入力時に文字が表示されないのは正常。
- 接続すると `kazuha@kazuha-sv:~$` のようなUbuntuのプロンプトになる。
- **踏み台のWindowsターミナル** と **Ubuntu内のSSHターミナル** では実行するコマンドが異なる。
- ネットワークのBMC管理口とOS側ネットワークは別。**BMCのネットワーク設定を変更しない**。OSのIPも管理者のIPAM割当を優先する。

### Ubuntu側の基本チェック

```bash
hostname
pwd
ip -br addr
java -version
mvn -version
git --version
```

物理サーバーで確認したJava実行パス:

```bash
readlink -f "$(command -v java)"
# /usr/lib/jvm/java-26-openjdk-amd64/bin/java
```

## 2. GitHubからアプリを取得してビルド

物理Ubuntu側で実施する。Java 26、Maven、Gitが導入されている前提。

```bash
cd ~
git clone https://github.com/Kazuha01102/first-app.git
cd ~/first-app
ls
mvn clean package
ls -lh target/myfirstapp-0.0.1-SNAPSHOT.jar
```

- 既に `~/first-app` が存在する場合は **再度cloneしない**。更新時の手順は末尾参照。
- `mvn clean package` はコンパイル・テストを行いJARを生成する。
- 実際にビルド成功、JARは約20MBだった。

## 3. 最初の手動起動と疎通確認（実施済み）

Ubuntuターミナルで:

```bash
cd ~/first-app
java -Djava.net.preferIPv4Stack=true -jar target/myfirstapp-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8081
```

**別のUbuntu SSHターミナル** から:

```bash
curl -I http://127.0.0.1:8081/
curl http://127.0.0.1:8081/api/monitor/health
```

実際の確認結果: トップページ `HTTP/1.1 200`、ヘルスAPIは `"status":"UP"`。手動起動したターミナルを閉じると、この方法のアプリは停止するので、恒久運用ではsystemdに切り替えた。

## 4. 踏み台ブラウザからSSHトンネルで画面表示（実施済み）

**Windows踏み台側のPowerShell** で実行する（Ubuntu内部ではなく踏み台側）。

```powershell
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:18081:127.0.0.1:8081 kazuha@<SERVER_IP>
```

パスワード認証後、PowerShellが待機状態になればトンネルとして正常。**そのPowerShellを開いたまま**、**踏み台上のブラウザ**で次を開く。

- ダッシュボード: `http://127.0.0.1:18081/`
- 計算機: `http://127.0.0.1:18081/calculator.html`

初回はSSH接続が `Connection aborted` になったが、通常の `ssh` 接続を確認したうえで、次の詳細ログ付きコマンドで再試行して成功した。

```powershell
ssh -vvv -N -o ExitOnForwardFailure=yes -L 127.0.0.1:18081:127.0.0.1:8081 kazuha@<SERVER_IP>
```

ログで `Authentication succeeded`、`Local forwarding listening on 127.0.0.1 port 18081` を確認し、ブラウザ表示にも成功した。

- `-N`: SSH先でシェルを開かずポート転送だけを行う。
- `-L`: 踏み台の `18081` を、Ubuntu内の `127.0.0.1:8081` に転送する。
- トンネルのPowerShellを閉じると **踏み台からの画面アクセスだけが切れる**。systemdのアプリは停止しない。
- `127.0.0.1` は「そのコマンド/ブラウザを実行しているPC自身」を指す。**手元PCのブラウザではなく踏み台内のブラウザで開く**。

## 5. systemdで常時稼働するサービスに変更（実施済み）

### 5-1. 手動起動中のJavaを確認

```bash
ps -ef | grep '[j]ava'
```

当時は `kazuha` ユーザーが `target/myfirstapp-0.0.1-SNAPSHOT.jar` を起動していた。出力例はコマンドではないので、プロセス行をシェルに貼り付けない。

### 5-2. アプリ専用ユーザーとJAR配置

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin myfirstapp
id myfirstapp
sudo mkdir -p /opt/myfirstapp
sudo cp ~/first-app/target/myfirstapp-0.0.1-SNAPSHOT.jar /opt/myfirstapp/app.jar
ls -lh /opt/myfirstapp/
```

実際の検証では `myfirstapp` ユーザーを作成し、約20MBの `/opt/myfirstapp/app.jar` を配置。専用ユーザーが既存の場合は `useradd` を繰り返さない。

### 5-3. サービス定義

```bash
sudo nano /etc/systemd/system/myfirstapp.service
```

ファイルの内容:

```ini
[Unit]
Description=SERVER CONTROL - Spring Boot Monitoring Dashboard
After=network.target

[Service]
Type=simple
User=myfirstapp
Group=myfirstapp
WorkingDirectory=/opt/myfirstapp
ExecStart=/usr/lib/jvm/java-26-openjdk-amd64/bin/java -Djava.net.preferIPv4Stack=true -jar /opt/myfirstapp/app.jar --server.address=127.0.0.1 --server.port=8081
Restart=on-failure
RestartSec=5
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

nanoの保存は **Ctrl+O → Enter → Ctrl+X**。一度保存できず `No such file or directory` が出たが、再保存して `cat` で正しい内容を確認した。

```bash
ls -l /etc/systemd/system/myfirstapp.service
sudo cat /etc/systemd/system/myfirstapp.service
sudo systemd-analyze verify /etc/systemd/system/myfirstapp.service
sudo systemctl daemon-reload
```

`verify` 時、別の `xfs_scrub` 関連ユニットの `CPUAccounting` 警告が出た。今回の `myfirstapp.service` 自体に対するエラーは出なかった。

### 5-4. 手動起動からsystemdへ切り替える

**8081番ポートを二重に使えないため、手動版を止めてからサービス版を起動した。**

```bash
ps -ef | grep '[j]ava'
# 実行中のアプリをコマンドラインまで確認して、該当するPIDだけを停止
kill <MANUAL_APP_PID>
sudo systemctl start myfirstapp
systemctl status myfirstapp --no-pager
```

実際の `systemctl status` は **`Active: active (running)`**。JARがsystemdにより起動されたことを確認した。

> **注意:** `kill` は対象のPIDを確認してから。`killall java` や、確認なしの `pkill` は使わない。SSHセッション自体を終了する `exit` とも違う。

## 6. 現在の運用（起動・停止・画面確認）

### 最初に一度だけ自動起動を有効化

ログ上、systemd起動直後の状態は `loaded; disabled` だった。**以下の完了ログは未取得なので、必ず確認すること。**

```bash
sudo systemctl enable myfirstapp
systemctl is-enabled myfirstapp
# enabled と表示されればOS起動時の自動起動が有効
```

### アプリ操作（Ubuntu SSHターミナル）

| 操作 | コマンド |
| --- | --- |
| 状態確認 | `systemctl status myfirstapp --no-pager` |
| アプリ起動 | `sudo systemctl start myfirstapp` |
| アプリ停止 | `sudo systemctl stop myfirstapp` |
| アプリ再起動 | `sudo systemctl restart myfirstapp` |
| 自動起動の確認 | `systemctl is-enabled myfirstapp` |
| 最新ログ | `sudo journalctl -u myfirstapp -n 50 --no-pager` |
| API応答確認 | `curl http://127.0.0.1:8081/api/monitor/health` |

### 操作終了・ウィンドウを閉じるとき

- **Tera Term / 通常SSHのPowerShell**: ほかの実行中作業がなければ `exit` して閉じてよい。
- **`ssh -N -L ...` のPowerShell**: ダッシュボードを見ている間は開いたまま。終了時は **Ctrl+C** でトンネルを停止して閉じてよい。
- systemdが `active (running)` なら、SSHクライアントを閉じてもアプリは動き続ける。
- **物理サーバー本体はシャットダウンされない**。踏み台からログアウトしても同じ。

### Ubuntu Server本体を停止・再起動するとき

```bash
# 物理Ubuntu Server側で実行。接続が切れるので注意。
sudo reboot           # OS再起動
sudo shutdown -h now  # OS停止
```

サーバー電源が落ちている場合は **iRMCの電源操作** で起動する。管理者の許可と共用環境の利用状況を先に確認する。iRMCのネットワーク設定は変更しない。

## 7. アプリを更新するとき（将来の手順・未実施）

公開済みサービスを更新する際は、次のように保守時間を決め、手動で変更する。更新中は画面が一時停止する。

```bash
cd ~/first-app
git status                 # ローカル変更がないか確認
git pull --ff-only         # main等、利用中ブランチの更新
mvn clean package          # テストを含むビルド
sudo systemctl stop myfirstapp
sudo cp target/myfirstapp-0.0.1-SNAPSHOT.jar /opt/myfirstapp/app.jar
sudo systemctl start myfirstapp
systemctl status myfirstapp --no-pager
curl http://127.0.0.1:8081/api/monitor/health
```

※ 安定運用するなら更新前のJARを別名でバックアップし、障害時に戻せるようにすること。JAR名は将来変わる可能性があるため、`ls target/` で確認する。

## 8. よくある詰まりどころ

| 症状 | 原因と対処 |
| --- | --- |
| `kazuha: command not found` | `ps` の出力例を入力してしまった。入力するのは `ps -ef | grep '[j]ava'`。 |
| `myfirstapp.service: No such file or directory` | nanoで保存未確定。**Ctrl+O → Enter → Ctrl+X** を確認。 |
| `Address already in use` | 8081番で手動版が動いていないか `ps` / `ss -lntp` で確認。 |
| `Connection aborted` | 通常のSSHで疎通確認し、`-vvv` 付きでトンネルを再試行。 |
| 踏み台のブラウザで表示されない | トンネルを維持しているか、ブラウザを**踏み台で**開いているか、`curl` が正常かを確認。 |
| `active (running)` なのにOS再起動後に上がらない | `systemctl is-enabled myfirstapp` を確認。`disabled` なら `enable`。 |
| 画面の履歴が再起動時に消える | このアプリの監視履歴はメモリ内。プロセス再起動でリセットされる仕様。 |

## 9. 安全上の注意

- 公開GitHubに **パスワード、秘密鍵、トークン、実IP/管理URLなどの接続情報** を残さない。
- ダッシュボードは認証未実装。`--server.address=127.0.0.1` を保持し、SSHトンネル越しに見る。
- BMC/iRMCとUbuntu OSは別のネットワーク。誤ってBMC設定を変えると管理アクセスを失う可能性がある。
- サーバー自体の電源操作は踏み台やSSHクライアントのウィンドウを閉じる操作とは違う。
- この手順は **2026-11-04 の物理サーバーでの成功記録**。WSL2の既存8080番サービスに適用しない。
