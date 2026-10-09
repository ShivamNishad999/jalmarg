import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Circle, useMap } from 'react-leaflet'
import { Link } from 'react-router-dom'
import { api, getPosition, NCR_CENTER, API_BASE, isNativeApp, TILE_URL, TILE_ATTRIBUTION } from '../api'
import { useLang } from '../i18n'
import { ReportCard, severityIcon, youIcon, SEV_HEX } from '../components/shared'

function FlyTo({ pos }) {
  const map = useMap()
  useEffect(() => {
    if (pos) map.flyTo([pos.lat, pos.lng], 13, { duration: 0.8 })
  }, [pos, map])
  return null
}

export default function MapPage() {
  const { t, lang } = useLang()
  const [reports, setReports] = useState([])
  const [me, setMe] = useState(null)
  const [feed, setFeed] = useState(null)
  const [error, setError] = useState('')
  const [sheetOpen, setSheetOpen] = useState(true)

  const load = () =>
    api.activeReports().then((r) => { setReports(Array.isArray(r) ? r : []); setError('') }).catch((e) => setError(e.message))

  useEffect(() => {
    load()
    const id = setInterval(load, 60_000) // refresh the live map every minute
    getPosition().then(setMe).catch(() => {})
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    if (!me) return
    api.feed(me.lat, me.lng, lang).then((f) => setFeed(f && Array.isArray(f.alerts) ? f : null)).catch(() => {})
  }, [me, lang, reports.length])

  const replace = (u) => setReports((rs) => rs.map((r) => (r.id === u.id ? u : r)).filter((r) => r.status === 'ACTIVE'))
  const severe = reports.filter((r) => r.severity === 'SEVERE' || r.severity === 'HIGH').length
  const rainBanner = feed && (feed.rainLevel === 'HEAVY' || feed.rainLevel === 'VERY_HEAVY')

  return (
    <div className="map-page">
      <MapContainer center={NCR_CENTER} zoom={11} className="map-full" zoomControl={false}>
        <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
        <FlyTo pos={me} />
        {me && (
          <>
            <Marker position={[me.lat, me.lng]} icon={youIcon} />
            <Circle center={[me.lat, me.lng]} radius={3000} pathOptions={{ color: '#0b3b5c', weight: 1, fillOpacity: 0.04, dashArray: '4 6' }} />
          </>
        )}
        {reports.map((r) => (
          <Marker key={r.id} position={[r.latitude, r.longitude]} icon={severityIcon(r.severity, r.severity === 'SEVERE')}>
            <Popup maxWidth={300} minWidth={260}>
              <ReportCard r={r} onChange={replace} />
            </Popup>
          </Marker>
        ))}
        {reports.map((r) => (
          <Circle
            key={'c' + r.id}
            center={[r.latitude, r.longitude]}
            radius={60 + r.depthFt * 60}
            pathOptions={{ color: SEV_HEX[r.severity], weight: 0, fillOpacity: 0.18 }}
          />
        ))}
      </MapContainer>

      <div className="map-overlay-top">
        {rainBanner && <div className="banner rain">🌧️ {t('rain_' + feed.rainLevel)} · ~{Math.round(feed.rainNext6hMm)} mm</div>}
        <div className="stat-chip">
          <span className="dot" style={{ background: SEV_HEX.SEVERE }} />
          <b>{reports.length}</b> {t('liveNow')}
          {severe > 0 && <span className="muted"> · {severe} 🚫🛵</span>}
        </div>
        {error && <Link to="/server" className="banner err">{error}</Link>}
        {isNativeApp && !API_BASE && (
          <Link to="/server" className="banner warn">{t('setServer')}</Link>
        )}
      </div>

      <section className={'sheet' + (sheetOpen ? ' open' : '')}>
        <button className="sheet-handle" onClick={() => setSheetOpen(!sheetOpen)} aria-label="Toggle list">
          <span />
        </button>
        <div className="sheet-head">
          <h2>{t('nearYou')}</h2>
          <Link to="/about" className="small muted">{t('nav_about')} →</Link>
        </div>
        {!me && <p className="muted small">{t('locating')}</p>}
        {me && feed && feed.alerts.filter((a) => a.reportId).length === 0 && <p className="muted">{t('noAlerts')}</p>}
        <ul className="alert-list">
          {feed && feed.alerts.filter((a) => a.reportId).map((a) => {
            const r = reports.find((x) => x.id === a.reportId)
            return (
              <li key={a.reportId} className="alert-item" style={{ borderColor: SEV_HEX[a.level] }}>
                <b>{a.title}</b>
                <div className="small">{a.message}</div>
                {r && <details><summary className="small">{t('details')}</summary><ReportCard r={r} onChange={replace} compact /></details>}
              </li>
            )
          })}
        </ul>
      </section>

      <Link to="/report" className="fab">
        <span aria-hidden>📷</span> {t('reportCta')}
      </Link>
    </div>
  )
}
