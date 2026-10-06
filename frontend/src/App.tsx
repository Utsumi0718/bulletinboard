import { useState } from 'react'
import { BrowserRouter, Link, Route, Routes } from 'react-router'
import { ErrorState, LoadingState, UnauthenticatedState } from './components/FeedbackStates'
import { SiteLayout } from './components/SiteLayout'

type PreviewState = 'none' | 'loading' | 'error' | 'unauthenticated'

const previewOptions: { value: PreviewState; label: string }[] = [
  { value: 'loading', label: '読み込み中' },
  { value: 'error', label: 'エラー' },
  { value: 'unauthenticated', label: '未ログイン' },
]

function Home() {
  const [preview, setPreview] = useState<PreviewState>('none')

  return (
    <>
      <section className="relative overflow-hidden rounded-[2rem] bg-slate-900 px-6 py-12 text-white sm:px-10 sm:py-16">
        <div aria-hidden="true" className="absolute -right-16 -top-20 h-72 w-72 rounded-full border border-white/15 sm:h-96 sm:w-96" />
        <div aria-hidden="true" className="absolute -bottom-32 right-8 h-72 w-72 rounded-full border border-white/15 sm:h-96 sm:w-96" />
        <div className="relative max-w-2xl">
          <p className="mb-4 text-sm font-bold tracking-[0.2em] text-amber-300">PHOTO OGIRI</p>
          <h1 className="text-4xl font-bold leading-tight tracking-tight sm:text-6xl">
            写真から、ひと言のひらめきへ。
          </h1>
          <p className="mt-6 max-w-xl text-base leading-8 text-slate-200 sm:text-lg">
            写真で一言を楽しむための画面を準備しています。ここでは、どの画面でも使う表示と操作を確認できます。
          </p>
          <a className="mt-8 inline-flex min-h-12 items-center rounded-full bg-amber-300 px-6 font-bold text-slate-950 transition-colors hover:bg-amber-200" href="#ui-states">
            共通表示を見る <span aria-hidden="true" className="ml-2">↗</span>
          </a>
        </div>
      </section>

      <section aria-labelledby="ui-states-title" className="mt-16 scroll-mt-8" id="ui-states">
        <div className="mb-6">
          <p className="text-sm font-bold tracking-widest text-amber-700">UI FOUNDATION</p>
          <h2 className="mt-2 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl" id="ui-states-title">
            共通表示の確認
          </h2>
          <p className="mt-3 text-slate-600">ボタンを選ぶと、各画面で再利用する状態表示を確認できます。</p>
        </div>
        <div aria-label="表示する状態" className="flex flex-wrap gap-3" role="group">
          {previewOptions.map((option) => (
            <button
              aria-pressed={preview === option.value}
              className={`min-h-11 rounded-full border px-5 text-sm font-semibold transition-colors ${preview === option.value ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-300 bg-white text-slate-800 hover:border-slate-700'}`}
              key={option.value}
              onClick={() => setPreview(option.value)}
              type="button"
            >
              {option.label}
            </button>
          ))}
        </div>
        <div className="mt-6 min-h-64 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-10">
          {preview === 'none' && <p className="text-slate-600">上のボタンから状態を選んでください。</p>}
          {preview === 'loading' && <LoadingState />}
          {preview === 'error' && <ErrorState />}
          {preview === 'unauthenticated' && <UnauthenticatedState />}
        </div>
      </section>
    </>
  )
}

function NotFound() {
  return (
    <section className="mx-auto max-w-xl py-20 text-center" aria-labelledby="not-found-title">
      <p className="text-sm font-bold tracking-widest text-amber-700">404</p>
      <h1 className="mt-3 text-3xl font-bold text-slate-900" id="not-found-title">ページが見つかりません</h1>
      <p className="mt-4 text-slate-600">URLをご確認ください。</p>
      <Link className="mt-8 inline-flex min-h-11 items-center rounded-full bg-slate-900 px-6 font-semibold text-white hover:bg-slate-700" to="/">
        ホームへ戻る
      </Link>
    </section>
  )
}

function App() {
  return (
    <BrowserRouter>
      <SiteLayout>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </SiteLayout>
    </BrowserRouter>
  )
}

export default App
