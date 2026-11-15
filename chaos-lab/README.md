# CHAOS LAB — 検証環境 Part 2

物理Ubuntu Serverで、**隔離した実験用Dockerコンテナ**にだけ意図的な障害を起こし、検知・復旧を学ぶプロジェクト。**既存のSpring Boot / SERVER CONTROL (`myfirstapp`, 8081) は変更しない。**

## 現在の完成範囲

- Python標準ライブラリ製のHTTPテストアプリ（`/health`, `/metrics`, `/work`）。
- 正常応答・遅延（最大3秒）・503エラーの再現。
- 最大200リクエスト、並列最大10のローカル負荷試験ツール。
- CPU 1コア相当・メモリ512MB・プロセス数64・権限制限のDocker Compose構成。
- コンテナ内でプロセスを異常終了させて自動再起動を観察する実験。
- コンテナ内で最大30秒のCPU負荷をかける補助プログラム。
- Python標準ライブラリによるテスト。

**重要:** GitHub上のコード・設定は作成済み。**物理サーバーへのDocker導入・デプロイ・実機テストは未実施**。この手順を実機で実行し、結果を確認する必要がある。

## 0. 安全条件

1. サーバーの所有者/管理者から負荷試験とDocker導入の許可を得る。共用環境なら利用者のいない時間に実施。
2. **踏み台・iRMC・Ubuntuのネットワーク設定は変更しない**。既存の `myfirstapp.service` も触らない。
3. このラボは**ホストの127.0.0.1:18082だけ**に公開。実IPへのポート公開や外部向け負荷試験はしない。
4. Dockerはカーネルをホストと共有する。制限を設けても影響が完全にゼロとは言えない。
5. コンテナの `restart` は試験用。**OS全体の再起動・OOM・ディスク破壊は実験しない**。
6. Dockerグループ所属は事実上root相当の権限になり得るため、必要がなければ追加しない。以下は `sudo docker compose` を使用する。

## 1. Ubuntuへログインして準備

Windows踏み台のPowerShellまたはTera Termで:

```powershell
ssh kazuha@<SERVER_IP>
```

`<SERVER_IP>` は実際の割当先に置き換える。内部IP・パスワードを公開GitHubに書かない。

Ubuntuで確認:

```bash
systemctl status myfirstapp --no-pager
docker --version
sudo docker compose version
free -h
df -h /
```

Dockerがない場合は管理者と導入方針を確認して、[Docker公式Ubuntu導入手順](https://docs.docker.com/engine/install/ubuntu/)に従う。既存のネットワークやファイアウォールを勝手に変更しないこと。

## 2. コードを取得して起動

UbuntuのSSHターミナル:

```bash
cd ~/first-app
git status
git fetch origin
git switch feature/chaos-lab
git pull --ff-only
cd chaos-lab
sudo docker compose config
sudo docker compose up -d --build
sudo docker compose ps
```

既存の作業ツリーに未コミットの変更があれば、`git switch` の前に確認する。既存サービスのJARは `/opt/myfirstapp/app.jar` なので、ブランチ切り替えだけではサービスは置き換わらない。

ブラウザは使わず、まず**Ubuntu内**から確認:

```bash
curl http://127.0.0.1:18082/health
curl http://127.0.0.1:18082/metrics
```

期待値: `"status":"UP"`。コンテナの `healthy` 表示はヘルスチェック完了まで少し時間がかかる。

## 3. 実験A: 正常応答・503エラー・遅延

UbuntuのSSHターミナル、`~/first-app/chaos-lab` で:

```bash
python3 tools/load_test.py --mode ok --requests 30 --concurrency 3
python3 tools/load_test.py --mode error --requests 30 --concurrency 3
python3 tools/load_test.py --mode slow --delay-ms 500 --requests 30 --concurrency 3
curl http://127.0.0.1:18082/metrics
```

結果はJSONで `meanMs`（平均応答時間）、`p95Ms`（95パーセンタイル）、`statusCodes`（HTTPステータス別件数）を表示。

- `ok`: HTTP 200
- `error`: HTTP 503（**意図的な障害**）
- `slow`: 最大3秒の遅延を再現
- `/metrics`: 累積リクエスト数、503件数、遅延リクエスト数。**コンテナ再起動でリセット**される。

## 4. 実験B: プロセスクラッシュと自動復旧

まず現在の状態を確認:

```bash
sudo docker compose ps
curl http://127.0.0.1:18082/health
```

**コンテナ内のPID 1だけ**にSIGKILLを送る（ホストOSのPID 1を殺す操作ではない）。

```bash
sudo docker compose exec -T lab sh -c 'kill -9 1'
```

`exec` はコンテナ内で実行する。`kill` によってSSH接続が切れるわけではない。実行コマンドがエラー表示になっても、コンテナのメインプロセスが停止したためである場合がある。

```bash
sleep 5
sudo docker compose ps
sudo docker compose logs --tail=40 lab
curl http://127.0.0.1:18082/health
```

`restart: on-failure:3` により、異常終了を検知したDockerが再起動を試みる。**Dockerデーモンが稼働し、ポリシーが有効であることが前提**。実際に復旧したかは `ps` と `curl` で確認する。自動復旧しない場合はログを確認し、`sudo docker compose up -d lab` で手動復旧。

## 5. 実験C: CPU負荷（1コア・30秒以下）

コンテナ側にCPU 1コア相当の制限を付けてある。短時間だけ計算を続ける:

```bash
sudo docker compose exec -T lab python /app/cpu_burn.py --seconds 10
sudo docker stats --no-stream
```

- `--seconds 10`: 10秒で終了
- `docker stats`: コンテナのCPU・メモリ使用量を表示
- コンテナ制限はホストへの影響を抑えるためのもの。**温度・ファン・負荷状況を確認しながら実施**。

## 6. ログ・停止・削除

```bash
sudo docker compose ps
sudo docker compose logs -f --tail=50 lab
# ログ表示を終える: Ctrl+C
sudo docker compose stop
# 再開:
sudo docker compose up -d
# コンテナとComposeネットワークを削除（イメージは残る）:
sudo docker compose down
```

- `stop`: ラボだけを停止
- `up -d`: バックグラウンドで起動
- `down`: ラボのコンテナ等を削除
- **既存のSpring Bootサービスには影響しない**（Docker導入自体の影響は別途評価）。

## 7. Python単体のテスト（Docker不要）

```bash
cd ~/first-app/chaos-lab
python3 -m unittest discover -s tests -v
```

## 8. よくあるトラブル

| 症状 | 対処 |
| --- | --- |
| `docker: command not found` | Docker未導入。管理者と導入方法を確認 |
| `docker compose` が使えない | Composeプラグインの導入状況を確認 |
| `permission denied` | `sudo docker compose` で実行。安易にDockerグループへ追加しない |
| ポート `18082` が使用中 | `sudo ss -lntp | grep 18082` で確認し、既存プロセスを勝手に停止しない |
| コンテナが `unhealthy` | `sudo docker compose logs --tail=100 lab` を確認 |
| クラッシュ後に復旧しない | `sudo docker compose ps -a` で状態確認、`sudo docker compose up -d lab` で手動復旧 |
| `git switch` で失敗 | ローカルの未コミット変更を確認してから対処 |

## 9. 学習目標

- HTTP 200/503、タイムアウト、p95の意味を説明できる
- コンテナのプロセスとホストのプロセスの違いを理解する
- Dockerの再起動ポリシーとヘルスチェックの違いを説明できる
- 障害前後の `/health`, `/metrics`, `docker compose logs` を比較できる
- 「異常を起こす」だけでなく、**復旧を確認して終える**

将来はSpring Boot製の専用実験アプリ、障害履歴保存、ダッシュボード可視化へ拡張可能。
