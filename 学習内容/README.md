# 学習内容：物理Ubuntu Server構築とSpring Boot公開

**学習日: 2026-11-04**  
対象: Windows踏み台 → SSH → 物理Ubuntu Server → Java 26 / Maven → Spring Boot → SSHトンネル → systemd

このフォルダは、今回の作業で**実際に入力したコマンドと、その意味**を初心者向けに復習する教材です。ターミナル出力や操作ログで確認できたものを「実施済み」、説明中に案内したが完了ログを見ていないものを「確認待ち」、追加の便利コマンドを「参考」として分けています。

> このGitHubリポジトリは公開されています。**パスワード・内部IP・管理URL・秘密鍵を記載しない**方針です。接続先は `<SERVER_IP>` に置き換えています。
> 自宅PCのWSL2と、今回の**物理サーバー**は別環境。サービス名が同じでも混同しないこと。

## 学習する順番

| 教材 | 内容 |
| --- | --- |
| [01_接続とLinux基礎.md](01_接続とLinux基礎.md) | PowerShell / Tera Term / SSH、Linuxの基本操作、結果の読み方 |
| [02_GitとJavaとMaven.md](02_GitとJavaとMaven.md) | GitHubからコード取得、Java 26確認、ビルド、実行、curl |
| [03_SSHトンネル.md](03_SSHトンネル.md) | ポート・localhost・踏み台のブラウザからのアクセス |
| [04_systemdと運用.md](04_systemdと運用.md) | 専用ユーザー、JAR配置、nano、サービス化、停止と自動起動 |
| [05_復習問題.md](05_復習問題.md) | コマンド暗記ではなく意味を理解する問題と解答 |

**操作の時系列を確認したいとき:** [docs/physical-server/README.md](../docs/physical-server/README.md) に実作業の手順書があります。

## 完成した構成

```text
Windows踏み台 (PowerShell / Tera Term)
  │ 通常のSSHでUbuntuにログイン
  ├── ssh kazuha@<SERVER_IP>
  │
  │ SSHローカルポート転送
  └── Windows 127.0.0.1:18081
           └────── SSH ──────> Ubuntu 127.0.0.1:8081
                                      │
                                  Spring Boot
                                      │
                        systemd: myfirstapp.service
                        JAR: /opt/myfirstapp/app.jar
```

実施できたこと:
- UbuntuのSSHログイン、`/home/kazuha/first-app` でのビルド成功
- Spring Bootを手動で起動、`curl` でHTTP 200と `"status":"UP"` を確認
- SSHトンネルを経由し、踏み台のブラウザに画面表示
- `myfirstapp` 専用ユーザーと20MB程度のJARを配置
- systemdサービスを作り、`Active: active (running)` を確認

**確認待ち:** `sudo systemctl enable myfirstapp` 実行後に `systemctl is-enabled myfirstapp` が `enabled` と表示されたか、OSの再起動後も自動復旧するか。

## コマンドを読むコツ

```bash
sudo systemctl status myfirstapp --no-pager
```

- `sudo` = 管理者権限が必要な操作を行うための仕組み
- `systemctl` = systemdのサービスを操作・照会する命令
- `status` = 「状態を表示する」という指示
- `myfirstapp` = 対象のサービス名
- `--no-pager` = 画面をページ送りせず、一度に結果を表示するオプション

**注意:** `kazuha@kazuha-sv:~$` や `kazuha 28872 ... java ...` は**画面表示**であり、入力するコマンドではありません。コマンドブロックのうち `#` で始まる行は通常、説明コメントです。
