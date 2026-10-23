import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { cleanupApi, type CleanupRow, type CleanupPreview } from '../api/cleanup'
import { toUserFacingError } from '../api/client'
import { LoadingState, UnauthenticatedState } from '../components/FeedbackStates'

const labels: Record<string, string> = {
  RUNNING: '実行中／中断の可能性あり', FINISHED: '終了', INTERRUPTED: '中断',
  COMPLETED: 'お題削除完了', IMAGE_COMPLETED: '画像再確認完了', HELD_REPORT: '未解決通報のため保留',
  HELD_X_REFERENCE: 'X履歴の参照により保留', HELD_TIME_UNVERIFIED: '過去の削除時刻が未確認',
  HELD_IMAGE: '原本画像を保持・再確認待ち', RETRY_WAIT: '次回以降に再試行', STOPPED: '自動処理停止',
  ELIGIBLE: '削除候補', STARTED: '開始済み・成否の照合待ち', RESERVED: '処理枠予約済み',
  FAILED: '失敗', INTEGRITY: '整合性異常', TRANSIENT: '一時障害',
}
const display = (value: string | number | null | undefined) => value == null ? '—' : labels[String(value)] ?? String(value)
function utc(value: string | number | null | undefined) {
  if (typeof value !== 'string') return '—'
  const parsed = new Date(value.endsWith('Z') || /[+-]\d\d:\d\d$/.test(value) ? value : `${value.replace(' ', 'T')}Z`)
  return Number.isNaN(parsed.getTime()) ? '—' : parsed.toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' })
}

export function TopicCleanupPage() {
  const { state } = useAuth()
  const [runs, setRuns] = useState<CleanupRow[]>([])
  const [targets, setTargets] = useState<CleanupRow[]>([])
  const [history, setHistory] = useState<CleanupRow[]>([])
  const [historyPage, setHistoryPage] = useState(0)
  const [preview, setPreview] = useState<CleanupPreview[] | null>(null)
  const [page, setPage] = useState(0)
  const [runPage, setRunPage] = useState(0)
  const [reason, setReason] = useState('')
  const [selected, setSelected] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [revision, setRevision] = useState(0)
  const allowed = state.status === 'authenticated' && state.user.role === 'ROLE_ADMIN'

  useEffect(() => {
    if (!allowed) return
    const controller = new AbortController()
    Promise.all([cleanupApi.runs(runPage, controller.signal), cleanupApi.targets(page, controller.signal)])
      .then(([r, t]) => { setRuns(r); setTargets(t); setError('') })
      .catch(e => { if (!controller.signal.aborted) setError(toUserFacingError(e).message) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [allowed, page, runPage, revision])

  async function action(work: () => Promise<void>) {
    if (busy) return
    setBusy(true); setError(''); setMessage('')
    try { await work() } catch (e) { setError(toUserFacingError(e).message) }
    finally { setBusy(false) }
  }
  if (state.status === 'loading') return <LoadingState />
  if (state.status === 'anonymous') return <UnauthenticatedState />
  if (!allowed) return <p role="alert">この画面は管理者のみ利用できます。認証状態を確認してください。</p>
  return <section aria-labelledby="cleanup-title" className="space-y-8">
    <h1 id="cleanup-title" className="text-3xl font-bold">クリーンアップ管理</h1>
    <p>定期実行は毎日04:00（日本時間）。停止解除から即時削除は行いません。時刻が確認できない過去のお題は保留します。</p>
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {message && <p role="status">{message}</p>}
    {loading && <LoadingState />}
    <button type="button" disabled={busy || loading} onClick={() => { setLoading(true); setRevision(x => x + 1) }}>最新の状態を取得</button>
    <section aria-labelledby="cleanup-runs"><h2 id="cleanup-runs" className="text-xl font-bold">実行履歴（最新順）</h2>
      {runs.length === 0 && !loading && <p>実行履歴はありません。</p>}
      <div className="overflow-x-auto"><table className="w-full text-left"><caption className="sr-only">日次処理の結果</caption>
        <thead><tr>{['日付', '結果', '処理枠', '成功', '失敗', '保留', '終了（JST）'].map(x => <th key={x} scope="col" className="p-2">{x}</th>)}</tr></thead>
        <tbody>{runs.map(r => <tr key={String(r.id)}>{[r.scheduled_date, display(r.status), r.processed_count, r.success_count, r.failure_count, r.held_count, utc(r.finished_at)].map((x, i) => <td key={i} className="p-2">{display(x)}</td>)}</tr>)}</tbody>
      </table></div>
      <button disabled={runPage === 0 || loading} onClick={() => { setLoading(true); setRunPage(x => x - 1) }}>前の実行履歴</button>{' '}
      <button disabled={runs.length < 20 || loading} onClick={() => { setLoading(true); setRunPage(x => x + 1) }}>次の実行履歴</button>
    </section>
    <section aria-labelledby="cleanup-targets"><h2 id="cleanup-targets" className="text-xl font-bold">対象・保留・再試行・停止</h2>
      {targets.length === 0 && !loading && <p>記録された対象はありません。候補確認はdry-runから行えます。</p>}
      <ul className="space-y-4">{targets.map(t => <li className="rounded border border-slate-300 p-4" key={String(t.original_topic_id)}>
        <p>お題ID {t.original_topic_id}：{display(t.state)}／{display(t.reason_code)}</p>
        <p>サイクル {t.cycle_no}・失敗 {t.failure_count}回・最終確認 {utc(t.last_checked_at)}（JST）</p>
        <p>{typeof t.elapsed_hours === 'number' ? `削除から約${Math.floor(t.elapsed_hours / 24)}日・候補化から約${Math.floor(Number(t.held_hours) / 24)}日` : '削除時刻の基準は未確認、または元データ削除済み'}</p>
        <button disabled={busy} onClick={() => action(async () => { setHistory(await cleanupApi.attempts(Number(t.original_topic_id))); setHistoryPage(0); setSelected(Number(t.original_topic_id)); setReason('') })}>詳細履歴・停止解除</button>
      </li>)}</ul>
      <button disabled={page === 0 || loading} onClick={() => { setLoading(true); setPage(x => x - 1) }}>前の対象</button>{' '}
      <button disabled={targets.length < 20 || loading} onClick={() => { setLoading(true); setPage(x => x + 1) }}>次の対象</button>
    </section>
    {selected !== null && <section aria-labelledby="cleanup-detail"><h2 id="cleanup-detail" className="text-xl font-bold">お題ID {selected} の詳細</h2>
      <ul>{history.map(a => <li key={String(a.id)}>サイクル {a.cycle_no}：{display(a.outcome)}／{display(a.reason_code)}／{display(a.error_type)}（{utc(a.finished_at)} JST）</li>)}</ul>
      <button disabled={busy || historyPage === 0} onClick={() => action(async () => { setHistory(await cleanupApi.attempts(selected, historyPage - 1)); setHistoryPage(x => x - 1) })}>前の詳細履歴</button>{' '}
      <button disabled={busy || history.length < 20} onClick={() => action(async () => { setHistory(await cleanupApi.attempts(selected, historyPage + 1)); setHistoryPage(x => x + 1) })}>次の詳細履歴</button>
      {targets.some(t => Number(t.original_topic_id) === selected && t.state === 'STOPPED') && <form className="mt-4 space-y-2" onSubmit={e => { e.preventDefault(); action(async () => {
        await cleanupApi.resume(selected, reason); setReason(''); setMessage('停止を解除しました。次回以降の定期実行で再判定します。'); setRevision(x => x + 1)
      }) }}>
        <label htmlFor="cleanup-resume-reason" className="block">原因確認・修正内容と解除理由（必須・500文字以内）</label>
        <textarea className="block w-full rounded border p-2" id="cleanup-resume-reason" value={reason} onChange={e => setReason(e.target.value)} required maxLength={500} disabled={busy} />
        <button type="submit" disabled={busy || !reason.trim()}>停止を解除する</button>
      </form>}
    </section>}
    <section aria-labelledby="cleanup-preview"><h2 id="cleanup-preview" className="text-xl font-bold">dry-run（保存・削除なし）</h2>
      <button disabled={busy} onClick={() => action(async () => { setPreview(await cleanupApi.preview()) })}>候補と保留理由を確認</button>
      {preview && <><p>今回の表示：{preview.length}件（最大100件）。定期処理時に条件を再確認します。</p>
        <ul>{preview.map(p => <li key={p.topicId}>お題ID {p.topicId}：{display(p.reason)}／{p.elapsedHours === null ? '経過時間未確認' : `削除から${p.elapsedHours}時間`}</li>)}</ul></>}
    </section>
  </section>
}
