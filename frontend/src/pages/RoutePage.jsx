import { useEffect, useState, useCallback, useRef } from 'react'
import { MapContainer, TileLayer, Marker, Polyline, Popup, useMap } from 'react-leaflet'
import L from 'leaflet'
import { api, getPosition, getRoutes, NCR_CENTER } from '../api'
import { useLang } from '../i18n'
import PlaceSearch from '../components/PlaceSearch'
import { mapAssessments, compareRoutes } from '../utils/routeAssessment'
import { latestTask } from '../utils/evidence'
import { severityIcon, youIcon, destIcon, SEV_HEX, RiskScoreBadge, timeAgo } from '../components/shared'

// Vehicle blocking water depth thresholds (ft)
// Mode thresholds are validated in utils/routeAssessment.js.

function FitBounds({ routes }) {
  const map = useMap()
  useEffect(() => {
    if (!routes || !routes.length) return
    const pts = routes.flatMap((r) => r.coords)
    if (pts.length > 0) {
      map.fitBounds(L.latLngBounds(pts), { padding: [40, 40] })
      map.invalidateSize()
    }
  }, [routes, map])
  return null
}

/** Points ~`m` metres to the left and right of a hazard, perpendicular to the route direction for detour search. */
function detourPoints(route, hazard, metres) {
  const c = route.coords
  let k = 0, best = Infinity
  c.forEach((p, i) => {
    const d = (p[0] - hazard.latitude) ** 2 + (p[1] - hazard.longitude) ** 2
    if (d < best) {
      best = d
      k = i
    }
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
  const { t, lang } = useLang()
  const routeListRef = useRef(null)
  const searchTask = useRef(latestTask())
  const gpsTask = useRef(latestTask())
  const searchBusy = useRef(false)
  useEffect(() => () => { searchTask.current.invalidate(); gpsTask.current.invalidate() }, [])
  const [from, setFrom] = useState(null)
  const [to, setTo] = useState(null)
  const [mode, setMode] = useState('bike')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [options, setOptions] = useState([])
  const [chosen, setChosen] = useState(0)
  const [whyOpen, setWhyOpen] = useState(true)

  const locateMe = useCallback(() => {
    const token = gpsTask.current.next()
    getPosition()
      .then((p) => { if (gpsTask.current.current(token)) { invalidateSearch(); setFrom({ lat: p.lat, lng: p.lng, name: t('myLocation') }) } })
      .catch(() => { if (gpsTask.current.current(token)) setError(t('gpsError')) })
  }, [t])

  useEffect(() => {
    locateMe()
  }, [])

  const scoreAll = async (routes) => {
    try {
      const ev = await api.evaluateRoutes(routes.map((r) => r.coords))
      return mapAssessments(routes, ev, mode)
    } catch {
      return mapAssessments(routes, null, mode)
    }
  }

  const invalidateSearch = () => {
    searchTask.current.invalidate()
    searchBusy.current = false
    setBusy(false)
    setOptions([])
    setChosen(0)
  }

  const find = async () => {
    setError('')
    if (!from || !to || searchBusy.current) return
    const token = searchTask.current.next()
    searchBusy.current = true
    setBusy(true)
    setOptions([])
    try {
      const start = [from.lat, from.lng], end = [to.lat, to.lng]
      let routes = await scoreAll(await getRoutes([start, end], mode))
      if (!searchTask.current.current(token)) return
      const fastest = routes[0]

      // If all standard routes have blocking water, evaluate detours around worst hazard
      if (routes.every((r) => r.assessed && r.blocking > 0) && fastest && fastest.hazards.length > 0) {
        const hz = [...fastest.hazards].sort((a, b) => (b.depthFt || 0) - (a.depthFt || 0))[0]
        const vias = [...detourPoints(fastest, hz, 700), ...detourPoints(fastest, hz, 1500)]
        const extra = []
        for (const v of vias) {
          if (!searchTask.current.current(token)) return
          try {
            const [r] = await getRoutes([start, v, end], mode, false)
            if (r && r.durationMin < fastest.durationMin * 2.2) extra.push({ ...r, via: v })
          } catch {
            // ignore individual detour failures
          }
        }
        if (extra.length) {
          routes = routes.concat(await scoreAll(extra))
        }
      }

      if (!searchTask.current.current(token)) return
      // Mark the fastest route based on duration
      const minDuration = Math.min(...routes.map((r) => r.durationMin))
      routes.forEach((r) => {
        r.isFastest = Math.abs(r.durationMin - minDuration) < 0.1
      })

      // Sort routes: lowest blocking first, then lowest risk, then shortest duration
      routes.sort(compareRoutes)
      setOptions(routes.slice(0, 4))
      setChosen(0)
    } catch (e) {
      if (searchTask.current.current(token)) setError(t('liveDataUnavailable'))
    } finally {
      if (searchTask.current.current(token)) { searchBusy.current = false; setBusy(false) }
    }
  }

  const best = options[chosen]
  const fastestOption = options.find((r) => r.isFastest) || options[0]
  const recommendedOption = options[0]
  const minDuration = fastestOption?.durationMin || 0

  // Explain recommendation based on real comparison metrics
  const getRecommendationExplanation = () => {
    if (!options.length) return ''
    if (options.some((r) => !r.assessed)) return t('assessedRecommendation')
    const totalHazardsAcrossAll = options.reduce((sum, r) => sum + (r.hazards?.length || 0), 0)

    if (totalHazardsAcrossAll === 0) {
      return t('reportedNoHazards')
    }

    if (recommendedOption === fastestOption && recommendedOption.hazards.length === 0) {
      return t('recommendationReasonFastestAndLowest')
    }

    if (fastestOption && recommendedOption !== fastestOption) {
      const avoided = Math.max(0, fastestOption.hazards.length - recommendedOption.hazards.length)
      const timeDiff = Math.max(0, Math.round(recommendedOption.durationMin - fastestOption.durationMin))
      return t('recommendationReasonFewerHazards', { hazardsAvoided: avoided, timeDiff })
    }

    if (options.every((r) => r.hazards.length > 0)) {
      return t('recommendationReasonAllRisky', { score: recommendedOption.risk })
    }

    return t('recommendationReasonEqual')
  }

  const scrollToAlternatives = () => {
    if (routeListRef.current) {
      routeListRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }

  const mapsLink = best && from && to
    ? `https://www.google.com/maps/dir/?api=1&origin=${from.lat},${from.lng}&destination=${to.lat},${to.lng}` +
      (best.via ? `&waypoints=${best.via[0].toFixed(5)},${best.via[1].toFixed(5)}` : '') +
      `&travelmode=${mode === 'walk' ? 'walking' : 'driving'}`
    : null

  // Collect unique hazards across all route options for map display
  const allHazards = [...new Map(options.flatMap((r) => r.hazards || []).map((h) => [
    h.reportId ? `r_${h.reportId}` : `pos_${h.latitude.toFixed(5)}_${h.longitude.toFixed(5)}`,
    h
  ])).values()]

  return (
    <div className="split">
      <aside className="panel">
        <div className="row between wrap">
          <div>
            <h1>{t('routeTitle')}</h1>
            <p className="muted small" style={{ marginBottom: 12 }}>{t('routeSubtitle')}</p>
          </div>
          {options.length > 0 && (
            <button
              type="button"
              className="btn small ghost"
              onClick={find}
              disabled={busy}
              title={t('recalculate')}
            >
              🔄 {t('recalculate')}
            </button>
          )}
        </div>

        <label className="label">{t('from')}</label>
        <PlaceSearch
          value={from}
          onSelect={(p) => { gpsTask.current.invalidate(); invalidateSearch(); setFrom(p) }}
          placeholder={t('searchPlace')}
          extraOption={{ label: t('myLocation'), onClick: locateMe }}
        />

        <label className="label">{t('to')}</label>
        <PlaceSearch
          value={to}
          onSelect={(p) => { invalidateSearch(); setTo(p) }}
          placeholder={t('searchPlace')}
        />

        <label className="label">{t('travelBy')}</label>
        <div className="seg">
          {[
            ['bike', '🛵', t('bike')],
            ['car', '🚗', t('car')],
            ['walk', '🚶', t('walk')]
          ].map(([m, icon, label]) => (
            <button
              key={m}
              type="button"
              className={mode === m ? 'on' : ''}
              onClick={() => { invalidateSearch(); setMode(m) }}
              aria-label={label}
            >
              {icon} {label}
            </button>
          ))}
        </div>

        <button
          className="btn primary block"
          disabled={!from || !to || busy}
          onClick={find}
          style={{ marginTop: 16 }}
        >
          {busy ? (
            <>
              <span className="spinner" /> {t('finding')}
            </>
          ) : (
            `🧭 ${t('findRoute')}`
          )}
        </button>

        {error && <div role="alert" className="banner err">⚠️ {error}</div>}

        {options.some((r) => !r.assessed) && <div role="status" className="banner warn">{t('riskUnknownNotice')}</div>}
        {/* Selected Route Hazard Alert Banner (Phase 5) */}
        {best && best.hazards.length > 0 && (
          <div className="banner warn" style={{ flexDirection: 'column', gap: 6, margin: '12px 0' }}>
            <div className="row gap" style={{ margin: 0 }}>
              <span style={{ fontSize: '1.2rem' }}>⚠️</span>
              <b>{t('routeHasHazardsAlert', { count: best.hazards.length })}</b>
            </div>
            {options.length > 1 && (
              <button
                type="button"
                className="btn small ghost"
                onClick={scrollToAlternatives}
                style={{ alignSelf: 'flex-start', marginTop: 4, background: '#FFFFFF' }}
              >
                🔍 {t('viewAlternatives')}
              </button>
            )}
          </div>
        )}

        {/* Explainable Recommendation Panel (Phase 1) */}
        {options.length > 0 && (
          <div className="route-expl-card">
            <button
              type="button"
              className="why-rec-toggle"
              onClick={() => setWhyOpen(!whyOpen)}
              aria-expanded={whyOpen}
            >
              <span>{whyOpen ? '▼' : '►'}</span>
              <b>{t('whyRecommended')}</b>
            </button>
            {whyOpen && (
              <p className="small muted" style={{ marginTop: 6, lineHeight: 1.45 }}>
                {getRecommendationExplanation()}
              </p>
            )}
          </div>
        )}

        {/* Essential Safety Disclaimer Banner */}
        <div className="banner info small" style={{ margin: '12px 0' }}>
          🛡️ {t('routeDisclaimer')}
        </div>

        {options.length > 0 && options[0].blocking > 0 && (
          <div className="banner warn small">
            ⚠️ {t('noSafe')}
          </div>
        )}

        {/* Candidate Route Comparison Cards */}
        {options.length > 0 && (
          <div ref={routeListRef}>
            <div className="row between" style={{ margin: '10px 0 6px' }}>
              <span className="label" style={{ margin: 0 }}>{t('riskOptions')}</span>
              <span className="small muted">{options.length}</span>
            </div>
            <ul className="route-list">
              {options.map((r, i) => {
                const isSelected = i === chosen
                const isRecommended = i === 0 && r.assessed
                const timeDiff = Math.round(r.durationMin - minDuration)
                const isFastest = r.isFastest

                return (
                  <li key={i}>
                    <button
                      className={'route-opt' + (isSelected ? ' on' : '')}
                      onClick={() => setChosen(i)}
                      aria-label={t('optionNumber', { n: i + 1 })}
                    >
                      <div className="row between wrap" style={{ marginBottom: 6 }}>
                        <div className="row gap" style={{ margin: 0, gap: 6 }}>
                          <b>
                            {t('optionNumber', { n: i + 1 })}: {Math.round(r.durationMin)} {t('min')} · {r.distanceKm.toFixed(1)} {t('km')}
                          </b>
                          {timeDiff > 0 ? (
                            <span className="route-time-diff">
                              {t('extraTime', { min: timeDiff })}
                            </span>
                          ) : (
                            <span className="route-time-diff" style={{ background: '#DCFCE7', color: '#166534' }}>
                              ⚡ {t('sameTime')}
                            </span>
                          )}
                        </div>

                        <span className="row gap" style={{ gap: 4, margin: 0 }}>
                          {isRecommended && (
                            <span className="pill" style={{ background: 'var(--ok)' }}>
                              ✓ {t('recommended')}
                            </span>
                          )}
                          {isFastest && !isRecommended && (
                            <span className="pill ghost">
                              ⚡ {t('fastest')}
                            </span>
                          )}
                          {isSelected && (
                            <span className="pill" style={{ background: 'var(--brand)' }}>
                              {t('selectedRoute')}
                            </span>
                          )}
                        </span>
                      </div>

                      <div className="row between" style={{ marginBottom: 6 }}>
                        <RiskScoreBadge score={r.risk} />
                        <span className="small muted">
                          {!r.assessed ? t('riskUnavailable') : r.hazards.length === 0 ? t('reportedNoHazards') : `${r.hazards.length} ${t('floodedSpots')}`} 
                        </span>
                      </div>

                      {/* Mode specific warnings */}
                      {mode === 'bike' && r.safeForBike === false && (
                        <div className="small" style={{ color: 'var(--sev-severe)', fontWeight: 600 }}>
                          🚫 🛵 {t('bikeWarning')}
                        </div>
                      )}
                      {mode === 'car' && r.safeForCar === false && (
                        <div className="small" style={{ color: 'var(--sev-severe)', fontWeight: 600 }}>
                          🚫 🚗 {t('carWarning')}
                        </div>
                      )}
                      {mode === 'walk' && (
                        <div className="small muted" style={{ marginTop: 2 }}>
                          ℹ️ {t('walkSafetyUnavailable')}
                        </div>
                      )}

                      {!r.assessed ? <p className="small muted">{t('riskUnavailable')}</p> : r.hazards.length === 0 ? (
                        <div className="small ok-text" style={{ marginTop: 4 }}>
                          {t('reportedNoHazards')}
                        </div>
                      ) : (
                        <div className="route-hazards-box">
                          <span style={{ color: r.blocking > 0 ? 'var(--sev-severe)' : 'var(--warn)', fontWeight: 600, fontSize: '0.82rem' }}>
                            ⚠️ {t('passes')} {r.hazards.length} {t('floodedSpots')}
                            {r.blocking > 0 ? ` (${r.blocking} ${t('unsafe')})` : ''}:
                          </span>
                          <div style={{ marginTop: 4, display: 'flex', flexDirection: 'column', gap: 3 }}>
                            {r.hazards.slice(0, 3).map((h, hIdx) => (
                              <div key={h.reportId ? `rh_${h.reportId}` : `ph_${hIdx}_${h.latitude}_${h.longitude}`} style={{ color: SEV_HEX[h.severity] || '#EA580C', fontSize: '0.78rem' }}>
                                • <b>{h.locationName || (h.hazardType === 'PREDICTED' ? '[Forecast Risk Spot]' : 'Hazard Spot')}</b> {h.hazardType === 'PREDICTED' ? `[🔮 ${t('layerPredicted')}]` : `(~${(h.depthFt || 0).toFixed(1)} ft)`}
                                {h.distanceFromRouteM != null && (
                                  <span className="muted"> · {t('hazardDist', { m: Math.round(h.distanceFromRouteM) })}</span>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </button>
                  </li>
                )
              })}
            </ul>
          </div>
        )}

        {mapsLink && (
          <a
            className="btn secondary block"
            href={mapsLink}
            target="_blank"
            rel="noreferrer"
            style={{ marginTop: 12 }}
          >
            🗺️ {t('openInMaps')}
          </a>
        )}
      </aside>

      <div className="map-wrap">
        <MapContainer
          center={NCR_CENTER}
          zoom={11}
          className="map-full"
          zoomControl={false}
        >
          <TileLayer
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            attribution="&copy; OpenStreetMap"
          />
          <FitBounds routes={options} />
          {from && <Marker position={[from.lat, from.lng]} icon={youIcon} />}
          {to && <Marker position={[to.lat, to.lng]} icon={destIcon} />}

          {/* Alternative (non-selected) routes */}
          {options.map((r, i) => i !== chosen && (
            <Polyline
              key={'alt-' + i}
              positions={r.coords}
              pathOptions={{
                color: '#64748B',
                weight: 5,
                opacity: 0.65,
                dashArray: '6 8'
              }}
              eventHandlers={{ click: () => setChosen(i) }}
            />
          ))}

          {/* Active selected route */}
          {best && (
            <Polyline
              positions={best.coords}
              pathOptions={{
                color: !best.assessed ? '#64748B' : best.blocking > 0 ? '#DC2626' : '#0F766E',
                weight: 7,
                opacity: 0.95
              }}
            />
          )}

          {/* Hazard markers on route */}
          {allHazards.map((h, idx) => (
            <Marker
              key={'hm-' + (h.reportId ? `r_${h.reportId}` : `pos_${idx}_${h.latitude}_${h.longitude}`)}
              position={[h.latitude, h.longitude]}
              icon={severityIcon(h.severity, true)}
            >
              <Popup maxWidth={280}>
                <div style={{ fontSize: '0.88rem' }}>
                  <div className="row between">
                    <b style={{ color: SEV_HEX[h.severity] || '#EA580C' }}>
                      {h.hazardType === 'PREDICTED' ? `🔮 ${t('layerPredicted')}` : `⚠️ ${t('routeHazardNotice')}`}
                    </b>
                    <span className="pill" style={{ background: SEV_HEX[h.severity] || '#64748B', fontSize: '0.65rem' }}>
                      {t('sev_' + h.severity)}
                    </span>
                  </div>
                  <div style={{ fontWeight: 700, marginTop: 4 }}>
                    {h.locationName || (h.hazardType === 'PREDICTED' ? '[Forecast Risk Spot]' : 'Hazard Spot')}
                  </div>
                  <div className="muted small" style={{ margin: '4px 0' }}>
                    {h.hazardType === 'PREDICTED' ? (
                      <span>{t('predictiveDisclaimer')}</span>
                    ) : (
                      <span>{t('depth')}: <b>{(h.depthFt || 0).toFixed(1)} {t('ft')}</b></span>
                    )}
                    {h.distanceFromRouteM != null && ` · ${t('hazardDist', { m: Math.round(h.distanceFromRouteM) })}`}
                  </div>
                  {h.createdAt && (
                    <div className="small muted">
                      ⏱️ {t('reported')} {timeAgo(h.createdAt, lang)} {t('ago')}
                    </div>
                  )}
                </div>
              </Popup>
            </Marker>
          ))}
        </MapContainer>
      </div>
    </div>
  )
}
