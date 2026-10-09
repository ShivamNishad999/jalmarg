import L from 'leaflet'
import { useState, useRef, useEffect } from 'react'
import AiAssessmentCard from './AiAssessmentCard'
import { validDepth, confirmationCount, lifecycleKey, formatTimestamp } from '../utils/evidence'
import { api, photoUrl } from '../api'
import { useLang } from '../i18n'

export const SEV_COLOR = {
  LOW: 'var(--sev-low)',
  MODERATE: 'var(--sev-mod)',
  HIGH: 'var(--sev-high)',
  SEVERE: 'var(--sev-severe)'
}

// Raw hex for Leaflet SVG pathOptions & marker divs
export const SEV_HEX = {
  LOW: '#16A34A',
  MODERATE: '#F59E0B',
  HIGH: '#EA580C',
  SEVERE: '#DC2626'
}

export const RISK_HEX = {
  LOW: '#10B981',
  MODERATE: '#F59E0B',
  HIGH: '#EA580C',
  CRITICAL: '#7C3AED',
  UNKNOWN: '#64748B'
}

const iconCache = {}
/** Round pulsing marker coloured by severity for observed ground reports. */
export function severityIcon(sev, pulse = false) {
  const key = 'sev_' + String(sev) + pulse
  if (!iconCache[key]) {
    iconCache[key] = L.divIcon({
      className: 'jm-marker-wrap',
      html: `<span class="jm-marker ${pulse ? 'pulse' : ''}" style="--c:${SEV_HEX[sev] || '#64748B'}"></span>`,
      iconSize: [24, 24],
      iconAnchor: [12, 12],
      popupAnchor: [0, -12]
    })
  }
  return iconCache[key]
}

/** Distinct diamond-shaped radar marker for autonomous predicted flood-risk hotspots. */
export function predictiveRiskIcon(level, pulse = true) {
  const key = 'pred_' + String(level) + pulse
  if (!iconCache[key]) {
    const color = RISK_HEX[level] || '#7C3AED'
    iconCache[key] = L.divIcon({
      className: 'jm-marker-wrap',
      html: `<span class="jm-predictive-marker ${pulse ? 'radar-pulse' : ''}" style="--c:${color}">
        <svg viewBox="0 0 24 24" width="22" height="22" style="transform:rotate(45deg);display:block;">
          <rect x="3" y="3" width="18" height="18" rx="4" fill="${color}" stroke="#FFFFFF" stroke-width="2.5"/>
        </svg>
      </span>`,
      iconSize: [26, 26],
      iconAnchor: [13, 13],
      popupAnchor: [0, -14]
    })
  }
  return iconCache[key]
}

export const youIcon = L.divIcon({
  className: 'jm-marker-wrap',
  html: '<span class="jm-you"></span>',
  iconSize: [20, 20],
  iconAnchor: [10, 10]
})

export const destIcon = L.divIcon({
  className: 'jm-marker-wrap',
  html: '<span class="jm-dest">🏁</span>',
  iconSize: [28, 28],
  iconAnchor: [14, 26],
  popupAnchor: [0, -26]
})

export function timeAgo(iso, lang) {
  if (!iso) return ''
  const s = Math.max(1, Math.round((Date.now() - new Date(iso).getTime()) / 1000))
  const units = lang === 'hi'
    ? [[86400, 'दिन'], [3600, 'घंटे'], [60, 'मिनट'], [1, 'सेकंड']]
    : [[86400, 'd'], [3600, 'h'], [60, 'min'], [1, 's']]
  for (const [sec, label] of units) {
    if (s >= sec) return `${Math.floor(s / sec)} ${label}`
  }
  return ''
}

/** Categorizes report freshness into clear human-readable buckets. */
export function getFreshnessCategory(iso) {
  if (!iso || !Number.isFinite(Date.parse(iso))) return 'unknown'
  const hours = (Date.now() - new Date(iso).getTime()) / (1000 * 3600)
  if (hours <= 2) return 'recent'
  if (hours <= 24) return 'today'
  return 'older'
}

export function SeverityPill({ sev }) {
  const { t } = useLang()
  return (
    <span className="pill" style={{ background: SEV_HEX[sev] || '#64748B' }}>
      {['LOW', 'MODERATE', 'HIGH', 'SEVERE'].includes(sev) ? t('sev_' + sev) : t('unknownValue')}
    </span>
  )
}

/** Risk Score Badge mapped directly from backend riskScore. */
export function RiskScoreBadge({ score }) {
  const { t } = useLang()
  if (!validDepth(score)) return <span className="tag">{t('riskUnavailable')}</span>
  let color = '#16A34A'
  let labelKey = 'riskLevel_LOW'

  if (score > 10) {
    color = '#DC2626'
    labelKey = 'riskLevel_SEVERE'
  } else if (score > 6) {
    color = '#EA580C'
    labelKey = 'riskLevel_HIGH'
  } else if (score > 2) {
    color = '#F59E0B'
    labelKey = 'riskLevel_MOD'
  }

  return (
    <span
      className="pill"
      style={{ background: color, display: 'inline-flex', alignItems: 'center', gap: 4 }}
      title={`Backend Risk Score: ${score}`}
    >
      <span>{t(labelKey)}</span>
      <small style={{ opacity: 0.9 }}>({score})</small>
    </span>
  )
}

export function SafetyChips({ r }) {
  const { t } = useLang()
  const chip = (label, ok, icon) => (
    <span className={'chip ' + (validDepth(r.depthFt) && typeof ok === 'boolean' ? (ok ? 'ok' : 'bad') : '')}>
      <span aria-hidden>{icon}</span> {label}: {!validDepth(r.depthFt) || typeof ok !== 'boolean' ? t('unknownValue') : ok ? t('safe') : t('unsafe')}
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

/** Water-depth gauge: ruler showing hazard thresholds for different vehicles. */
export function DepthGauge({ ft }) {
  const { t } = useLang()
  if (!validDepth(ft)) return <span>{t('depthUnavailable')}</span>
  const max = 3.2
  const pct = Math.min(100, Math.max(0, (ft / max) * 100))
  return (
    <div className="gauge" title={`${t('reportedDepth')}: ${ft.toFixed(1)} ${t('ft')}`} aria-label={`${t('reportedDepth')}: ${ft.toFixed(1)} ${t('ft')}`}>
      <div className="gauge-water" style={{ height: pct + '%' }} />
      <div className="gauge-mark" style={{ bottom: (0.5 / max) * 100 + '%' }}><span>🛵</span></div>
      <div className="gauge-mark" style={{ bottom: (0.8 / max) * 100 + '%' }}><span>🚗</span></div>
      <div className="gauge-mark" style={{ bottom: (1.5 / max) * 100 + '%' }}><span>🚶</span></div>
    </div>
  )
}

/** Report verification & status indicators based on actual backend data. */
export function VerificationBadges({ r }) {
  const { t } = useLang()
  if (!r) return null

  const count = confirmationCount(r)
  return <div className="row wrap gap">
    <span className="tag">{t(lifecycleKey(r.status))}</span>
    <span className="tag">{count === null ? t('unknownValue') : t('confirmationEvents', { n: count })}</span>
  </div>
}

/** Only submission time and current lifecycle are known; no inferred history or AI completion. */
export function ReportStatusTimeline({ r }) {
  const { t, lang } = useLang()
  if (!r) return null
  return <div className="timeline-container" aria-label={t('timelineTitle')}>
    <p>{t('submittedAt')}: {formatTimestamp(r.createdAt, lang, t('unknownValue'))}</p>
    <p>{t('lifecycle')}: <b>{t(lifecycleKey(r.status))}</b></p>
  </div>
}

/** Full report card used in map popups, lists, and the report result screen. */
export function ReportCard({ r, onChange, compact = false, showTimeline = false }) {
  const { t, lang } = useLang()
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')
  const [failed, setFailed] = useState(false)
  const actionLock = useRef(false)
  const [photoFailed, setPhotoFailed] = useState(false)
  useEffect(() => setPhotoFailed(false), [r?.photoUrl])

  if (!r) return null

  const act = async (fn) => {
    if (actionLock.current) return
    actionLock.current = true
    setBusy(true)
    setFailed(false)
    try {
      const updated = await fn(r.id)
      setMsg(t('thanks'))
      onChange && onChange(updated)
    } catch (e) {
      setFailed(true)
      setMsg(t('actionFailed'))
    } finally {
      actionLock.current = false
      setBusy(false)
    }
  }

  const freshness = getFreshnessCategory(r.createdAt)
  const freshnessLabel = freshness === 'recent'
    ? t('freshnessRecent')
    : freshness === 'today'
    ? t('freshnessToday')
    : freshness === 'older' ? t('freshnessOlder') : t('unknownValue')

  return (
    <div className={'report-card' + (compact ? ' compact' : '')}>
      {r.photoUrl && !compact && !photoFailed && (
        <img
          className="report-photo"
          src={photoUrl(r.photoUrl)}
          alt={r.locationName || t('photoAlt')}
          loading="lazy"
          onError={() => setPhotoFailed(true)}
        />
      )}
      {photoFailed && <p role="status">{t('photoUnavailable')}</p>}
      <div className="report-head">
        <div>
          <div className="report-place">{r.locationName || t('reportedLocation')}</div>
          <div className="muted small">
            {r.area ? `${r.area} · ` : ''}{t('reported')} {timeAgo(r.createdAt, lang)} {t('ago')}
            <span className="freshness-chip" style={{ marginLeft: 6 }}>
              ⏱️ {freshnessLabel}
            </span>
          </div>
        </div>
        <SeverityPill sev={r.severity} />
      </div>

      <VerificationBadges r={r} />

      <div className="report-depth">
        {validDepth(r.depthFt) && <DepthGauge ft={r.depthFt} />}
        <AiAssessmentCard report={r} />
      </div>

      <SafetyChips r={r} />

      {showTimeline && <ReportStatusTimeline r={r} />}

      {r.note && !r.demo && <p className="small muted">📝 {r.note}</p>}

      {onChange && r.status === 'ACTIVE' && (
        <div className="row gap" style={{ marginTop: '4px' }}>
          <button className="btn small" disabled={busy} onClick={() => act(api.confirm)}>
            💧 {t('stillFlooded')}
          </button>
          <button className="btn small ghost" disabled={busy} onClick={() => act(api.clear)}>
            ✅ {t('cleared')}
          </button>
        </div>
      )}
      {msg && <div role={failed ? "alert" : "status"} className={failed ? "banner err" : "small muted"}>{msg}</div>}
    </div>
  )
}

/** Card for autonomous predicted flood risk hotspots in map popups and lists. */
export function PredictedRiskCard({ ra, compact = false }) {
  const { t, lang } = useLang()
  if (!ra) return null

  let factors = null
  try {
    if (ra.factorBreakdown) factors = JSON.parse(ra.factorBreakdown)
  } catch {}

  const summary = lang === 'hi' && ra.summaryHi ? ra.summaryHi : (ra.summaryEn || '')
  const color = RISK_HEX[ra.riskLevel] || '#7C3AED'
  const isMissingTelemetry = ra.dataQuality === 'MISSING_TELEMETRY'

  return (
    <div className={'report-card predicted-card' + (compact ? ' compact' : '')} style={{ borderLeft: `4px solid ${color}` }}>
      <div className="report-head">
        <div>
          <div className="report-place">🔮 {ra.hotspotName || 'Forecast Risk Hotspot'}</div>
          <div className="muted small">
            {ra.area ? `${ra.area} · ` : ''}{t('assessedAt')}: {timeAgo(ra.assessedAt, lang)} {t('ago')}
          </div>
        </div>
        <span className="pill" style={{ background: color, color: '#FFFFFF', fontWeight: 700 }}>
          {t('riskLevel_' + (ra.riskLevel || 'UNKNOWN'))}
        </span>
      </div>

      <div className="row wrap gap" style={{ margin: '6px 0' }}>
        <span className="pill" style={{ background: '#1E293B', color: '#F8FAFC', fontSize: '0.78rem' }}>
          🎯 {t('riskScore')}: <b>{ra.riskScore ?? 0}/100</b>
        </span>
        <span className={`tag ${isMissingTelemetry ? 'warn' : ''}`}>
          {isMissingTelemetry ? `⚠️ ${t('dataQualityMissing')}` : `📡 ${t('dataQualityFresh')}`}
        </span>
        {ra.historicalProneness != null && (
          <span className="tag">
            📊 {t('historicalProneness')}: <b>{ra.historicalProneness}/5</b>
          </span>
        )}
      </div>

      {summary && (
        <div className="small" style={{ margin: '6px 0', lineHeight: 1.45, color: 'var(--text)' }}>
          {summary}
        </div>
      )}

      {/* Rainfall telemetry box */}
      <div className="route-hazards-box" style={{ background: 'rgba(14, 165, 233, 0.08)', borderColor: 'rgba(14, 165, 233, 0.25)' }}>
        <div className="row between small" style={{ fontWeight: 600 }}>
          <span>🌧️ {t('rainfallForecast')}:</span>
          <b>{ra.rainfallNext6hMm?.toFixed(1) || 0} mm</b>
        </div>
        <div className="row between small muted" style={{ marginTop: 2 }}>
          <span>{t('maxHourlyRain')}:</span>
          <span>{ra.maxHourlyMm?.toFixed(1) || 0} mm/h ({ra.rainLevel || 'NONE'})</span>
        </div>
        {factors?.provenance?.elevationMeters != null && (
          <div className="row between small muted" style={{ marginTop: 4, borderTop: '1px dashed rgba(0,0,0,0.1)', paddingTop: 4 }}>
            <span>⛰️ {t('terrainElevation')}:</span>
            <span>{factors.provenance.elevationMeters.toFixed(1)}m AMSL {factors.provenance.relativeDepressionM > 0 ? `(${factors.provenance.relativeDepressionM.toFixed(1)}m dip)` : ''}</span>
          </div>
        )}
      </div>

      {/* Scientific & Operational Disclaimers */}
      <div className="small muted" style={{ fontSize: '0.72rem', marginTop: 6, lineHeight: 1.35 }}>
        ℹ️ {t('predictiveDisclaimer')}
        <br />
        📐 {t('terrainDisclaimer')}
      </div>
    </div>
  )
}

/** Weather and Terrain environmental telemetry panel. */
export function WeatherTelemetryWidget({ weather, status, onRefresh }) {
  const { t } = useLang()
  const [open, setOpen] = useState(false)
  if (!weather) return null

  const isMissing = weather.level === 'UNKNOWN' || weather.provenance?.dataQuality === 'MISSING_TELEMETRY'

  return (
    <div className="weather-telemetry-card">
      <div className="row between" style={{ cursor: 'pointer' }} onClick={() => setOpen(!open)}>
        <div className="row gap" style={{ margin: 0 }}>
          <span style={{ fontSize: '1.2rem' }}>🌧️</span>
          <div>
            <div style={{ fontWeight: 700, fontSize: '0.85rem' }}>
              {t('weatherTelemetry')}
            </div>
            <div className="small muted">
              Next 6h: <b>{weather.next6hMm?.toFixed(1) || 0} mm</b> · Peak: {weather.maxHourlyMm?.toFixed(1) || 0} mm/h ({weather.level})
            </div>
          </div>
        </div>
        <button
          type="button"
          className="btn small ghost"
          style={{ padding: '2px 8px', minHeight: 26, fontSize: '0.75rem' }}
          onClick={(e) => { e.stopPropagation(); setOpen(!open) }}
        >
          {open ? '▲' : '▼'}
        </button>
      </div>

      {open && (
        <div className="stack small" style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--line)', gap: 4 }}>
          <div className="row between">
            <span>📡 {t('telemetryStatus')}:</span>
            <span className={`tag ${isMissing ? 'warn' : ''}`}>{isMissing ? t('dataQualityMissing') : t('dataQualityFresh')}</span>
          </div>
          <div className="row between">
            <span>🌦️ Open-Meteo IFS Model:</span>
            <span>{weather.provenance?.product || 'ECMWF_IFS_OPENMETEO'}</span>
          </div>
          <div className="row between">
            <span>⛰️ Copernicus DEM GLO-30:</span>
            <span>30m DSM (WGS84 / EGM2008)</span>
          </div>
          <div className="row between">
            <span>🛰️ NASA GPM IMERG V07B:</span>
            <span>Near Real-Time Early Run</span>
          </div>
          {status && (
            <div className="row between muted" style={{ marginTop: 4 }}>
              <span>Autonomous Monitor:</span>
              <span>{status.isRunning ? 'Active' : 'Standby'} ({status.totalCycles || 0} cycles)</span>
            </div>
          )}
          <p className="muted small" style={{ margin: '6px 0 0', fontSize: '0.7rem', lineHeight: 1.3 }}>
            🛡️ Telemetry is derived directly from Open-Meteo & ESA Copernicus feeds without synthetic inflation.
          </p>
        </div>
      )}
    </div>
  )
}
