import { useEffect, useState } from 'react'
import { getGhostSummary, type Bus } from '../routes/getRoutes'

export function GhostSummary({ buses, busError }: { buses: Bus[] | null; busError: boolean }) {
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

  const hasFreshness = buses !== null && buses.every((bus) => typeof bus.observedRecently === 'boolean')
  const recent = buses?.filter((bus) => bus.observedRecently) ?? []
  const withSpeed = recent.filter((bus) => typeof bus.speed === 'number' && Number.isFinite(bus.speed) && bus.speed >= 0)
  const moving = withSpeed.filter((bus) => bus.speed! > 0).length

  return (
    <aside className="ghost-summary" aria-label="Network statistics">
      <section aria-label="Ghost buses">
        <p className="ghost-summary-label">Ghost buses</p>
        <div role="status">
          <strong>{summary ? summary.count.toLocaleString() : '—'}</strong>
          <p>{summary ? 'Since the last counter reset' : error ? 'Count unavailable' : 'Loading count…'}</p>
        </div>
        <small>Distinct trips with no visited stops recorded.</small>
        {error && summary && <small>Update failed · showing last count</small>}
      </section>
      <section className="network-buses" aria-label="Bus observations">
        <div className="network-metrics">
          <div><span>Buses with data</span><strong>{buses ? buses.length.toLocaleString() : '—'}</strong></div>
          <div><span>Seen in last 2 min</span><strong>{hasFreshness && !busError ? recent.length.toLocaleString() : '—'}</strong></div>
        </div>
        <p>{busError ? 'Bus updates unavailable' : buses === null ? 'Loading bus observations…' : !hasFreshness ? 'Movement data unavailable' : withSpeed.length || recent.length === 0 ? `${moving.toLocaleString()} reported moving` : 'Speed data unavailable'}</p>
        {hasFreshness && !busError && withSpeed.length < recent.length && <small>Speed available for {withSpeed.length} of {recent.length} recent buses.</small>}
        <small>{busError && buses ? 'Showing last received bus count.' : 'Moving = reported speed above zero in recent data.'}</small>
      </section>
    </aside>
  )
}
