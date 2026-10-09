import L from 'leaflet'
import { useState } from 'react'
import { api, photoUrl } from '../api'
import { useLang } from '../i18n'

export const SEV_COLOR = {
  LOW: 'var(--sev-low)',
  MODERATE: 'var(--sev-mod)',
  HIGH: 'var(--sev-high)',
  SEVERE: 'var(--sev-severe)'
}

// Raw hex for Leaflet (CSS vars don't work inside SVG path options everywhere)
export const SEV_HEX = { LOW: '#3c9d6b', MODERATE: '#d99a1e', HIGH: '#e0612b', SEVERE: '#c2273a' }

const iconCache = {}
/** Round pulsing marker coloured by severity. */
export function severityIcon(sev, pulse = false) {
  const key = sev + pulse
  if (!iconCache[key]) {
    iconCache[key] = L.divIcon({
      className: 'jm-marker-wrap',
      html: `<span class="jm-marker ${pulse ? 'pulse' : ''}" style="--c:${SEV_HEX[sev] || '#555'}"></span>`,
      iconSize: [22, 22],
      iconAnchor: [11, 11],
      popupAnchor: [0, -10]
    })
  }
  return iconCache[key]
}

export const youIcon = L.divIcon({
  className: 'jm-marker-wrap',
  html: '<span class="jm-you"></span>',
  iconSize: [18, 18],
  iconAnchor: [9, 9]
})

export function timeAgo(iso, lang) {
  const s = Math.max(1, Math.round((Date.now() - new Date(iso).getTime()) / 1000))
  const units = lang === 'hi'
    ? [[86400, 'दिन'], [3600, 'घंटे'], [60, 'मिनट'], [1, 'सेकंड']]
    : [[86400, 'd'], [3600, 'h'], [60, 'min'], [1, 's']]
  for (const [sec, label] of units) {
    if (s >= sec) return `${Math.floor(s / sec)} ${label}`
  }
  return ''
}

export function SeverityPill({ sev }) {
  const { t } = useLang()
  return (
    <span className="pill" style={{ background: SEV_COLOR[sev] }}>
      {t('sev_' + sev)}
    </span>
  )
}

export function SafetyChips({ r }) {
  const { t } = useLang()
  const chip = (label, ok, icon) => (
    <span className={'chip ' + (ok ? 'ok' : 'bad')}>
      <span aria-hidden>{icon}</span> {label} · {ok ? t('safe') : t('unsafe')}
    </span>
  )
  return (
    <div className="chips">
      {chip(t('walk'), r.walkSafe, '🚶')}
      {chip(t('bike'), r.bikeSafe, '🛵')}
      {chip(t('car'), r.carSafe, '🚗')}
    </div>
  )
}

/** Water-depth gauge: a little ruler next to a bike wheel for scale. */
export function DepthGauge({ ft }) {
  const max = 3
  const pct = Math.min(100, (ft / max) * 100)
  return (
    <div className="gauge" title={`${ft} ft`}>
      <div className="gauge-water" style={{ height: pct + '%' }} />
      <div className="gauge-mark" style={{ bottom: (0.5 / max) * 100 + '%' }}><span>🛵</span></div>
      <div className="gauge-mark" style={{ bottom: (0.8 / max) * 100 + '%' }}><span>🚗</span></div>
      <div className="gauge-mark" style={{ bottom: (1.5 / max) * 100 + '%' }}><span>🚶</span></div>
    </div>
  )
}

/** Full report card used in map popups, lists and the report result screen. */
export function ReportCard({ r, onChange, compact = false }) {
  const { t, lang } = useLang()
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')

  const act = async (fn) => {
    setBusy(true)
    try {
      const updated = await fn(r.id)
      setMsg(t('thanks'))
      onChange && onChange(updated)
    } catch (e) {
      setMsg(e.message)
    } finally {
      setBusy(false)
    }
  }

  const src = { AI: t('aiEstimate'), CITIZEN: t('citizenEstimate'), AI_AND_CITIZEN: t('bothEstimate') }[r.depthSource]
  const summary = lang === 'hi' && r.aiSummaryHi ? r.aiSummaryHi : r.aiSummary

  return (
    <div className={'report-card' + (compact ? ' compact' : '')}>
      {r.photoUrl && !compact && <img className="report-photo" src={photoUrl(r.photoUrl)} alt="" loading="lazy" />}
      <div className="report-head">
        <div>
          <div className="report-place">{r.locationName}</div>
          <div className="muted small">
            {r.area ? r.area + ' · ' : ''}{t('reported')} {timeAgo(r.createdAt, lang)} {t('ago')}
            {r.confirmations > 0 && <> · {t('confirmedBy')} {r.confirmations} {t('people')}</>}
            {r.demo && <span className="tag">{t('demoTag')}</span>}
          </div>
        </div>
        <SeverityPill sev={r.severity} />
      </div>
      <div className="report-depth">
        <DepthGauge ft={r.depthFt} />
        <div>
          <div className="depth-num">{r.depthFt.toFixed(1)} <small>{t('ft')}</small></div>
          {src && <div className="muted small">{src}{r.aiConfidence != null && ` · ${Math.round(r.aiConfidence * 100)}%`}</div>}
          {summary && <p className="ai-summary">“{summary}”</p>}
        </div>
      </div>
      <SafetyChips r={r} />
      {r.note && !r.demo && <p className="small">{r.note}</p>}
      {onChange && r.status === 'ACTIVE' && (
        <div className="row gap">
          <button className="btn small" disabled={busy} onClick={() => act(api.confirm)}>💧 {t('stillFlooded')}</button>
          <button className="btn small ghost" disabled={busy} onClick={() => act(api.clear)}>✅ {t('cleared')}</button>
        </div>
      )}
      {msg && <div className="small muted">{msg}</div>}
    </div>
  )
}
