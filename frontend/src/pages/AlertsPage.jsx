import { useState, useEffect } from 'react'
import { MapContainer, TileLayer, Marker, Circle, useMapEvents, useMap } from 'react-leaflet'
import { api, getPosition, NCR_CENTER } from '../api'
import { useLang } from '../i18n'
import { youIcon } from '../components/shared'

function Picker({ onPick }) {
  useMapEvents({
    click: (e) => onPick({ lat: e.latlng.lat, lng: e.latlng.lng })
  })
  return null
}

function Follow({ pos }) {
  const map = useMap()
  useEffect(() => {
    if (pos) {
      map.setView([pos.lat, pos.lng], 13)
      map.invalidateSize()
    }
  }, [pos?.lat, pos?.lng, map])
  return null
}

export default function AlertsPage() {
  const { t, lang } = useLang()
  const [form, setForm] = useState({ name: '', email: '', label: 'Home', radiusKm: 3, lang })
  const [pos, setPos] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(null)
  const [lookupEmail, setLookupEmail] = useState('')
  const [mine, setMine] = useState(null)
  const [lookupBusy, setLookupBusy] = useState(false)

  // Keep form language aligned if user hasn't explicitly overridden
  useEffect(() => {
    setForm((f) => ({ ...f, lang }))
  }, [lang])

  const setField = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const setGpsLocation = () => {
    setError('')
    getPosition()
      .then(setPos)
      .catch(() => setError(t('gpsError')))
  }

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    if (!pos) return setError(t('needLocation'))
    if (!form.email || !form.email.includes('@')) return setError('Valid email address is required.')

    setBusy(true)
    try {
      const s = await api.subscribe({
        ...form,
        radiusKm: Number(form.radiusKm),
        latitude: pos.lat,
        longitude: pos.lng
      })
      setDone(s)
      setLookupEmail(s.email)
      const list = await api.mySubscriptions(s.email)
      setMine(Array.isArray(list) ? list : [])
      window.scrollTo(0, 0)
    } catch (err) {
      setError(err.message || 'Failed to create subscription.')
    } finally {
      setBusy(false)
    }
  }

  const lookup = async (e) => {
    if (e) e.preventDefault()
    if (!lookupEmail) return
    setError('')
    setLookupBusy(true)
    try {
      const data = await api.mySubscriptions(lookupEmail)
      setMine(Array.isArray(data) ? data : [])
    } catch (e) {
      setError(e.message || 'Could not fetch subscriptions.')
    } finally {
      setLookupBusy(false)
    }
  }

  const remove = async (id) => {
    try {
      await api.unsubscribe(id, lookupEmail)
      setMine((prev) => (prev ? prev.filter((m) => m.id !== id) : []))
    } catch (e) {
      setError(e.message || 'Failed to remove subscription.')
    }
  }

  return (
    <div className="page narrow">
      <h1>{t('alertsTitle')}</h1>
      <p className="muted">{t('alertsSub')}</p>

      {done && (
        <div className="banner ok" style={{ flexDirection: 'column', gap: 6 }}>
          <b>✓ {t('subscribed')}</b>
          <span className="small">{t('confirmEmail')}</span>
        </div>
      )}

      <form className="card stack" onSubmit={submit}>
        <div className="grid2">
          <div>
            <label className="label">{t('yourName')}</label>
            <input
              className="input"
              value={form.name}
              onChange={setField('name')}
              placeholder="e.g. Tanya"
            />
          </div>
          <div>
            <label className="label">{t('email')} *</label>
            <input
              className="input"
              type="email"
              required
              value={form.email}
              onChange={setField('email')}
              placeholder="you@example.com"
            />
          </div>
        </div>

        <label className="label">{t('label')}</label>
        <div className="seg">
          {[
            ['Home', 'labelHome'],
            ['College', 'labelCollege'],
            ['Office', 'labelOffice']
          ].map(([v, k]) => (
            <button
              type="button"
              key={v}
              className={form.label === v ? 'on' : ''}
              onClick={() => setForm({ ...form, label: v })}
            >
              {t(k)}
            </button>
          ))}
        </div>

        <label className="label">{t('step1')}</label>
        <div className="row gap between wrap">
          <button
            type="button"
            className="btn small ghost"
            onClick={setGpsLocation}
          >
            📍 {t('setFromGps')}
          </button>
          <span className="small muted">{t('tapMapToSet')}</span>
        </div>

        <div className="mini-map">
          <MapContainer
            center={NCR_CENTER}
            zoom={10}
            style={{ height: '100%', width: '100%' }}
            zoomControl={false}
          >
            <TileLayer
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              attribution="&copy; OpenStreetMap"
            />
            <Picker onPick={setPos} />
            <Follow pos={pos} />
            {pos && (
              <>
                <Marker position={[pos.lat, pos.lng]} icon={youIcon} />
                <Circle
                  center={[pos.lat, pos.lng]}
                  radius={form.radiusKm * 1000}
                  pathOptions={{ color: '#0EA5E9', weight: 1.5, fillOpacity: 0.12 }}
                />
              </>
            )}
          </MapContainer>
        </div>
        {pos && (
          <div className="small muted">
            Location: {pos.lat.toFixed(5)}, {pos.lng.toFixed(5)} · Radius: {form.radiusKm} km
          </div>
        )}

        <div className="grid2">
          <div>
            <label className="label">
              {t('radius')}: <b>{form.radiusKm} km</b>
            </label>
            <input
              type="range"
              min="1"
              max="10"
              step="1"
              value={form.radiusKm}
              onChange={setField('radiusKm')}
              aria-label={t('radius')}
            />
          </div>
          <div>
            <label className="label">{t('language')}</label>
            <div className="seg">
              <button
                type="button"
                className={form.lang === 'en' ? 'on' : ''}
                onClick={() => setForm({ ...form, lang: 'en' })}
              >
                English
              </button>
              <button
                type="button"
                className={form.lang === 'hi' ? 'on' : ''}
                onClick={() => setForm({ ...form, lang: 'hi' })}
              >
                हिंदी
              </button>
            </div>
          </div>
        </div>

        {error && <div className="banner err">⚠️ {error}</div>}

        <button
          type="submit"
          className="btn primary block"
          disabled={busy}
          style={{ marginTop: 14, minHeight: 48 }}
        >
          {busy ? (
            <>
              <span className="spinner" /> {t('loading')}
            </>
          ) : (
            `🔔 ${t('subscribe')}`
          )}
        </button>
      </form>

      {/* Subscription Management Section */}
      <section className="card stack" style={{ marginTop: 24 }}>
        <h3>📋 {t('mySubs')}</h3>
        <p className="small muted">Lookup or manage your registered email notification zones.</p>

        <form onSubmit={lookup} className="row gap" style={{ marginTop: 8 }}>
          <input
            className="input"
            type="email"
            placeholder={t('email')}
            value={lookupEmail}
            onChange={(e) => setLookupEmail(e.target.value)}
            required
          />
          <button
            type="submit"
            className="btn small primary"
            disabled={lookupBusy || !lookupEmail}
          >
            {lookupBusy ? <span className="spinner" /> : t('lookup')}
          </button>
        </form>

        {mine !== null && mine.length === 0 && (
          <p className="muted small" style={{ margin: '8px 0' }}>{t('noSubsFound')}</p>
        )}

        {mine && mine.length > 0 && (
          <div className="stack" style={{ marginTop: 8 }}>
            {mine.map((m) => (
              <div key={m.id} className="row between sub-row">
                <div>
                  <b>🔔 {m.label || 'Monitored Spot'}</b>
                  <div className="small muted">
                    {m.radiusKm} km radius · {m.lang?.toUpperCase() || 'EN'} · {m.email}
                  </div>
                </div>
                <button
                  type="button"
                  className="btn small ghost"
                  onClick={() => remove(m.id)}
                  style={{ color: 'var(--sev-severe)' }}
                >
                  🗑️ {t('remove')}
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
