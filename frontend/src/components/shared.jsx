import L from 'leaflet'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Bike, Car, CheckCircle2, Droplets, Footprints, Landmark, Users } from 'lucide-react'
import { api, photoUrl } from '../api'
import { useAuth } from '../auth'
import { useLang, errorText } from '../i18n'

export const SEV_COLOR = {
  LOW: 'var(--sev-low)',
  MODERATE: 'var(--sev-mod)',
  HIGH: 'var(--sev-high)',
  SEVERE: 'var(--sev-severe)'
}

// Raw hex for Leaflet (CSS vars do not reach Leaflet's SVG options)
export const SEV_HEX = { LOW: '#0891B2', MODERATE: '#F59E0B', HIGH: '#EA580C', SEVERE: '#DC2626' }

const iconCache = {}
/** Round marker coloured by severity; severe ones pulse. */
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
  return <span className={`pill sev-${(sev || 'low').toLowerCase()}`}>{t('sev_' + sev)}</span>
}

export function SafetyChips({ r }) {
  const { t } = useLang()
  const chip = (label, ok, Icon) => (
    <span className={'chip ' + (ok ? 'ok' : 'bad')}>
      <Icon size={14} aria-hidden /> {label} · {ok ? t('safe') : t('unsafe')}
    </span>
  )
  return (
    <div className="chips">
      {chip(t('walk'), r.walkSafe, Footprints)}
      {chip(t('bike'), r.bikeSafe, Bike)}
      {chip(t('car'), r.carSafe, Car)}
    </div>
  )
}

/** Water-depth gauge with bike / car / walking reference lines. */
export function DepthGauge({ ft }) {
  const max = 3
  const pct = Math.min(100, (ft / max) * 100)
  const mark = (v) => ({ bottom: (v / max) * 100 + '%' })
  return (
    <div className="gauge" title={`${ft} ft`} aria-hidden>
      <div className="gauge-water" style={{ height: pct + '%' }} />
      <div className="gauge-mark" style={mark(0.5)} />
      <div className="gauge-mark" style={mark(0.8)} />
      <div className="gauge-mark" style={mark(1.5)} />
    </div>
  )
}

/** Full report card used in map popups, lists and the result screen. */
export function ReportCard({ r, onChange, compact = false }) {
  const { t, lang } = useLang()
  const { user } = useAuth()
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')

  const act = async (fn) => {
    setBusy(true)
    setMsg('')
    try {
      const updated = await fn(r.id)
      setMsg(fn === api.clear && updated.status === 'ACTIVE'
        ? t('clearVoteSaved').replace('{n}', updated.clearVotes).replace('{m}', updated.clearVotesNeeded)
        : t('thanks'))
      onChange && onChange(updated)
    } catch (e) {
      setMsg(errorText(e, t))
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
            {r.demo && <span className="tag">{t('demoTag')}</span>}
          </div>
        </div>
        <SeverityPill sev={r.severity} />
      </div>
      <div className="report-depth">
        <DepthGauge ft={r.depthFt} />
        <div>
          <div className="depth-num">≈{r.depthFt.toFixed(1)} <small>{t('ft')}</small></div>
          <div className="muted small">{t('estDepth')}{src ? ` · ${src}` : ''}{r.aiConfidence != null && ` · ${Math.round(r.aiConfidence * 100)}%`}</div>
          {r.confirmations > 0 && <div className="muted small"><Users size={12} aria-hidden /> {t('confirmedBy')} {r.confirmations} {t('people')}</div>}
        </div>
      </div>
      {summary && <p className="ai-summary">“{summary}”</p>}
      <SafetyChips r={r} />
      <p className="disclaimer">{t('estimateNote')}</p>
      {r.note && !r.demo && <p className="small">{r.note}</p>}
      {r.authorityNote && <p className="small authority-note"><Landmark size={14} aria-hidden /> {r.authorityNote}</p>}
      {r.status === 'ACTIVE' && r.clearVotes > 0 && (
        <div className="clear-progress" aria-label={t('clearProgress')}>
          <div className="row between small"><span>{t('clearProgress')}</span><b>{r.clearVotes}/{r.clearVotesNeeded || 5} · {Math.min(100, Math.round((r.clearVotes / (r.clearVotesNeeded || 5)) * 100))}%</b></div>
          <div className="clear-bar"><span style={{ width: Math.min(100, (r.clearVotes / (r.clearVotesNeeded || 5)) * 100) + '%' }} /></div>
        </div>
      )}
      {onChange && r.status === 'ACTIVE' && (
        user ? (
          <div className="row gap">
            <button className="btn small secondary" disabled={busy} onClick={() => act(api.confirm)}><Droplets size={14} aria-hidden /> {t('stillFlooded')}</button>
            <button className="btn small ghost" disabled={busy} onClick={() => act(api.clear)}><CheckCircle2 size={14} aria-hidden /> {t('cleared')}</button>
          </div>
        ) : (
          <Link to="/login" className="small">{t('signIn')} → {t('stillFlooded')} / {t('cleared')}</Link>
        )
      )}
      {msg && <div className="small muted">{msg}</div>}
    </div>
  )
}
