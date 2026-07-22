# 掲示板アプリケーション (bulletinboard)

## 0. プロジェクトの目的
- Spring Boot を使用した Web アプリケーション開発の学習
- Java によるサーバーサイド処理と、画面表示（フロントエンド）の連携の流れを理解する

## 1. 開発環境
- **言語**: Java 21
- **フレームワーク**: Spring Boot 3.x
- **ビルドツール**: Maven
- **開発ツール**: Visual Studio Code

## 2. 実装予定の機能
- [ ] 投稿一覧表示（TOP画面）
- [ ] 新規投稿の作成機能
- [ ] 投稿の削除機能
- [ ] ログイン機能（今後のステップアップとして実装予定）

※学習の進捗や理解度に応じて、徐々に高度な機能を追加していく予定です。

## 開発中に発生したエラーと解決記録（2026/07/21）


Spring BootとMySQLの連携にあたり、以下の3つのエラーに直面し、それぞれ解決しました。

### 1. MySQLドライバーの未検出エラー
* **事象**: `Cannot load driver class: com.mysql.cj.jdbc.Driver`
* **原因**: 初期設定の `pom.xml` にMySQLと接続するためのライブラリ（JDBCドライバー）が用意されていなかったため、JavaとMySQLが通信できませんでした。
* **解決策**: `pom.xml` に `mysql-connector-j` の依存関係を追加し、システム間の「通訳」を配置しました。

### 2. 旧バージョン用Hibernate方言（Dialect）の廃止エラー
* **事象**: `Unable to resolve name [org.hibernate.dialect.MySQL8Dialect]`
* **原因**: `application.properties` に記述されていた古い形式の方言設定（MySQL8Dialect）が、使用している新しいバージョンのHibernateで完全に廃止・削除されていたためです。
* **解決策**: 設定を最新の共通規格である `MySQLDialect` に変更（または削除による自動判別化）し、新しい言語変換に対応させました。

### 3. MySQLセキュリティによる公開鍵の回収拒否エラー
* **事象**: `Unable to open JDBC Connection for DDL execution [Public Key Retrieval is not allowed]`
* **原因**: MySQL 8.0以降の安全対策により、Java側からの暗号化通信用「公開鍵」の自動要求がデフォルトでブロックされたため、ログインを門前払いされました。
* **解決策**: 接続URLの末尾に `allowPublicKeyRetrieval=true` を明示的に追記し、安全なアクセス鍵の回収を許可しました。

## 開発中に発生したエラーと解決記録（2026/07/22）
* **事象**:新規投稿ページで投稿内容を入力し、投稿ボタンをクリックしするとページエラー
* **原因**PostController.javaの新規投稿保存するメソッドにHTTPリクエストを受け取るためのアノテーション（@PostMapping）が抜けていため、新規投稿ボタンを押しても保存処理が走らなかった。
* **解決策**: 新規保存メソッドの上に@PostMappingを追記。
