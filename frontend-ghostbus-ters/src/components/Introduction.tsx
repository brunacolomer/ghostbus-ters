import { useEffect, useRef } from 'react'
import './Introduction.css'

const storageKey = 'ghostbus-introduction-seen'
const openData = 'https://www.miamidade.gov/global/transportation/open-data-feeds.page'
const transitPlan = 'https://www.miamidade.gov/resources/transportation_publicworks/documents/2026-04-07-dtpw-25-tdp-book.pdf'

export function Introduction() {
  const dialog = useRef<HTMLDialogElement>(null)
  const about = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    // The introduction still works when browser storage is unavailable.
    try {
      if (localStorage.getItem(storageKey)) return
    } catch { /* Show it for this visit. */ }
    dialog.current?.showModal()
  }, [])

  function dismissed() {
    try { localStorage.setItem(storageKey, 'true') } catch { /* Storage is optional. */ }
    about.current?.focus()
  }

  return (
    <>
      <button ref={about} className="about-button" type="button" title="About Ghost Bus-ters" aria-label="About Ghost Bus-ters" onClick={() => dialog.current?.showModal()}>
        ?
      </button>
      <dialog ref={dialog} className="intro" role="dialog" aria-modal="true" aria-labelledby="intro-title" onClose={dismissed}>
        <button className="intro-close" type="button" aria-label="Close introduction" onClick={() => dialog.current?.close()}>×</button>
        <div className="intro-layout">
          <section className="intro-welcome" aria-labelledby="intro-title">
            <p className="intro-brand"><img src={`${import.meta.env.BASE_URL}favicon.svg`} width="48" height="48" alt="" /> Ghost Bus-ters</p>
            <h1 id="intro-title">Where’s my bus?</h1>
            <p className="intro-mission">Less uncertainty.<br />A clearer view of your journey.</p>
            <p>Miami-Dade publishes transit data. Our mission is to bring schedules and live vehicle information together, making the difference easier to understand.</p>
            <button className="intro-cta" type="button" autoFocus onClick={() => dialog.current?.close()}>Explore the map <span aria-hidden="true">→</span></button>
          </section>

          <section className="intro-explanation" aria-labelledby="intro-how">
            <h2 id="intro-how">A schedule is a plan.</h2>
            <p>Real-time data shows what’s happening now. When the two don’t line up, you’re left wondering: is my bus delayed? Is it still coming?</p>
            <dl>
              <div><dt>Scheduled</dt><dd>Where and when a bus is expected.</dd></div>
              <div><dt>Live</dt><dd>Where a vehicle is currently observed.</dd></div>
              <div><dt>Our goal</dt><dd>Make the gap easier to see, without treating missing data as a cancelled bus.</dd></div>
            </dl>
            <p className="intro-current"><strong>Explore today</strong>Browse routes and stops. Hover or tap for details. Live vehicle comparison isn’t connected yet.</p>
          </section>
        </div>
        <footer className="intro-sources" aria-label="Sources">
          <span>Built on public transit data</span>
          <a href={openData} target="_blank" rel="noopener noreferrer">Miami-Dade Open Data</a>
          <a href={transitPlan} target="_blank" rel="noopener noreferrer">Transit Development Plan</a>
          <a href="https://www.transit.dot.gov/research-innovation/marketing-information-and-avl" target="_blank" rel="noopener noreferrer">Why real-time info matters</a>
        </footer>
      </dialog>
    </>
  )
}
