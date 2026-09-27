import { useEffect, useState } from 'react'
import { getGhostSummary } from '../routes/getRoutes'

export function GhostSummary() {
  const [summary, setSummary] = useState<{ count: number } | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    let timer: number | undefined
    async function refresh() {
      try {
        const data = await getGhostSummary(controller.signal)
        if (controller.signal.aborted) return
        setSummary(data)
        setError(false)
      } catch {
        if (!controller.signal.aborted) setError(true)
      } finally {
        if (!controller.signal.aborted) timer = window.setTimeout(refresh, 60_000)
      }
    }
    void refresh()
    return () => { controller.abort(); window.clearTimeout(timer) }
  }, [])

  return (
    <aside className="ghost-summary" aria-label="Estimated missed arrivals">
      <p className="ghost-summary-label">Estimated missed arrivals</p>
      <div role="status">
        <strong>{summary ? summary.count.toLocaleString() : '—'}</strong>
        <p>{summary ? 'Recorded in the last hour' : error ? 'Count unavailable' : 'Loading count…'}</p>
      </div>
      <small>Missed stop records, not unique buses.</small>
      {error && summary && <small>Update failed · showing last count</small>}
    </aside>
  )
}
