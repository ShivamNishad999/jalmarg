import { useState } from 'react'
import { MapContainer, TileLayer, Marker, Circle, useMapEvents, useMap } from 'react-leaflet'
import { useEffect } from 'react'
import { api, getPosition, NCR_CENTER, TILE_URL, TILE_ATTRIBUTION } from '../api'
import { useLang } from '../i18n'
import { youIcon } from '../components/shared'

function Picker({ onPick }) {
  useMapEvents({ click: (e) => onPick({ lat: e.latlng.lat, lng: e.latlng.lng }) })
  return null
}
function Follow({ pos }) {
  const map = useMap()
  useEffect(() => { if (pos) map.setView([pos.lat, pos.lng], 13) }, [pos?.lat, pos?.lng]) // eslint-disable-line
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

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    if (!pos) return setError(t('needLocation'))
    setBusy(true)
    try {
      const s = await api.subscribe({ ...form, radiusKm: +form.radiusKm, latitude: pos.lat, longitude: pos.lng })
      setDone(s)
      setLookupEmail(s.email)
      setMine(await api.mySubscriptions(s.email))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const lookup = async () => {
    try { setMine(await api.mySubscriptions(lookupEmail)) } catch (e) { setError(e.message) }
  }
  const remove = async (id) => {
    await api.unsubscribe(id, lookupEmail)
    setMine(mine.filter((m) => m.id !== id))
  }

  return (
    <div className="page narrow">
      <h1>{t('alertsTitle')}</h1>
      <p className="muted">{t('alertsSub')}</p>

      {done && (
        <div className="banner ok">
          ✓ {t('subscribed')} {done.emailAlertsActive && <><br /><span className="small">{t('confirmEmail')}</span></>}
        </div>
      )}

      <form className="card stack" onSubmit={submit}>
        <div className="grid2">
          <div>
            <label className="label">{t('yourName')}</label>
            <input className="input" value={form.name} onChange={set('name')} />
          </div>
          <div>
            <label className="label">{t('email')} *</label>
            <input className="input" type="email" required value={form.email} onChange={set('email')} />
          </div>
        </div>

        <label className="label">{t('label')}</label>
        <div className="seg">
          {[['Home', 'labelHome'], ['College', 'labelCollege'], ['Office', 'labelOffice']].map(([v, k]) => (
            <button type="button" key={v} className={form.label === v ? 'on' : ''} onClick={() => setForm({ ...form, label: v })}>{t(k)}</button>
          ))}
        </div>

        <div className="row gap">
          <button type="button" className="btn small ghost" onClick={() => getPosition().then(setPos).catch(() => setError(t('needLocation')))}>
            📍 {t('setFromGps')}
          </button>
          <span className="small muted">{t('tapMapToSet')}</span>
        </div>
        <div className="mini-map">
          <MapContainer center={NCR_CENTER} zoom={10} style={{ height: '100%' }}>
            <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
            <Picker onPick={setPos} />
            <Follow pos={pos} />
            {pos && <>
              <Marker position={[pos.lat, pos.lng]} icon={youIcon} />
              <Circle center={[pos.lat, pos.lng]} radius={form.radiusKm * 1000} pathOptions={{ color: '#1769aa', weight: 1, fillOpacity: 0.08 }} />
            </>}
          </MapContainer>
        </div>

        <div className="grid2">
          <div>
            <label className="label">{t('radius')}: {form.radiusKm} km</label>
            <input type="range" min="1" max="10" step="1" value={form.radiusKm} onChange={set('radiusKm')} />
          </div>
          <div>
            <label className="label">{t('language')}</label>
            <div className="seg">
              <button type="button" className={form.lang === 'en' ? 'on' : ''} onClick={() => setForm({ ...form, lang: 'en' })}>English</button>
              <button type="button" className={form.lang === 'hi' ? 'on' : ''} onClick={() => setForm({ ...form, lang: 'hi' })}>हिंदी</button>
            </div>
          </div>
        </div>

        {error && <div className="banner err">{error}</div>}
        <button className="btn primary block" disabled={busy}>{busy ? <span className="spinner" /> : '🔔'} {t('subscribe')}</button>
      </form>

      <section className="card stack">
        <h3>{t('mySubs')}</h3>
        <div className="row gap">
          <input className="input" type="email" placeholder={t('email')} value={lookupEmail} onChange={(e) => setLookupEmail(e.target.value)} />
          <button className="btn small" onClick={lookup}>{t('lookup')}</button>
        </div>
        {mine && mine.length === 0 && <p className="muted small">—</p>}
        {mine && mine.map((m) => (
          <div key={m.id} className="row between sub-row">
            <span>🔔 <b>{m.label}</b> · {m.radiusKm} km · {m.lang.toUpperCase()}</span>
            <button className="btn small ghost" onClick={() => remove(m.id)}>{t('remove')}</button>
          </div>
        ))}
      </section>
    </div>
  )
}
