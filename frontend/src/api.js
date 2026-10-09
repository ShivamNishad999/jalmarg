// All calls to the JalMarg backend (Spring Boot) and to free map services.

export const API_BASE = (import.meta.env.VITE_API_BASE || '').trim().replace(/\/+$/, '')

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
