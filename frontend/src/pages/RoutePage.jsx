import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, Marker, Polyline, Popup, useMap } from 'react-leaflet'
import L from 'leaflet'
import { api, getPosition, getRoutes, NCR_CENTER } from '../api'
import { useLang } from '../i18n'
import PlaceSearch from '../components/PlaceSearch'
import { severityIcon, youIcon, SEV_HEX } from '../components/shared'

// Depth (ft) above which a flooded spot blocks this way of travelling
const LIMIT = { bike: 0.5, car: 0.8, walk: 1.5 }
const destIcon = L.divIcon({ className: 'jm-marker-wrap', html: '<span class="jm-dest">🏁</span>', iconSize: [26, 26], iconAnchor: [13, 24] })

function FitBounds({ routes }) {
  const map = useMap()
  useEffect(() => {
    if (!routes.length) return
    const pts = routes.flatMap((r) => r.coords)
    map.fitBounds(L.latLngBounds(pts), { padding: [30, 30] })
  }, [routes, map])
  return null
}

/** Points ~`m` metres to the left and right of a hazard, perpendicular to the route direction. */
function detourPoints(route, hazard, metres) {
  const c = route.coords
  let k = 0, best = Infinity
  c.forEach((p, i) => {
    const d = (p[0] - hazard.latitude) ** 2 + (p[1] - hazard.longitude) ** 2
    if (d < best) { best = d; k = i }
  })
  const a = c[Math.max(0, k - 3)], b = c[Math.min(c.length - 1, k + 3)]
  const cosLat = Math.cos((hazard.latitude * Math.PI) / 180)
  const dx = (b[1] - a[1]) * cosLat, dy = b[0] - a[0]
  const len = Math.hypot(dx, dy) || 1
  const nx = -dy / len, ny = dx / len // unit normal (x = east, y = north)
  const deg = metres / 111320
  return [1, -1].map((s) => [hazard.latitude + s * ny * deg, hazard.longitude + (s * nx * deg) / cosLat])
}

export default function RoutePage() {
  const { t } = useLang()
  const [from, setFrom] = useState(null)
  const [to, setTo] = useState(null)
  const [mode, setMode] = useState('bike')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [options, setOptions] = useState([]) // [{coords, distanceKm, durationMin, via, hazards, blocking}]
  const [chosen, setChosen] = useState(0)

  const locateMe = () =>
    getPosition()
      .then((p) => setFrom({ lat: p.lat, lng: p.lng, name: t('myLocation') }))
      .catch(() => setError(t('needLocation')))

  useEffect(() => { locateMe() }, []) // eslint-disable-line

  const scoreAll = async (routes) => {
    const ev = await api.evaluateRoutes(routes.map((r) => r.coords))
    return routes.map((r, i) => {
      const hazards = ev.routes[i].hazards
      return { ...r, hazards, blocking: hazards.filter((h) => h.depthFt >= LIMIT[mode]).length, risk: ev.routes[i].riskScore }
    })
  }

  const find = async () => {
    setError('')
    if (!from || !to) return
    setBusy(true)
    setOptions([])
    try {
      const start = [from.lat, from.lng], end = [to.lat, to.lng]
      let routes = await scoreAll(await getRoutes([start, end], mode))
      const fastest = routes[0]

      // If every route crosses water that blocks this vehicle, try going around the worst spot.
      if (routes.every((r) => r.blocking > 0)) {
        const hz = [...fastest.hazards].sort((a, b) => b.depthFt - a.depthFt)[0]
        const vias = [...detourPoints(fastest, hz, 700), ...detourPoints(fastest, hz, 1500)]
        const extra = []
        for (const v of vias) {
          try {
            const [r] = await getRoutes([start, v, end], mode, false)
            if (r.durationMin < fastest.durationMin * 2.2) extra.push({ ...r, via: v })
          } catch { /* ignore one failed detour */ }
        }
        if (extra.length) routes = routes.concat(await scoreAll(extra))
      }

      routes.forEach((r, i) => { r.isFastest = i === 0 })
      routes.sort((a, b) => a.blocking - b.blocking || a.risk - b.risk || a.durationMin - b.durationMin)
      setOptions(routes.slice(0, 4))
      setChosen(0)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const best = options[chosen]
  const mapsLink = best && from && to
    ? `https://www.google.com/maps/dir/?api=1&origin=${from.lat},${from.lng}&destination=${to.lat},${to.lng}` +
      (best.via ? `&waypoints=${best.via[0].toFixed(5)},${best.via[1].toFixed(5)}` : '') +
      `&travelmode=${mode === 'walk' ? 'walking' : 'driving'}`
    : null

  return (
    <div className="split">
      <aside className="panel">
        <h1>{t('routeTitle')}</h1>
        <label className="label">{t('from')}</label>
        <PlaceSearch value={from} onSelect={setFrom} placeholder={t('searchPlace')}
          extraOption={{ label: t('myLocation'), onClick: locateMe }} />
        <label className="label">{t('to')}</label>
        <PlaceSearch value={to} onSelect={setTo} placeholder={t('searchPlace')} />

        <label className="label">{t('travelBy')}</label>
        <div className="seg">
          {[['bike', '🛵'], ['car', '🚗'], ['walk', '🚶']].map(([m, i]) => (
            <button key={m} className={mode === m ? 'on' : ''} onClick={() => setMode(m)}>{i} {t(m)}</button>
          ))}
        </div>

        <button className="btn primary block" disabled={!from || !to || busy} onClick={find}>
          {busy ? <><span className="spinner" /> {t('finding')}</> : t('findRoute')}
        </button>
        {error && <div className="banner err">{error}</div>}

        {options.length > 0 && options[0].blocking > 0 && <div className="banner warn">⚠️ {t('noSafe')}</div>}

        <ul className="route-list">
          {options.map((r, i) => (
            <li key={i}>
              <button className={'route-opt' + (i === chosen ? ' on' : '')} onClick={() => setChosen(i)}>
                <div className="row between">
                  <b>{Math.round(r.durationMin)} {t('min')} · {r.distanceKm.toFixed(1)} {t('km')}</b>
                  <span>
                    {i === 0 && <span className="pill" style={{ background: 'var(--ok)' }}>{t('recommended')}</span>}
                    {r.isFastest && <span className="pill ghost">{t('fastest')}</span>}
                  </span>
                </div>
                {r.hazards.length === 0
                  ? <div className="small ok-text">✓ {t('avoids')}</div>
                  : (
                    <div className="small">
                      {t('passes')} {r.hazards.length} {t('floodedSpots')}:
                      {r.hazards.slice(0, 3).map((h) => (
                        <div key={h.reportId} style={{ color: SEV_HEX[h.severity] }}>• {h.locationName} ({h.depthFt.toFixed(1)} ft)</div>
                      ))}
                    </div>
                  )}
              </button>
            </li>
          ))}
        </ul>
        {mapsLink && <a className="btn block" href={mapsLink} target="_blank" rel="noreferrer">🧭 {t('openInMaps')}</a>}
      </aside>

      <div className="map-wrap">
        <MapContainer center={NCR_CENTER} zoom={11} className="map-full">
          <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" attribution="&copy; OpenStreetMap" />
          <FitBounds routes={options} />
          {from && <Marker position={[from.lat, from.lng]} icon={youIcon} />}
          {to && <Marker position={[to.lat, to.lng]} icon={destIcon} />}
          {options.map((r, i) => i !== chosen && (
            <Polyline key={i} positions={r.coords} pathOptions={{ color: '#7d8a96', weight: 5, opacity: 0.55, dashArray: '6 8' }}
              eventHandlers={{ click: () => setChosen(i) }} />
          ))}
          {best && <Polyline positions={best.coords} pathOptions={{ color: best.blocking ? '#e0612b' : '#1769aa', weight: 7, opacity: 0.9 }} />}
          {[...new Map(options.flatMap((r) => r.hazards).map((h) => [h.reportId, h])).values()].map((h) => (
            <Marker key={'h' + h.reportId} position={[h.latitude, h.longitude]} icon={severityIcon(h.severity, true)}>
              <Popup>{h.locationName}<br />{h.depthFt.toFixed(1)} ft</Popup>
            </Marker>
          ))}
        </MapContainer>
      </div>
    </div>
  )
}
