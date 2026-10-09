import L from 'leaflet'
import { CloudRain, Gauge, Mountain, Radar, ShieldAlert } from 'lucide-react'
import { useLang } from '../i18n'
import { timeAgo } from './shared'

/** Colours for the forecast-risk layer (distinct from observed-report severity). */
export const RISK_HEX = { LOW: '#16A34A', MODERATE: '#F59E0B', HIGH: '#EA580C', CRITICAL: '#B91C1C', UNKNOWN: '#64748B' }

const cache = {}
/** Diamond marker so a forecast is never mistaken for a confirmed report (round marker). */
export function riskIcon(level) {
  const key = level || 'UNKNOWN'
  if (!cache[key]) {
    const c = RISK_HEX[key] || RISK_HEX.UNKNOWN
    cache[key] = L.divIcon({
      className: 'jm-marker-wrap',
      html: `<span class="jm-risk ${key === 'CRITICAL' || key === 'HIGH' ? 'pulse' : ''}" style="--c:${c}"></span>`,
      iconSize: [22, 22],
      iconAnchor: [11, 11],
      popupAnchor: [0, -12]
    })
  }
  return cache[key]
}

function factors(ra) {
  try { return ra.factorBreakdown ? JSON.parse(ra.factorBreakdown) : null } catch { return null }
}

/** Forecast risk for one place: level, score, why, and what it is not. */
export function RiskCard({ ra, title }) {
  const { t, lang } = useLang()
  if (!ra) return null
  const f = factors(ra)
  const elev = f?.provenance?.elevationMeters
  const dip = f?.provenance?.relativeDepressionM
  const summary = lang === 'hi' && ra.summaryHi ? ra.summaryHi : ra.summaryEn
  const level = ra.riskLevel || 'UNKNOWN'
  const missing = ra.dataQuality === 'MISSING_TELEMETRY'
  return (
    <div className="risk-card" style={{ '--c': RISK_HEX[level] || RISK_HEX.UNKNOWN }}>
      <div className="report-head">
        <div>
          <div className="report-place"><Radar size={14} aria-hidden /> {title || ra.hotspotName || t('forecastHere')}</div>
          <div className="muted small">
            {ra.area ? ra.area + ' · ' : ''}{t('assessedAt')} {ra.assessedAt ? timeAgo(ra.assessedAt, lang) + ' ' + t('ago') : '-'}
          </div>
        </div>
        <span className="pill risk-pill">{t('risk_' + level)}</span>
      </div>
      <div className="risk-score">
        <div className="row between small"><span><Gauge size={14} aria-hidden /> {t('riskScore')}</span><b>{ra.riskScore ?? 0}/100</b></div>
        <div className="clear-bar"><span style={{ width: Math.min(100, ra.riskScore || 0) + '%', background: 'var(--c)' }} /></div>
      </div>
      {summary && <p className="small risk-summary">{summary}</p>}
      <div className="diag">
        <div className="diag-row"><span><CloudRain size={14} aria-hidden /> {t('rainfallForecast')}</span><span>{(ra.rainfallNext6hMm ?? 0).toFixed(1)} mm · {t('peak')} {(ra.maxHourlyMm ?? 0).toFixed(1)} mm/h</span></div>
        {ra.activeReportCount > 0 && <div className="diag-row"><span>{t('liveReportsNear')}</span><span>{ra.activeReportCount}</span></div>}
        {ra.historicalProneness != null && <div className="diag-row"><span>{t('historicalProneness')}</span><span>{ra.historicalProneness}/5</span></div>}
        {typeof elev === 'number' && (
          <div className="diag-row"><span><Mountain size={14} aria-hidden /> {t('terrainElevation')}</span>
            <span>{elev.toFixed(1)} m{typeof dip === 'number' && dip > 0 ? ` · ${dip.toFixed(1)} m ${t('lowerThanAround')}` : ''}</span></div>
        )}
        <div className="diag-row"><span>{t('telemetryStatus')}</span><span className={missing ? 'muted' : 'ok-text'}>{missing ? t('dataQualityMissing') : t('dataQualityFresh')}</span></div>
      </div>
      <p className="disclaimer"><ShieldAlert size={12} aria-hidden /> {t('predictiveDisclaimer')} {typeof elev === 'number' ? t('terrainDisclaimer') : ''}</p>
    </div>
  )
}
