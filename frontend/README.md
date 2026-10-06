# Photo Ogiri frontend

STEP8で作成するReactフロントエンドです。Node.js 24とnpmを使用します。

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

本番用ビルドは`npm.cmd run build`、静的チェックは`npm.cmd run lint`、通信処理のテストは`npm.cmd test`で確認します。
開発サーバーの`/api`はローカルのbackend（`127.0.0.1:8080`）へ転送します。backendへの実ブラウザ接続と本番の配置・Cookie・CORS設定は後続工程で確認します。
通信処理はCookieを含め、変更系リクエストの直前に`GET /api/csrf`で取得したトークンを返却されたヘッダー名で送信します。認証状態は`GET /api/auth/me`から取得し、`useAuth()`で参照します。
画面は`api.request()`の失敗を`toUserFacingError()`へ渡し、`category`で入力エラー（400）、認証（401）、権限・CSRF（403）などを分けます。`fieldErrors`は入力欄に、`reason`はログイン失敗理由の表示に使えます。500と通信失敗の内部情報は画面に渡しません。
`POST /api/auth/login`はJSONではなく`URLSearchParams`の`email`・`password`を送信します。ログイン後は`useAuth().refresh()`で本人情報を読み直し、ログアウト後は`useAuth().clear()`で画面の認証状態を消します。
