import { useEffect, useState, useCallback, useMemo } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Circle, CircleMarker, useMap } from 'react-leaflet'
import L from 'leaflet'
import { Link } from 'react-router-dom'
import { api, getPosition, NCR_CENTER } from '../api'
import { useLang } from '../i18n'
import {
  ReportCard,
  PredictedRiskCard,
  WeatherTelemetryWidget,
  severityIcon,
  predictiveRiskIcon,
  youIcon,
  SEV_HEX,
  RISK_HEX
} from '../components/shared'

function MapHelper({ pos, setMapInstance, onZoomChange }) {
  const map = useMap()
  useEffect(() => {
    if (setMapInstance) setMapInstance(map)
    const onResize = () => map.invalidateSize()
    const onZoom = () => onZoomChange && onZoomChange(map.getZoom())

    window.addEventListener('resize', onResize)
    map.on('zoomend', onZoom)

    return () => {
      window.removeEventListener('resize', onResize)
      map.off('zoomend', onZoom)
    }
  }, [map, setMapInstance, onZoomChange])

  useEffect(() => {
    if (pos) map.flyTo([pos.lat, pos.lng], 13, { duration: 0.8 })
  }, [pos, map])
  return null
}

/** Group nearby reports into geospatial clusters for high-density visualization. */
function buildClusters(reports, thresholdDegrees = 0.015) {
  const clusters = []
  const visited = new Set()

  reports.forEach((r, idx) => {
    if (visited.has(idx)) return
    visited.add(idx)

    const clusterReports = [r]
    let maxSeverity = r.severity

    reports.forEach((other, oIdx) => {
      if (visited.has(oIdx)) return
      const dist = Math.hypot(r.latitude - other.latitude, r.longitude - other.longitude)
      if (dist < thresholdDegrees) {
        visited.add(oIdx)
        clusterReports.push(other)
        if (other.severity === 'SEVERE' || (other.severity === 'HIGH' && maxSeverity !== 'SEVERE')) {
          maxSeverity = other.severity
        }
      }
    })

    const avgLat = clusterReports.reduce((sum, item) => sum + item.latitude, 0) / clusterReports.length
    const avgLng = clusterReports.reduce((sum, item) => sum + item.longitude, 0) / clusterReports.length
    const totalDepth = clusterReports.reduce((sum, item) => sum + (item.depthFt || 0), 0)

    clusters.push({
      id: `cluster-${r.id}`,
      center: [avgLat, avgLng],
      count: clusterReports.length,
      maxSeverity,
      avgDepth: totalDepth / clusterReports.length,
      reports: clusterReports
    })
  })

  return clusters
}

export default function MapPage() {
  const { t, lang } = useLang()
  const [reports, setReports] = useState([])
  const [predictedHotspots, setPredictedHotspots] = useState([])
  const [weatherData, setWeatherData] = useState(null)
  const [monitorStatus, setMonitorStatus] = useState(null)
  const [me, setMe] = useState(null)
  const [feed, setFeed] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [sheetOpen, setSheetOpen] = useState(false)
  const [legendOpen, setLegendOpen] = useState(true)
  const [mapInstance, setMapInstance] = useState(null)
  const [mapMode, setMapMode] = useState('markers') // 'markers' | 'predicted' | 'heatmap'
  const [currentZoom, setCurrentZoom] = useState(11)

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    const targetLat = me?.lat || NCR_CENTER[0]
    const targetLng = me?.lng || NCR_CENTER[1]

    return Promise.allSettled([
      api.activeReports(),
      api.floodRiskHotspots(),
      api.weather(targetLat, targetLng),
      api.floodRiskStatus()
    ])
      .then(([reportsRes, riskRes, weatherRes, statusRes]) => {
        if (reportsRes.status === 'fulfilled' && Array.isArray(reportsRes.value)) {
          setReports(reportsRes.value)
        }
        if (riskRes.status === 'fulfilled' && Array.isArray(riskRes.value)) {
          setPredictedHotspots(riskRes.value)
        }
        if (weatherRes.status === 'fulfilled' && weatherRes.value) {
          setWeatherData(weatherRes.value)
        }
        if (statusRes.status === 'fulfilled' && statusRes.value) {
          setMonitorStatus(statusRes.value)
        }
      })
      .catch((e) => setError(e.message || t('error')))
      .finally(() => setLoading(false))
  }, [me?.lat, me?.lng, t])

  useEffect(() => {
    load()
    const id = setInterval(load, 60_000) // refresh live map every minute
    getPosition().then(setMe).catch(() => {})
    return () => clearInterval(id)
  }, [load])

  useEffect(() => {
    if (!me) return
    api.feed(me.lat, me.lng, lang).then(setFeed).catch(() => {})
  }, [me, lang, reports.length])

  const centerOnMe = () => {
    if (me && mapInstance) {
      mapInstance.flyTo([me.lat, me.lng], 14, { duration: 0.8 })
    } else {
      getPosition().then((p) => {
        setMe(p)
        if (mapInstance) mapInstance.flyTo([p.lat, p.lng], 14, { duration: 0.8 })
      }).catch(() => setError(t('gpsError')))
    }
  }

  const zoomIn = () => mapInstance && mapInstance.zoomIn()
  const zoomOut = () => mapInstance && mapInstance.zoomOut()

  const replace = (u) => setReports((rs) => rs.map((r) => (r.id === u.id ? u : r)).filter((r) => r.status === 'ACTIVE'))
  const severe = reports.filter((r) => r.severity === 'SEVERE' || r.severity === 'HIGH').length
  const criticalForecast = predictedHotspots.filter((h) => h.riskLevel === 'CRITICAL' || h.riskLevel === 'HIGH').length
  const rainBanner = feed && (feed.rainLevel === 'HEAVY' || feed.rainLevel === 'VERY_HEAVY')

  // Generate clusters from actual data for heatmap view & low-zoom display
  const clusters = useMemo(() => buildClusters(reports, 0.016), [reports])

  return (
    <div className="map-page">
      <MapContainer
        center={NCR_CENTER}
        zoom={11}
        className="map-full"
        zoomControl={false}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <MapHelper pos={me} setMapInstance={setMapInstance} onZoomChange={setCurrentZoom} />

        {/* User GPS position indicator */}
        {me && (
          <>
            <Marker position={[me.lat, me.lng]} icon={youIcon} />
            <Circle
              center={[me.lat, me.lng]}
              radius={3000}
              pathOptions={{ color: '#0EA5E9', weight: 1.5, fillOpacity: 0.05, dashArray: '4 6' }}
            />
          </>
        )}

        {/* MODE 1: Standard Individual Observed Report Markers */}
        {mapMode === 'markers' && reports.map((r) => (
          <Marker
            key={r.id}
            position={[r.latitude, r.longitude]}
            icon={severityIcon(r.severity, r.severity === 'SEVERE')}
          >
            <Popup maxWidth={320} minWidth={270}>
              <ReportCard r={r} onChange={replace} />
            </Popup>
          </Marker>
        ))}

        {/* Report hazard extent circles in marker mode */}
        {mapMode === 'markers' && reports.map((r) => (
          <Circle
            key={'c' + r.id}
            center={[r.latitude, r.longitude]}
            radius={70 + (r.depthFt || 0.5) * 60}
            pathOptions={{ color: SEV_HEX[r.severity] || '#EA580C', weight: 0, fillOpacity: 0.22 }}
          />
        ))}

        {/* MODE 2: Autonomous Predicted Flood-Risk Markers */}
        {mapMode === 'predicted' && predictedHotspots.map((ra) => {
          const color = RISK_HEX[ra.riskLevel] || '#7C3AED'
          const isCritical = ra.riskLevel === 'CRITICAL'

          return (
            <span key={'pred-wrap-' + ra.id}>
              <Marker
                position={[ra.latitude, ra.longitude]}
                icon={predictiveRiskIcon(ra.riskLevel, isCritical)}
              >
                <Popup maxWidth={320} minWidth={280}>
                  <PredictedRiskCard ra={ra} />
                </Popup>
              </Marker>
              <Circle
                center={[ra.latitude, ra.longitude]}
                radius={250 + (ra.riskScore || 20) * 4}
                pathOptions={{
                  color,
                  fillColor: color,
                  weight: 1.5,
                  fillOpacity: 0.15,
                  dashArray: '5 6'
                }}
              />
            </span>
          )
        })}

        {/* MODE 3: Hotspot Concentration / Heatmap Layer */}
        {mapMode === 'heatmap' && clusters.map((c) => {
          const isSevere = c.maxSeverity === 'SEVERE' || c.maxSeverity === 'HIGH'
          const baseColor = isSevere ? '#DC2626' : (c.count > 2 ? '#EA580C' : '#F59E0B')
          const radiusMeters = Math.min(1800, 400 + c.count * 300)

          return (
            <span key={c.id}>
              {/* Outer soft glow concentration ring */}
              <Circle
                center={c.center}
                radius={radiusMeters * 1.5}
                pathOptions={{
                  color: baseColor,
                  weight: 0,
                  fillOpacity: 0.12
                }}
              />
              {/* Core concentration ring */}
              <Circle
                center={c.center}
                radius={radiusMeters}
                pathOptions={{
                  color: baseColor,
                  weight: 1,
                  fillOpacity: 0.32
                }}
              />
              {/* Cluster center badge */}
              <CircleMarker
                center={c.center}
                radius={Math.min(24, 10 + c.count * 3)}
                pathOptions={{
                  color: '#FFFFFF',
                  fillColor: baseColor,
                  fillOpacity: 0.9,
                  weight: 2.5
                }}
                eventHandlers={{
                  click: () => {
                    if (mapInstance) mapInstance.flyTo(c.center, Math.max(14, currentZoom + 2))
                  }
                }}
              >
                <Popup maxWidth={300}>
                  <div style={{ fontSize: '0.9rem' }}>
                    <b>🔥 {t('heatmapTitle')}</b>
                    <div style={{ marginTop: 4, fontWeight: 700 }}>
                      {c.count} {t('clusterSummary')}
                    </div>
                    <div className="small muted">
                      Avg estimated depth: {c.avgDepth.toFixed(1)} ft
                    </div>
                    <ul style={{ listStyle: 'none', padding: 0, margin: '8px 0 0', display: 'flex', flexDirection: 'column', gap: 4 }}>
                      {c.reports.slice(0, 3).map((cr) => (
                        <li key={cr.id} style={{ fontSize: '0.8rem', borderLeft: `3px solid ${SEV_HEX[cr.severity]}`, paddingLeft: 6 }}>
                          <b>{cr.locationName}</b> ({cr.depthFt?.toFixed(1) || 0} ft)
                        </li>
                      ))}
                    </ul>
                  </div>
                </Popup>
              </CircleMarker>
            </span>
          )
        })}
      </MapContainer>

      {/* Floating map controls */}
      <div className="map-controls-floating">
        <button className="map-btn-floating" onClick={centerOnMe} title={t('locateMe')} aria-label={t('locateMe')}>
          📍
        </button>
        <button className="map-btn-floating" onClick={zoomIn} title={t('zoomIn')} aria-label={t('zoomIn')}>
          +
        </button>
        <button className="map-btn-floating" onClick={zoomOut} title={t('zoomOut')} aria-label={t('zoomOut')}>
          −
        </button>
        <button className="map-btn-floating" onClick={load} title={t('refreshMap')} aria-label={t('refreshMap')}>
          🔄
        </button>
      </div>

      {/* Top Banner, Telemetry Panel and Layer Toggle */}
      <div className="map-overlay-top">
        {rainBanner && (
          <div className="banner rain">
            🌧️ <b>{t('rain_' + feed.rainLevel)}</b> · ~{Math.round(feed.rainNext6hMm)} mm
          </div>
        )}

        {/* Live Weather & Copernicus DEM Telemetry Widget */}
        {weatherData && (
          <WeatherTelemetryWidget
            weather={weatherData}
            status={monitorStatus}
            onRefresh={load}
          />
        )}

        <div className="row wrap gap" style={{ gap: 8, margin: '4px 0 0' }}>
          <div className="stat-chip">
            <span className="dot" style={{ background: SEV_HEX.SEVERE }} />
            <b>{reports.length}</b> {t('liveNow')}
            {severe > 0 && <span className="muted"> · {severe} 🚫🛵 {t('unsafe')}</span>}
            {loading && <span className="spinner" style={{ borderColor: 'rgba(0,0,0,0.2)', borderTopColor: 'var(--brand)', marginLeft: 6 }} />}
          </div>

          {predictedHotspots.length > 0 && (
            <div className="stat-chip" style={{ background: '#F5F3FF', borderColor: '#DDD6FE', color: '#5B21B6' }}>
              <span className="dot" style={{ background: '#7C3AED' }} />
              <b>{predictedHotspots.length}</b> {t('layerPredicted')}
              {criticalForecast > 0 && <span className="muted" style={{ color: '#6D28D9' }}> · {criticalForecast} Critical</span>}
            </div>
          )}

          {/* Layer Toggle Button Group */}
          <div className="map-layer-toggle" role="tablist" aria-label="Map Layer Toggle">
            <button
              type="button"
              className={`map-layer-btn ${mapMode === 'markers' ? 'on' : ''}`}
              onClick={() => setMapMode('markers')}
              role="tab"
              aria-selected={mapMode === 'markers'}
            >
              📍 {t('layerMarkers')}
            </button>
            <button
              type="button"
              className={`map-layer-btn ${mapMode === 'predicted' ? 'on' : ''}`}
              onClick={() => setMapMode('predicted')}
              role="tab"
              aria-selected={mapMode === 'predicted'}
            >
              🔮 {t('layerPredicted')}
            </button>
            <button
              type="button"
              className={`map-layer-btn ${mapMode === 'heatmap' ? 'on' : ''}`}
              onClick={() => setMapMode('heatmap')}
              role="tab"
              aria-selected={mapMode === 'heatmap'}
            >
              🔥 {t('layerHeatmap')}
            </button>
          </div>
        </div>

        {error && (
          <div className="banner err">
            <span>⚠️ {error}</span>
            <button className="btn small ghost" onClick={load} style={{ marginLeft: 8, padding: '2px 8px', minHeight: 28 }}>
              {t('retry')}
            </button>
          </div>
        )}

        {!loading && mapMode === 'markers' && reports.length === 0 && (
          <div className="banner warn small">
            ℹ️ {t('noReportsInArea')}
          </div>
        )}
      </div>

      {/* Map Legend */}
      {legendOpen && (
        <div className="map-legend-card">
          <div className="row between" style={{ marginBottom: 4 }}>
            <h4>
              {mapMode === 'heatmap' ? t('heatmapTitle') : mapMode === 'predicted' ? t('predictiveTitle') : t('legend_title')}
            </h4>
            <button
              onClick={() => setLegendOpen(false)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '0.8rem', color: 'var(--muted)' }}
              aria-label="Close legend"
            >
              ✕
            </button>
          </div>

          {mapMode === 'markers' ? (
            <ul className="map-legend-items">
              <li><span className="dot" style={{ background: SEV_HEX.LOW }} /> {t('legend_low_mod')}</li>
              <li><span className="dot" style={{ background: SEV_HEX.SEVERE }} /> {t('legend_high_sev')}</li>
              {me && <li><span className="dot" style={{ background: '#0EA5E9' }} /> {t('legend_you')}</li>}
            </ul>
          ) : mapMode === 'predicted' ? (
            <ul className="map-legend-items">
              <li><span className="dot" style={{ background: RISK_HEX.LOW }} /> {t('riskLevel_LOW')}</li>
              <li><span className="dot" style={{ background: RISK_HEX.MODERATE }} /> {t('riskLevel_MOD')}</li>
              <li><span className="dot" style={{ background: RISK_HEX.HIGH }} /> {t('riskLevel_HIGH')}</li>
              <li><span className="dot" style={{ background: RISK_HEX.CRITICAL }} /> {t('riskLevel_CRITICAL')}</li>
            </ul>
          ) : (
            <ul className="map-legend-items">
              <li><span className="dot" style={{ background: '#F59E0B' }} /> {t('heatmapLow')}</li>
              <li><span className="dot" style={{ background: '#EA580C' }} /> {t('heatmapMed')}</li>
              <li><span className="dot" style={{ background: '#DC2626' }} /> {t('heatmapHigh')}</li>
            </ul>
          )}

          <p className="muted small" style={{ marginTop: 6, fontSize: '0.72rem', lineHeight: 1.3 }}>
            ⚠️ {mapMode === 'heatmap' ? t('heatmapDisclaimer') : mapMode === 'predicted' ? t('predictiveDisclaimer') : t('mapDisclaimer')}
          </p>
        </div>
      )}

      {/* Mobile/Desktop Bottom Sheet with Nearby Reports */}
      <section className={'sheet' + (sheetOpen ? ' open' : '')}>
        <button className="sheet-handle" onClick={() => setSheetOpen(!sheetOpen)} aria-label="Toggle nearby reports list">
          <span />
        </button>
        <div className="sheet-head">
          <h2>{t('nearYou')}</h2>
          <Link to="/about" className="small muted">{t('nav_about')} →</Link>
        </div>
        {!me && <p className="muted small">{t('locating')}</p>}
        {me && feed && feed.alerts.filter((a) => a.reportId).length === 0 && (
          <p className="muted small">{t('noAlerts')}</p>
        )}
        <ul className="alert-list">
          {feed && feed.alerts.filter((a) => a.reportId).map((a) => {
            const r = reports.find((x) => x.id === a.reportId)
            return (
              <li key={a.reportId} className="alert-item" style={{ borderColor: SEV_HEX[a.level] || '#EA580C' }}>
                <b>{a.title}</b>
                <div className="small" style={{ margin: '4px 0' }}>{a.message}</div>
                {r && (
                  <details>
                    <summary className="small" style={{ cursor: 'pointer', fontWeight: 600, color: 'var(--brand)' }}>
                      {t('details')}
                    </summary>
                    <ReportCard r={r} onChange={replace} compact showTimeline />
                  </details>
                )}
              </li>
            )
          })}
        </ul>
      </section>

      <Link to="/report" className="fab" aria-label={t('reportCta')}>
        <span aria-hidden>📷</span> {t('reportCta')}
      </Link>
    </div>
  )
}
