import { useEffect, useRef, useState } from 'react'
import { MapContainer, TileLayer, Marker, useMap, useMapEvents } from 'react-leaflet'
import { Link, useNavigate } from 'react-router-dom'
import { api, compressImage, getPosition, reverseGeocode, NCR_CENTER } from '../api'
import { useLang } from '../i18n'
import { ReportCard, severityIcon } from '../components/shared'

const LEVELS = ['PATCHY', 'ANKLE', 'SHIN', 'KNEE', 'WAIST']
const LEVEL_FILL = { PATCHY: 12, ANKLE: 25, SHIN: 45, KNEE: 65, WAIST: 90 }

function PickOnMap({ onPick }) {
  useMapEvents({ click: (e) => onPick({ lat: e.latlng.lat, lng: e.latlng.lng }) })
  return null
}
function Recenter({ pos }) {
  const map = useMap()
  useEffect(() => { if (pos) map.setView([pos.lat, pos.lng], 16) }, [pos?.lat, pos?.lng]) // eslint-disable-line
  return null
}

export default function ReportPage() {
  const { t, lang } = useLang()
  const navigate = useNavigate()
  const fileRef = useRef()
  const [pos, setPos] = useState(null)
  const [locating, setLocating] = useState(false)
  const [place, setPlace] = useState('')
  const [photo, setPhoto] = useState(null)
  const [preview, setPreview] = useState(null)
  const [level, setLevel] = useState(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  const locate = async () => {
    setLocating(true)
    setError('')
    try {
      const p = await getPosition()
      setPos(p)
    } catch {
      setError(t('needLocation'))
    } finally {
      setLocating(false)
    }
  }

  useEffect(() => { locate() }, []) // eslint-disable-line

  // fill the place name from the map position (user can edit)
  useEffect(() => {
    if (!pos) return
    let cancelled = false
    reverseGeocode(pos.lat, pos.lng).then((n) => { if (!cancelled && n) setPlace(n) })
    return () => { cancelled = true }
  }, [pos?.lat, pos?.lng]) // eslint-disable-line

  const onPhoto = async (e) => {
    const f = e.target.files?.[0]
    if (!f) return
    const small = await compressImage(f)
    setPhoto(small)
    setPreview((old) => { if (old) URL.revokeObjectURL(old); return URL.createObjectURL(small) })
  }

  const submit = async () => {
    setError('')
    if (!pos) return setError(t('needLocation'))
    if (!photo && !level) return setError(t('needPhotoOrDepth'))
    const fd = new FormData()
    fd.append('latitude', pos.lat)
    fd.append('longitude', pos.lng)
    if (place) fd.append('locationName', place.slice(0, 200))
    if (level) fd.append('depthLevel', level)
    if (note) fd.append('note', note.slice(0, 500))
    if (photo) fd.append('photo', photo, 'photo.jpg')
    setBusy(true)
    try {
      setResult(await api.createReport(fd))
      window.scrollTo(0, 0)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const reset = () => {
    setResult(null); setPhoto(null); setPreview(null); setLevel(null); setNote('')
  }

  if (result) {
    const msg = lang === 'hi'
      ? `⚠️ जलभराव: ${result.locationName} — लगभग ${result.depthFt.toFixed(1)} फीट पानी। ${result.bikeSafe ? '' : 'बाइक से न जाएँ। '}JalMarg पर लाइव देखें: ${location.origin}`
      : `⚠️ Waterlogging at ${result.locationName} — about ${result.depthFt.toFixed(1)} ft of water. ${result.bikeSafe ? '' : 'Avoid on two-wheelers. '}Live map: ${location.origin}`
    return (
      <div className="page narrow">
        <div className="success-badge">✓</div>
        <h1>{t('resultTitle')}</h1>
        <p className="muted">{t('resultSub')}</p>
        <ReportCard r={result} />
        <div className="stack">
          <a className="btn whatsapp" href={`https://wa.me/?text=${encodeURIComponent(msg)}`} target="_blank" rel="noreferrer">
            {t('shareWhatsapp')}
          </a>
          <button className="btn ghost" onClick={() => navigate('/')}>{t('viewOnMap')}</button>
          <button className="btn ghost" onClick={reset}>{t('reportAnother')}</button>
        </div>
      </div>
    )
  }

  return (
    <div className="page narrow">
      <h1>{t('reportTitle')}</h1>
      <p className="muted">{t('reportSub')}</p>

      <section className="card step">
        <h3><span className="step-n">1</span>{t('step1')}</h3>
        <div className="mini-map">
          <MapContainer center={pos ? [pos.lat, pos.lng] : NCR_CENTER} zoom={pos ? 16 : 11} style={{ height: '100%' }}>
            <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" attribution="&copy; OpenStreetMap" />
            <Recenter pos={pos} />
            <PickOnMap onPick={setPos} />
            {pos && (
              <Marker
                position={[pos.lat, pos.lng]}
                draggable
                icon={severityIcon('HIGH', true)}
                eventHandlers={{ dragend: (e) => { const ll = e.target.getLatLng(); setPos({ lat: ll.lat, lng: ll.lng }) } }}
              />
            )}
          </MapContainer>
        </div>
        <div className="row gap">
          <button className="btn small ghost" onClick={locate} disabled={locating}>📍 {locating ? t('locating') : t('useMyLocation')}</button>
          <span className="small muted">{t('dragPin')}</span>
        </div>
        <input className="input" value={place} onChange={(e) => setPlace(e.target.value)} placeholder={t('placeName')} />
      </section>

      <section className="card step">
        <h3><span className="step-n">2</span>{t('step2')}</h3>
        <input ref={fileRef} type="file" accept="image/*" capture="environment" hidden onChange={onPhoto} />
        {preview ? (
          <img src={preview} alt="" className="photo-preview" onClick={() => fileRef.current.click()} />
        ) : (
          <button className="photo-drop" onClick={() => fileRef.current.click()}>
            <span className="big" aria-hidden>📷</span>
            <b>{t('takePhoto')}</b>
          </button>
        )}
        {preview && <button className="btn small ghost" onClick={() => fileRef.current.click()}>{t('changePhoto')}</button>}
        <p className="small muted">💡 {t('photoHint')}</p>
      </section>

      <section className="card step">
        <h3><span className="step-n">3</span>{t('step3')}</h3>
        <p className="small muted">{t('depthOptional')}</p>
        <div className="levels">
          {LEVELS.map((l) => (
            <button key={l} className={'level' + (level === l ? ' on' : '')} onClick={() => setLevel(level === l ? null : l)}>
              <span className="level-fig" aria-hidden>
                <span className="level-person">🧍</span>
                <span className="level-water" style={{ height: LEVEL_FILL[l] + '%' }} />
              </span>
              <b>{t('lvl_' + l)}</b>
              <small>{t('lvl_' + l + '_d')}</small>
            </button>
          ))}
        </div>
        <textarea className="input" rows={2} value={note} onChange={(e) => setNote(e.target.value)} placeholder={t('note')} />
      </section>

      {error && <div className="banner err">{error}</div>}
      <button className="btn primary block" disabled={busy} onClick={submit}>
        {busy ? <><span className="spinner" /> {t('sending')}</> : t('submit')}
      </button>
      <p className="center small"><Link to="/">← {t('nav_map')}</Link></p>
    </div>
  )
}
