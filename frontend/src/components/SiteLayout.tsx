import { useEffect, type ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router'

function Navigation() {
  return (
    <nav aria-label="メインナビゲーション">
      <ul className="flex flex-wrap items-center gap-2 sm:gap-3">
        <li>
          <NavLink
            className={({ isActive }) => `inline-flex min-h-11 items-center rounded-full px-4 text-sm font-semibold transition-colors ${isActive ? 'bg-slate-900 text-white' : 'text-slate-700 hover:bg-slate-100'}`}
            end
            to="/"
          >
            ホーム
          </NavLink>
        </li>
        <li>
          <Link className="inline-flex min-h-11 items-center rounded-full px-4 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100" to="/#ui-states">
            共通表示
          </Link>
        </li>
      </ul>
    </nav>
  )
}

function Header() {
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-8">
        <Link aria-label="写真で一言 ホーム" className="inline-flex w-fit items-center gap-3 font-bold tracking-tight text-slate-900" to="/">
          <span aria-hidden="true" className="grid h-10 w-10 place-items-center rounded-xl bg-amber-300 text-xl">P.</span>
          <span>写真で一言</span>
        </Link>
        <Navigation />
      </div>
    </header>
  )
}

function Footer() {
  return (
    <footer className="mt-auto border-t border-slate-200 bg-white">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-2 px-5 py-6 text-sm text-slate-600 sm:flex-row sm:items-center sm:justify-between sm:px-8">
        <span>Photo Ogiri</span>
        <span>写真から生まれる、ひと言を楽しもう。</span>
      </div>
    </footer>
  )
}

export function SiteLayout({ children }: { children: ReactNode }) {
  const location = useLocation()

  useEffect(() => {
    if (location.hash) {
      document.getElementById(location.hash.slice(1))?.scrollIntoView()
    }
  }, [location.key, location.hash])

  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <a className="skip-link" href="#main-content">本文へ移動</a>
      <Header />
      <main className="mx-auto w-full max-w-6xl flex-1 px-5 py-10 sm:px-8 sm:py-14" id="main-content" tabIndex={-1}>
        {children}
      </main>
      <Footer />
    </div>
  )
}
