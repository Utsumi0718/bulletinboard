import { Link } from 'react-router'

export function LoadingState({ message = '読み込み中です…' }: { message?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-10 text-center" role="status">
      <span aria-hidden="true" className="h-10 w-10 animate-spin rounded-full border-4 border-slate-200 border-t-amber-500 motion-reduce:animate-none" />
      <p className="font-medium text-slate-700">{message}</p>
    </div>
  )
}

export function ErrorState({ message = '処理を完了できませんでした。時間をおいて再度お試しください。', onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <div className="mx-auto max-w-lg py-6 text-center" role="alert">
      <span aria-hidden="true" className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-rose-100 text-2xl font-bold text-rose-700">!</span>
      <h2 className="mt-4 text-xl font-bold text-slate-900">エラーが発生しました</h2>
      <p className="mt-3 leading-7 text-slate-700">{message}</p>
      {onRetry && (
        <button className="mt-6 min-h-11 rounded-full bg-slate-900 px-6 font-semibold text-white hover:bg-slate-700" onClick={onRetry} type="button">
          再試行する
        </button>
      )}
    </div>
  )
}

export function UnauthenticatedState({ loginPath }: { loginPath?: string }) {
  return (
    <div className="mx-auto max-w-lg py-6 text-center">
      <span aria-hidden="true" className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-amber-100 text-xl font-bold text-amber-800">?</span>
      <h2 className="mt-4 text-xl font-bold text-slate-900">ログインが必要です</h2>
      <p className="mt-3 leading-7 text-slate-700">この操作を行うにはログインしてください。</p>
      {loginPath && (
        <Link className="mt-6 inline-flex min-h-11 items-center rounded-full bg-slate-900 px-6 font-semibold text-white hover:bg-slate-700" to={loginPath}>
          ログインへ進む
        </Link>
      )}
    </div>
  )
}
