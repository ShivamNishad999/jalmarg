import { useEffect, useMemo, useState } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Circle, useMap } from 'react-leaflet'
import { Link } from 'react-router-dom'
import { Camera, ChevronDown, ChevronUp, CloudRain, Crosshair, Info, RefreshCw } from 'lucide-react'
import { api, getPosition, NCR_CENTER, TILE_URL, TILE_ATTRIBUTION } from '../api'
import { useLang, errorText } from '../i18n'
import { ReportCard, severityIcon, youIcon, SEV_HEX, timeAgo } from '../components/shared'

const SEV_ORDER = { LOW: 0, MODERATE: 1, HIGH: 2, SEVERE: 3 }

function FlyTo({ pos, zoom = 13 }) {
  const map = useMap()
  useEffect(() => {
    if (pos) map.flyTo([pos.lat, pos.lng], zoom, { duration: 0.8 })
  }, [pos, map, zoom])
  return null
}

export default function MapPage() {
  const { t, lang } = useLang()
  const [reports, setReports] = useState(null) // null = loading
  const [me, setMe] = useState(null)
  const [locError, setLocError] = useState(false)
  const [feed, setFeed] = useState(null)
  const [error, setError] = useState(null)
  const [sheetOpen, setSheetOpen] = useState(true)
  const [minSev, setMinSev] = useState('LOW')   // filter: severity at least
  const [maxAgeH, setMaxAgeH] = useState(0)     // filter: 0 = any age, else hours

  const load = () =>
    api.activeReports()
      .then((r) => { setReports(Array.isArray(r) ? r : []); setError(null) })
      .catch((e) => { setError(e); setReports((old) => old || []) })

  const locate = () => {
    setLocError(false)
    getPosition().then(setMe).catch(() => setLocError(true))
  }

  useEffect(() => {
    load()
    const id = setInterval(load, 60_000) // live map refreshes every minute
    locate()
    return () => clearInterval(id)
  }, []) // eslint-disable-line

  useEffect(() => {
    if (!me) return
    api.feed(me.lat, me.lng, lang).then((f) => setFeed(f && Array.isArray(f.alerts) ? f : null)).catch(() => {})
  }, [me, lang, reports?.length])

  const replace = (u) => setReports((rs) => (rs || []).map((r) => (r.id === u.id ? u : r)).filter((r) => r.status === 'ACTIVE'))

  const shown = useMemo(() => (reports || []).filter((r) =>
    SEV_ORDER[r.severity] >= SEV_ORDER[minSev] &&
    (!maxAgeH || Date.now() - new Date(r.lastConfirmedAt || r.createdAt).getTime() <= maxAgeH * 3600_000)
  ), [reports, minSev, maxAgeH])

  const nearby = feed ? feed.alerts.filter((a) => a.reportId) : []
  const rainBanner = feed && (feed.rainLevel === 'HEAVY' || feed.rainLevel === 'VERY_HEAVY')

  return (
    <div className="map-page">
      <MapContainer center={NCR_CENTER} zoom={11} className="map-full" zoomControl={false}>
        <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
        <FlyTo pos={me} />
        {me && (
          <>
            <Marker position={[me.lat, me.lng]} icon={youIcon} />
            <Circle center={[me.lat, me.lng]} radius={3000} pathOptions={{ color: '#0EA5E9', weight: 1, fillOpacity: 0.04, dashArray: '4 6' }} />
          </>
        )}
        {shown.map((r) => (
          <Circle key={'c' + r.id} center={[r.latitude, r.longitude]} radius={60 + r.depthFt * 60}
            pathOptions={{ color: SEV_HEX[r.severity], weight: 0, fillOpacity: 0.16 }} />
        ))}
        {shown.map((r) => (
          <Marker key={r.id} position={[r.latitude, r.longitude]} icon={severityIcon(r.severity, r.severity === 'SEVERE')}>
            <Popup maxWidth={300} minWidth={260}>
              <ReportCard r={r} onChange={replace} />
            </Popup>
          </Marker>
        ))}
      </MapContainer>

      <div className="map-overlay-top">
        {rainBanner && (
          <div className="banner rain"><CloudRain size={16} aria-hidden /> {t('rain_' + feed.rainLevel)} · ~{Math.round(feed.rainNext6hMm)} mm</div>
        )}
        <div className="map-toolbar">
          <div className="stat-chip">
            <span className="dot" style={{ background: SEV_HEX.SEVERE }} />
            {reports === null ? t('loading') : <><b>{shown.length}</b>&nbsp;{t('liveNow')}</>}
          </div>
          <select className="chip-select" value={minSev} onChange={(e) => setMinSev(e.target.value)} aria-label="Severity filter">
            <option value="LOW">{t('allSev')}</option>
            <option value="MODERATE">{t('sev_MODERATE')}+</option>
            <option value="HIGH">{t('sev_HIGH')}+</option>
          </select>
          <select className="chip-select" value={maxAgeH} onChange={(e) => setMaxAgeH(+e.target.value)} aria-label="Age filter">
            <option value={0}>{t('anyTime')}</option>
            <option value={1}>{t('lastHour')}</option>
            <option value={3}>{t('last3h')}</option>
          </select>
        </div>
        {error && (
          <Link to="/settings#server" className="banner err">
            {errorText(error, t)} <RefreshCw size={14} aria-hidden />
          </Link>
        )}
      </div>

      <div className="map-legend" aria-label="Legend">
        {['SEVERE', 'HIGH', 'MODERATE', 'LOW'].map((s) => (
          <span key={s}><i style={{ background: SEV_HEX[s] }} />{t('sev_' + s)}</span>
        ))}
      </div>

      <button className="locate-btn" onClick={locate} aria-label={t('useMyLocation')}><Crosshair size={20} /></button>

      <section className={'sheet' + (sheetOpen ? ' open' : '')}>
        <button className="sheet-handle" onClick={() => setSheetOpen(!sheetOpen)} aria-label="Toggle list">
          <span />
        </button>
        <div className="sheet-head">
          <h2>{t('nearYou')}</h2>
          <button className="icon-btn" onClick={() => setSheetOpen(!sheetOpen)} aria-hidden tabIndex={-1}>
            {sheetOpen ? <ChevronDown size={18} /> : <ChevronUp size={18} />}
          </button>
        </div>
        {!me && !locError && <p className="muted small">{t('locating')}</p>}
        {locError && <p className="muted small">{t('locationFallback')}</p>}
        {me && feed && nearby.length === 0 && <p className="muted small"><Info size={14} aria-hidden /> {t('noAlerts')}</p>}
        <ul className="alert-list">
          {nearby.map((a) => {
            const r = (reports || []).find((x) => x.id === a.reportId)
            return (
              <li key={a.reportId} className="alert-item" style={{ borderColor: SEV_HEX[a.level] }}>
                <b>{a.title}</b>
                <div className="small">{a.message}</div>
                {r && <div className="small muted">{t('reported')} {timeAgo(r.createdAt, lang)} {t('ago')}{r.demo ? ` · ${t('demoTag')}` : ''}</div>}
              </li>
            )
          })}
        </ul>
      </section>

      <Link to="/report" className="fab">
        <Camera size={20} aria-hidden /> {t('reportCta')}
      </Link>
    </div>
  )
}
