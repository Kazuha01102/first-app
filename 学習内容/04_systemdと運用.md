# 04. systemd・専用ユーザー・サーバー運用

## 1. systemdとは

Linux起動時に様々なサービスを立ち上げ、停止や障害時の再起動を管理する仕組み。Spring Bootを単に `java -jar` で起動するより、**SSH端末を閉じても続けて動かす**用途に向く。

サービスの定義ファイルを **unitファイル** という。今回は `/etc/systemd/system/myfirstapp.service`。

## 2. [実施済み] 専用ユーザーを作る

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin myfirstapp
id myfirstapp
```

- `sudo`: 管理者権限で実行
- `useradd`: ユーザーを作成
- `--system`: サービス実行用のシステムユーザーを作る
- `--no-create-home`: 通常のホームディレクトリを自動作成しない
- `--shell /usr/sbin/nologin`: 通常の対話ログインに使えないシェルを設定
- `myfirstapp`: サービス専用のユーザー名
- `id myfirstapp`: 作成結果を確認

確認ログ:

```text
uid=999(myfirstapp) gid=983(myfirstapp) groups=983(myfirstapp)
```

ユーザーを分ける理由は、Webアプリを一般ユーザー `kazuha` の権限で動かさず、被害範囲を小さくするため。ただしこれだけで十分なセキュリティが保証されるわけではない。

## 3. [実施済み] JARを運用ディレクトリへ配置

```bash
sudo mkdir -p /opt/myfirstapp
sudo cp ~/first-app/target/myfirstapp-0.0.1-SNAPSHOT.jar /opt/myfirstapp/app.jar
ls -lh /opt/myfirstapp/
```

- `mkdir`: ディレクトリを作成（make directory）
- `-p`: 親ディレクトリも必要なら作り、既存ディレクトリでエラーにしない
- `/opt`: 追加アプリを置くときによく用いられる場所
- `cp`: ファイルをコピー（copy）
- `~/first-app/target/...`: Mavenビルドで生成した**元JAR**
- `/opt/myfirstapp/app.jar`: systemdが実行する**コピー先JAR**
- `ls -lh`: 配置されたファイルをサイズ付きで確認

ログでは `app.jar` は約20MB、所有者 `root`。ファイルは全ユーザーが読み込めるモードだったので、`myfirstapp` ユーザーからも実行時に読み込める。

## 4. [実施済み] nanoでサービス定義を編集

```bash
sudo nano /etc/systemd/system/myfirstapp.service
```

- `nano`: ターミナルで使えるテキストエディタ
- `/etc/systemd/system/`: 管理者が用意するsystemdのユニットファイルを置く場所
- `.service`: サービスの定義ファイル

### 保存方法

1. `Ctrl+O` = 書き込み（Write Out）
2. `Enter` = 表示されたファイル名を確定して保存
3. `Ctrl+X` = エディタを終了

**実際に起きた失敗:** 初回は保存されておらず、`cat: ... No such file or directory` と表示。再度nanoで保存し直して解決した。

### 設定ファイル全文

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

| 設定 | 意味 |
| --- | --- |
| `[Unit]` | サービスの基本情報と依存関係 |
| `Description` | 人間が読むサービス名・説明文 |
| `After=network.target` | 起動順序をnetwork.targetの後にする（ネットワーク接続が必ず確立したという意味ではない） |
| `[Service]` | プログラムの起動条件 |
| `Type=simple` | 起動したプロセスをメインとして管理 |
| `User` / `Group` | 専用ユーザー・グループで起動 |
| `WorkingDirectory` | アプリ実行時の現在のディレクトリ |
| `ExecStart` | サービスの実際の起動コマンド。Javaの絶対パスを指定 |
| `Restart=on-failure` | 異常終了時に自動再起動 |
| `RestartSec=5` | 再起動まで5秒待つ |
| `NoNewPrivileges=true` | 子プロセス等が追加の権限を得ることを制限 |
| `[Install]` | enableしたときの有効化方法 |
| `WantedBy=multi-user.target` | 通常のサーバー起動時にサービスを起動する設定に利用 |

**重要:** `WantedBy` を書いただけでは自動起動は有効化されない。別途 `systemctl enable` が必要。

## 5. [実施済み] ファイル・設定を確認する

```bash
ls -l /etc/systemd/system/myfirstapp.service
sudo cat /etc/systemd/system/myfirstapp.service
sudo systemd-analyze verify /etc/systemd/system/myfirstapp.service
sudo systemctl daemon-reload
```

- `cat`: ファイルの内容を標準出力に表示
- `systemd-analyze verify`: ユニット定義の文法や依存関係などを検証
- `systemctl daemon-reload`: 変更したunitファイルをsystemdに読み直させる

今回 `verify` で出た警告:

```text
/usr/lib/systemd/system/xfs_scrub_all.service:26: Support for option CPUAccounting= has been removed and it is ignored
/usr/lib/systemd/system/system-xfs_scrub.slice:15: Support for option CPUAccounting= has been removed and it is ignored
```

これは**XFS関連の別ユニットに対する警告**。今回の `myfirstapp.service` に対するエラーは表示されなかった。

## 6. [実施済み] 手動プロセスからサービスへ切り替え

```bash
ps -ef | grep '[j]ava'
kill <確認した手動版のPID>
sudo systemctl start myfirstapp
systemctl status myfirstapp --no-pager
```

**重要な順番:** 手動版が `127.0.0.1:8081` を使用中なら、先にそのプロセスを停止する。同じアドレスとポートで2つのアプリは通常同時には待ち受けられない。

今回の実ログ:

```text
Loaded: loaded (/etc/systemd/system/myfirstapp.service; disabled; preset: enabled)
Active: active (running)
Main PID: 29784 (java)
Memory: 238.2M (peak: 238.2M)
```

- `Loaded: loaded`: systemdがユニットを認識
- `disabled`: **OS起動時の自動起動はまだ無効**（この時点）
- `preset: enabled`: 配布元の既定方針。実際にenable済みという意味ではない
- `Active: active (running)`: **現在起動中**
- `Main PID`: systemdが管理するJavaのプロセスID
- `Memory`: メモリの使用量。時々刻々変わる

## 7. [確認待ち] 再起動時の自動起動を有効にする

```bash
sudo systemctl enable myfirstapp
systemctl is-enabled myfirstapp
```

- `enable`: OS起動時に起動するようサービスを登録（**今すぐ起動する命令ではない**）
- `is-enabled`: 自動起動が有効か確認。期待する結果は `enabled`

今回 `enable` の実行成功ログはまだ共有されていない。**登録済みと断定しない**。確認後、安全な日時にOS再起動テストを行う。

## 8. 運用コマンド（案内済み）

**UbuntuにSSHログインしたターミナル**で操作する:

| コマンド | 意味 |
| --- | --- |
| `systemctl status myfirstapp --no-pager` | サービスの起動状態を確認 |
| `sudo systemctl start myfirstapp` | サービスを起動 |
| `sudo systemctl stop myfirstapp` | サービスを停止 |
| `sudo systemctl restart myfirstapp` | サービスを停止・再起動 |
| `sudo systemctl enable myfirstapp` | OS起動時の自動起動を設定 |
| `sudo systemctl disable myfirstapp` | 自動起動を無効化（動作中サービスはこれだけでは停止しない） |
| `systemctl is-enabled myfirstapp` | 自動起動設定の状態を表示 |
| `sudo journalctl -u myfirstapp -n 30 --no-pager` | サービスの最近30行のログを表示 |
| `sudo systemctl daemon-reload` | 設定ファイルの変更を再読み込み |

### サーバーOS自体の操作（参考・今回の実施ログなし）

```bash
sudo reboot
sudo shutdown -h now
```

- `reboot`: **Ubuntu Server本体を再起動**。SSHもいったん切れる。
- `shutdown -h now`: **OSを停止**。再度使うには管理手順に従い電源を入れる。
- これはアプリだけを `stop` する操作とは異なる。
- 共用の検証環境では電源操作の前に管理者や利用者に確認する。

## 9. ウィンドウを閉じるとき

| ウィンドウ | 終了方法 | 結果 |
| --- | --- | --- |
| Tera Termの通常SSH接続 | `exit` | UbuntuへのSSH接続のみ終了 |
| 2つ目の通常SSH接続 | `exit` | そのSSH接続のみ終了 |
| SSHトンネル用PowerShell | `Ctrl+C` | 踏み台でのポート転送が終了 |
| Ubuntuのsystemdサービス | `systemctl` で管理 | SSHを切っても通常は継続 |

**systemdサービスを停止せず、ターミナルだけ閉じる**のが普段の作業終了方法。

## 10. 更新時の流れ（参考、未実施）

```bash
cd ~/first-app
git status
git pull --ff-only
mvn clean package
sudo systemctl stop myfirstapp
sudo cp target/myfirstapp-0.0.1-SNAPSHOT.jar /opt/myfirstapp/app.jar
sudo systemctl start myfirstapp
systemctl status myfirstapp --no-pager
curl http://127.0.0.1:8081/api/monitor/health
```

更新前にJARをバックアップし、利用者への停止連絡が必要なら実施する。ビルド失敗時は**現行サービスを止めない**。新しいJAR名に変わったらパスを確認して修正する。
