# 03. SSHポートフォワードの仕組みと操作

## 1. なぜトンネルが必要？

UbuntuのSpring Bootは **`127.0.0.1:8081`** で起動した。これはUbuntu Server**自身**からしか直接アクセスできない。

そこでWindows踏み台のSSHクライアントが通信を中継する。

```text
Windows踏み台のEdge
 http://127.0.0.1:18081/
              │
              ▼
Windows踏み台のSSHクライアント
              │ 暗号化されたSSH接続
              ▼
Ubuntu Server 127.0.0.1:8081
              │
              ▼
Spring Boot / SERVER CONTROL
```

`localhost` は**各PCごとに別物**。踏み台のブラウザから `127.0.0.1:18081` にアクセスする場合、通常は手元のPCのブラウザで同じURLを開いても表示できない。

## 2. [実施済み] トンネル作成

**踏み台 Windows PowerShell** で:

```powershell
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:18081:127.0.0.1:8081 kazuha@<SERVER_IP>
```

| 部分 | 意味 |
| --- | --- |
| `ssh` | SSHでサーバーに接続 |
| `-N` | リモート側のシェルやコマンドを起動せず、転送だけを行う |
| `-o ExitOnForwardFailure=yes` | 転送の設定に失敗した場合は接続を終了 |
| `-L` | ローカルポートフォワードを有効化 |
| 1つ目の `127.0.0.1` | **Windows踏み台側**の待受アドレス |
| `18081` | **Windows踏み台側**の待受ポート |
| 2つ目の `127.0.0.1` | SSHの接続先（Ubuntu）から見たローカルアドレス |
| `8081` | Ubuntuで動くSpring Bootの待受ポート |
| `kazuha@<SERVER_IP>` | UbuntuへのSSH接続先 |

### 認証が終わったら

PowerShellに何も出ず、プロンプトへ戻らずに止まっているように見えても正常なことがある。ポート転送のため接続を保持している状態。

この**PowerShellを開いたまま**、踏み台のEdgeでアクセス:

```text
http://127.0.0.1:18081/
```

**URLはコマンドではない。** ブラウザのアドレス欄に入力する。

## 3. [実施済み] 接続トラブルの調査

初回のトンネル接続では、ホスト鍵確認後に `Connection aborted` が出た。

通常のSSH接続が可能か確かめた後、詳細ログの `-vvv` 付きで再試行:

```powershell
ssh -vvv -N -o ExitOnForwardFailure=yes -L 127.0.0.1:18081:127.0.0.1:8081 kazuha@<SERVER_IP>
```

`-vvv`: デバッグ用の詳細な情報を多く表示するオプション。今回はログから以下を確認した:

```text
Authentication succeeded (password)
Local forwarding listening on 127.0.0.1 port 18081
Entering interactive session
```

その後、**踏み台ブラウザで実際の監視画面を表示できた**。

ただし初回の切断原因そのものは断定していない。調査時は接続先、ホスト鍵、認証結果、待受ポートの競合などを順番に確認する。

## 4. よくある勘違い

- **「ブラウザにアクセスできない」** → トンネルのPowerShellが開いたままか、ブラウザを**踏み台のWindows上**で開いているかを確認。
- **「Port already in use」** → 踏み台のポート `18081` をほかのプロセスが使用中かもしれない。先に既存の転送を確認。
- **「Ubuntuが動いているのに画面が消えた」** → トンネルが終了しただけの可能性。`systemctl status myfirstapp` と `curl` で切り分ける。
- **「SSH接続を切るとJavaは止まる？」** → 手動起動のJavaは停止する場合があるが、**systemdで起動したサービスは通常継続**する。

## 5. 終了方法

トンネルのPowerShellで `Ctrl+C` を押して終了。その後PowerShellを閉じてよい。**これはSSH転送を終了するだけ**で、UbuntuのOS電源やsystemdサービスは停止しない。

今後インターネットからのアクセスを提供したい場合は、先に認証・TLS・アクセス制限などを設計すること。**8081をそのまま全ネットワークへ開放しない。**
