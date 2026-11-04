# 01. 踏み台・SSH接続・Linux基本コマンド

## 1. Windows踏み台とUbuntuの違い

今回の作業では、Windows踏み台の **PowerShell / Tera Term** から、物理Ubuntu ServerにSSH接続した。

- **PowerShell**: Windowsでコマンドを入力するターミナル。
- **Tera Term**: SSH接続に利用できるWindowsアプリ。
- **Ubuntu Server**: 実際にSpring Bootを動かすLinux OS。
- **踏み台サーバー**: 制限されたサーバーネットワークに入るための中継地点。
- **BMC / iRMC**: OSとは独立してサーバー電源・コンソールなどを管理する機能。OS側IP設定と混同しない。

### [実施済み] SSH接続（踏み台のPowerShell）

```powershell
ssh kazuha@<SERVER_IP>
```

- `ssh`: 暗号化通信でリモートコンピューターを操作するコマンド。
- `kazuha`: Ubuntuのログインユーザー。
- `@`: ユーザーと接続先を区切る記号。
- `<SERVER_IP>`: 管理者から割り当てられたUbuntu側のIPに置き換える。**山括弧も入力しない**。
- 初回のホスト鍵確認は、接続先が本物か指紋を照合してから承認する。
- パスワード入力中、画面に文字や `*` が出ないのは通常の動作。

Tera Termは接続先アドレス、SSH、ユーザー名、認証情報を画面で指定できる。複数のSSHウィンドウは同じサーバーに接続していても別セッション。

## 2. プロンプトを読む

```text
kazuha@kazuha-sv:~$
```

- 最初の `kazuha`: 自分のユーザー名
- `kazuha-sv`: サーバー名（ホスト名）
- `~`: 自分のホームディレクトリ
- `$`: 一般ユーザーのプロンプト

**入力するのは `$` の右側だけ**。例示された出力をそのまま打ち込むと、`kazuha: command not found` のようなエラーになる。

## 3. パスと現在地

### [実施済み] `pwd`

```bash
pwd
```

`print working directory` の略。**今いるフォルダの絶対パス**を表示する。今回は `/home/kazuha` と出た。

### [実施済み] `cd`

```bash
cd ~
cd ~/first-app
```

`change directory` の略。作業するディレクトリを移動する。

- `~` は自分のホームディレクトリ（今回は `/home/kazuha`）。
- `~/first-app` は `/home/kazuha/first-app` の意味。
- `cd ..` は一つ上のディレクトリに移動する（参考）。

### [実施済み] `ls` / `ls -lh`

```bash
ls
ls -lh /opt/myfirstapp/
ls -l /etc/systemd/system/myfirstapp.service
```

- `ls`: ファイルやディレクトリを一覧表示
- `-l`: 権限・所有者・サイズ・更新日時などの詳細を表示
- `-h`: サイズを `20M` のような読みやすい単位で表示

```text
-rw-r--r-- 1 root root 20M ... app.jar
```

この出力の意味:
- `-`: 通常ファイル
- `rw-`: 所有者は読み・書き可能
- `r--`: グループとその他のユーザーは読み込み可能
- `root root`: 所有ユーザーと所有グループ
- `20M`: ファイルサイズは約20MB
- `app.jar`: ファイル名

## 4. 管理者権限とユーザー

### [実施済み] `sudo`

```bash
sudo mkdir -p /opt/myfirstapp
```

`sudo` は、許可されたユーザーが管理者権限でコマンドを実行するための仕組み。**削除や設定変更などを伴うため、内容を確認してから実行**。

`[sudo: authenticate] Password:` と出るのは、権限確認のため。

### [実施済み] `id`

```bash
id myfirstapp
```

ユーザーのUID（ユーザーID）とGID（グループID）を確認する。ログでは `uid=999(myfirstapp) gid=983(myfirstapp)` と表示。数字は環境によって変わる。

## 5. プロセス

### [実施済み] `ps -ef | grep '[j]ava'`

```bash
ps -ef | grep '[j]ava'
```

- `ps`: 実行中のプロセス（プログラム）を表示
- `-e`: すべてのプロセス
- `-f`: 詳細形式で表示
- `|`（パイプ）: 左側の出力を右側の入力として渡す
- `grep`: 指定した文字列に一致する行を探す
- `'[j]ava'`: `java` と一致するが、grep自身のコマンド行には同じ文字列が直接現れず、自己一致しにくい書き方

出力例:

```text
kazuha 28872 ... java -Djava.net.preferIPv4Stack=true -jar target/myfirstapp-0.0.1-SNAPSHOT.jar ...
```

- `28872` は**当時の**PID（プロセスID）。起動し直せば変わる。
- Javaが動いていることと、誰が実行しているかを確認できる。

### [実施済み] `kill`

```bash
kill <PID>
```

通常は対象に `SIGTERM`（終了を依頼する信号）を送る。`kill` は名前の印象と違い、即座の強制終了を意味するわけではない。今回、手動起動のJavaが当時のPID `28872` だと確認して終了した。

**現在のPIDを確認せず過去の数字を再利用しない**。同じPIDを別のプロセスが使っている可能性もある。`kill -9` は強制終了であり、通常は使わない。

## 6. 画面とターミナル操作

- `Ctrl+C`: 前面で動いているコマンドを中断。SSHトンネルや手動起動中のJavaの終了に使うことがある。
- `Ctrl+C` を通常のプロンプト上で押しても、**別のターミナルで動くJavaは止まらない**。
- `exit`: SSHのシェルからログアウトする。systemdサービスは通常止まらない。
- Tera Termは作業中のコマンドがなければ `exit` で閉じられる。
- `systemctl` で動くJavaと、`java -jar` で手動起動したJavaは管理方法が違う。

## 7. その他の確認コマンド（参考）

```bash
hostname
ip -br addr
ss -lntp
```

- `hostname`: ホスト名を確認
- `ip -br addr`: ネットワークIFとIPアドレスを簡潔に表示
- `ss -lntp`: 待受TCPポートや関連プロセスを確認（権限により詳細が見えない場合がある）

今回の会話では、これら3つの**実行ログは確認していない**。実IPを公開GitHubに貼らないこと。
