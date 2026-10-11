// All calls to the JalMarg backend (Spring Boot) and to free map services.

export const API_BASE = (import.meta.env?.VITE_API_BASE || '').trim().replace(/\/+$/, '')

export const apiUrl = (path) => `${API_BASE}/${path.replace(/^\/+/, '')}`

async function request(path, options = {}, responseType = 'json') {
  let res
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(new Error('Request timed out after 12s')), 12000)

  let combinedSignal = controller.signal
  if (options.signal) {
    if (options.signal.aborted) {
      clearTimeout(timeoutId)
      throw options.signal.reason || new Error('Request was aborted')
    }
    options.signal.addEventListener('abort', () => controller.abort(options.signal.reason))
  }

  try {
    res = await fetch(apiUrl(path), { ...options, signal: combinedSignal })
  } catch (error) {
    clearTimeout(timeoutId)
    if (error.name === 'AbortError' || options.signal?.aborted) throw error
    throw new Error('Unable to reach the server. Please check your connection.', { cause: error })
  } finally {
    clearTimeout(timeoutId)
  }

  const type = (res.headers.get('content-type') || '').split(';')[0].trim().toLowerCase()
  const isJson = type === 'application/json' || type.endsWith('+json')
  if (!res.ok) {
    const body = isJson ? await res.json().catch(() => null) : null
    const error = new Error((typeof body?.error === 'string' && body.error) || `Request failed (${res.status})`)
    error.status = res.status
    throw error
  }
  if (responseType === 'pdf') {
    if (type !== 'application/pdf') throw new Error('Expected a PDF response from the server.')
    return res.blob()
  }
  if (res.status === 204) return null
  if (!isJson) throw new Error('Expected a JSON response from the server.')
  try {
    return await res.json()
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new Error('The server returned invalid JSON.', { cause: error })
  }
}

/** Fetch an existing backend PDF as a Blob; callers own saving and object URL cleanup. */
export const requestPdf = (path, options = {}) => request(path, options, 'pdf')

const json = (method, body, options = {}) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
  ...options
})

export const photoUrl = (p) => {
  if (typeof p !== 'string' || !p.trim()) return null
  const path = p.trim()
  if (/^https?:\/\//i.test(path)) return path
  if (/^[a-z][a-z\d+.-]*:/i.test(path) || path.startsWith('//')) return null
  return apiUrl(path)
}

export const api = {
  // System metadata & capabilities
  meta: (options = {}) => request('/api/meta', options),

  // Citizen flood reports
  activeReports: ({ lat, lng, radiusKm, signal } = {}) => {
    const q = new URLSearchParams()
    if (lat != null && lng != null) {
      q.set('lat', lat)
      q.set('lng', lng)
      if (radiusKm != null) q.set('radiusKm', radiusKm)
    }
    const qs = q.toString()
    return request(`/api/reports/active${qs ? `?${qs}` : ''}`, { signal })
  },
  recentReports: (days = 7, options = {}) => request(`/api/reports/recent?days=${days}`, options),
  getReport: (id, options = {}) => request(`/api/reports/${id}`, options),
  createReport: (formData, { signal } = {}) => request('/api/reports', { method: 'POST', body: formData, signal }),
  confirm: (id, options = {}) => request(`/api/reports/${id}/confirm`, { method: 'POST', ...options }),
  clear: (id, options = {}) => request(`/api/reports/${id}/clear`, { method: 'POST', ...options }),

  // Autonomous flood-risk engine
  floodRisk: (lat, lng, { area, hotspotId, signal } = {}) => {
    const q = new URLSearchParams({ lat: String(lat), lng: String(lng) })
    if (area) q.set('area', area)
    if (hotspotId) q.set('hotspotId', String(hotspotId))
    return request(`/api/flood-risk?${q.toString()}`, { signal })
  },
  floodRiskHotspots: (options = {}) => request('/api/flood-risk/hotspots', options),
  floodRiskStatus: (options = {}) => request('/api/flood-risk/status', options),

  // Live weather forecast (Open-Meteo via backend)
  weather: (lat, lng, options = {}) =>
    request(`/api/weather?lat=${lat}&lng=${lng}`, options),

  // Route flood risk evaluation
  evaluateRoutes: (routes, { bufferMeters = 60, signal } = {}) =>
    request('/api/routes/evaluate', { ...json('POST', { routes, bufferMeters }), signal }),

  // Alerts & Subscriptions
  feed: (lat, lng, lang = 'en', radiusKm = 3, options = {}) =>
    request(`/api/alerts/feed?lat=${lat}&lng=${lng}&lang=${lang}&radiusKm=${radiusKm}`, options),
  subscribe: (body, options = {}) => request('/api/alerts/subscriptions', json('POST', body, options)),
  mySubscriptions: (email, options = {}) =>
    request(`/api/alerts/subscriptions?email=${encodeURIComponent(email)}`, options),
  unsubscribe: (id, email, options = {}) =>
    request(`/api/alerts/subscriptions/${id}?email=${encodeURIComponent(email)}`, { method: 'DELETE', ...options }),

  // Authority & Hotspots
  hotspots: (options = {}) => request('/api/hotspots', options),
  summary: (area, days = 30, options = {}) =>
    request(`/api/authority/summary?days=${days}${area ? `&area=${encodeURIComponent(area)}` : ''}`, options),
  pdfUrl: ({ area, hotspotId } = {}) => {
    const q = new URLSearchParams()
    if (area) q.set('area', area)
    if (hotspotId) q.set('hotspotId', hotspotId)
    return `${API_BASE}/api/authority/report.pdf?${q}`
  },

  // Municipal Flood Digital Twin (Phase 18)
  twinStatus: (options = {}) => request('/api/v1/twin/status', options),
  twinZones: (options = {}) => request('/api/v1/twin/zones', options),
  twinRoads: (zoneId, options = {}) => request(`/api/v1/twin/roads${zoneId ? `?zoneId=${zoneId}` : ''}`, options),
  twinDrainage: (options = {}) => request('/api/v1/twin/drainage', options),
  twinSources: (options = {}) => request('/api/v1/twin/sources', options),
  twinSnapshots: (type, options = {}) => request(`/api/v1/twin/snapshots${type ? `?type=${type}` : ''}`, options),
  twinSnapshot: (id, options = {}) => request(`/api/v1/twin/snapshots/${id}`, options),
  twinScenarios: (options = {}) => request('/api/v1/twin/scenarios', options),
  twinScenario: (id, options = {}) => request(`/api/v1/twin/scenarios/${id}`, options),
  runScenario: (body, auth = {}, options = {}) => {
    const token = typeof auth === 'string' ? auth : auth?.token || (typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('jm_auth_token') : null)
    const adminKey = typeof auth === 'object' ? auth?.adminKey : null
    const headers = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(adminKey ? { 'X-Admin-Key': adminKey } : {})
    }
    return request('/api/v1/twin/scenarios', {
      method: 'POST',
      headers: { ...headers, ...(options?.headers || {}) },
      body: JSON.stringify(body),
      ...options
    })
  },

  // Municipal Authentication & Session Management (Phase 24)
  authLogin: (username, password, options = {}) =>
    request('/api/v1/auth/login', json('POST', { username, password }, options)),
  authLogout: (token, options = {}) =>
    request('/api/v1/auth/logout', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      ...options
    }),
  authMe: (token, options = {}) =>
    request('/api/v1/auth/me', {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      ...options
    }),

  // Municipal Citizen Report Review & Verification (Phase 24)
  authorityReports: (params = {}, options = {}) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    if (params.minSeverity) q.set('minSeverity', params.minSeverity)
    if (params.area) q.set('area', params.area)
    const headers = {
      ...(params.token ? { Authorization: `Bearer ${params.token}` } : {}),
      ...(params.adminKey ? { 'X-Admin-Key': params.adminKey } : {})
    }
    return request(`/api/v1/authority/reports${q.toString() ? `?${q.toString()}` : ''}`, { headers, ...options })
  },
  authorityReportDetails: (id, { token, adminKey } = {}, options = {}) => {
    const headers = {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(adminKey ? { 'X-Admin-Key': adminKey } : {})
    }
    return request(`/api/v1/authority/reports/${id}`, { headers, ...options })
  },
  authorityReviewReport: (id, body, { token, adminKey } = {}, options = {}) => {
    const headers = {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(adminKey ? { 'X-Admin-Key': adminKey } : {})
    }
    return request(`/api/v1/authority/reports/${id}/review`, {
      ...json('POST', body),
      headers,
      ...options
    })
  },

  // Municipal Authority Incidents & Digital Twin Operations
  authorityIncidents: (params = {}, options = {}) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    if (params.minSeverity) q.set('minSeverity', params.minSeverity)
    const token = params.token || (typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('jm_auth_token') : null)
    const headers = {
      'Accept': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(params.adminKey ? { 'X-Admin-Key': params.adminKey } : {})
    }
    return request(`/api/v1/authority/incidents${q.toString() ? `?${q.toString()}` : ''}`, { headers, ...options })
  },
  createAuthorityIncident: (body, auth = {}, options = {}) => {
    const token = typeof auth === 'string' ? auth : auth?.token || (typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('jm_auth_token') : null)
    const adminKey = typeof auth === 'object' ? auth?.adminKey : null
    return request('/api/v1/authority/incidents', {
      method: 'POST',
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(adminKey ? { 'X-Admin-Key': adminKey } : {})
      },
      ...options
    })
  },
  assignAuthorityIncident: (id, body, auth = {}, options = {}) => {
    const token = typeof auth === 'string' ? auth : auth?.token || (typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('jm_auth_token') : null)
    const adminKey = typeof auth === 'object' ? auth?.adminKey : null
    return request(`/api/v1/authority/incidents/${id}/assign`, {
      method: 'POST',
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(adminKey ? { 'X-Admin-Key': adminKey } : {})
      },
      ...options
    })
  },
  updateAuthorityIncidentStatus: (id, body, auth = {}, options = {}) => {
    const token = typeof auth === 'string' ? auth : auth?.token || (typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('jm_auth_token') : null)
    const adminKey = typeof auth === 'object' ? auth?.adminKey : null
    return request(`/api/v1/authority/incidents/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(adminKey ? { 'X-Admin-Key': adminKey } : {})
      },
      ...options
    })
  },
  authorityResources: (params = {}, options = {}) => {
    const q = new URLSearchParams()
    if (params.status) q.set('status', params.status)
    const token = (typeof params === 'object' && params.token) || (typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('jm_auth_token') : null)
    const headers = {
      'Accept': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(params.adminKey ? { 'X-Admin-Key': params.adminKey } : {})
    }
    return request(`/api/v1/authority/resources${q.toString() ? `?${q.toString()}` : ''}`, { headers, ...options })
  },
  authorityAuditLogs: (params = {}, options = {}) => {
    const token = (typeof params === 'object' && params.token) || (typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('jm_auth_token') : null)
    const headers = {
      'Accept': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(params.adminKey ? { 'X-Admin-Key': params.adminKey } : {})
    }
    return request('/api/v1/authority/audit-logs', { headers, ...options })
  },

  // ML Foundation & Training Readiness (Phase 25A)
  mlDatasets: (options = {}) => request('/api/v1/ml/datasets', options),
  mlReadiness: (options = {}) => request('/api/v1/ml/readiness', options),
  mlQualityReports: (options = {}) => request('/api/v1/ml/quality-reports', options),

  // Computer Vision & IRDID Dataset (Phase 25B)
  visionReadiness: (options = {}) => request('/api/v1/ml/vision/readiness', options),
  visionEvaluation: (options = {}) => request('/api/v1/ml/vision/evaluation', options),
  visionDatasetAudit: (options = {}) => request('/api/v1/ml/vision/dataset-audit', options),

  // Ground Truth Observations, Negative Workflow & Reliability (Phase 25C)
  groundTruthObservations: ({ status, verificationStatus, corridor } = {}) => {
    const q = new URLSearchParams()
    if (status) q.set('status', status)
    if (verificationStatus) q.set('verificationStatus', verificationStatus)
    if (corridor) q.set('corridor', corridor)
    const qs = q.toString()
    return request(`/api/v1/ml/ground-truth/observations${qs ? `?${qs}` : ''}`)
  },
  groundTruthSummary: (options = {}) => request('/api/v1/ml/ground-truth/summary', options),
  groundTruthQueue: (options = {}) => request('/api/v1/ml/ground-truth/queue', options),
  groundTruthProvenance: (options = {}) => request('/api/v1/ml/ground-truth/provenance', options),
  recordNegativeObservation: (payload, { adminKey, jalmargKey, token, operatorId } = {}) => {
    const headers = { 'Content-Type': 'application/json' }
    if (token) headers['Authorization'] = `Bearer ${token}`
    if (adminKey) headers['X-Admin-Key'] = adminKey
    if (jalmargKey) headers['X-Jalmarg-Key'] = jalmargKey
    if (operatorId) headers['X-Operator-Id'] = operatorId
    return request('/api/v1/ml/ground-truth/negative-observation', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    })
  },
  reviewObservation: (id, payload, { adminKey, jalmargKey, token, operatorId } = {}) => {
    const headers = { 'Content-Type': 'application/json' }
    if (token) headers['Authorization'] = `Bearer ${token}`
    if (adminKey) headers['X-Admin-Key'] = adminKey
    if (jalmargKey) headers['X-Jalmarg-Key'] = jalmargKey
    if (operatorId) headers['X-Operator-Id'] = operatorId
    return request(`/api/v1/ml/ground-truth/observations/${id}/review`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    })
  },
  visionReliabilityAudit: (options = {}) => request('/api/v1/ml/vision/reliability-audit', options),
  evidenceAuditSummary: (options = {}) => request('/api/v1/ml/evidence-audit/summary', options),
  evidenceAuditObservations: (options = {}) => request('/api/v1/ml/evidence-audit/observations', options),
  flywayIntegrityStatus: (options = {}) => request('/api/v1/ml/evidence-audit/flyway-status', options),

  // Authentic Historical Rainfall & Municipal Workflows (Phase 25E-B)
  rainfallCoverage: (options = {}) => request('/api/v1/ml/rainfall/coverage', options),
  rainfallSummary: (options = {}) => request('/api/v1/ml/rainfall/summary', options),
  dryRoadInspections: ({ reviewStatus } = {}, options = {}) => {
    const q = new URLSearchParams()
    if (reviewStatus) q.set('reviewStatus', reviewStatus)
    const qs = q.toString()
    return request(`/api/v1/ml/inspections/dry-road${qs ? `?${qs}` : ''}`, options)
  },
  recordDryRoadInspection: (payload, { adminKey, jalmargKey, token, operatorId } = {}) => {
    const headers = { 'Content-Type': 'application/json' }
    if (token) headers['Authorization'] = `Bearer ${token}`
    if (adminKey) headers['X-Admin-Key'] = adminKey
    if (jalmargKey) headers['X-Jalmarg-Key'] = jalmargKey
    if (operatorId) headers['X-Operator-Id'] = operatorId
    return request('/api/v1/ml/inspections/dry-road', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    })
  },
  reviewDryRoadInspection: (id, payload, { adminKey, jalmargKey, token, operatorId } = {}) => {
    const headers = { 'Content-Type': 'application/json' }
    if (token) headers['Authorization'] = `Bearer ${token}`
    if (adminKey) headers['X-Admin-Key'] = adminKey
    if (jalmargKey) headers['X-Jalmarg-Key'] = jalmargKey
    if (operatorId) headers['X-Operator-Id'] = operatorId
    return request(`/api/v1/ml/inspections/dry-road/${id}/review`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    })
  },
  calibratedDepthMeasurements: ({ evidenceStatus } = {}, options = {}) => {
    const q = new URLSearchParams()
    if (evidenceStatus) q.set('evidenceStatus', evidenceStatus)
    const qs = q.toString()
    return request(`/api/v1/ml/depth-measurements${qs ? `?${qs}` : ''}`, options)
  },
  recordCalibratedDepthMeasurement: (payload, { adminKey, jalmargKey, token, operatorId } = {}) => {
    const headers = { 'Content-Type': 'application/json' }
    if (token) headers['Authorization'] = `Bearer ${token}`
    if (adminKey) headers['X-Admin-Key'] = adminKey
    if (jalmargKey) headers['X-Jalmarg-Key'] = jalmargKey
    if (operatorId) headers['X-Operator-Id'] = operatorId
    return request('/api/v1/ml/depth-measurements', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    })
  },
  reviewCalibratedDepthMeasurement: (id, payload, { adminKey, jalmargKey, token, operatorId } = {}) => {
    const headers = { 'Content-Type': 'application/json' }
    if (token) headers['Authorization'] = `Bearer ${token}`
    if (adminKey) headers['X-Admin-Key'] = adminKey
    if (jalmargKey) headers['X-Jalmarg-Key'] = jalmargKey
    if (operatorId) headers['X-Operator-Id'] = operatorId
    return request(`/api/v1/ml/depth-measurements/${id}/review`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    })
  }
}

// ---------------------------------------------------------------- free map services

// Delhi NCR bounding box, used to keep place search local
const NCR_VIEWBOX = '76.80,28.90,77.70,28.30'

/** Place search (OpenStreetMap Nominatim). */
export async function searchPlaces(q) {
  if (!q || q.trim().length < 3) return []
  const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&countrycodes=in&viewbox=${NCR_VIEWBOX}&bounded=0&q=${encodeURIComponent(q)}`
  const res = await fetch(url, { headers: { 'Accept-Language': 'en' } })
  if (!res.ok) return []
  const data = await res.json()
  return data.map((d) => ({ name: d.display_name, lat: +d.lat, lng: +d.lon }))
}

/** Short place name for a coordinate. */
export async function reverseGeocode(lat, lng) {
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=17&lat=${lat}&lon=${lng}`,
      { headers: { 'Accept-Language': 'en' } }
    )
    const d = await res.json()
    const a = d.address || {}
    const parts = [a.road || a.pedestrian || a.neighbourhood, a.suburb || a.city_district || a.village, a.city || a.town || a.state_district]
    return parts.filter(Boolean).slice(0, 3).join(', ') || d.display_name || ''
  } catch {
    return ''
  }
}

/**
 * Routes from public OSRM servers. `points` = [[lat,lng], ...] (start, optional via, end).
 * mode: 'bike' | 'car' use the car network, 'walk' uses the foot network.
 * Returns [{ coords: [[lat,lng]...], distanceKm, durationMin }]
 */
export async function getRoutes(points, mode = 'car', alternatives = true) {
  const coords = points.map(([lat, lng]) => `${lng.toFixed(6)},${lat.toFixed(6)}`).join(';')
  const base = mode === 'walk'
    ? 'https://routing.openstreetmap.de/routed-foot/route/v1/foot'
    : 'https://router.project-osrm.org/route/v1/driving'
  const url = `${base}/${coords}?overview=full&geometries=geojson&alternatives=${alternatives ? 3 : 'false'}`
  const res = await fetch(url)
  if (!res.ok) throw new Error('Routing service is busy, try again')
  const data = await res.json()
  if (data.code !== 'Ok') throw new Error('No route found')
  return data.routes.map((r) => ({
    coords: r.geometry.coordinates.map(([lng, lat]) => [lat, lng]),
    distanceKm: r.distance / 1000,
    durationMin: r.duration / 60
  }))
}

/** Current GPS position as a promise. */
export function getPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) return reject(new Error('Location not supported'))
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude, accuracy: p.coords.accuracy }),
      (e) => reject(e),
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 30000 }
    )
  })
}

/** Shrinks a camera photo to max 1280px JPEG before upload (faster on mobile data, cheaper AI call). */
export function compressImage(file, maxSide = 1280, quality = 0.82) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    const fail = () => { URL.revokeObjectURL(url); reject(new Error('Image processing failed')) }
    img.onload = () => {
      try {
      const scale = Math.min(1, maxSide / Math.max(img.width, img.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(img.width * scale)
      canvas.height = Math.round(img.height * scale)
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height)
      canvas.toBlob(
        (blob) => {
          URL.revokeObjectURL(url)
          if (blob) resolve(blob)
          else reject(new Error('Image compression failed'))
        },
        'image/jpeg',
        quality
      )
      } catch { fail() }
    }
    img.onerror = fail
    img.src = url
  })
}

// Default map centre: Delhi NCR
export const NCR_CENTER = [28.58, 77.25]
