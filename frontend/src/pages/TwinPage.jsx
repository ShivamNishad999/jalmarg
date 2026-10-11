import { useEffect, useState, useCallback, useMemo } from 'react'
import { MapContainer, TileLayer, Marker, Popup, Polyline, Circle, Polygon, useMap } from 'react-leaflet'
import L from 'leaflet'
import { api, NCR_CENTER } from '../api'
import { useLang } from '../i18n'
import { SEV_HEX, RISK_HEX } from '../components/shared'

function MapHelper({ center, zoom }) {
  const map = useMap()
  useEffect(() => {
    if (center) map.flyTo(center, zoom || 12, { duration: 0.6 })
  }, [center, zoom, map])
  return null
}

const zoneIcon = (color = '#2563EB') => L.divIcon({
  className: 'jm-twin-marker',
  html: `<div style="background:${color};width:18px;height:18px;border-radius:50%;border:2px solid #FFFFFF;box-shadow:0 0 10px ${color}88;display:flex;align-items:center;justify-content:center;color:white;font-size:10px;font-weight:bold;">Z</div>`,
  iconSize: [20, 20],
  iconAnchor: [10, 10]
})

export default function TwinPage() {
  const { t, lang } = useLang()
  const [tab, setTab] = useState('overview') // 'overview' | 'time' | 'scenario' | 'operations' | 'quality'
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [twinStatus, setTwinStatus] = useState(null)
  const [zones, setZones] = useState([])
  const [roads, setRoads] = useState([])
  const [drainageAssets, setDrainageAssets] = useState([])
  const [sources, setSources] = useState([])
  const [snapshots, setSnapshots] = useState([])
  const [selectedSnapshot, setSelectedSnapshot] = useState(null)
  const [activeZone, setActiveZone] = useState(null)

  // What-If Scenario State
  const [scenarioForm, setScenarioForm] = useState({
    title: 'Monsoon Cloudburst Simulation',
    scenarioType: 'RAINFALL_STRESS',
    rainfallMm: 65,
    rainfallIntensityMmPerHour: 35,
    durationHours: 3,
    drainageCapacityFactor: 0.5,
    roadClosureSimulated: true,
    availableResponseTeams: 4,
    zoneId: ''
  })
  const [scenarioFormErrors, setScenarioFormErrors] = useState({})
  const [runningScenario, setRunningScenario] = useState(false)
  const [scenarioResult, setScenarioResult] = useState(null)
  const [scenariosList, setScenariosList] = useState([])

  // Operations / Municipal Authentication State
  const [authToken, setAuthToken] = useState(() => (typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('jm_auth_token') || '' : ''))
  const [authUser, setAuthUser] = useState(() => {
    try {
      const saved = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('jm_auth_user') : null
      return saved ? JSON.parse(saved) : null
    } catch {
      return null
    }
  })
  const [staffLoginForm, setStaffLoginForm] = useState({ username: 'operator_pwd', password: '' })
  const [staffLoginLoading, setStaffLoginLoading] = useState(false)
  const [staffLoginError, setStaffLoginError] = useState('')

  // Incident & Dispatch State
  const [incidents, setIncidents] = useState([])
  const [incidentsLoading, setIncidentsLoading] = useState(false)
  const [incidentsError, setIncidentsError] = useState(null)
  const [resources, setResources] = useState([])
  const [auditLogs, setAuditLogs] = useState([])
  const [selectedIncident, setSelectedIncident] = useState(null)
  const [statusFilter, setStatusFilter] = useState('')
  const [newIncidentForm, setNewIncidentForm] = useState({
    title: '',
    description: '',
    incidentType: 'SURFACE_FLOODING',
    severity: 'HIGH',
    latitude: 28.6289,
    longitude: 77.2065,
    zoneId: '',
    waterDepthMeters: '',
    roadBlockage: true
  })
  const [assignForm, setAssignForm] = useState({ assignedUnit: 'NDRF Quick Response Team 02', equipmentDetails: '1x Heavy Dewatering Pump (5000 LPM)' })
  const [statusUpdateForm, setStatusUpdateForm] = useState({ targetStatus: 'IN_PROGRESS', resolutionNotes: '' })
  const [actionMessage, setActionMessage] = useState(null)

  const isOperator = useMemo(() => {
    if (!authUser || !authUser.role) return false
    return authUser.role === 'MUNICIPAL_OPERATOR' || authUser.role === 'MUNICIPAL_ADMIN'
  }, [authUser])

  const loadTwinData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [statusRes, zonesRes, roadsRes, drainsRes, sourcesRes, snapRes] = await Promise.all([
        api.twinStatus().catch(() => null),
        api.twinZones().catch(() => []),
        api.twinRoads().catch(() => []),
        api.twinDrainage().catch(() => []),
        api.twinSources().catch(() => []),
        api.twinSnapshots().catch(() => [])
      ])
      setTwinStatus(statusRes)
      setZones(zonesRes || [])
      setRoads(roadsRes || [])
      setDrainageAssets(drainsRes || [])
      setSources(sourcesRes || [])
      setSnapshots(snapRes || [])
      if (snapRes && snapRes.length > 0) {
        setSelectedSnapshot(snapRes[0])
      }
    } catch (e) {
      setError(e.message || 'Failed to load Digital Twin model')
    } finally {
      setLoading(false)
    }
  }, [])

  const loadOperationsData = useCallback(async () => {
    if (!authToken) {
      setIncidents([])
      setResources([])
      setAuditLogs([])
      return
    }
    setIncidentsLoading(true)
    setIncidentsError(null)
    try {
      const [incRes, resRes, auditRes] = await Promise.all([
        api.authorityIncidents({ status: statusFilter, token: authToken }),
        api.authorityResources({ token: authToken }).catch(() => []),
        api.authorityAuditLogs({ token: authToken }).catch(() => [])
      ])
      setIncidents(incRes || [])
      setResources(resRes || [])
      setAuditLogs(auditRes || [])
    } catch (e) {
      if (e.status === 401) {
        setIncidentsError('Session expired or unauthorized. Please re-authenticate.')
        handleStaffLogout()
      } else if (e.status === 403) {
        setIncidentsError('Access forbidden: Insufficient role permissions.')
      } else {
        setIncidentsError(e.message || 'Could not load incident queue.')
      }
    } finally {
      setIncidentsLoading(false)
    }
  }, [statusFilter, authToken])

  const loadScenarios = useCallback(async () => {
    try {
      const list = await api.twinScenarios().catch(() => [])
      setScenariosList(list || [])
    } catch (e) {
      console.warn('Could not load scenarios:', e)
    }
  }, [])

  useEffect(() => {
    loadTwinData()
    loadScenarios()
  }, [loadTwinData, loadScenarios])

  useEffect(() => {
    if (tab === 'operations') {
      loadOperationsData()
    }
  }, [tab, loadOperationsData])

  const handleStaffLogin = async (e) => {
    if (e) e.preventDefault()
    setStaffLoginLoading(true)
    setStaffLoginError('')
    try {
      const res = await api.authLogin(staffLoginForm.username, staffLoginForm.password)
      const token = res.token
      const sessionUser = res.session || {
        username: res.username || staffLoginForm.username,
        fullName: res.fullName || 'Municipal Officer',
        role: res.role || 'MUNICIPAL_OPERATOR',
        department: res.department || 'Municipal Operations'
      }
      setAuthToken(token)
      setAuthUser(sessionUser)
      if (typeof sessionStorage !== 'undefined') {
        sessionStorage.setItem('jm_auth_token', token)
        sessionStorage.setItem('jm_auth_user', JSON.stringify(sessionUser))
      }
      setStaffLoginForm({ username: staffLoginForm.username, password: '' })
      setActionMessage({ type: 'success', text: `Authenticated successfully as ${sessionUser.fullName || sessionUser.username} (${sessionUser.role})` })
    } catch (err) {
      setStaffLoginError(err.message || 'Authentication failed. Please verify credentials.')
    } finally {
      setStaffLoginLoading(false)
    }
  }

  const handleStaffLogout = () => {
    if (authToken) {
      api.authLogout(authToken).catch(() => {})
    }
    setAuthToken('')
    setAuthUser(null)
    if (typeof sessionStorage !== 'undefined') {
      sessionStorage.removeItem('jm_auth_token')
      sessionStorage.removeItem('jm_auth_user')
    }
    setIncidents([])
    setSelectedIncident(null)
    setActionMessage({ type: 'info', text: 'Municipal session cleared.' })
  }

  const validateScenarioForm = () => {
    const errors = {}
    if (!scenarioForm.title || !scenarioForm.title.trim()) {
      errors.title = 'Scenario title is required.'
    }
    const rain = Number(scenarioForm.rainfallMm)
    if (isNaN(rain) || rain < 0 || rain > 500) {
      errors.rainfallMm = 'Rainfall total must be between 0 and 500 mm.'
    }
    const intensity = Number(scenarioForm.rainfallIntensityMmPerHour)
    if (isNaN(intensity) || intensity < 0 || intensity > 200) {
      errors.rainfallIntensityMmPerHour = 'Rainfall intensity must be between 0 and 200 mm/h.'
    }
    const duration = Number(scenarioForm.durationHours)
    if (isNaN(duration) || duration < 1 || duration > 72) {
      errors.durationHours = 'Duration must be between 1 and 72 hours.'
    }
    const teams = Number(scenarioForm.availableResponseTeams)
    if (isNaN(teams) || teams < 0 || teams > 50) {
      errors.availableResponseTeams = 'Dewatering teams count must be between 0 and 50.'
    }
    setScenarioFormErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleRunScenario = async (e) => {
    e.preventDefault()
    if (!validateScenarioForm()) return

    if (!authToken) {
      setActionMessage({
        type: 'error',
        text: 'Simulation authorization required: Please sign in with an authorized Municipal Operator account to run What-If simulations.'
      })
      return
    }

    setRunningScenario(true)
    setActionMessage(null)
    try {
      const payload = {
        scenarioName: scenarioForm.title.trim(),
        rainfallTotalMm: Number(scenarioForm.rainfallMm),
        rainfallIntensityMmPerHour: Number(scenarioForm.rainfallIntensityMmPerHour),
        durationHours: Math.max(1, Math.min(72, Number(scenarioForm.durationHours))),
        assumedDrainageCapacityPercent: Math.max(0, Math.min(100, Number(scenarioForm.drainageCapacityFactor) * 100)),
        closedRoadSegmentCodes: scenarioForm.roadClosureSimulated ? ['MINTO_UNDERPASS'] : [],
        availablePumpCount: Math.max(0, Math.min(50, Number(scenarioForm.availableResponseTeams))),
        targetZoneId: scenarioForm.zoneId ? Number(scenarioForm.zoneId) : null
      }
      const res = await api.runScenario(payload, { token: authToken })
      setScenarioResult(res)
      loadScenarios()
      setActionMessage({
        type: 'success',
        text: `Hypothetical Scenario "${res.scenarioName || 'Simulation'}" executed successfully.`
      })
    } catch (err) {
      setActionMessage({
        type: 'error',
        text: err.message || 'Scenario run execution failed.'
      })
    } finally {
      setRunningScenario(false)
    }
  }

  const handleCreateIncident = async (e) => {
    e.preventDefault()
    setActionMessage(null)
    if (!isOperator) {
      setActionMessage({ type: 'error', text: 'Action forbidden: Only Municipal Operators or Admins can register incidents.' })
      return
    }
    try {
      const payload = {
        ...newIncidentForm,
        locationName: newIncidentForm.title.trim() || 'Delhi NCR Incident Site',
        latitude: Number(newIncidentForm.latitude),
        longitude: Number(newIncidentForm.longitude),
        zoneId: newIncidentForm.zoneId ? Number(newIncidentForm.zoneId) : null,
        waterDepthMeters: newIncidentForm.waterDepthMeters ? Number(newIncidentForm.waterDepthMeters) : null
      }
      const created = await api.createAuthorityIncident(payload, { token: authToken })
      setActionMessage({ type: 'success', text: `Municipal Incident created: ${created.incidentNumber}` })
      setNewIncidentForm({
        title: '',
        description: '',
        incidentType: 'SURFACE_FLOODING',
        severity: 'HIGH',
        latitude: 28.6289,
        longitude: 77.2065,
        zoneId: '',
        waterDepthMeters: '',
        roadBlockage: true
      })
      loadOperationsData()
    } catch (err) {
      setActionMessage({ type: 'error', text: err.message || 'Incident creation failed.' })
    }
  }

  const handleAssignIncident = async (incidentId) => {
    if (!incidentId) return
    setActionMessage(null)
    if (!isOperator) {
      setActionMessage({ type: 'error', text: 'Action forbidden: Only Municipal Operators or Admins can dispatch resources.' })
      return
    }
    try {
      await api.assignAuthorityIncident(incidentId, assignForm, { token: authToken })
      setActionMessage({ type: 'success', text: `Resource assigned to incident ${incidentId} successfully.` })
      loadOperationsData()
    } catch (err) {
      setActionMessage({ type: 'error', text: err.message || 'Assignment failed.' })
    }
  }

  const handleStatusUpdate = async (incidentId) => {
    if (!incidentId) return
    setActionMessage(null)
    if (!isOperator) {
      setActionMessage({ type: 'error', text: 'Action forbidden: Only Municipal Operators or Admins can transition incident state.' })
      return
    }
    try {
      await api.updateAuthorityIncidentStatus(incidentId, statusUpdateForm, { token: authToken })
      setActionMessage({ type: 'success', text: `Status updated to ${statusUpdateForm.targetStatus}.` })
      loadOperationsData()
    } catch (err) {
      setActionMessage({ type: 'error', text: err.message || 'Status transition failed.' })
    }
  }

  return (
    <div className="twin-page-container">
      {/* Top Header Bar */}
      <header className="twin-header-bar">
        <div className="twin-title-group">
          <div className="twin-badge">MUNICIPAL DIGITAL TWIN</div>
          <h2>Delhi NCR Urban Waterlogging Twin</h2>
          <p className="twin-subtitle">
            Evidence-backed geospatial state engine, what-if scenario simulator & municipal incident lifecycle
          </p>
        </div>
        <div className="twin-header-actions">
          <div className="twin-status-pill">
            <span className="status-dot online"></span>
            <span>SRID 4326 PostGIS Verified</span>
            <span className="status-divider">•</span>
            <span>{twinStatus ? `${twinStatus.totalZonesCount || zones.length} Zones / ${twinStatus.totalRoadSegmentsCount || roads.length} Corridors / ${twinStatus.totalDrainageAssetsCount || drainageAssets.length} Drains / ${twinStatus.totalRegisteredSourcesCount || sources.length} Sources` : 'Connecting…'}</span>
          </div>

          {authUser ? (
            <div className="twin-staff-chip">
              <span className="staff-icon">👤</span>
              <span className="staff-name"><strong>{authUser.fullName || authUser.username}</strong></span>
              <span className={`staff-role-badge ${authUser.role?.toLowerCase()}`}>{authUser.role}</span>
              <button className="btn btn-xs btn-outline" onClick={handleStaffLogout} title="Sign Out">Sign Out</button>
            </div>
          ) : (
            <div className="twin-unauth-chip">
              <span className="unauth-dot"></span>
              <span className="unauth-lbl">Public Visitor Mode</span>
            </div>
          )}
        </div>
      </header>

      {/* Navigation Sub-Tabs */}
      <nav className="twin-nav-tabs">
        <button className={tab === 'overview' ? 'active' : ''} onClick={() => setTab('overview')}>
          🌐 Twin Overview & Map
        </button>
        <button className={tab === 'time' ? 'active' : ''} onClick={() => setTab('time')}>
          ⏱️ Time Explorer & State Isolation
        </button>
        <button className={tab === 'scenario' ? 'active' : ''} onClick={() => setTab('scenario')}>
          🧪 What-If Scenario Sandbox
        </button>
        <button className={tab === 'operations' ? 'active' : ''} onClick={() => setTab('operations')}>
          🚨 Municipal Incident Queue {incidents.length > 0 ? `(${incidents.length})` : ''}
        </button>
        <button className={tab === 'quality' ? 'active' : ''} onClick={() => setTab('quality')}>
          🛡️ Data Provenance & Integrity
        </button>
      </nav>

      {actionMessage && (
        <div className={`twin-alert-toast ${actionMessage.type}`}>
          <span>{actionMessage.text}</span>
          <button onClick={() => setActionMessage(null)}>✕</button>
        </div>
      )}

      {loading ? (
        <div className="twin-loading-card">
          <div className="spinner"></div>
          <p>Loading Digital Twin infrastructure layers and risk models…</p>
        </div>
      ) : error ? (
        <div className="twin-error-card">
          <p>{error}</p>
          <button className="btn btn-primary" onClick={loadTwinData}>Retry</button>
        </div>
      ) : (
        <div className="twin-content-grid">
          {/* TAB 1: OVERVIEW */}
          {tab === 'overview' && (
            <div className="twin-overview-layout">
              <div className="twin-map-pane">
                <div className="twin-map-wrapper">
                  <MapContainer center={NCR_CENTER} zoom={11} scrollWheelZoom={false} style={{ height: '520px', width: '100%', borderRadius: '12px' }}>
                    <TileLayer
                      url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                      attribution="&copy; OpenStreetMap contributors"
                    />
                    <MapHelper center={activeZone ? [activeZone.centroidLat ?? activeZone.centerLat, activeZone.centroidLng ?? activeZone.centerLng] : null} zoom={activeZone ? 13 : 11} />

                    {/* Render Zone Polygons and Centroid Markers */}
                    {zones.map((z) => {
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
                        <div key={`zone-layer-${z.id}`}>
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
                            icon={zoneIcon(isVerified ? '#16A34A' : '#3B82F6')}
                            eventHandlers={{ click: () => setActiveZone(z) }}
                          >
                            <Popup>
                              <div className="twin-popup">
                                <h4>{z.name || z.zoneName} ({z.zoneCode})</h4>
                                <p><strong>Municipality:</strong> {z.municipalityName}</p>
                                <p><strong>Boundary Status:</strong> <span className="tag">{z.boundaryType}</span></p>
                                {z.areaSqKm && <p><strong>Area:</strong> {z.areaSqKm} km²</p>}
                              </div>
                            </Popup>
                          </Marker>
                        </div>
                      )
                    })}
                  </MapContainer>
                </div>

                <div className="twin-legend-card">
                  <h4>Map Layers Legend</h4>
                  <div className="legend-items">
                    <span className="legend-chip"><span className="dot green"></span> Official Zone Boundary</span>
                    <span className="legend-chip"><span className="dot blue"></span> Statutory NCR Extent</span>
                    <span className="legend-chip">🌊 Drainage Outfall</span>
                    <span className="legend-chip">⚙️ Pumping Station</span>
                    <span className="legend-chip">⚠️ Critical Underpass</span>
                  </div>
                </div>
              </div>

              <div className="twin-sidebar-pane">
                <div className="twin-card">
                  <h3>🏛️ Municipal Zones ({zones.length})</h3>
                  <div className="twin-zone-list">
                    {zones.map((z) => (
                      <div
                        key={z.id}
                        className={`twin-zone-item ${activeZone?.id === z.id ? 'selected' : ''}`}
                        onClick={() => setActiveZone(z)}
                      >
                        <div className="zone-item-header">
                          <strong>{z.zoneName}</strong>
                          <span className="zone-code">{z.zoneCode}</span>
                        </div>
                        <div className="zone-item-meta">
                          <span>{z.municipalityName}</span>
                          <span>{z.areaSqKm ? `${z.areaSqKm} km²` : 'NCR'}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="twin-card">
                  <h3>🛣️ Infrastructure Inventory</h3>
                  <div className="twin-road-summary">
                    <div className="stat-box">
                      <span className="num">{roads.length}</span>
                      <span className="lbl">Mapped Corridors</span>
                    </div>
                    <div className="stat-box">
                      <span className="num">{drainageAssets.length}</span>
                      <span className="lbl">Drainage Assets</span>
                    </div>
                  </div>
                  <p className="notice-text" style={{ marginTop: '12px', fontSize: '0.8rem', color: 'var(--muted)' }}>
                    {twinStatus?.drainageTelemetryNotice || 'Static infrastructure inventory backed by PostGIS 3.6.2.'}
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: TIME EXPLORER */}
          {tab === 'time' && (
            <div className="twin-time-layout">
              <div className="twin-card">
                <h3>⏱️ Temporal Twin Snapshots ({snapshots.length})</h3>
                <p className="form-subtext">
                  Immutable point-in-time state records preserving input provenance, model versions, and environmental observations.
                </p>

                {snapshots.length === 0 ? (
                  <div className="empty-state-card">No published snapshots found. Snapshots are produced during live monitoring cycles.</div>
                ) : (
                  <div className="snapshot-grid">
                    {snapshots.map((s) => (
                      <div
                        key={s.id}
                        className={`snapshot-card ${s.snapshotType.toLowerCase()} ${selectedSnapshot?.id === s.id ? 'active' : ''}`}
                        onClick={() => setSelectedSnapshot(s)}
                      >
                        <div className="snap-top">
                          <span className={`snap-badge ${s.snapshotType.toLowerCase()}`}>{s.snapshotType}</span>
                          <span className="snap-time">{new Date(s.snapshotTimestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                        </div>
                        <h4 className="snap-title">Snapshot #{s.id}: Zone {s.zoneName || 'NCR Unified'}</h4>
                        <div className="snap-score-row">
                          <span className="risk-score" style={{ color: RISK_HEX[s.riskLevel] || '#2563EB' }}>
                            Risk: {s.riskScore?.toFixed(2)} ({s.riskLevel})
                          </span>
                          <span className="status-label">{s.riskStatus}</span>
                        </div>
                        <div className="snap-meta">
                          <div>Model: <code>{s.modelVersion}</code></div>
                          <div className="provenance-tag">Provenances: {s.provenanceClassification}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {selectedSnapshot && (
                <div className="snapshot-detail-card">
                  <h4>🔬 Snapshot Integrity & Audit Inspector (ID: {selectedSnapshot.id})</h4>
                  <div className="detail-grid">
                    <div><strong>State Classification:</strong> <span className="badge-highlight">{selectedSnapshot.snapshotType}</span></div>
                    <div><strong>Published Timestamp:</strong> {new Date(selectedSnapshot.snapshotTimestamp).toLocaleString()}</div>
                    <div><strong>Risk Score / Level:</strong> {selectedSnapshot.riskScore} ({selectedSnapshot.riskLevel})</div>
                    <div><strong>Data Freshness Index:</strong> {selectedSnapshot.freshnessMinutes != null ? `${selectedSnapshot.freshnessMinutes} mins ago` : 'Real-time sync'}</div>
                    <div><strong>Contributing Evidence Count:</strong> {selectedSnapshot.contributingReportCount || 0} citizen report(s)</div>
                    <div><strong>Observed Rainfall:</strong> {selectedSnapshot.observedRainfallMm != null ? `${selectedSnapshot.observedRainfallMm} mm` : 'None measured (0 mm)'}</div>
                    <div><strong>Forecast Rainfall (6h):</strong> {selectedSnapshot.forecastRainfallMm != null ? `${selectedSnapshot.forecastRainfallMm} mm` : 'Forecast sync'}</div>
                    <div><strong>Payload Hash (SHA-256):</strong> <code>{selectedSnapshot.payloadChecksum || 'SHA256_VERIFIED'}</code></div>
                  </div>
                  <div className="evidence-refs-box">
                    <strong>Evidence References & Data Quality:</strong>
                    <p>{selectedSnapshot.evidenceReferences || 'Open-Meteo hourly forecast + Copernicus GLO-30 contextual DEM elevation'}</p>
                    <p className="missing-data-alert">
                      <strong>Missing Data Flags:</strong> {selectedSnapshot.missingDataFlags || 'No municipal sensor depth telemetry available — strictly rule-derived estimate.'}
                    </p>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* TAB 3: WHAT-IF SCENARIO SANDBOX */}
          {tab === 'scenario' && (
            <div className="twin-scenario-layout">
              <div className="scenario-form-pane">
                <div className="twin-card">
                  <div className="card-header-badge">
                    <span className="badge-simulation">HYPOTHETICAL SIMULATION</span>
                  </div>
                  <h3>🧪 What-If Scenario Sandbox</h3>
                  <p className="form-subtext">
                    Simulate extreme weather shocks, drainage restrictions, and response deployments without mutating live state.
                  </p>

                  {!authToken && (
                    <div className="scenario-auth-notice">
                      <span className="notice-icon">ℹ️</span>
                      <div className="notice-text">
                        <strong>Municipal Operator Clearance Recommended</strong>
                        <p>Executing scenario simulations requires authorized staff credentials. Sign in via Tab 4 or the header to run simulations.</p>
                      </div>
                    </div>
                  )}

                  <form onSubmit={handleRunScenario}>
                    {/* Section 1: Weather Assumptions */}
                    <div className="form-section-card">
                      <h4 className="section-title">🌧️ Weather Assumptions</h4>
                      <div className="form-group">
                        <label>Scenario Title <span className="req">*</span></label>
                        <input
                          type="text"
                          className={scenarioFormErrors.title ? 'input-error' : ''}
                          value={scenarioForm.title}
                          onChange={(e) => setScenarioForm({ ...scenarioForm, title: e.target.value })}
                          placeholder="e.g. Monsoon Cloudburst Over Central Delhi"
                          required
                        />
                        {scenarioFormErrors.title && <small className="error-text">{scenarioFormErrors.title}</small>}
                      </div>

                      <div className="form-row-2">
                        <div className="form-group">
                          <label>Simulated Rainfall Total (mm) <span className="req">*</span></label>
                          <input
                            type="number"
                            min="0"
                            max="500"
                            step="1"
                            className={scenarioFormErrors.rainfallMm ? 'input-error' : ''}
                            value={scenarioForm.rainfallMm}
                            onChange={(e) => setScenarioForm({ ...scenarioForm, rainfallMm: e.target.value })}
                            required
                          />
                          <small className="field-hint">Range: 0 – 500 mm</small>
                          {scenarioFormErrors.rainfallMm && <small className="error-text">{scenarioFormErrors.rainfallMm}</small>}
                        </div>
                        <div className="form-group">
                          <label>Rainfall Intensity (mm/h) <span className="req">*</span></label>
                          <input
                            type="number"
                            min="0"
                            max="200"
                            step="1"
                            className={scenarioFormErrors.rainfallIntensityMmPerHour ? 'input-error' : ''}
                            value={scenarioForm.rainfallIntensityMmPerHour}
                            onChange={(e) => setScenarioForm({ ...scenarioForm, rainfallIntensityMmPerHour: e.target.value })}
                            required
                          />
                          <small className="field-hint">Range: 0 – 200 mm/h</small>
                          {scenarioFormErrors.rainfallIntensityMmPerHour && <small className="error-text">{scenarioFormErrors.rainfallIntensityMmPerHour}</small>}
                        </div>
                      </div>

                      <div className="form-group">
                        <label>Scenario Duration (hours) <span className="req">*</span></label>
                        <input
                          type="number"
                          min="1"
                          max="72"
                          step="1"
                          className={scenarioFormErrors.durationHours ? 'input-error' : ''}
                          value={scenarioForm.durationHours}
                          onChange={(e) => setScenarioForm({ ...scenarioForm, durationHours: e.target.value })}
                          required
                        />
                        <small className="field-hint">Range: 1 – 72 hours</small>
                        {scenarioFormErrors.durationHours && <small className="error-text">{scenarioFormErrors.durationHours}</small>}
                      </div>
                    </div>

                    {/* Section 2: Infrastructure Assumptions */}
                    <div className="form-section-card">
                      <h4 className="section-title">🏗️ Infrastructure Assumptions</h4>
                      <div className="form-group">
                        <label>Target Municipal Zone</label>
                        <select
                          value={scenarioForm.zoneId}
                          onChange={(e) => setScenarioForm({ ...scenarioForm, zoneId: e.target.value })}
                        >
                          <option value="">NCR Unified (All Zones)</option>
                          {zones.map((z) => (
                            <option key={z.id} value={z.id}>{z.zoneName} ({z.zoneCode})</option>
                          ))}
                        </select>
                      </div>

                      <div className="form-group">
                        <label>
                          Assumed Drainage Availability Factor: <strong>{(Number(scenarioForm.drainageCapacityFactor) * 100).toFixed(0)}%</strong>
                        </label>
                        <input
                          type="range"
                          min="0.0"
                          max="1.0"
                          step="0.05"
                          value={scenarioForm.drainageCapacityFactor}
                          onChange={(e) => setScenarioForm({ ...scenarioForm, drainageCapacityFactor: e.target.value })}
                        />
                        <small className="field-hint">Note: Drainage capacity is a hypothetical simulation parameter, not measured flow rate.</small>
                      </div>

                      <div className="form-group checkbox-group">
                        <label>
                          <input
                            type="checkbox"
                            checked={scenarioForm.roadClosureSimulated}
                            onChange={(e) => setScenarioForm({ ...scenarioForm, roadClosureSimulated: e.target.checked })}
                          />
                          Simulate Underpass Inundation Closures (Minto Bridge / Pul Prahladpur)
                        </label>
                      </div>
                    </div>

                    {/* Section 3: Response Resources */}
                    <div className="form-section-card">
                      <h4 className="section-title">🚒 Response Resources</h4>
                      <div className="form-group">
                        <label>Available Dewatering Response Teams</label>
                        <input
                          type="number"
                          min="0"
                          max="50"
                          value={scenarioForm.availableResponseTeams}
                          onChange={(e) => setScenarioForm({ ...scenarioForm, availableResponseTeams: e.target.value })}
                        />
                        <small className="field-hint">Range: 0 – 50 pump deployment units</small>
                        {scenarioFormErrors.availableResponseTeams && <small className="error-text">{scenarioFormErrors.availableResponseTeams}</small>}
                      </div>
                    </div>

                    <button type="submit" className="btn btn-primary btn-block" disabled={runningScenario}>
                      {runningScenario ? 'Running Deterministic Simulation…' : '🚀 Run What-If Simulation'}
                    </button>
                  </form>
                </div>
              </div>

              <div className="scenario-result-pane">
                {scenarioResult ? (
                  <div className="twin-card scenario-result-card">
                    <div className="scenario-tag-bar">
                      <span className="badge-simulation">HYPOTHETICAL SCENARIO — NOT AN OBSERVED EVENT</span>
                      <span className="scenario-code">Run #{scenarioResult.id || '1'}</span>
                    </div>
                    <h3>{scenarioResult.scenarioName}</h3>
                    <p className="scenario-desc">
                      Simulated: {scenarioResult.rainfallTotalMm ?? scenarioResult.rainfallMm} mm ({scenarioResult.rainfallIntensityMmPerHour} mm/h) · Zone: {scenarioResult.targetZoneName || 'NCR Unified'} · Assumed Drainage: {scenarioResult.assumedDrainageCapacityPercent != null ? `${scenarioResult.assumedDrainageCapacityPercent}%` : '50%'}
                    </p>

                    <div className="scenario-metrics-comparison">
                      <div className="metric-col baseline">
                        <span className="lbl">Baseline Risk</span>
                        <span className="val">{scenarioResult.baselineRiskScore != null ? Number(scenarioResult.baselineRiskScore).toFixed(1) : '10.0'}</span>
                        <small className="metric-sub">Dry baseline index</small>
                      </div>
                      <div className="metric-arrow">➔</div>
                      <div className="metric-col simulated">
                        <span className="lbl">Simulated Risk</span>
                        <span className="val">{scenarioResult.simulatedRiskScore != null ? Number(scenarioResult.simulatedRiskScore).toFixed(1) : '0.0'}</span>
                        <small className="metric-sub">Estimated stress</small>
                      </div>
                      <div className="metric-col delta">
                        <span className="lbl">Risk Delta</span>
                        {(() => {
                          const delta = scenarioResult.deltaRiskScore != null ? scenarioResult.deltaRiskScore : (scenarioResult.riskDeltaScore || 0)
                          return (
                            <span className={`val ${delta > 0 ? 'inc' : 'dec'}`}>
                              {delta > 0 ? `+${Number(delta).toFixed(1)}` : Number(delta).toFixed(1)}
                            </span>
                          )
                        })()}
                        <small className="metric-sub">Relative change</small>
                      </div>
                    </div>

                    <div className="scenario-summary-box">
                      <h4>📊 Impact & Mitigation Breakdown</h4>
                      <div className="breakdown-grid">
                        <div className="breakdown-item">
                          <span className="bk-lbl">Affected Road Segments:</span>
                          <span className="bk-val">{scenarioResult.affectedRoadsCount ?? 0} corridors</span>
                        </div>
                        <div className="breakdown-item">
                          <span className="bk-lbl">Target Municipality:</span>
                          <span className="bk-val">{scenarioResult.targetZoneName || 'NCR Regional'}</span>
                        </div>
                        <div className="breakdown-item">
                          <span className="bk-lbl">Heuristic Model:</span>
                          <span className="bk-val">{scenarioResult.modelVersion || 'JALMARG_WHATIF_V1.0'}</span>
                        </div>
                        <div className="breakdown-item">
                          <span className="bk-lbl">Simulated By:</span>
                          <span className="bk-val">{scenarioResult.createdBy || 'Staff Operator'}</span>
                        </div>
                      </div>

                      <div className="scientific-disclaimer">
                        ⚠️ <strong>Scientific Disclaimer:</strong> This computation represents a decision-support heuristic index. It is NOT physically measured water depth and does not replace calibrated 2D hydrodynamic modeling (SWMM/HEC-RAS).
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="twin-card">
                    <h3>📜 Previous Scenario Runs ({scenariosList.length})</h3>
                    {scenariosList.length === 0 ? (
                      <p className="empty-text">No scenarios executed yet. Configure parameters on the left and run a simulation.</p>
                    ) : (
                      <div className="scenario-history-list">
                        {scenariosList.map((sc) => {
                          const delta = sc.deltaRiskScore != null ? sc.deltaRiskScore : (sc.riskDeltaScore || 0)
                          return (
                            <div key={sc.id} className="scenario-history-item" onClick={() => setScenarioResult(sc)}>
                              <div className="hist-top">
                                <strong>{sc.scenarioName}</strong>
                                <span className="badge-simulation-sm">SIMULATION</span>
                              </div>
                              <div className="hist-meta">
                                <span>Rain: {sc.rainfallTotalMm ?? sc.rainfallMm} mm ({sc.rainfallIntensityMmPerHour} mm/h)</span>
                                <span>Risk: {Number(sc.simulatedRiskScore || 0).toFixed(1)} (Δ {delta > 0 ? `+${Number(delta).toFixed(1)}` : Number(delta).toFixed(1)})</span>
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 4: MUNICIPAL OPERATIONS & INCIDENT QUEUE */}
          {tab === 'operations' && (
            <div className="twin-operations-layout">
              {/* If Unauthenticated: Render High-Trust Municipal Access Guard */}
              {!authToken ? (
                <div className="twin-card twin-auth-guard-card">
                  <div className="auth-guard-header">
                    <div className="guard-icon">🛡️</div>
                    <div>
                      <h3>Municipal Incident Management Requires Authorized Staff Access</h3>
                      <p className="auth-guard-subtext">
                        Real-time incident triage, pump dispatching, and lifecycle verification are restricted to verified municipal personnel (PWD Drainage, NDMC Control Room, Delhi Traffic Police).
                      </p>
                    </div>
                  </div>

                  <div className="quick-login-box">
                    <h4>Staff Quick Authentication</h4>
                    <p className="login-hint">Select a provisioned municipal role or enter credentials to authenticate your session:</p>

                    <div className="role-preset-buttons">
                      <button
                        type="button"
                        className={`preset-btn ${staffLoginForm.username === 'operator_pwd' ? 'active' : ''}`}
                        onClick={() => setStaffLoginForm({ username: 'operator_pwd', password: '' })}
                      >
                        🚜 PWD Field Operator <code>(operator_pwd)</code>
                      </button>
                      <button
                        type="button"
                        className={`preset-btn ${staffLoginForm.username === 'viewer_traffic' ? 'active' : ''}`}
                        onClick={() => setStaffLoginForm({ username: 'viewer_traffic', password: '' })}
                      >
                        🚔 Traffic Duty Officer <code>(viewer_traffic)</code>
                      </button>
                      <button
                        type="button"
                        className={`preset-btn ${staffLoginForm.username === 'admin_ndmc' ? 'active' : ''}`}
                        onClick={() => setStaffLoginForm({ username: 'admin_ndmc', password: '' })}
                      >
                        🏛️ NDMC Control Room Admin <code>(admin_ndmc)</code>
                      </button>
                    </div>

                    <form onSubmit={handleStaffLogin} className="staff-login-form">
                      <div className="form-row-2">
                        <div className="form-group">
                          <label>Username</label>
                          <input
                            type="text"
                            value={staffLoginForm.username}
                            onChange={(e) => setStaffLoginForm({ ...staffLoginForm, username: e.target.value })}
                            required
                          />
                        </div>
                        <div className="form-group">
                          <label>Password</label>
                          <input
                            type="password"
                            placeholder="Enter password"
                            value={staffLoginForm.password}
                            onChange={(e) => setStaffLoginForm({ ...staffLoginForm, password: e.target.value })}
                            required
                          />
                        </div>
                      </div>

                      {staffLoginError && <div className="login-error-alert">{staffLoginError}</div>}

                      <div className="login-action-row">
                        <button type="submit" className="btn btn-primary" disabled={staffLoginLoading}>
                          {staffLoginLoading ? 'Verifying Credentials…' : '🔐 Authenticate Municipal Session'}
                        </button>
                        <a href="/authority" className="btn btn-secondary">
                          Go to Municipal Authority Console ➔
                        </a>
                      </div>
                    </form>
                  </div>
                </div>
              ) : (
                /* Authenticated State */
                <>
                  <div className="ops-controls-card">
                    <div className="staff-session-bar">
                      <div className="session-info">
                        <span className="session-avatar">🏛️</span>
                        <div>
                          <strong>{authUser.fullName || authUser.username}</strong>
                          <span className="session-meta"> · {authUser.department} · <span className={`role-tag ${authUser.role?.toLowerCase()}`}>{authUser.role}</span></span>
                        </div>
                      </div>
                      <div className="session-actions">
                        {!isOperator && (
                          <span className="view-only-badge">👁️ View-Only Mode (Triage disabled)</span>
                        )}
                        <button className="btn btn-xs btn-outline" onClick={handleStaffLogout}>Sign Out</button>
                      </div>
                    </div>

                    <div className="status-filter-pills">
                      <span>Filter Incidents:</span>
                      {['', 'NEW', 'TRIAGED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED_CLOSED'].map((st) => (
                        <button
                          key={st}
                          className={`pill-btn ${statusFilter === st ? 'active' : ''}`}
                          onClick={() => setStatusFilter(st)}
                        >
                          {st || 'ALL'}
                        </button>
                      ))}
                    </div>
                  </div>

                  {incidentsError && (
                    <div className="twin-alert-toast error">
                      <span>{incidentsError}</span>
                      <button onClick={() => setIncidentsError(null)}>✕</button>
                    </div>
                  )}

                  <div className="ops-main-grid">
                    {/* Incident Queue */}
                    <div className="incident-queue-pane">
                      <div className="pane-header">
                        <h3>🚨 Municipal Incident Queue ({incidents.length})</h3>
                        <button className="btn btn-sm btn-secondary" onClick={loadOperationsData} disabled={incidentsLoading}>
                          {incidentsLoading ? 'Refreshing…' : '🔄 Refresh Queue'}
                        </button>
                      </div>

                      <div className="incident-list">
                        {incidentsLoading ? (
                          <div className="empty-state-card"><div className="spinner-sm"></div> Loading incident queue…</div>
                        ) : incidents.length === 0 ? (
                          <div className="empty-state-card">No incidents found matching current status filter.</div>
                        ) : (
                          incidents.map((inc) => (
                            <div
                              key={inc.id}
                              className={`incident-card sev-${inc.severity?.toLowerCase()} ${selectedIncident?.id === inc.id ? 'active' : ''}`}
                              onClick={() => setSelectedIncident(inc)}
                            >
                              <div className="inc-top">
                                <span className="inc-num">{inc.incidentNumber}</span>
                                <span className={`inc-status-tag ${inc.status?.toLowerCase()}`}>{inc.status}</span>
                                <span className={`inc-sev-tag ${inc.severity?.toLowerCase()}`}>{inc.severity}</span>
                              </div>
                              <h4 className="inc-title">{inc.title}</h4>
                              <div className="inc-meta-row">
                                <span>Priority: <strong>{inc.priorityScore?.toFixed(1)}</strong></span>
                                <span>{inc.zoneName || 'NCR General'}</span>
                                <span>{inc.assignedUnit || 'Unassigned'}</span>
                              </div>
                            </div>
                          ))
                        )}
                      </div>
                    </div>

                    {/* Incident Action & Details Drawer */}
                    <div className="incident-action-pane">
                      {selectedIncident ? (
                        <div className="twin-card incident-detail-card">
                          <div className="drawer-header">
                            <div>
                              <span className="inc-num-lg">{selectedIncident.incidentNumber}</span>
                              <h3>{selectedIncident.title}</h3>
                            </div>
                            <span className={`inc-status-tag ${selectedIncident.status?.toLowerCase()}`}>{selectedIncident.status}</span>
                          </div>
                          <p className="inc-desc">{selectedIncident.description}</p>

                          <div className="inc-stats-grid">
                            <div><strong>Location:</strong> {selectedIncident.latitude?.toFixed(4)}, {selectedIncident.longitude?.toFixed(4)}</div>
                            <div><strong>Zone:</strong> {selectedIncident.zoneName || 'NCR Municipal'}</div>
                            <div><strong>Priority Score:</strong> {selectedIncident.priorityScore?.toFixed(2)}</div>
                            <div><strong>Water Depth:</strong> {selectedIncident.waterDepthMeters != null ? `${selectedIncident.waterDepthMeters} m` : 'Unverified / Unknown'}</div>
                            <div><strong>Road Blocked:</strong> {selectedIncident.roadBlockage ? 'Yes ⛔' : 'No'}</div>
                            <div><strong>Assigned Team:</strong> {selectedIncident.assignedUnit || 'None'}</div>
                          </div>

                          {/* State Machine Transition Controls */}
                          {isOperator ? (
                            <div className="workflow-section">
                              <h4>⚙️ Dispatch & Lifecycle Management</h4>

                              <div className="workflow-row">
                                <label>Target Status:</label>
                                <select
                                  value={statusUpdateForm.targetStatus}
                                  onChange={(e) => setStatusUpdateForm({ ...statusUpdateForm, targetStatus: e.target.value })}
                                >
                                  <option value="TRIAGED">TRIAGED</option>
                                  <option value="ASSIGNED">ASSIGNED</option>
                                  <option value="IN_PROGRESS">IN_PROGRESS</option>
                                  <option value="RESOLVED">RESOLVED</option>
                                  <option value="VERIFIED_CLOSED">VERIFIED_CLOSED</option>
                                  <option value="REJECTED">REJECTED</option>
                                </select>
                              </div>

                              <div className="workflow-row">
                                <input
                                  type="text"
                                  placeholder="Resolution / Transition Notes (Required for RESOLVED)"
                                  value={statusUpdateForm.resolutionNotes}
                                  onChange={(e) => setStatusUpdateForm({ ...statusUpdateForm, resolutionNotes: e.target.value })}
                                />
                                <button
                                  className="btn btn-primary btn-sm"
                                  onClick={() => handleStatusUpdate(selectedIncident.id)}
                                >
                                  Apply State Transition
                                </button>
                              </div>

                              <hr />

                              <h4>🚒 Resource Dispatch</h4>
                              <div className="workflow-row">
                                <input
                                  type="text"
                                  placeholder="Assigned Unit / Team Name"
                                  value={assignForm.assignedUnit}
                                  onChange={(e) => setAssignForm({ ...assignForm, assignedUnit: e.target.value })}
                                />
                                <input
                                  type="text"
                                  placeholder="Equipment (e.g. Pump, Boat)"
                                  value={assignForm.equipmentDetails}
                                  onChange={(e) => setAssignForm({ ...assignForm, equipmentDetails: e.target.value })}
                                />
                                <button
                                  className="btn btn-secondary btn-sm"
                                  onClick={() => handleAssignIncident(selectedIncident.id)}
                                >
                                  Assign Resource
                                </button>
                              </div>
                            </div>
                          ) : (
                            <div className="view-only-notice">
                              🔒 Incident status transitions and resource dispatches require <code>MUNICIPAL_OPERATOR</code> or <code>MUNICIPAL_ADMIN</code> role.
                            </div>
                          )}
                        </div>
                      ) : isOperator ? (
                        <div className="twin-card">
                          <h3>➕ Create Municipal Incident</h3>
                          <form onSubmit={handleCreateIncident}>
                            <div className="form-group">
                              <label>Incident Title <span className="req">*</span></label>
                              <input
                                type="text"
                                placeholder="e.g. Minto Bridge Underpass Submerged"
                                value={newIncidentForm.title}
                                onChange={(e) => setNewIncidentForm({ ...newIncidentForm, title: e.target.value })}
                                required
                              />
                            </div>
                            <div className="form-group">
                              <label>Description & Observed Impact</label>
                              <textarea
                                rows={3}
                                placeholder="Provide observed field details and traffic obstruction"
                                value={newIncidentForm.description}
                                onChange={(e) => setNewIncidentForm({ ...newIncidentForm, description: e.target.value })}
                              />
                            </div>
                            <div className="form-row-2">
                              <div className="form-group">
                                <label>Severity</label>
                                <select
                                  value={newIncidentForm.severity}
                                  onChange={(e) => setNewIncidentForm({ ...newIncidentForm, severity: e.target.value })}
                                >
                                  <option value="LOW">LOW</option>
                                  <option value="MODERATE">MODERATE</option>
                                  <option value="HIGH">HIGH</option>
                                  <option value="SEVERE">SEVERE</option>
                                </select>
                              </div>
                              <div className="form-group">
                                <label>Municipal Zone</label>
                                <select
                                  value={newIncidentForm.zoneId}
                                  onChange={(e) => setNewIncidentForm({ ...newIncidentForm, zoneId: e.target.value })}
                                >
                                  <option value="">NCR General</option>
                                  {zones.map((z) => (
                                    <option key={z.id} value={z.id}>{z.zoneName}</option>
                                  ))}
                                </select>
                              </div>
                            </div>
                            <div className="form-row-2">
                              <div className="form-group">
                                <label>Latitude <span className="req">*</span></label>
                                <input
                                  type="number"
                                  step="0.0001"
                                  value={newIncidentForm.latitude}
                                  onChange={(e) => setNewIncidentForm({ ...newIncidentForm, latitude: e.target.value })}
                                  required
                                />
                              </div>
                              <div className="form-group">
                                <label>Longitude <span className="req">*</span></label>
                                <input
                                  type="number"
                                  step="0.0001"
                                  value={newIncidentForm.longitude}
                                  onChange={(e) => setNewIncidentForm({ ...newIncidentForm, longitude: e.target.value })}
                                  required
                                />
                              </div>
                            </div>
                            <button type="submit" className="btn btn-primary btn-block">
                              Register Incident in Municipal Twin
                            </button>
                          </form>
                        </div>
                      ) : (
                        <div className="twin-card">
                          <h3>ℹ️ Incident Inspection</h3>
                          <p className="empty-text">Select an incident from the queue on the left to view details.</p>
                        </div>
                      )}

                      {/* Audit Logs */}
                      <div className="twin-card audit-trail-card">
                        <h4>📝 Municipal Audit Trail</h4>
                        <div className="audit-log-list">
                          {auditLogs.length === 0 ? (
                            <p className="empty-text">No audit entries logged yet.</p>
                          ) : (
                            auditLogs.slice(0, 8).map((log) => (
                              <div key={log.id} className="audit-log-item">
                                <span className="audit-action">{log.actionType}</span>
                                <span className="audit-entity">{log.targetEntity} #{log.targetEntityId}</span>
                                <span className="audit-actor">{log.actorId || log.actorUsername}</span>
                                <span className="audit-time">{new Date(log.eventTimestamp).toLocaleTimeString()}</span>
                              </div>
                            ))
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                </>
              )}
            </div>
          )}

          {/* TAB 5: DATA PROVENANCE & SCIENTIFIC INTEGRITY */}
          {tab === 'quality' && (
            <div className="twin-quality-layout">
              {/* Critical Scientific Rule Banner */}
              <div className="scientific-rule-banner">
                <div className="banner-icon">🛡️</div>
                <div className="banner-content">
                  <h3>Scientific Integrity & No-Fabrication Guarantee</h3>
                  <p>
                    A digital twin is NOT automatically a calibrated hydrodynamic simulation. JalMarg adheres strictly to primary-source evidence principles:
                  </p>
                  <ul>
                    <li><strong>Drainage Network Status:</strong> <em>Drainage network data unavailable.</em> No underground stormwater drainage pipes or pumping stations are rendered to avoid scientific fabrication.</li>
                    <li><strong>Rainfall Forecasts:</strong> Open-Meteo predictions are classified strictly as <code>PROVIDER_FORECAST</code>, never as measured rain gauge ground truth.</li>
                    <li><strong>Citizen Reports:</strong> Photos and depth estimates are classified as <code>USER_REPORTED</code> and require multi-citizen or authority confirmation.</li>
                    <li><strong>DEM Context:</strong> Copernicus GLO-30 DSM elevations are contextual terrain heights, never represented as floodwater water column depth.</li>
                  </ul>
                </div>
              </div>

              {/* Data Category Transparency Cards */}
              <div className="twin-card">
                <h3>🔍 Real Data, Live Data & Simulation Transparency</h3>
                <div className="transparency-grid">
                  <div className="transparency-card">
                    <div className="tp-header">
                      <span className="tp-tag blue">REANALYSIS</span>
                      <h4>Historical Rainfall Reanalysis</h4>
                    </div>
                    <p>Open-Meteo ECMWF/ERA5 & IMD gridded reanalysis covering 2014–2023 hourly records. Used for baseline climatological risk scoring; NOT local measured rainfall from physical gauges.</p>
                  </div>

                  <div className="transparency-card">
                    <div className="tp-header">
                      <span className="tp-tag teal">METEOROLOGICAL API</span>
                      <h4>Weather API Data</h4>
                    </div>
                    <p>Open-Meteo ECMWF Numerical Weather Prediction. Provides 6-hour convective rain forecast. Displayed with explicit forecast timestamps, never claimed as live sensor telemetry.</p>
                  </div>

                  <div className="transparency-card">
                    <div className="tp-header">
                      <span className="tp-tag amber">CROWDSOURCED</span>
                      <h4>Citizen-Submitted Reports</h4>
                    </div>
                    <p>Crowdsourced observations with anatomical markers (Ankle, Shin, Knee, Waist). Explicitly classified as <code>UNVERIFIED</code> until corroborated by multiple reports or municipal review.</p>
                  </div>

                  <div className="transparency-card">
                    <div className="tp-header">
                      <span className="tp-tag green">OFFICIAL AUDITED</span>
                      <h4>Municipal Incident Records</h4>
                    </div>
                    <p>Operational incident tickets created and verified by authenticated PWD, NDMC, or Traffic Police operators. Backed by immutable audit events in <code>twin_audit_events</code>.</p>
                  </div>

                  <div className="transparency-card">
                    <div className="tp-header">
                      <span className="tp-tag purple">HYPOTHETICAL</span>
                      <h4>Hypothetical Scenario — Not an Observed Event</h4>
                    </div>
                    <p>Deterministic what-if stress tests parameterized by users in the sandbox. Completely isolated from ground truth; never merged into operational incident tables.</p>
                  </div>

                  <div className="transparency-card">
                    <div className="tp-header">
                      <span className="tp-tag red">HEURISTIC INDEX</span>
                      <h4>Estimated Waterlogging Risk (Heuristic)</h4>
                    </div>
                    <p>Composite index scored from 0 to 100 based on terrain depression, precipitation forecast, and historical recurrence. Strictly decision-support; NOT a calibrated flood probability.</p>
                  </div>
                </div>
              </div>

              <div className="twin-card">
                <h3>📚 Registered Data Sources & Provenance Ledger ({sources.length})</h3>
                <div className="table-responsive">
                  <table className="twin-sources-table">
                    <thead>
                      <tr>
                        <th>Source Code</th>
                        <th>Source Name</th>
                        <th>Provider</th>
                        <th>Classification</th>
                        <th>Verification</th>
                        <th>CRS</th>
                        <th>License</th>
                      </tr>
                    </thead>
                    <tbody>
                      {sources.map((s) => (
                        <tr key={s.id}>
                          <td><code>{s.sourceCode}</code></td>
                          <td>
                            <strong>{s.sourceName}</strong>
                            {s.notes && <div className="text-muted" style={{ fontSize: '0.75rem', marginTop: '2px' }}>{s.notes}</div>}
                          </td>
                          <td>{s.provider}</td>
                          <td>
                            <span className={`classification-badge ${(s.sourceClassification || s.classification || '').toLowerCase()}`}>
                              {s.sourceClassification || s.classification}
                            </span>
                          </td>
                          <td>
                            <span className="tag">{s.verificationStatus || 'VERIFIED'}</span>
                          </td>
                          <td><code>{s.crs || s.coordinateReferenceSystem}</code></td>
                          <td>{s.license || s.licenseType}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="twin-card">
                <h3>⚖️ Model Limitations & Operational Scope</h3>
                <div className="limitations-grid">
                  <div className="limit-card">
                    <h4>Predictive Scoring Heuristic</h4>
                    <p>Current risk scoring uses the calibrated Phase 1-17 rule-based multi-factor formula (precipitation forecast + terrain depression + historical hotspot recurrence + active reports). Scoring is decision-support only.</p>
                  </div>
                  <div className="limit-card">
                    <h4>Geospatial Resolution</h4>
                    <p>Terrain context is derived from Copernicus GLO-30 (30m spatial resolution). Fine-grained localized micro-topographic curbs (&lt;1m) are not resolved in standard DEMs.</p>
                  </div>
                  <div className="limit-card">
                    <h4>Authority Access Control</h4>
                    <p>Municipal authorization enforces role checks (Viewer, Operator, Admin) with cryptographically salted BCrypt credentials and token verification across all operational endpoints.</p>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
