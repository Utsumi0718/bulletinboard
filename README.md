#  Photo Ogiri（仮称）

## プロジェクトの目的

写真を使った大喜利を楽しめるWebアプリケーションです。

本プロジェクトは、もともとSpring Bootの学習用として開発していた
掲示板アプリケーションをベースにしています。

旧掲示板で実装した認証・投稿・コメント・いいね・管理者機能などを活かしながら、
現在は「写真で一言」をテーマにした大喜利Webサービスへ段階的に再構築しています。

新規プロジェクトとして作り直すのではなく、
既存コードを分析し、要件変更に合わせて
ドメインモデル・認証方式・Repository・Service・Controller・テストを
順番にリファクタリングしていく方針で開発しています。


## データベース管理方針

開発初期は、Spring Boot / JPAの学習を優先するため、
`spring.jpa.hibernate.ddl-auto` を利用してEntity定義からテーブルを自動生成していました。

その後、機能追加やテーブル構成の変更が増えてきたため、
現在はFlywayを導入し、SQLマイグレーションファイルによって
データベース構造の変更履歴を管理する方針へ変更しています。

これにより、

- DB構造の変更内容をSQLとして明示できる
- 環境ごとの差異を減らせる
- 過去の変更履歴を追跡しやすくなる
- 本番環境へのデプロイを意識したDB管理ができる

ようにしています。


## 開発環境・技術スタック

### Backend

- Java 21
- Spring Boot 3.4.x
- Spring MVC
- Spring Security
- Spring Data JPA
- Hibernate
- Bean Validation
- Maven

### Database

- MySQL 8
- Flyway

### Frontend（現在）

- Thymeleaf
- HTML
- CSS
- JavaScript

現在は旧掲示板アプリで使用していたThymeleaf画面が一部残っています。

### Frontend（今後）

- React
- Tailwind CSS

今後はバックエンドをAPI化し、
Reactから利用する構成へ段階的に移行する予定です。

### Test

- JUnit 5
- MockMvc
- Mockito
- Spring Security Test

### Development Tools

- Visual Studio Code
- Git
- GitHub
- MySQL



## 実装予定の機能

### 認証・アカウント

- [x] ユーザー新規登録
- [x] emailによるログイン
- [x] ログアウト
- [x] ログイン失敗回数によるアカウントロック
- [x] パスワード再設定
- [x] アカウント凍結 / 凍結解除
- [x] ユーザー退会
- [x] ROLE_USER / ROLE_ADMINによる認可制御

### お題（Topic）

- [ ] お題一覧表示
- [ ] お題詳細表示
- [ ] 新規お題投稿
- [ ] お題編集
- [ ] お題削除
- [ ] 画像投稿
- [ ] お題タイトル検索
- [ ] ソート
- [ ] ページネーション

検索仕様は以下とします。

- 検索対象：お題タイトルのみ
- 検索方式：部分一致のみ

### 回答（Answer）

- [ ] お題への回答投稿
- [ ] 回答一覧表示
- [ ] 自分の回答削除
- [ ] 管理者による不適切な回答の削除

### いいね（Like）

- [ ] 回答へのいいね登録
- [ ] いいね解除
- [ ] いいね件数表示
- [ ] 重複いいね防止
- [ ] 自分の回答へのいいね禁止

### ランキング

- [ ] 週間ランキング

### 管理・運営

- [ ] ユーザー管理画面
- [ ] 通報機能
- [ ] お問い合わせ機能
- [ ] 管理者による投稿・回答の管理

### フロントエンド

- [ ] Reactによる画面再構築
- [ ] Tailwind CSSによるUI実装
- [ ] Backend APIとの連携

### インフラ・公開

- [ ] AWS環境構築
- [ ] 本番デプロイ
- [ ] 運用・監視


## これまでに実施した主な改修

### 1. Repository / Serviceとの再構築

旧プロジェクトは一般的な掲示板アプリとして開発していたため、
投稿を表す `Post` と、その投稿へのコメントを表す `Comment` を中心とした構成になっていました。

大喜利サービスへの変更に伴い、
単純にクラス名を変更するのではなく、
それぞれの役割を大喜利サービスのドメインに合わせて再設計しました。

#### Post → Topic

旧掲示板の `Post` は、

- 投稿タイトル
- 投稿本文
- 投稿者

などを保持する、一般的な掲示板投稿を表すEntityでした。

大喜利サービスではこの役割を `Topic` に変更し、
「ユーザーが回答するためのお題」を表すEntityとして再設計しています。

`Topic` では主に、

- お題タイトル（title）
- お題に使用する画像（image）
- 写真に対する問題文・お題文（question）
- 投稿者（User）
- 作成日時・更新日時
- 論理削除日時（deletedAt）

を管理します。

そのため、

`Post = 掲示板への文章投稿`

から、

`Topic = 写真と問題文を組み合わせた大喜利のお題`

へ役割そのものを変更しています。

あわせて、

- `PostRepository` → `TopicRepository`
- `PostService` → `TopicService`

へ変更し、Repository / Service層もTopicを中心とした構成へ再構築しました。

また、Topicの削除には物理削除ではなく、
`deletedAt` を利用した論理削除方式を採用しています。

#### Comment → Answer

旧掲示板の `Comment` は、
Postに対してユーザーが文章でコメントするためのEntityでした。

大喜利サービスではこの役割を `Answer` に変更し、
Topicに対してユーザーが投稿する「大喜利の回答」を表すEntityとして再設計しています。

つまり、

`Comment = 掲示板投稿へのコメント`

から、

`Answer = 大喜利のお題に対する一言回答`

へ変更しています。

あわせて、

- `CommentRepository` → `AnswerRepository`
- `CommentService` → `AnswerService`

へ変更し、
Topic単位でAnswerを取得できる構造へ移行しました。

Answerについても削除時にデータを直接消さず、
論理削除を前提とした構成へ変更しています。

#### Likeの対象変更

旧掲示板では `Post` に対して「いいね」を付ける仕様でした。

大喜利サービスでは、
お題そのものではなくユーザーが投稿した回答を評価するため、

`PostへのLike`

から、

`AnswerへのLike`

へ仕様を変更しました。

そのためLikeRepository / LikeServiceについても、
UserとAnswerを紐付ける構造へ変更しています。

これにより、

- 同じユーザーによる同一Answerへの重複Like判定
- Answer単位のLike数取得
- Answerに紐づくLikeの削除

を扱える構成にしています。

#### 検索仕様の整理

旧掲示板では複数項目や複数の一致方式を使った検索を実装していましたが、
大喜利サービスでは仕様をシンプルに整理しました。

現在の検索仕様は、

- 検索対象：`Topic.title` のみ
- 検索方式：部分一致のみ

です。

旧仕様に存在した、

- 前方一致
- 後方一致
- matchTypeによる検索方式の切り替え

は廃止し、今後も実装しない方針としています。

#### テストコードの移行

Repository / Serviceの変更に合わせてテストコードも再構築しました。

主な変更：

- `PostRepositoryTest` → `TopicRepositoryTest`
- `PostServiceTest` → `TopicServiceTest`
- `AnswerRepositoryTest`を新規作成
- `AnswerServiceTest`を新規作成
- `LikeServiceTest`をAnswerへのLike仕様へ変更

これにより、Repository / Service層については
旧 `Post` / `Comment` への実依存を除去し、
新しい `Topic` / `Answer` / `Like` を中心とした構成へ移行しました。

### 2. 旧Post / Comment参照の整理

Repository / Service層を先に `Topic` / `Answer` へ移行したことで、
Controllerやテストコード側には旧 `Post` / `Comment` を前提とした処理が残っていました。

このブランチでは、
それらの旧参照を整理し、
プロジェクト全体を新しい構造へつなぎ直しました。

#### PostController → TopicController

旧掲示板では `PostController` が、
投稿一覧・詳細・作成・編集・削除・検索などの処理を担当していました。

大喜利サービスへの移行に伴い、
これを `TopicController` へ変更し、
大喜利のお題を扱うControllerとして再構成しました。

主な変更内容：

- `Post` → `Topic`
- `PostService` → `TopicService`
- `Page<Post>` → `Page<Topic>`
- 投稿本文を前提とした `content` の処理を削除
- `Topic.title` / `Topic.question` を利用する構造へ変更
- 旧 `matchType` による検索条件切り替えを削除
- PostへのLike取得処理をControllerから除去
- Comment取得処理を除去
- Topic削除処理を論理削除方式へ変更

この段階では、
旧Thymeleaf画面との接続を完全には作り直していないため、

`/posts`

というURLは暫定的に維持しています。

URLや画面構成の全面的な変更は、
後続のAPI化・フロントエンド再構築フェーズで対応する方針です。

#### CommentController → AnswerController

旧 `CommentController` は、
掲示板投稿に対するコメントの投稿・削除を担当していました。

これを `AnswerController` へ変更し、
大喜利のお題に対する回答を扱うControllerへ再構成しました。

主な変更内容：

- `Comment` → `Answer`
- `Post` → `Topic`
- `CommentService` → `AnswerService`
- `PostService` → `TopicService`
- `postId` → `topicId`
- Topicに対するAnswer登録処理へ変更
- Answer削除を論理削除方式へ変更

この段階ではURLも暫定的に、

`/comments`

を維持しています。

最終的なAPI URLは、
後続の `feature/topic-answer-api` で整理する予定です。

#### LikeControllerのPost依存を除去

旧掲示板では、
Postに対していいねを付けるControllerになっていました。

大喜利サービスでは、
回答を評価する仕組みに変更するため、
LikeControllerからPost依存を除去しました。

主な変更内容：

- `Post` 依存を削除
- `PostService` 依存を削除
- PostへのLike → AnswerへのLikeへ変更
- `postId` → `answerId`
- いいねAPIを `/answers/{answerId}/like` へ変更

なお、

- 自分の回答へのいいね禁止
- 削除済みAnswerへのいいね禁止
- 重複制御

などの詳細な業務ルールは、
後続の `feature/like-feature` で実装する方針としています。

#### AdminControllerの旧活動履歴依存を整理

旧掲示板の管理者機能では、
Userに紐づくPost / Commentを直接参照して
ユーザー活動履歴を表示していました。

しかし、
`User#getPosts()` や `User#getComments()` といった
旧ドメイン依存を新しいEntity設計では使用しないため、
これらの参照を削除しました。

主な変更内容：

- `User#getPosts()` の参照を削除
- `User#getComments()` の参照を削除
- 旧Post / Comment活動履歴依存を除去

この段階では、
管理画面へ対象User情報のみ渡す暫定構成としています。

Topic / Answerを利用した活動履歴表示や、
管理者機能の完成は後続フェーズへ引き継いでいます。

#### Controllerテストの移行

Controllerの変更に合わせて、
ControllerTestも新しいドメイン構造へ移行しました。

主な変更：

- `PostControllerTest` → `TopicControllerTest`
- `CommentControllerTest` → `AnswerControllerTest`
- `LikeControllerTest`をAnswerへのLike仕様へ変更
- `FlashMessageTest`をTopic / Answer仕様へ変更
- ControllerTestから旧 `PostService` / `CommentService` 依存を除去

これにより、
Controller層とそのテストについても、
旧 `Post` / `Comment` を前提としない構成へ移行しました。

#### 旧IntegrationTestの整理

旧 `PostIntegrationTest` は、

- Post Entity
- PostRepository
- content
- usernameログイン
- 物理削除
- 旧Thymeleaf
- 旧URL構造

など、掲示板時代の仕様への依存が非常に強い状態でした。

そのため、
単純に `Post` を `Topic` へ置き換えるのではなく、
一度テスト自体を削除しました。

新しいIntegrationTestは、
各機能が完成した段階で、
現在の大喜利サービス仕様に合わせて作り直す方針としています。

#### コンパイル可能な状態への復旧

このブランチの重要な目的の一つは、
旧 `Post.java` / `Comment.java` が存在しない状態でも、
プロジェクト全体がコンパイルできる状態へ戻すことでした。

そのため、

- `mvn compile`
- `mvn test-compile`

を実行し、
`src/main/java` と `src/test/java` の双方が
新しいEntity構造でコンパイル可能であることを確認しました。

また、プロジェクト全体を検索し、

- PostRepository
- CommentRepository
- PostService
- CommentService
- Post Entity
- Comment Entity
- User#getPosts()
- User#getComments()

への実依存が残っていないことを確認しました。

この時点で、
Repository / Service / Controller / ControllerTestまで、
旧掲示板の主要な実依存を取り除き、
新しい `Topic` / `Answer` を中心とした構造へ移行できています。


### 3. 認証・アカウント機能の再設計

旧掲示板では、
`username + password` を使ったログインを前提としていました。

大喜利サービスへの移行に伴い、
ユーザー名を公開表示用として扱い、
ログインIDにはemailを使用する構成へ変更しました。

単純にログインフォームだけを変更するのではなく、
認証・アカウント状態・退会・管理者による利用停止などの責務を整理し、
新しいUser設計へ合わせて再構築しています。

#### usernameログイン → emailログイン

旧仕様では `username` をログインIDとして使用していました。

新仕様では、

- `username`：他のユーザーへ表示する公開ユーザー名
- `email`：ログイン時に使用する非公開のログインID

として役割を分離しました。

そのため、
Spring SecurityのPrincipalにはemailが設定される構成へ変更しています。

Controllerなどで現在のログインユーザーを取得する場合も、

`findByUsername(...)`

ではなく、

`findByEmail(...)`

を使用するように変更しました。

一方、
画面上に表示する名前には `username` を使用します。

これにより、

`認証に使用する情報 = email`

`ユーザーへ公開する情報 = username`

という役割を明確に分離しています。

#### ユーザー登録処理の変更

ユーザー新規登録についても、
emailログインへ対応するため仕様を見直しました。

登録時には、

- username
- email
- password

を入力します。

また、

- usernameの重複
- emailの重複

をそれぞれチェックし、
同じusernameやemailで複数アカウントを登録できないようにしています。

新規登録されたユーザーには、
一般ユーザー権限である `ROLE_USER` を設定します。

#### ログイン失敗によるセキュリティロック

ログイン失敗回数を管理するため、

- `failedAttempt`
- `accountNonLocked`

を使用しています。

パスワードを3回連続で間違えると、

`accountNonLocked = false`

となり、
ログインできない状態になります。

この仕組みは、
不正なログイン試行への対策として利用する
「セキュリティロック」です。

パスワード再設定を行った場合は、

- パスワードを更新
- failedAttemptを0へリセット
- accountNonLockedをtrueへ戻す

ことで、
ログイン失敗によるロックを解除します。

#### AccountStatusの導入

ログイン失敗によるセキュリティロックとは別に、
ユーザーの業務上の状態を管理するため
`AccountStatus` を導入しました。

現在は以下の3状態を使用しています。

`ACTIVE`

通常利用可能な状態です。

`FROZEN`

管理者によって利用停止されている状態です。

ログインできませんが、
管理者による凍結解除によって `ACTIVE` へ戻すことができます。

`WITHDRAWN`

ユーザー自身が退会した状態です。

ログインできません。

過去に投稿したTopicやAnswerなどのデータは保持し、
現在は原則として復元しない仕様としています。

#### セキュリティロックとアカウント状態の責務分離

旧実装では、
アカウントを利用できない状態を
`accountNonLocked` だけで表現していました。

しかし、

- ログイン失敗による一時的なロック
- 管理者によるアカウント凍結
- ユーザー自身による退会

では意味が異なります。

そのため現在は、

`accountNonLocked`
→ ログイン失敗によるセキュリティロック

`accountStatus`
→ 管理者凍結や退会などのアカウント状態

として明確に分離しています。

これにより、
管理者による凍結処理で
`accountNonLocked` を変更することはありません。

#### 管理者によるアカウント凍結

管理者は一般ユーザーのアカウントを
凍結・凍結解除できます。

凍結時には、

`accountStatus = FROZEN`

へ変更します。

凍結解除時には、

`FROZEN → ACTIVE`

へ戻します。

一方で、

- WITHDRAWNユーザーは復元しない
- 管理者自身を凍結しない
- 他の管理者を凍結しない

という制御も追加しています。

管理者画面へのアクセスは
`ROLE_ADMIN` を持つユーザーのみに制限しています。

#### ユーザー退会機能

一般ユーザーが自分のアカウントを退会できる処理を追加しました。

退会時には、

`accountStatus = WITHDRAWN`

へ変更し、
`withdrawnAt` に退会日時を記録します。

退会後はログインできませんが、
過去のTopicやAnswerなどの関連データは削除せず保持します。

また、
システム管理を担う `ROLE_ADMIN` アカウントについては、
管理者自身による退会を禁止しています。

#### パスワード再設定のemail対応

パスワード再設定処理も、
usernameではなくemailを基準として
対象ユーザーを特定する構成へ変更しました。

パスワード再設定時には、

- 新しいパスワードをハッシュ化して保存
- failedAttemptを0へリセット
- ログイン失敗ロックを解除

します。

ただし、

- FROZEN
- WITHDRAWN

などの `accountStatus` は変更しません。

そのため、
凍結済みユーザーがパスワードを変更しても、
凍結状態そのものが解除されることはありません。

#### Controllerのemail認証対応

認証方式をemailへ変更したことで、
Topic / Answer / LikeなどのController側にも
旧username認証を前提とした処理が残っていました。

そのため、

- `TopicController`
- `AnswerController`
- `LikeController`
- `AdminController`
- `WithdrawalController`

などについて、
Principalのemailを基準として
ログインユーザーを特定・本人判定するように整理しました。

また、
回答投稿・削除時などに画面へ表示する名前については、
emailではなく公開用の `username` を使用するようにしています。

#### 認証関連テストの再構築

認証・アカウント仕様の変更に合わせて、
既存テストもemailログイン基準へ移行しました。

主な検証内容：

- emailによるログイン成功
- パスワード不一致
- 3回失敗によるアカウントロック
- ロック済みユーザーのログイン拒否
- ACTIVEユーザーのログイン
- FROZENユーザーのログイン拒否
- WITHDRAWNユーザーのログイン拒否
- username重複
- email重複
- パスワード再設定
- 管理者による凍結 / 凍結解除
- ユーザー退会
- ROLE_USER / ROLE_ADMINによる認可
- 管理者自身の退会禁止
- Topic / Answer / Likeのemail認証対応

また、
プロジェクト全体を横断検索し、
認証用途として残っていた

`findByUsername(userDetails.getUsername())`

などの旧username認証処理を整理しました。

このフェーズ完了時点で、
全62件のテストについて、

`Failures: 0`
`Errors: 0`

となり、
認証・アカウント再設計後の状態で
テストがすべて成功することを確認しています。

### 4. Topic / Answer APIの実装と旧Thymeleaf構成の整理

Repository / Service / 認証・アカウント機能の再設計が完了した後、

大喜利サービスの中心機能である `Topic` / `Answer` を、

Reactなどのフロントエンドから利用できるREST APIとして実装しました。

このブランチでは、

単純にControllerをAPI化するだけではなく、

Topic / Answerの業務ルール・例外処理・レスポンス形式・テストを整理したうえで、

旧Thymeleaf構成のうち不要になった部分も段階的に削除しています。

#### Topic一覧API

Topic一覧を取得するREST APIを実装しました。

エンドポイント：

`GET /api/topics`

主な仕様：

- ページネーション対応
- `createdAt` の降順で固定ソート
- keyword未指定時は通常の一覧取得
- keyword指定時はTopicタイトル検索
- 検索対象は `Topic.title` のみ
- 検索方式は部分一致のみ
- 論理削除済みTopicは取得対象外

検索仕様については、

旧掲示板に存在していた前方一致・後方一致・matchTypeによる検索切り替えを使用せず、

大喜利サービス向けにシンプルな検索仕様へ統一しています。

#### Topic詳細API

Topic単体を取得するREST APIを実装しました。

エンドポイント：

`GET /api/topics/{id}`

存在しないTopic、

または論理削除済みTopicを指定した場合は、

`TopicNotFoundException`

を発生させ、

HTTPステータス `404 Not Found` を返す構成にしています。

#### Topic新規投稿API

新しいTopicを投稿するREST APIを実装しました。

エンドポイント：

`POST /api/topics`

主な処理：

- `TopicRequest` による入力受付
- Bean Validationによる入力検証
- Spring SecurityのPrincipalからログイン中ユーザーのemailを取得
- emailを基準に投稿者Userを特定
- Topicを保存
- 作成したTopicを `TopicResponse` として返却

作成成功時は、

HTTPステータス `201 Created`

を返し、

作成したTopicのURLを `Location` ヘッダーへ設定しています。

#### Topic編集API

Topicを編集するREST APIを実装しました。

エンドポイント：

`PUT /api/topics/{id}`

編集には以下の業務ルールを設定しています。

- Topicを投稿した本人のみ編集可能
- 管理者であっても他ユーザーのTopicは編集しない
- 一度でもAnswerが投稿されたTopicは編集不可
- 論理削除されたAnswerも「過去に回答が存在した」として編集可否判定に含める

そのため、

Topicに現在表示されているAnswerが0件であっても、

過去にAnswerが投稿されていれば編集できません。

編集権限がない場合は、

HTTPステータス `403 Forbidden`

を返します。

Answer投稿履歴が存在して編集できない場合は、

`TopicEditConflictException`

を発生させ、

HTTPステータス `409 Conflict`

を返す構成にしています。

#### Topic削除API

Topicを削除するREST APIを実装しました。

エンドポイント：

`DELETE /api/topics/{id}`

削除可能なユーザーは、

- Topicを投稿した本人
- ROLE_ADMINを持つ管理者

としています。

削除時にはデータを物理削除せず、

`deletedAt`

へ削除日時を設定する論理削除方式を使用しています。

削除成功時は、

HTTPステータス `204 No Content`

を返します。

#### TopicService / TopicRepositoryのAPI対応

Topic APIの実装に合わせて、

TopicService / TopicRepositoryも整理しました。

主な変更内容：

- API向けの `getById()` を追加
- `TopicNotFoundException` による404制御
- Topic編集時の本人判定
- Answer投稿履歴による編集可否判定
- Topic削除時の本人 / 管理者判定
- 論理削除済みTopicを除外した取得処理
- Topicタイトル部分一致検索
- ページネーション処理の整理

TopicRepositoryでは、

`findByDeletedAtIsNull(...)`

`findByIdAndDeletedAtIsNull(...)`

`findByTitleContainingAndDeletedAtIsNull(...)`

などを利用し、

論理削除を前提とした取得処理へ統一しています。

#### Answer一覧API

指定されたTopicに投稿されたAnswer一覧を取得するREST APIを実装しました。

エンドポイント：

`GET /api/topics/{topicId}/answers`

主な仕様：

- 対象Topicの存在確認
- 論理削除済みAnswerを取得対象外とする
- Answerが存在しない場合は空配列を返す

対象Topic自体が存在しない、

または論理削除済みの場合は、

HTTPステータス `404 Not Found`

を返します。

#### Answer投稿API

TopicへAnswerを投稿するREST APIを実装しました。

エンドポイント：

`POST /api/topics/{topicId}/answers`

主な処理：

- `AnswerRequest` による入力受付
- Bean Validationによる入力検証
- Principalからログイン中ユーザーのemailを取得
- emailを基準に回答者Userを特定
- 対象Topicの存在確認
- Answerを保存
- `AnswerResponse` を返却

作成成功時は、

HTTPステータス `201 Created`

を返します。

#### Answer編集API

Answerを編集するREST APIを実装しました。

エンドポイント：

`PUT /api/answers/{id}`

編集には以下の業務ルールを設定しています。

- Answerを投稿した本人のみ編集可能
- 管理者であっても他ユーザーのAnswerは編集不可
- Likeが1件以上付いているAnswerは編集不可
- 論理削除済みAnswerは編集不可

権限がない場合は、

HTTPステータス `403 Forbidden`

を返します。

Likeが付いているため編集できない場合は、

`AnswerEditConflictException`

を発生させ、

HTTPステータス `409 Conflict`

を返します。

存在しない、

または論理削除済みAnswerの場合は、

`AnswerNotFoundException`

によって `404 Not Found` を返します。

#### Answer削除API

Answerを削除するREST APIを実装しました。

エンドポイント：

`DELETE /api/answers/{id}`

削除可能なユーザーは、

- Answerを投稿した本人
- ROLE_ADMINを持つ管理者

としています。

Topic編集とは異なり、

管理者は不適切なAnswerを削除できる仕様としています。

削除方法には物理削除ではなく、

`deletedAt`

を利用した論理削除方式を採用しています。

削除成功時は、

HTTPステータス `204 No Content`

を返します。

#### AnswerService / AnswerRepositoryのAPI対応

Answer APIの実装に合わせて、

AnswerService / AnswerRepositoryを整理しました。

主な変更内容：

- Topic単位のAnswer一覧取得
- Answer IDによる取得
- Answer編集処理
- Answer削除処理
- Answer所有者判定
- 管理者による削除判定
- Like件数を利用した編集可否判定
- 論理削除済みAnswerの除外
- Topicに過去のAnswerが存在するか確認する処理

Answer編集・削除時に使用していた汎用的な例外処理も整理し、

API仕様に合わせた独自Exceptionを利用する構成へ変更しました。

#### Topic / Answer用DTOの整備

REST APIとEntityを直接結び付けないため、

Topic / Answer用のRequest / Response DTOを使用しています。

Answerでは、

- `AnswerRequest`
- `AnswerResponse`

を利用し、

一覧系のAPIでは共通ページングレスポンスとして

`PageResponse`

を利用しています。

これにより、

Entity内部の構造をそのまま外部へ公開せず、

APIとして必要な情報だけをJSONで返す構成にしています。

#### 独自Exceptionと共通エラーレスポンスの整理

Topic / Answer APIで発生するエラーを、

HTTPステータスとJSONレスポンスで統一して扱うため、

独自ExceptionとGlobalExceptionHandlerを整理しました。

主なException：

- `TopicNotFoundException`
- `AnswerNotFoundException`
- `ForbiddenOperationException`
- `TopicEditConflictException`
- `AnswerEditConflictException`
- `UserNotFoundException`

主なHTTPステータス：

- 入力エラー → `400 Bad Request`
- Topic / Answer不存在 → `404 Not Found`
- 操作権限なし → `403 Forbidden`
- 編集条件の競合 → `409 Conflict`
- 想定外のUser取得失敗 → `500 Internal Server Error`

通常のエラーでは、

`ErrorResponse`

Validationエラーでは、

`ValidationErrorResponse`

を返す構成にしています。

これにより、

旧Thymeleaf画面で使用していたFlashMessageではなく、

React側がHTTPステータスとJSONレスポンスを基準に

エラー表示を制御できる構成へ移行しています。

#### Topic / Answer APIテストの整備

REST API化に合わせて、

Controller / Serviceテストも現在のAPI仕様へ対応させました。

Topic APIでは、

- 一覧取得
- 詳細取得
- 新規投稿
- 編集
- 削除
- Validationエラー
- 404
- 403
- 409

などを検証しています。

Answer APIでは、

- Answer一覧取得
- Answer 0件時の空配列
- Topic不存在時の404
- Answer投稿
- Validationエラー
- User取得失敗
- Answer編集
- 所有者以外による編集禁止
- Like付きAnswerの編集禁止
- Answer削除
- 管理者によるAnswer削除
- 所有者 / 管理者以外による削除禁止
- Answer不存在時の404

などを検証しています。

また、

AnswerServiceTestについても、

独自Exceptionを利用する現在の業務ルールへ追従させています。

#### 旧TopicController / AnswerControllerの削除

Topic / AnswerのREST APIが完成したため、

旧Thymeleaf画面向けに使用していた、

- `TopicController`
- `AnswerController`

を削除しました。

旧Controllerが担当していた処理は、

それぞれ、

`TopicApiController`

`AnswerApiController`

へ移行しています。

この段階で、

Topic / Answerのバックエンド処理については、

Thymeleaf画面へ直接Viewを返す構成から、

JSONを返すREST API中心の構成へ移行しています。

#### 旧ControllerTestの削除

旧Thymeleaf Controllerの削除に伴い、

以下のテストも削除しました。

- `TopicControllerTest`
- `AnswerControllerTest`
- `FlashControllerTest`

今後は、

REST APIとしてのHTTPステータス・JSONレスポンス・業務ルールを

ControllerTest / ServiceTestで検証する方針としています。

#### templates/posts/ の削除

TopicControllerの削除に伴い、

旧 `/posts` 画面で使用していたThymeleafテンプレートを削除しました。

削除対象：

- `templates/posts/detail.html`
- `templates/posts/edit.html`
- `templates/posts/list.html`
- `templates/posts/new.html`

Topic画面については、

今後、

`TopicApiController + React`

による構成へ置き換える予定です。

#### 旧Like用JavaScriptの削除

旧掲示板画面で使用していた、

`static/js/like.js`

についても整理しました。

このJavaScriptは、

旧 `/posts` を前提としたLikeリクエストを送信する実装でしたが、

プロジェクト内から参照されていないことを確認したため削除しました。

Like機能そのものについては、

後続の `feature/like-feature`

で正式なREST APIとして再設計する方針です。

#### 残存Thymeleaf Controllerの依存確認

Topic / Answer関連の旧Controllerを削除した後、

残っているControllerについても

Thymeleaf画面への依存状況を確認しました。

確認対象：

- `AdminController`
- `AuthController`
- `PasswordResetController`
- `ContactController`
- `WithdrawalController`
- `LikeController`

`AdminController`

→ Model / RedirectAttributes / Thymeleaf View / FlashMessageへの依存が残っているため、

管理機能のREST API化・React移行まで維持します。

`AuthController`

→ Model / @ModelAttribute / BindingResult / Thymeleaf View / redirectを使用しているため、

React認証画面・認証APIの実装まで維持します。

`PasswordResetController`

→ ThymeleafフォームとBindingResultを利用しているため、

React向けパスワード再設定APIへ移行するまで維持します。

`ContactController`

→ お問い合わせフォーム・確認画面・完了画面に加え、

Contact保存とメール送信処理も担当しているため、

お問い合わせREST API化まで維持します。

`WithdrawalController`

→ Thymeleaf Viewそのものへの直接依存はありませんが、

redirectによるMVC画面遷移を使用しているため、

React向け退会API化まで維持します。

`LikeController`

→ JSONを返す中間的なControllerとして現在は残し、

`feature/like-feature`

で正式なLike APIへ移行した後に削除する方針です。

#### SecurityConfigの旧画面URL依存確認

TopicController削除後、

SecurityConfigに残っている旧 `/posts` 依存も確認しました。

`permitAll()` に設定されていた

`/posts`

については削除しました。

一方、

`defaultSuccessUrl("/posts", true)`

および、

`LoginUrlAuthenticationEntryPoint("/posts?error=unauthorized")`

については、

React側の認証画面・ルーティング設計とまとめて変更する必要があるため、

このブランチでは変更を見送りました。

今後React認証へ移行する際に、

- `loginPage`
- `failureHandler`
- `defaultSuccessUrl`
- `authenticationEntryPoint`
- `logoutSuccessUrl`

をまとめて再設計する方針です。

#### 残存Thymeleafテンプレートの扱い

`templates/posts/`

は削除しましたが、

以下のテンプレートは現在も対応するControllerから使用されているため残しています。

- `templates/admin/`
- `templates/auth/`
- `templates/contact/`
- `templates/layout/`

これらは、

各機能をReact画面・REST APIへ移行したタイミングで

順次削除する方針です。

また、

`templates/admin/user_activities.html`

には旧Post / Comment依存が残っていることを確認しています。

管理者によるTopic / Answer活動履歴については、

後続の管理機能実装フェーズで再構築する予定です。

#### pom.xmlのThymeleaf依存確認

pom.xmlについても、

Thymeleaf関連依存が残っていることを確認しました。

現在使用している主な依存：

- `spring-boot-starter-thymeleaf`
- `thymeleaf-layout-dialect`
- `thymeleaf-extras-springsecurity6`

現在も、

Admin / Auth / ContactなどのThymeleaf画面が残っているため、

このブランチでは削除していません。

Reactへの完全移行後、

旧Thymeleaf Controller / templateをすべて削除した段階で、

これらの依存もpom.xmlから削除する予定です。

#### 最終確認

このブランチの完了時には、

- `mvn compile`
- 全テスト実行
- `git status`

を確認しました。

その結果、

コンパイル・テストともに成功し、

未コミットの変更が残っていない状態で

`feature/topic-answer-api`

を完了しました。