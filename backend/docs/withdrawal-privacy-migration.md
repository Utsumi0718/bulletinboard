# 退会情報の処理・既存退会者の移行準備

この文書はNo.32改修②のデプロイ・運用確認用です。正式なSTEP手順書・ロードマップの代替ではありません。
以下のSQLや移行処理を実データへ実行した記録ではありません。本番の変更は別途承認が必要です。

## 変更とデプロイ順序

- Cookie／Session認証、メール＋パスワードのログイン入力、CSRF保護を維持します。
- 認証後のPrincipal名はUser IDの文字列です。旧PrincipalのSessionは拒否して再ログインを要求します。
- `authentication_version`で再設定・退会前の認証を拒否します。SessionRegistryでの失効だけに依存しません。
- V13は列・管理テーブルを追加するだけで、既存退会者のメール・名前等を更新しません。既存Migrationは変更しません。
- 旧コードと新コードの混在中にメールの解放・再登録を始めないでください。全ノードを切り替えるメンテナンス手順が必要です。
- 事前照会・衝突確認→V13の検証／適用→全ノードの新コードへの更新→旧Session拒否の確認→新規退会の確認、の順で計画します。
- ロールバックで旧メール認証のコードへ戻すと、再利用済みメールと古いSessionの取り違えを再導入する可能性があります。データの復元や旧版への切り戻しを自動実行しません。

## 確定した通常処理

- User IDとProfile ID、投稿・回答・いいね・ランキング・実績・通報の参照は維持します。
- emailは`withdrawn-{ID}@withdrawn.invalid`、usernameは`withdrawn_user_{ID}`へ変更します。
- 元ハッシュを破棄し、保存しないランダム秘密値由来のBCryptハッシュへ置換します。ログ・履歴に元値や秘密値を残しません。
- 再設定トークンを削除、失敗回数を0、ロックを解除状態にします。WITHDRAWNによる認証拒否は維持します。
- Profileのbio・iconをクリアします。Profile画像原本は別処理で扱い、通報証拠には連動削除しません。
- 一般向け表示名の既存共通変換を維持します。通常管理APIでも退会者の元の名前・メール・内部代替値を返しません。

## 専用権限と保持例外

- `ROLE_ADMIN`に加え、DBの`users.privacy_reviewer=true`が必要です。既定はfalseです。
- 権限の自動付与・一般管理APIからの付与は実装していません。付与・取消・担当者は別承認の運用で管理してください。
- 通常通報詳細の`evidence`はnullです。証拠文章は専用APIへ移行します。既存の証拠画像URLも専用権限が必要です。
- 保持記録の対象は`USER_IDENTITY`・`PROFILE_IMAGE`・`REPORT_EVIDENCE`です。
- 有効な例外の作成には、目的・根拠・将来の期限が必要です。根拠の法的妥当性をコードが認定するものではありません。
- USER_IDENTITYはEMAIL／USERNAMEから必要項目だけを指定します。元パスワードやトークンは保存しません。
- 未確定の証拠・画像はPOLICY_PENDINGとして、目的・根拠・期限を推測で補完しません。期限未確定データは自動削除しません。
- 新規通報には未確定の保持記録を作成します。既存証拠の保持記録は自動バックフィルしません。未登録の証拠は専用取得で拒否し、移行の承認が必要です。
- 専用権限の閲覧・変更は監査記録を残します。通常ログ・監査行には保持した元の値をコピーしません。
- 期限終了／解除後の本人情報ペイロードには専用削除APIがあります。証拠・監査・メール・バックアップの保存期間と自動削除は別途確定が必要です。

### 暗号鍵

- `PRIVACY_ENCRYPTION_KEY`：Base64で表した32バイトの鍵。DB・Git・コマンドログへ保存しないでください。
- `PRIVACY_ENCRYPTION_KEY_ID`：鍵の識別子。既定primary。暗号はAES-256-GCMで、対象ID等をAADに使用します。
- 本番の鍵をこの改修で発行・設定していません。テストの鍵は架空の専用値です。
- 鍵未設定・不正な設定では本人情報の保持を拒否します。有効な本人情報保持例外がある退会も、保存に失敗したら全体をロールバックします。
- 復号には記録のkey_idと対応する鍵が必要です。複数世代の同時復号・自動鍵ローテーションは今回の対象外です。鍵の変更は既存暗号文の移行・旧鍵の保管を別計画にしてください。

## 専用API

すべて`/api/admin/privacy`配下で、専用権限・ACTIVEをDBから確認します。変更系はCSRF必須、応答はno-storeです。
新しい保持記録の期限入力はオフセット付きISO日時、保存・出力はUTCのLocalDateTimeです。

| メソッド・パス | 用途 |
|---|---|
| POST /retentions | 目的・根拠・期限と必要項目を指定して例外を作成 |
| GET /retentions/{id} | 暗号文・元情報を含めない管理メタデータ |
| PUT /retentions/{id} | 同じ対象の保持方針を設定・更新 |
| GET /retentions/{id}/identity | 有効期間内の保持本人情報だけを復号して閲覧 |
| POST /retentions/{id}/release | 保持解除。未確定の方針は先に設定する |
| DELETE /retentions/{id}/identity | 期限終了・解除後の暗号化ペイロードを削除 |
| GET /reports/{id}/evidence | 通報証拠の文章を監査付きで取得 |
| POST /withdrawal-tasks/{userId}/retry | 後続処理を再試行 |
| POST /migration-preview | 指定IDを最大100件で事前確認。対象Userは変更しないが閲覧監査を記録 |

例外作成の入力項目：targetType、targetId、purpose、basis、expiresAt、identityFields。
targetIdはUser ID／画像UUID／Report IDです。userId・reportId・権限は対象からサーバー側で確定します。
証拠画像の既存API`GET /api/admin/reports/{id}/evidence-image`も専用権限で確認します。

## 後続処理と再試行

- 退会DB更新とwithdrawal_tasksの作成を同じトランザクションにします。
- コミット後にこのノードのSessionRegistryへ失効を通知し、失敗は永続記録から再試行します。DB状態・認証版数により他ノードもアクセス時に旧認証を拒否します。
- セッション再試行間隔は`privacy.session-retry-delay-ms`で、既定60秒です。
- `privacy.profile-image-deletion.enabled=false`が初期値です。保持方針と全参照の運用確認前に有効化しないでください。
- 画像削除を明示有効化しても、POLICY_PENDING・有効な例外・Profile参照がある画像は削除しません。
- 保持方針設定・解除・設定の有効化を承認後、専用再試行APIを呼びます。画像削除の自動定期ジョブは有効化していません。
- session_status／image_status、試行回数、例外型だけを確認します。失敗した元情報・SQL・原因メッセージを記録しません。
- SessionRegistryの失効マークは全セッションデータの即時物理消去とは異なります。実際のセッション寿命・配置構成は別途検証してください。

## 移行前の確認用SQL（読み取りのみ・ここでは未実行）

実DBの照合順序・制約を確認します。

```sql
SHOW FULL COLUMNS FROM users;
SHOW CREATE TABLE users;
SHOW CREATE TABLE profiles;
SHOW CREATE TABLE password_reset_tokens;
SELECT id, account_status, withdrawn_at, personal_data_erased_at
FROM users WHERE account_status='WITHDRAWN' ORDER BY id;
```

予約値との衝突をIDだけで確認します。実DB照合順序に加えて大小文字・前後空白を保守的に点検します。

```sql
SELECT u.id AS withdrawing_user_id, other.id AS conflicting_user_id, 'EMAIL' AS kind
FROM users u JOIN users other
  ON LOWER(TRIM(other.email))=LOWER(CONCAT('withdrawn-',u.id,'@withdrawn.invalid'))
 AND other.id<>u.id
WHERE u.account_status='WITHDRAWN'
UNION ALL
SELECT u.id, other.id, 'USERNAME'
FROM users u JOIN users other
  ON LOWER(TRIM(other.username))=LOWER(CONCAT('withdrawn_user_',u.id))
 AND other.id<>u.id
WHERE u.account_status='WITHDRAWN';

SELECT id, account_status FROM users
WHERE LOWER(email) LIKE '%.invalid' OR LOWER(username) LIKE 'withdrawn\_user\_%';

SELECT user_id,target_type,target_id,status,expires_at
FROM privacy_retention_records WHERE user_id IN (/* 承認されたID */);
```

元のメールや名前を照会結果・作業ログ・チケットへ貼らないでください。
衝突時に他のUserを自動改名せず、移行を止めて個別承認を受けます。

## ドライラン・段階的な移行

1. バックアップ・復旧権限・保管期間を運用担当者と確認する。バックアップへの個人情報残存も別管理する。
2. 実MySQLの隔離環境でV13、FK、一意制約、照合順序、ロックとロールバックを検証する。
3. 対象IDを小さな集合として承認する。新旧コードが混在する間は移行しない。
4. 専用プレビューと読取SQLで、退会状態、未処理／処理済み、衝突、保持例外、画像参照を確認する。
5. REQUIRES_RETENTION_REVIEWは移行許可ではない。保持例外・必要な鍵・既存証拠の所有者を確認する。
6. 実行対象・停止条件・担当者をユーザーへ提示し、本番変更の承認を別途受ける。
7. 承認後の実行経路からWithdrawalService.eraseExistingをIDごとの短いトランザクションで呼ぶ。自動起動・一括移行APIは実装していない。
8. personal_data_erased_atを確認し、処理済みは再実行しても秘密値を再生成しない。部分変換らしい値・衝突・例外は停止して確認する。
9. 画像原本の削除は同時実行せず、保持方針確定と別の承認後に後続処理を進める。

## 移行前後の検証・停止条件

- User ID・Profile ID、投稿・回答・いいね・ランキング・実績・通報のIDと件数、FKを比較する。
- 元メール・名前がUserに残らず、内部代替値が通常APIへ出ないことを確認する。
- 元パスワード・旧トークン・旧Sessionで認証できないこと、同メール再登録が別User IDであることを確認する。
- 保持例外の暗号文・期限・権限・監査、Snapshotの文章と画像、後続タスクの状態を確認する。
- 衝突、暗号鍵不足、DB例外、参照不明、想定外の件数差分で停止する。
- DB内の途中失敗はロールバックする。コミット後の失敗は元情報を戻さず、永続タスクから再試行する。
- 既存退会者の元情報は一度除去すると通常Userから復元できない。未承認の例外コピーや一括バックアップを新設しない。

## 未確定・別工程

証拠・Profile原本・監査・ログ・メール・バックアップの本番保持期間、法的根拠、鍵配布・更新、担当者の権限付与、複数ノードのSession管理、実MySQL検証は別途確定が必要です。
このコードの存在をもって、本番データの移行・画像削除・完全匿名化・法令適合性まで完了したとは扱いません。
