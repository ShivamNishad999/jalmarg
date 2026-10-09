// All calls to the JalMarg backend (Spring Boot) and to free map services.

// Backend URL. Order: value saved in the app (About > Server) → build-time VITE_API_BASE → same origin.
// The saved value lets one Android APK talk to a laptop on Wi-Fi or to the AWS deployment.
const clean = (u) => (u || '').trim().replace(/\/$/, '')
function readSaved() {
  try { return localStorage.getItem('jalmarg-api') } catch { return null }
}
export let API_BASE = clean(readSaved() || import.meta.env.VITE_API_BASE)

export function setApiBase(url) {
  API_BASE = clean(url)
  try {
    if (API_BASE) localStorage.setItem('jalmarg-api', API_BASE)
    else localStorage.removeItem('jalmarg-api')
  } catch { /* storage blocked */ }
}

/** True when running inside the installed Android app (Capacitor). */
export { isNative as isNativeApp } from './native'
import { isNative, nativePosition } from './native'

// Map tiles: CARTO Voyager (OpenStreetMap data). Works inside the Android WebView, where
// tile.openstreetmap.org may refuse requests without a website referrer.
export const TILE_URL = 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png'
export const TILE_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'

async function request(path, options = {}) {
  const res = await fetch(API_BASE + path, options)
  const isJson = (res.headers.get('content-type') || '').includes('application/json')
  const body = isJson ? await res.json() : null
  if (!res.ok) throw new Error((body && body.error) || `Request failed (${res.status})`)
  return body
}

const json = (method, body) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body)
})

export const photoUrl = (p) => (p ? (p.startsWith('http') ? p : API_BASE + p) : null)

export const api = {
  meta: () => request('/api/meta'),
  activeReports: () => request('/api/reports/active'),
  recentReports: (days = 7) => request(`/api/reports/recent?days=${days}`),
  createReport: (formData) => request('/api/reports', { method: 'POST', body: formData }),
  confirm: (id) => request(`/api/reports/${id}/confirm`, { method: 'POST' }),
  clear: (id) => request(`/api/reports/${id}/clear`, { method: 'POST' }),
  feed: (lat, lng, lang, radiusKm = 3) =>
    request(`/api/alerts/feed?lat=${lat}&lng=${lng}&lang=${lang}&radiusKm=${radiusKm}`),
  subscribe: (body) => request('/api/alerts/subscriptions', json('POST', body)),
  mySubscriptions: (email) => request(`/api/alerts/subscriptions?email=${encodeURIComponent(email)}`),
  unsubscribe: (id, email) =>
    request(`/api/alerts/subscriptions/${id}?email=${encodeURIComponent(email)}`, { method: 'DELETE' }),
  evaluateRoutes: (routes) => request('/api/routes/evaluate', json('POST', { routes, bufferMeters: 60 })),
  hotspots: () => request('/api/hotspots'),
  summary: (area, days = 30) =>
    request(`/api/authority/summary?days=${days}${area ? `&area=${encodeURIComponent(area)}` : ''}`),
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
  if (isNative) return nativePosition()
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
  return new Promise((resolve) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      const scale = Math.min(1, maxSide / Math.max(img.width, img.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(img.width * scale)
      canvas.height = Math.round(img.height * scale)
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height)
      canvas.toBlob(
        (blob) => {
          URL.revokeObjectURL(url)
          resolve(blob || file)
        },
        'image/jpeg',
        quality
      )
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(file)
    }
    img.src = url
  })
}

// Default map centre: Delhi NCR
export const NCR_CENTER = [28.58, 77.25]
