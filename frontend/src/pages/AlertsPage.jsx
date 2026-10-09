import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, Marker, Circle, useMapEvents, useMap } from 'react-leaflet'
import { Bell, Crosshair, Trash2 } from 'lucide-react'
import { api, getPosition, NCR_CENTER, TILE_URL, TILE_ATTRIBUTION } from '../api'
import { useAuth } from '../auth'
import { useLang, errorText } from '../i18n'
import { youIcon } from '../components/shared'
import { Button, Notice, PageHead, Skeleton } from '../components/ui'

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
  const { user } = useAuth()
  const [form, setForm] = useState({ label: 'Home', radiusKm: 3, lang })
  const [pos, setPos] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [saved, setSaved] = useState(null)
  const [mine, setMine] = useState(null)

  const loadMine = () => api.mySubscriptions().then(setMine).catch((e) => { setError(e); setMine([]) })
  useEffect(() => { loadMine() }, [])

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    setSaved(null)
    if (!pos) return setError({ message: t('needLocation') })
    setBusy(true)
    try {
      const s = await api.subscribe({ ...form, radiusKm: +form.radiusKm, latitude: pos.lat, longitude: pos.lng })
      setSaved(s)
      loadMine()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const remove = async (id) => {
    try {
      await api.unsubscribe(id)
      setMine((m) => m.filter((x) => x.id !== id))
    } catch (e) {
      setError(e)
    }
  }

  return (
    <div className="page narrow">
      <PageHead icon={Bell} title={t('alertsTitle')} sub={t('alertsSub')} />

      {saved && (
        <Notice kind="success">
          {t('prefSaved')} {saved.emailAlertsActive ? t('confirmEmail') : t('emailNotActive')}
        </Notice>
      )}

      <form className="card stack" onSubmit={submit}>
        <div className="diag-row"><span>{t('email')}</span><b>{user?.email}</b></div>

        <label className="label">{t('label')}</label>
        <div className="seg">
          {[['Home', 'labelHome'], ['College', 'labelCollege'], ['Office', 'labelOffice']].map(([v, k]) => (
            <button type="button" key={v} className={form.label === v ? 'on' : ''} onClick={() => setForm({ ...form, label: v })}>{t(k)}</button>
          ))}
        </div>

        <div className="row gap wrap">
          <Button type="button" variant="secondary" className="small" onClick={() => getPosition().then(setPos).catch(() => setError({ message: t('locationFallback') }))}>
            <Crosshair size={16} aria-hidden /> {t('setFromGps')}
          </Button>
          <span className="small muted">{t('tapMapToSet')}</span>
        </div>
        <div className="mini-map">
          <MapContainer center={NCR_CENTER} zoom={10} style={{ height: '100%' }}>
            <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
            <Picker onPick={setPos} />
            <Follow pos={pos} />
            {pos && <>
              <Marker position={[pos.lat, pos.lng]} icon={youIcon} />
              <Circle center={[pos.lat, pos.lng]} radius={form.radiusKm * 1000} pathOptions={{ color: '#0EA5E9', weight: 1, fillOpacity: 0.08 }} />
            </>}
          </MapContainer>
        </div>

        <div className="grid2">
          <div>
            <label className="label">{t('radius')}: {form.radiusKm} km</label>
            <input type="range" min="1" max="10" step="1" value={form.radiusKm} onChange={(e) => setForm({ ...form, radiusKm: e.target.value })} />
          </div>
          <div>
            <label className="label">{t('language')}</label>
            <div className="seg">
              <button type="button" className={form.lang === 'en' ? 'on' : ''} onClick={() => setForm({ ...form, lang: 'en' })}>English</button>
              <button type="button" className={form.lang === 'hi' ? 'on' : ''} onClick={() => setForm({ ...form, lang: 'hi' })}>हिंदी</button>
            </div>
          </div>
        </div>

        {error && <Notice kind="error">{errorText(error, t)}</Notice>}
        <Button block loading={busy} type="submit"><Bell size={18} aria-hidden /> {t('subscribe')}</Button>
      </form>

      <section className="card stack">
        <h3 className="card-title">{t('mySubs')}</h3>
        {mine === null && <><Skeleton h={18} /><Skeleton h={18} w="70%" /></>}
        {mine && mine.length === 0 && <p className="muted small">{t('noSubs')}</p>}
        {mine && mine.map((m) => (
          <div key={m.id} className="row between sub-row">
            <span><b>{m.label}</b> · {m.radiusKm} km · {m.lang.toUpperCase()} · <span className="muted small">{m.emailAlertsActive ? t('emailPending') : t('savedOnly')}</span></span>
            <Button variant="ghost" className="small" onClick={() => remove(m.id)} aria-label={t('remove')}><Trash2 size={16} aria-hidden /></Button>
          </div>
        ))}
      </section>
    </div>
  )
}
