import { useEffect, useRef, useState } from 'react'
import { MapContainer, TileLayer, Marker, useMap, useMapEvents } from 'react-leaflet'
import { Link, useNavigate } from 'react-router-dom'
import { Camera, CheckCircle2, Crosshair, Image as ImageIcon, MapPin, RotateCcw, Send, Share2, Trash2 } from 'lucide-react'
import { api, compressImage, getPosition, reverseGeocode, NCR_CENTER, isNativeApp, TILE_URL, TILE_ATTRIBUTION } from '../api'
import { nativePhoto } from '../native'
import { useLang, errorText } from '../i18n'
import { ReportCard, severityIcon } from '../components/shared'
import { Button, Notice, PageHead } from '../components/ui'

const LEVELS = ['PATCHY', 'ANKLE', 'SHIN', 'KNEE', 'WAIST']
const LEVEL_FILL = { PATCHY: 12, ANKLE: 25, SHIN: 45, KNEE: 65, WAIST: 90 }
const OK_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif']

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
  const cameraInput = useRef()
  const galleryInput = useRef()
  const sendingRef = useRef(false)
  const [pos, setPos] = useState(null)
  const [locating, setLocating] = useState(false)
  const [gpsError, setGpsError] = useState('')
  const [place, setPlace] = useState('')
  const [photo, setPhoto] = useState(null)
  const [preview, setPreview] = useState(null)
  const [photoBusy, setPhotoBusy] = useState(false)
  const [photoError, setPhotoError] = useState('')
  const [level, setLevel] = useState(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)

  const locate = async () => {
    setLocating(true)
    setGpsError('')
    try {
      setPos(await getPosition())
    } catch {
      setGpsError(t('locationFallback'))
    } finally {
      setLocating(false)
    }
  }
  useEffect(() => { locate() }, []) // eslint-disable-line

  useEffect(() => {
    if (!pos) return
    let cancelled = false
    reverseGeocode(pos.lat, pos.lng).then((n) => { if (!cancelled && n) setPlace(n) })
    return () => { cancelled = true }
  }, [pos?.lat, pos?.lng]) // eslint-disable-line

  const acceptPhoto = async (blob) => {
    if (!blob) return
    if (blob.type && !OK_TYPES.includes(blob.type)) return setPhotoError(t('notImage'))
    if (blob.size > 25 * 1024 * 1024) return setPhotoError(t('tooBig'))
    const small = await compressImage(blob) // always JPEG, max 1280 px
    setPhoto(small)
    setPreview((old) => { if (old) URL.revokeObjectURL(old); return URL.createObjectURL(small) })
    setPhotoError('')
  }

  /** "Take photo" opens the live camera; "Gallery" opens the photo picker. Separate paths. */
  const pick = async (source) => {
    setPhotoError('')
    if (isNativeApp) {
      setPhotoBusy(true)
      try {
        await acceptPhoto(await nativePhoto(source))
      } catch (e) {
        setPhotoError(e.message === 'camera-denied' ? t('cameraDenied') : (e.message || t('error')))
      } finally {
        setPhotoBusy(false)
      }
      return
    }
    ;(source === 'camera' ? cameraInput : galleryInput).current.click()
  }

  const onFile = async (e) => {
    const f = e.target.files?.[0]
    e.target.value = ''
    if (f) await acceptPhoto(f)
  }

  const removePhoto = () => {
    setPhoto(null)
    setPreview((old) => { if (old) URL.revokeObjectURL(old); return null })
  }

  const submit = async () => {
    setError(null)
    if (!pos) return setError({ message: t('needLocation') })
    if (!photo && !level) return setError({ message: t('needPhotoOrDepth') })
    if (sendingRef.current) return
    sendingRef.current = true
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
      setError(e) // form values stay so the person can retry
    } finally {
      sendingRef.current = false
      setBusy(false)
    }
  }

  const reset = () => { setResult(null); removePhoto(); setLevel(null); setNote('') }

  if (result) {
    const appLink = import.meta.env.VITE_PUBLIC_URL || (isNativeApp ? '' : location.origin)
    const msg = lang === 'hi'
      ? `⚠️ जलभराव: ${result.locationName} — अनुमानित ${result.depthFt.toFixed(1)} फीट पानी। ${result.bikeSafe ? '' : 'बाइक से न जाएँ। '}${appLink}`
      : `⚠️ Waterlogging at ${result.locationName} — about ${result.depthFt.toFixed(1)} ft of water (estimate). ${result.bikeSafe ? '' : 'Avoid on two-wheelers. '}${appLink}`
    return (
      <div className="page narrow">
        <div className="success-head">
          <CheckCircle2 size={40} aria-hidden />
          <div>
            <h1>{t('resultTitle')}</h1>
            <p className="muted small">Report #{result.id} · {new Date(result.createdAt).toLocaleString(lang === 'hi' ? 'hi-IN' : 'en-IN')}</p>
          </div>
        </div>
        <p className="muted">{t('resultSub')}</p>
        {result.depthSource === 'CITIZEN' && photo && (
          <Notice kind="info">{lang === 'hi' ? 'AI विश्लेषण अभी उपलब्ध नहीं था; आपकी चुनी गहराई सेव हुई।' : 'AI analysis was not available; your chosen depth was saved.'}</Notice>
        )}
        <ReportCard r={result} />
        <div className="stack">
          <a className="btn whatsapp block" href={`https://wa.me/?text=${encodeURIComponent(msg)}`} target="_blank" rel="noreferrer">
            <Share2 size={18} aria-hidden /> {t('shareWhatsapp')}
          </a>
          <Button block variant="secondary" onClick={() => navigate('/')}>{t('viewOnMap')}</Button>
          <Button block variant="ghost" onClick={reset}>{t('reportAnother')}</Button>
        </div>
      </div>
    )
  }

  const serverProblem = error && (error.kind === 'NOT_CONFIGURED' || error.kind === 'UNREACHABLE' || error.kind === 'TIMEOUT' || error.kind === 'NOT_JALMARG')

  return (
    <div className="page narrow">
      <PageHead icon={Camera} title={t('reportTitle')} sub={t('reportSub')} />

      <section className="card step">
        <h3><span className="step-n">1</span>{t('step1')}</h3>
        <div className="mini-map">
          <MapContainer center={pos ? [pos.lat, pos.lng] : NCR_CENTER} zoom={pos ? 16 : 11} style={{ height: '100%' }}>
            <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
            <Recenter pos={pos} />
            <PickOnMap onPick={setPos} />
            {pos && (
              <Marker position={[pos.lat, pos.lng]} draggable icon={severityIcon('HIGH', true)}
                eventHandlers={{ dragend: (e) => { const ll = e.target.getLatLng(); setPos({ lat: ll.lat, lng: ll.lng }) } }} />
            )}
          </MapContainer>
        </div>
        <div className="row gap wrap">
          <Button variant="secondary" className="small" loading={locating} onClick={locate}><Crosshair size={16} aria-hidden /> {t('useMyLocation')}</Button>
          <span className="small muted">{t('dragPin')}</span>
        </div>
        {gpsError && <Notice kind="warn">{gpsError}</Notice>}
        <div className="input-wrap">
          <MapPin size={16} className="input-lead" aria-hidden />
          <input className="input with-lead" value={place} onChange={(e) => setPlace(e.target.value)} placeholder={t('placeName')} maxLength={200} />
        </div>
      </section>

      <section className="card step">
        <h3><span className="step-n">2</span>{t('step2')}</h3>
        <input ref={cameraInput} type="file" accept="image/*" capture="environment" hidden onChange={onFile} />
        <input ref={galleryInput} type="file" accept="image/*" hidden onChange={onFile} />
        {preview ? (
          <div className="photo-frame">
            <img src={preview} alt="" className="photo-preview" />
            <span className="photo-badge"><CheckCircle2 size={14} aria-hidden /> {t('photoReady')}</span>
          </div>
        ) : (
          <div className="photo-actions">
            <button className="photo-tile primary" onClick={() => pick('camera')} disabled={photoBusy}>
              <Camera size={28} aria-hidden /><b>{t('takePhotoBtn')}</b>
            </button>
            <button className="photo-tile" onClick={() => pick('photos')} disabled={photoBusy}>
              <ImageIcon size={28} aria-hidden /><b>{t('fromGallery')}</b>
            </button>
          </div>
        )}
        {preview && (
          <div className="row gap wrap">
            <Button variant="secondary" className="small" onClick={() => pick('camera')}><RotateCcw size={16} aria-hidden /> {t('changePhoto')}</Button>
            <Button variant="secondary" className="small" onClick={() => pick('photos')}><ImageIcon size={16} aria-hidden /> {t('fromGallery')}</Button>
            <Button variant="ghost" className="small" onClick={removePhoto}><Trash2 size={16} aria-hidden /> {t('removePhoto')}</Button>
          </div>
        )}
        {photoError && <Notice kind="error">{photoError}</Notice>}
        <p className="small muted">{t('photoHint')}</p>
      </section>

      <section className="card step">
        <h3><span className="step-n">3</span>{t('step3')}</h3>
        <p className="small muted">{t('depthOptional')}</p>
        <div className="levels" role="radiogroup">
          {LEVELS.map((l) => (
            <button key={l} role="radio" aria-checked={level === l} className={'level' + (level === l ? ' on' : '')} onClick={() => setLevel(level === l ? null : l)}>
              <span className="level-fig" aria-hidden>
                <span className="level-person" />
                <span className="level-water" style={{ height: LEVEL_FILL[l] + '%' }} />
              </span>
              <b>{t('lvl_' + l)}</b>
              <small>{t('lvl_' + l + '_d')}</small>
            </button>
          ))}
        </div>
        <textarea className="input" rows={2} value={note} onChange={(e) => setNote(e.target.value)} placeholder={t('note')} maxLength={500} />
      </section>

      {error && (
        <Notice kind="error" action={serverProblem ? <Link to="/settings#server" className="btn small secondary">{t('setupServer')}</Link> : null}>
          {errorText(error, t)}
        </Notice>
      )}
      <Button block loading={busy} onClick={submit}><Send size={18} aria-hidden /> {busy ? t('sending') : t('submit')}</Button>
    </div>
  )
}
