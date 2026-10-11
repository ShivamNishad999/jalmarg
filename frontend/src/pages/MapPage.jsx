import { useEffect, useState, useCallback, useMemo } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Circle, CircleMarker, Polyline, Polygon, useMap, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import { Link, useNavigate } from 'react-router-dom'
import { api, getPosition, reverseGeocode, NCR_CENTER } from '../api'
import { useLang } from '../i18n'
import PlaceSearch from '../components/PlaceSearch'
import {
  ReportCard,
  PredictedRiskCard,
  WeatherTelemetryWidget,
  severityIcon,
  predictiveRiskIcon,
  youIcon,
  destIcon,
  SEV_HEX,
  RISK_HEX
} from '../components/shared'

function MapHelper({ pos, center, zoom, setMapInstance, onZoomChange }) {
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
    if (center) {
      map.flyTo(center, zoom || 13, { duration: 0.8 })
    } else if (pos) {
      map.flyTo([pos.lat, pos.lng], 13, { duration: 0.8 })
    }
  }, [pos, center, zoom, map])
  return null
}

function MapClickHandler({ onMapClick }) {
  useMapEvents({
    click: (e) => onMapClick({ lat: e.latlng.lat, lng: e.latlng.lng })
  })
  return null
}

const inspectedIcon = L.divIcon({
  className: 'jm-marker-wrap',
  html: `<div style="background:#0F766E;width:24px;height:24px;border-radius:50%;border:3px solid #FFFFFF;box-shadow:0 0 14px rgba(15,118,110,0.8);display:flex;align-items:center;justify-content:center;color:white;font-size:12px;font-weight:bold;">🔍</div>`,
  iconSize: [26, 26],
  iconAnchor: [13, 13],
  popupAnchor: [0, -14]
})

const zoneMapIcon = (color = '#2563EB') => L.divIcon({
  className: 'jm-twin-marker',
  html: `<div style="background:${color};width:18px;height:18px;border-radius:50%;border:2px solid #FFFFFF;box-shadow:0 0 8px ${color}88;display:flex;align-items:center;justify-content:center;color:white;font-size:10px;font-weight:bold;">Z</div>`,
  iconSize: [20, 20],
  iconAnchor: [10, 10]
})

const drainMapIcon = (type = 'SLUICE_GATE') => L.divIcon({
  className: 'jm-drainage-marker',
  html: `<div style="background:${type === 'PUMPING_STATION' ? '#0284C7' : '#0D9488'};width:20px;height:20px;border-radius:50%;border:2px solid #FFFFFF;box-shadow:0 0 10px rgba(13,148,136,0.6);display:flex;align-items:center;justify-content:center;color:white;font-size:11px;">${type === 'PUMPING_STATION' ? '⚙️' : '🌊'}</div>`,
  iconSize: [22, 22],
  iconAnchor: [11, 11]
})

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
  const navigate = useNavigate()
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
  const [mapMode, setMapMode] = useState('markers') // 'markers' | 'predicted' | 'heatmap' | 'zones' | 'roads' | 'drainage'
  const [currentZoom, setCurrentZoom] = useState(11)

  // Point Risk Inspection State
  const [inspectedPoint, setInspectedPoint] = useState(null)
  const [inspectedRisk, setInspectedRisk] = useState(null)
  const [inspectingRisk, setInspectingRisk] = useState(false)
  const [searchQuery, setSearchQuery] = useState(null)
  const [mapCenterOverride, setMapCenterOverride] = useState(null)

  // Lazy Loaded Municipal Digital Twin Layers
  const [twinZones, setTwinZones] = useState([])
  const [twinRoads, setTwinRoads] = useState([])
  const [twinDrainage, setTwinDrainage] = useState([])
  const [loadingTwinLayer, setLoadingTwinLayer] = useState(false)

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

  // Lazy fetch municipal twin layers when toggled
  useEffect(() => {
    if (mapMode === 'zones' && twinZones.length === 0) {
      setLoadingTwinLayer(true)
      api.twinZones().then(setTwinZones).catch(() => {}).finally(() => setLoadingTwinLayer(false))
    } else if (mapMode === 'roads' && twinRoads.length === 0) {
      setLoadingTwinLayer(true)
      api.twinRoads().then(setTwinRoads).catch(() => {}).finally(() => setLoadingTwinLayer(false))
    } else if (mapMode === 'drainage' && twinDrainage.length === 0) {
      setLoadingTwinLayer(true)
      api.twinDrainage().then(setTwinDrainage).catch(() => {}).finally(() => setLoadingTwinLayer(false))
    }
  }, [mapMode, twinZones.length, twinRoads.length, twinDrainage.length])

  const inspectLocation = useCallback(async (lat, lng, nameHint = '') => {
    setInspectingRisk(true)
    setInspectedRisk(null)
    const pt = { lat, lng, name: nameHint || `${lat.toFixed(4)}, ${lng.toFixed(4)}` }
    setInspectedPoint(pt)
    setMapCenterOverride([lat, lng])

    try {
      if (!nameHint) {
        reverseGeocode(lat, lng).then((resolved) => {
          if (resolved) {
            setInspectedPoint((prev) => prev && prev.lat === lat && prev.lng === lng ? { ...prev, name: resolved } : prev)
          }
        }).catch(() => {})
      }
      const risk = await api.floodRisk(lat, lng, { area: nameHint })
      setInspectedRisk(risk)
    } catch (err) {
      setInspectedRisk({
        riskLevel: 'UNKNOWN',
        riskScore: 0,
        summaryEn: 'Point assessment could not be computed from server feeds.',
        summaryHi: 'सर्वर से इस स्थान का जोखिम आकलन प्राप्त नहीं हो सका।'
      })
    } finally {
      setInspectingRisk(false)
    }
  }, [])

  const handlePlaceSelect = (place) => {
    if (!place || !Number.isFinite(place.lat) || !Number.isFinite(place.lng)) return
    setSearchQuery(place)
    inspectLocation(place.lat, place.lng, place.name)
  }

  const handleMapClick = ({ lat, lng }) => {
    inspectLocation(lat, lng)
  }

  const centerOnMe = () => {
    if (me && mapInstance) {
      mapInstance.flyTo([me.lat, me.lng], 14, { duration: 0.8 })
      inspectLocation(me.lat, me.lng, t('legend_you'))
    } else {
      getPosition().then((p) => {
        setMe(p)
        if (mapInstance) mapInstance.flyTo([p.lat, p.lng], 14, { duration: 0.8 })
        inspectLocation(p.lat, p.lng, t('legend_you'))
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
        <MapHelper
          pos={me}
          center={mapCenterOverride}
          zoom={mapCenterOverride ? 14 : undefined}
          setMapInstance={setMapInstance}
          onZoomChange={setCurrentZoom}
        />
        <MapClickHandler onMapClick={handleMapClick} />

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

        {/* Selected / Inspected Point Marker */}
        {inspectedPoint && (
          <>
            <Marker position={[inspectedPoint.lat, inspectedPoint.lng]} icon={inspectedIcon}>
              <Popup maxWidth={320} minWidth={280}>
                <div style={{ fontSize: '0.88rem' }}>
                  <div className="row between">
                    <b style={{ color: '#0F766E' }}>📍 {t('inspectRiskTitle')}</b>
                    {inspectedRisk && (
                      <span className="pill" style={{ background: RISK_HEX[inspectedRisk.riskLevel] || '#64748B' }}>
                        {t('riskLevel_' + (inspectedRisk.riskLevel || 'UNKNOWN'))}
                      </span>
                    )}
                  </div>
                  <div style={{ fontWeight: 700, margin: '4px 0' }}>{inspectedPoint.name}</div>
                  {inspectedRisk && (
                    <div className="small" style={{ margin: '6px 0', lineHeight: 1.4 }}>
                      <div>{lang === 'hi' && inspectedRisk.summaryHi ? inspectedRisk.summaryHi : (inspectedRisk.summaryEn || '')}</div>
                      <div className="row wrap gap" style={{ margin: '6px 0' }}>
                        <span className="pill" style={{ background: '#1E293B', fontSize: '0.72rem' }}>
                          🎯 {t('riskScore')}: <b>{inspectedRisk.riskScore ?? 0}/100</b>
                        </span>
                        {inspectedRisk.rainfallNext6hMm != null && (
                          <span className="tag">
                            🌧️ {inspectedRisk.rainfallNext6hMm.toFixed(1)} mm (6h)
                          </span>
                        )}
                      </div>
                    </div>
                  )}
                  <div className="row gap" style={{ marginTop: 8 }}>
                    <button
                      type="button"
                      className="btn small primary"
                      style={{ flex: 1, padding: '4px 8px', minHeight: 30, fontSize: '0.78rem' }}
                      onClick={() => navigate('/route', { state: { from: { lat: inspectedPoint.lat, lng: inspectedPoint.lng, name: inspectedPoint.name } } })}
                    >
                      🧭 {t('routeFromHere')}
                    </button>
                    <button
                      type="button"
                      className="btn small ghost"
                      style={{ flex: 1, padding: '4px 8px', minHeight: 30, fontSize: '0.78rem' }}
                      onClick={() => navigate('/report', { state: { lat: inspectedPoint.lat, lng: inspectedPoint.lng, placeName: inspectedPoint.name } })}
                    >
                      📷 {t('reportHere')}
                    </button>
                  </div>
                </div>
              </Popup>
            </Marker>
            <Circle
              center={[inspectedPoint.lat, inspectedPoint.lng]}
              radius={400}
              pathOptions={{ color: '#0F766E', weight: 2, fillOpacity: 0.1, dashArray: '4 4' }}
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
              <Circle
                center={c.center}
                radius={radiusMeters * 1.5}
                pathOptions={{ color: baseColor, weight: 0, fillOpacity: 0.12 }}
              />
              <Circle
                center={c.center}
                radius={radiusMeters}
                pathOptions={{ color: baseColor, weight: 1, fillOpacity: 0.32 }}
              />
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

        {/* MODE 4: Municipal Planning Zones Layer */}
        {mapMode === 'zones' && twinZones.map((z) => {
          let polyCoords = null
          try {
            if (z.boundaryGeoJson) {
              const geo = JSON.parse(z.boundaryGeoJson)
              if (geo.type === 'Polygon' && geo.coordinates?.[0]) {
                polyCoords = geo.coordinates[0].map(([lng, lat]) => [lat, lng])
              }
            }
          } catch (e) {}
          const lat = z.centroidLat ?? z.centerLat
          const lng = z.centroidLng ?? z.centerLng
          if (!lat || !lng) return null
          const isVerified = z.boundaryType === 'VERIFIED_POLYGON' || z.boundaryType === 'VERIFIED_OFFICIAL'

          return (
            <span key={`twin-zone-${z.id}`}>
              {polyCoords && (
                <Polygon
                  positions={polyCoords}
                  pathOptions={{
                    color: isVerified ? '#16A34A' : '#3B82F6',
                    fillColor: isVerified ? '#16A34A' : '#3B82F6',
                    fillOpacity: 0.12,
                    weight: 2
                  }}
                />
              )}
              <Marker
                position={[lat, lng]}
                icon={zoneMapIcon(isVerified ? '#16A34A' : '#3B82F6')}
              >
                <Popup>
                  <div style={{ fontSize: '0.85rem' }}>
                    <div className="row between">
                      <b>🏛️ {z.name || z.zoneName}</b>
                      <span className="tag">{z.zoneCode}</span>
                    </div>
                    <p style={{ margin: '4px 0' }}><strong>Municipality:</strong> {z.municipalityName}</p>
                    <p style={{ margin: '2px 0' }}><strong>Boundary:</strong> {z.boundaryType}</p>
                    {z.areaSqKm && <p style={{ margin: '2px 0' }}><strong>Area:</strong> {z.areaSqKm} km²</p>}
                    <p className="muted small" style={{ marginTop: 4 }}>
                      Drainage: {z.drainageNetworkStatus || 'UNAVAILABLE'}
                    </p>
                  </div>
                </Popup>
              </Marker>
            </span>
          )
        })}

        {/* MODE 5: Road Corridors & Underpass Sumps Layer */}
        {mapMode === 'roads' && twinRoads.map((r) => {
          const isUnderpass = r.isUnderpass
          let linePositions = [[r.startLat, r.startLng], [r.endLat, r.endLng]]
          try {
            if (r.geometryGeoJson) {
              const geo = JSON.parse(r.geometryGeoJson)
              if (geo.type === 'LineString' && geo.coordinates) {
                linePositions = geo.coordinates.map(([lng, lat]) => [lat, lng])
              }
            }
          } catch (e) {}
          return (
            <Polyline
              key={`twin-road-${r.id}`}
              positions={linePositions}
              pathOptions={{
                color: isUnderpass ? '#DC2626' : '#6366F1',
                weight: isUnderpass ? 5 : 3,
                dashArray: isUnderpass ? '4, 4' : null
              }}
            >
              <Popup>
                <div style={{ fontSize: '0.85rem' }}>
                  <div className="row between">
                    <b>🛣️ {r.roadName}</b>
                    <span className="tag">{r.segmentCode}</span>
                  </div>
                  <p style={{ margin: '4px 0' }}><strong>Type:</strong> {r.roadType}</p>
                  <p style={{ margin: '2px 0' }}><strong>Underpass Sump:</strong> {r.isUnderpass ? '⚠️ Critical Vulnerability' : 'Grade Level'}</p>
                  {r.elevationMinMeters && <p style={{ margin: '2px 0' }}><strong>Elevation:</strong> {r.elevationMinMeters}m – {r.elevationMaxMeters}m AMSL</p>}
                </div>
              </Popup>
            </Polyline>
          )
        })}

        {/* MODE 6: Stormwater Drainage Assets Layer */}
        {mapMode === 'drainage' && twinDrainage.map((d) => (
          <Marker
            key={`twin-drain-${d.id}`}
            position={[d.latitude, d.longitude]}
            icon={drainMapIcon(d.assetType)}
          >
            <Popup>
              <div style={{ fontSize: '0.85rem' }}>
                <div className="row between">
                  <b>🌊 {d.name}</b>
                  <span className="tag">{d.assetCode}</span>
                </div>
                <p style={{ margin: '4px 0' }}><strong>Type:</strong> {d.assetType?.replace(/_/g, ' ')}</p>
                <p style={{ margin: '2px 0' }}><strong>Design Capacity:</strong> {d.designCapacityCusecs ? `${d.designCapacityCusecs} cusecs` : 'Unmetered'}</p>
                <p style={{ margin: '2px 0' }}><strong>Status:</strong> {d.status}</p>
                <p className="muted small" style={{ marginTop: 4 }}>
                  {d.isTelemetryConnected ? '🟢 Live SCADA Telemetry' : '⚪ Unmetered Flow (No SCADA)'}
                </p>
              </div>
            </Popup>
          </Marker>
        ))}
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

      {/* Top Banner, Telemetry Panel, Location Search, and Layer Toggles */}
      <div className="map-overlay-top">
        {rainBanner && (
          <div className="banner rain">
            🌧️ <b>{t('rain_' + feed.rainLevel)}</b> · ~{Math.round(feed.rainNext6hMm)} mm
          </div>
        )}

        {/* Delhi NCR Locality Search Bar */}
        <div className="map-search-bar" style={{ marginBottom: 6 }}>
          <PlaceSearch
            value={searchQuery}
            onSelect={handlePlaceSelect}
            placeholder={t('searchLocationPlaceholder')}
            extraOption={{ label: t('myLocation'), onClick: centerOnMe }}
          />
        </div>

        {/* Live Weather & Copernicus DEM Telemetry Widget */}
        {weatherData && (
          <WeatherTelemetryWidget
            weather={weatherData}
            status={monitorStatus}
            onRefresh={load}
          />
        )}

        {/* Stat chips & Layer toggles */}
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

          {/* Layer Toggle Button Group with Citizen & Municipal Views */}
          <div className="map-layer-toggle" role="tablist" aria-label="Map Layer Toggle">
            <button
              type="button"
              className={`map-layer-btn ${mapMode === 'markers' ? 'on' : ''}`}
              onClick={() => setMapMode('markers')}
              role="tab"
              aria-selected={mapMode === 'markers'}
              title="Citizen Ground Reports"
            >
              📍 {t('layerMarkers')}
            </button>
            <button
              type="button"
              className={`map-layer-btn ${mapMode === 'predicted' ? 'on' : ''}`}
              onClick={() => setMapMode('predicted')}
              role="tab"
              aria-selected={mapMode === 'predicted'}
              title="Forecast Risk Hotspots"
            >
              🔮 {t('layerPredicted')}
            </button>
            <button
              type="button"
              className={`map-layer-btn ${mapMode === 'heatmap' ? 'on' : ''}`}
              onClick={() => setMapMode('heatmap')}
              role="tab"
              aria-selected={mapMode === 'heatmap'}
              title="Cluster Density"
            >
              🔥 {t('layerHeatmap')}
            </button>
            <button
              type="button"
              className={`map-layer-btn ${mapMode === 'zones' ? 'on' : ''}`}
              onClick={() => setMapMode('zones')}
              role="tab"
              aria-selected={mapMode === 'zones'}
              title="Municipal Planning Boundaries"
            >
              🏛️ {t('layerTwinZones')}
            </button>
            <button
              type="button"
              className={`map-layer-btn ${mapMode === 'roads' ? 'on' : ''}`}
              onClick={() => setMapMode('roads')}
              role="tab"
              aria-selected={mapMode === 'roads'}
              title="Monitored Road Corridors"
            >
              🛣️ {t('layerTwinRoads')}
            </button>
            <button
              type="button"
              className={`map-layer-btn ${mapMode === 'drainage' ? 'on' : ''}`}
              onClick={() => setMapMode('drainage')}
              role="tab"
              aria-selected={mapMode === 'drainage'}
              title="Drainage Outfalls"
            >
              🌊 {t('layerTwinDrainage')}
            </button>
          </div>
        </div>

        {loadingTwinLayer && (
          <div className="banner info small" style={{ margin: '4px 0 0' }}>
            <span className="spinner" style={{ width: 14, height: 14, borderColor: 'rgba(0,0,0,0.2)', borderTopColor: 'var(--brand)' }} />
            <span>Loading PostGIS Digital Twin layer data…</span>
          </div>
        )}

        {/* Inspected Point Risk Card */}
        {inspectedPoint && (
          <div className="card" style={{ margin: '8px 0 0', padding: '12px 14px', background: 'var(--surface)', border: '1.5px solid var(--brand)', boxShadow: 'var(--shadow)' }}>
            <div className="row between">
              <div className="row gap" style={{ margin: 0, gap: 6 }}>
                <span style={{ fontSize: '1.1rem' }}>📍</span>
                <div>
                  <b style={{ fontSize: '0.9rem' }}>{inspectedPoint.name}</b>
                  <div className="small muted">{inspectedPoint.lat.toFixed(4)}, {inspectedPoint.lng.toFixed(4)}</div>
                </div>
              </div>
              <button
                type="button"
                className="btn small ghost"
                style={{ padding: '2px 6px', minHeight: 24 }}
                onClick={() => { setInspectedPoint(null); setInspectedRisk(null) }}
                aria-label="Close inspection"
              >
                ✕
              </button>
            </div>

            {inspectingRisk ? (
              <div className="row gap" style={{ margin: '8px 0 4px', fontSize: '0.84rem' }}>
                <span className="spinner" style={{ width: 14, height: 14, borderColor: 'rgba(0,0,0,0.2)', borderTopColor: 'var(--brand)' }} />
                <span>Evaluating multi-factor waterlogging risk…</span>
              </div>
            ) : inspectedRisk ? (
              <div style={{ marginTop: 8 }}>
                <div className="row wrap gap" style={{ margin: '4px 0 8px', gap: 6 }}>
                  <span className="pill" style={{ background: RISK_HEX[inspectedRisk.riskLevel] || '#64748B', fontWeight: 700 }}>
                    {t('riskLevel_' + (inspectedRisk.riskLevel || 'UNKNOWN'))}
                  </span>
                  <span className="pill" style={{ background: '#1E293B', fontSize: '0.75rem' }}>
                    🎯 Score: <b>{inspectedRisk.riskScore ?? 0}/100</b>
                  </span>
                  <span className="tag">
                    🌧️ 6h Rain: <b>{inspectedRisk.rainfallNext6hMm?.toFixed(1) || 0} mm</b>
                  </span>
                  <span className="tag">
                    📊 Hotspot Proneness: <b>{inspectedRisk.historicalProneness ?? 0}/5</b>
                  </span>
                  {inspectedRisk.activeReportCount > 0 && (
                    <span className="tag" style={{ background: '#FEE2E2', color: '#991B1B' }}>
                      ⚠️ {inspectedRisk.activeReportCount} Active Reports (Max {inspectedRisk.maxObservedDepthFt?.toFixed(1)} ft)
                    </span>
                  )}
                </div>

                <p className="small" style={{ margin: '4px 0 8px', lineHeight: 1.45 }}>
                  {lang === 'hi' && inspectedRisk.summaryHi ? inspectedRisk.summaryHi : (inspectedRisk.summaryEn || '')}
                </p>

                <div className="row gap" style={{ margin: '6px 0 0' }}>
                  <button
                    type="button"
                    className="btn small primary"
                    style={{ flex: 1, minHeight: 34, fontSize: '0.82rem' }}
                    onClick={() => navigate('/route', { state: { from: { lat: inspectedPoint.lat, lng: inspectedPoint.lng, name: inspectedPoint.name } } })}
                  >
                    🧭 {t('routeFromHere')}
                  </button>
                  <button
                    type="button"
                    className="btn small ghost"
                    style={{ flex: 1, minHeight: 34, fontSize: '0.82rem' }}
                    onClick={() => navigate('/report', { state: { lat: inspectedPoint.lat, lng: inspectedPoint.lng, placeName: inspectedPoint.name } })}
                  >
                    📷 {t('reportHere')}
                  </button>
                </div>
                <div className="small muted" style={{ fontSize: '0.72rem', marginTop: 6 }}>
                  ℹ️ {t('pointRiskNote')}
                </div>
              </div>
            ) : null}
          </div>
        )}

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
              {mapMode === 'heatmap' ? t('heatmapTitle') : mapMode === 'predicted' ? t('predictiveTitle') : mapMode === 'zones' ? t('layerTwinZones') : mapMode === 'roads' ? t('layerTwinRoads') : mapMode === 'drainage' ? t('layerTwinDrainage') : t('legend_title')}
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
          ) : mapMode === 'heatmap' ? (
            <ul className="map-legend-items">
              <li><span className="dot" style={{ background: '#F59E0B' }} /> {t('heatmapLow')}</li>
              <li><span className="dot" style={{ background: '#EA580C' }} /> {t('heatmapMed')}</li>
              <li><span className="dot" style={{ background: '#DC2626' }} /> {t('heatmapHigh')}</li>
            </ul>
          ) : mapMode === 'zones' ? (
            <ul className="map-legend-items">
              <li><span className="dot" style={{ background: '#16A34A' }} /> Verified Municipal Boundary</li>
              <li><span className="dot" style={{ background: '#3B82F6' }} /> Approximate Boundary</li>
            </ul>
          ) : mapMode === 'roads' ? (
            <ul className="map-legend-items">
              <li><span className="line" style={{ background: '#6366F1', width: 14, height: 3, display: 'inline-block' }} /> Monitored Arterial Highway</li>
              <li><span className="line" style={{ background: '#DC2626', width: 14, height: 3, display: 'inline-block', borderStyle: 'dashed' }} /> Critical Underpass Sump</li>
            </ul>
          ) : (
            <ul className="map-legend-items">
              <li><span className="dot" style={{ background: '#0D9488' }} /> Sluice Gate Outfall (Unmetered)</li>
              <li><span className="dot" style={{ background: '#0284C7' }} /> Stormwater Sump / Pump</li>
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
