// All calls to the JalMarg backend (Spring Boot) and to free map services.

import { isNative, nativePosition } from './native'
export { isNative as isNativeApp } from './native'

// ---------------------------------------------------------------- backend address

/**
 * Validates and normalises a backend address typed by a person.
 * - trims, drops trailing slashes and a trailing "/api"
 * - no scheme: http:// for localhost / private LAN IPs (local testing), https:// otherwise
 * Returns { ok: true, url } or { ok: false, error }.
 */
export function parseServerUrl(input) {
  let v = (input || '').trim()
  if (!v) return { ok: false, error: 'EMPTY' }
  if (!/^[a-z]+:\/\//i.test(v)) {
    const host = v.split(/[/:]/)[0]
    const local = /^(localhost|127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(host)
    v = (local ? 'http://' : 'https://') + v
  }
  let u
  try {
    u = new URL(v)
  } catch {
    return { ok: false, error: 'MALFORMED' }
  }
  if (u.protocol !== 'http:' && u.protocol !== 'https:') return { ok: false, error: 'MALFORMED' }
  if (!u.hostname || /\s/.test(u.hostname)) return { ok: false, error: 'MALFORMED' }
  const path = u.pathname.replace(/\/+$/, '').replace(/\/api$/, '')
  return { ok: true, url: `${u.protocol}//${u.host}${path}` }
}

function readSaved() {
  try { return localStorage.getItem('jalmarg-api') } catch { return null }
}
const initial = parseServerUrl(readSaved() || import.meta.env.VITE_API_BASE || '')
/** Backend base URL ('' = same origin, used by the web app in development). */
export let API_BASE = initial.ok ? initial.url : ''

export function setApiBase(url) {
  const p = parseServerUrl(url)
  API_BASE = p.ok ? p.url : ''
  try {
    if (API_BASE) localStorage.setItem('jalmarg-api', API_BASE)
    else localStorage.removeItem('jalmarg-api')
  } catch { /* storage blocked */ }
}

/** True when the app knows where its backend is. */
export const serverConfigured = () => !isNative || !!API_BASE

// Map tiles: Esri World Street Map (no API key, works inside the Android WebView).
// CARTO's free basemaps now show an "API key required" watermark, and tile.openstreetmap.org
// refuses requests from apps without a website referrer. Override with VITE_TILE_URL if needed.
export const TILE_URL = import.meta.env.VITE_TILE_URL ||
  'https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}'
export const TILE_ATTRIBUTION = import.meta.env.VITE_TILE_ATTRIBUTION ||
  'Tiles &copy; Esri &mdash; Esri, HERE, Garmin, &copy; OpenStreetMap contributors'

// ---------------------------------------------------------------- errors

/**
 * Every failed call throws an ApiError with a `kind` the UI can act on:
 * NOT_CONFIGURED | OFFLINE | UNREACHABLE | TIMEOUT | NOT_JALMARG | AUTH | FORBIDDEN | HTTP
 * plus `status` and the backend's `code` (e.g. EMAIL_NOT_VERIFIED, OTP_INVALID).
 */
export class ApiError extends Error {
  constructor(kind, message, { status = 0, code = '' } = {}) {
    super(message)
    this.kind = kind
    this.status = status
    this.code = code
  }
}

// ---------------------------------------------------------------- auth token

let token = null
let onUnauthorized = () => {}
export const setAuthToken = (t) => { token = t || null }
export const setUnauthorizedHandler = (fn) => { onUnauthorized = fn }

async function request(path, { method = 'GET', body, json: jsonBody, timeout = 15000, auth = true } = {}) {
  if (!serverConfigured()) throw new ApiError('NOT_CONFIGURED', 'The server address is not set.')
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    throw new ApiError('OFFLINE', 'No internet connection.')
  }
  const headers = {}
  if (jsonBody !== undefined) headers['Content-Type'] = 'application/json'
  if (auth && token) headers.Authorization = 'Bearer ' + token
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), timeout)
  let res
  try {
    res = await fetch(API_BASE + path, {
      method,
      headers,
      body: jsonBody !== undefined ? JSON.stringify(jsonBody) : body,
      signal: ctrl.signal
    })
  } catch (e) {
    if (e && e.name === 'AbortError') throw new ApiError('TIMEOUT', 'The server did not answer in time.')
    throw new ApiError('UNREACHABLE', 'Could not reach the server.')
  } finally {
    clearTimeout(timer)
  }
  if (res.status === 204) return null
  const isJson = (res.headers.get('content-type') || '').includes('application/json')
  let data = null
  if (isJson) {
    try { data = await res.json() } catch { data = null }
  }
  if (!res.ok) {
    const code = (data && data.code) || ''
    const msg = (data && data.error) || `Request failed (${res.status})`
    if (res.status === 401) {
      if (token && auth) onUnauthorized()
      throw new ApiError('AUTH', msg, { status: 401, code })
    }
    if (res.status === 403 && code !== 'EMAIL_NOT_VERIFIED') throw new ApiError('FORBIDDEN', msg, { status: 403, code })
    throw new ApiError('HTTP', msg, { status: res.status, code })
  }
  // A non-JSON answer means the address does not point at the JalMarg backend
  if (!isJson) throw new ApiError('NOT_JALMARG', 'This address is not a JalMarg server.')
  return data
}

export const photoUrl = (p) => (p ? (p.startsWith('http') ? p : API_BASE + p) : null)

export const api = {
  health: () => request('/api/health', { timeout: 8000, auth: false }),
  meta: () => request('/api/meta'),
  activeReports: () => request('/api/reports/active'),
  recentReports: (days = 7) => request(`/api/reports/recent?days=${days}`),
  createReport: (formData) => request('/api/reports', { method: 'POST', body: formData, timeout: 60000 }),
  confirm: (id) => request(`/api/reports/${id}/confirm`, { method: 'POST' }),
  clear: (id) => request(`/api/reports/${id}/clear`, { method: 'POST' }),
  feed: (lat, lng, lang, radiusKm = 3) =>
    request(`/api/alerts/feed?lat=${lat}&lng=${lng}&lang=${lang}&radiusKm=${radiusKm}`),
  subscribe: (b) => request('/api/alerts/subscriptions', { method: 'POST', json: b }),
  mySubscriptions: () => request('/api/alerts/subscriptions'),
  unsubscribe: (id) => request(`/api/alerts/subscriptions/${id}`, { method: 'DELETE' }),
  evaluateRoutes: (routes) => request('/api/routes/evaluate', { method: 'POST', json: { routes, bufferMeters: 60 } }),
  hotspots: () => request('/api/hotspots'),
  // autonomous flood-risk forecast (Open-Meteo rain + Copernicus DEM terrain + history)
  floodRisk: (lat, lng) => request(`/api/flood-risk?lat=${lat}&lng=${lng}`, { timeout: 20000 }),
  floodRiskHotspots: () => request('/api/flood-risk/hotspots'),
  floodRiskStatus: () => request('/api/flood-risk/status'),
  weather: (lat, lng) => request(`/api/weather?lat=${lat}&lng=${lng}`),
  summary: (area, days = 30) =>
    request(`/api/authority/summary?days=${days}${area ? `&area=${encodeURIComponent(area)}` : ''}`),
  updateStatus: (id, status, note) => request(`/api/authority/reports/${id}`, { method: 'PATCH', json: { status, note } }),
  /** PDF needs the auth header, so it is fetched as a blob and opened. */
  pdf: async ({ area, hotspotId } = {}) => {
    const q = new URLSearchParams()
    if (area) q.set('area', area)
    if (hotspotId) q.set('hotspotId', hotspotId)
    const res = await fetch(`${API_BASE}/api/authority/report.pdf?${q}`, { headers: token ? { Authorization: 'Bearer ' + token } : {} })
    if (!res.ok) throw new ApiError('HTTP', `Could not create the PDF (${res.status})`, { status: res.status })
    return res.blob()
  },
  // accounts
  register: (b) => request('/api/auth/register', { method: 'POST', json: b, auth: false }),
  verifyEmail: (email, code) => request('/api/auth/verify-email', { method: 'POST', json: { email, code }, auth: false }),
  resendCode: (email, purpose = 'VERIFY_EMAIL') => request('/api/auth/resend-code', { method: 'POST', json: { email, purpose }, auth: false }),
  login: (email, password) => request('/api/auth/login', { method: 'POST', json: { email, password }, auth: false }),
  me: () => request('/api/auth/me'),
  logout: () => request('/api/auth/logout', { method: 'POST', timeout: 8000 }),
  forgot: (email) => request('/api/auth/forgot-password', { method: 'POST', json: { email }, auth: false }),
  reset: (email, code, newPassword) => request('/api/auth/reset-password', { method: 'POST', json: { email, code, newPassword }, auth: false })
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
