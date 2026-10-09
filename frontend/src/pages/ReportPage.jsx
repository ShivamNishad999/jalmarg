import { useEffect, useRef, useState, useCallback } from 'react'
import { MapContainer, TileLayer, Marker, useMap, useMapEvents } from 'react-leaflet'
import { Link, useNavigate } from 'react-router-dom'
import { api, compressImage, getPosition, reverseGeocode, NCR_CENTER } from '../api'
import { useLang } from '../i18n'
import { severityIcon } from '../components/shared'
import ReportEvidencePanel from '../components/ReportEvidencePanel'
import { depthText, validId, latestTask } from '../utils/evidence'

const LEVELS = ['PATCHY', 'ANKLE', 'SHIN', 'KNEE', 'WAIST']
const LEVEL_FILL = { PATCHY: 12, ANKLE: 28, SHIN: 50, KNEE: 72, WAIST: 95 }

function PickOnMap({ onPick }) {
  useMapEvents({
    click: (e) => onPick({ lat: e.latlng.lat, lng: e.latlng.lng })
  })
  return null
}

function Recenter({ pos }) {
  const map = useMap()
  useEffect(() => {
    if (pos) {
      map.setView([pos.lat, pos.lng], 16)
      map.invalidateSize()
    }
  }, [pos?.lat, pos?.lng, map])
  return null
}

export default function ReportPage() {
  const { t } = useLang()
  const navigate = useNavigate()
  const cameraInputRef = useRef(null)
  const fileInputRef = useRef(null)
  const [pos, setPos] = useState(null)
  const [locating, setLocating] = useState(false)
  const [place, setPlace] = useState('')
  const [photo, setPhoto] = useState(null)
  const [preview, setPreview] = useState(null)
  const [processing, setProcessing] = useState(false)
  const photoTask = useRef(latestTask())
  const locationTask = useRef(latestTask())
  const placeVersion = useRef(0)
  const submitLock = useRef(false)
  const submission = useRef(null)
  const [manual, setManual] = useState({ lat: '', lng: '' })
  const [level, setLevel] = useState(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  useEffect(() => {
    if (!photo) { setPreview(null); return }
    const url = URL.createObjectURL(photo)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [photo])

  useEffect(() => () => {
    photoTask.current.invalidate()
    locationTask.current.invalidate()
    submission.current?.abort()
  }, [])

  const pickPosition = (p) => {
    locationTask.current.invalidate()
    placeVersion.current++
    setLocating(false)
    setPos(p)
  }

  const locate = useCallback(async () => {
    const token = locationTask.current.next()
    setLocating(true)
    setError('')
    try {
      const p = await getPosition()
      if (locationTask.current.current(token)) setPos(p)
    } catch {
      if (locationTask.current.current(token)) setError(t('gpsError'))
    } finally {
      if (locationTask.current.current(token)) setLocating(false)
    }
  }, [t])

  // Initial GPS only; changing display language must not overwrite a manual pin.
  useEffect(() => { locate() }, [])

  useEffect(() => {
    if (!pos) return
    let cancelled = false
    const version = placeVersion.current
    reverseGeocode(pos.lat, pos.lng).then((n) => {
      if (!cancelled && version === placeVersion.current && n) setPlace(n)
    })
    return () => { cancelled = true }
  }, [pos?.lat, pos?.lng])

  const removePhoto = () => {
    photoTask.current.invalidate()
    setProcessing(false)
    setPhoto(null)
  }

  const onPhotoSelected = async (e) => {
    const f = e.target.files?.[0]
    e.target.value = '' // permit selecting the same file after failure; cancellation preserves current photo
    if (!f) return
    const token = photoTask.current.next()
    setProcessing(false)
    setError('')
    if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(f.type) || f.size > 10 * 1024 * 1024 || f.size === 0) {
      setError(t('photoUnsupported'))
      return
    }
    setProcessing(true)
    try {
      const small = await compressImage(f)
      if (photoTask.current.current(token)) setPhoto(small)
    } catch {
      if (photoTask.current.current(token)) setError(t('photoError'))
    } finally {
      if (photoTask.current.current(token)) setProcessing(false)
    }
  }

  const submit = async () => {
    if (submitLock.current || processing) return
    setError('')
    if (!pos || !Number.isFinite(pos.lat) || !Number.isFinite(pos.lng) || Math.abs(pos.lat) > 90 || Math.abs(pos.lng) > 180) return setError(t('invalidLocation'))
    if (!photo && !level) return setError(t('needPhotoOrDepth'))
    const fd = new FormData()
    fd.append('latitude', String(pos.lat))
    fd.append('longitude', String(pos.lng))
    if (place.trim()) fd.append('locationName', place.trim().slice(0, 200))
    if (level) fd.append('depthLevel', level)
    if (note.trim()) fd.append('note', note.trim().slice(0, 500))
    if (photo) fd.append('photo', photo, 'photo.jpg')
    submitLock.current = true
    locationTask.current.invalidate()
    setLocating(false)
    const controller = new AbortController()
    submission.current = controller
    setBusy(true)
    try {
      const res = await api.createReport(fd, { signal: controller.signal })
      if (controller.signal.aborted) return
      if (!validId(res?.id)) throw new Error('Unconfirmed save')
      setResult(res)
      setPhoto(null)
      window.scrollTo(0, 0)
    } catch {
      if (!controller.signal.aborted) setError(t('saveError'))
    } finally {
      submitLock.current = false
      if (!controller.signal.aborted) setBusy(false)
    }
  }

  const reset = () => {
    setResult(null)
    setPhoto(null)
    photoTask.current.invalidate()
    setProcessing(false)
    setLevel(null)
    setNote('')
    setError('')
  }

  if (result) {
    const msg = t('shareEvidence', { id: result.id, location: result.locationName || t('reportedLocation'), depth: depthText(result.depthFt, t), url: window.location.origin })

    return (
      <div className="page narrow">
        <div className="success-badge" role="img" aria-label={t('savedReport')}>✓</div>
        <h1 className="center">{t('savedReport')}</h1>

        <p role="status" className="banner ok">{t('savedReport')}</p>
        <ReportEvidencePanel report={result} />

        <div className="stack" style={{ marginTop: 20 }}>
          <a
            className="btn whatsapp block"
            href={`https://wa.me/?text=${encodeURIComponent(msg)}`}
            target="_blank"
            rel="noreferrer"
          >
            📢 {t('shareWhatsapp')}
          </a>
          <button className="btn primary block" onClick={() => navigate('/')}>
            🗺️ {t('viewOnMap')}
          </button>
          <button className="btn ghost block" onClick={reset}>
            ➕ {t('reportAnother')}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="page narrow">
      <h1>{t('reportTitle')}</h1>
      <p className="muted">{t('reportSub')}</p>

      <fieldset disabled={busy} className="report-fields">
      {/* Step 1: Location */}
      <section className="card step">
        <h3>
          <span className="step-n">1</span>
          {t('step1')}
        </h3>
        <div className="mini-map">
          <MapContainer
            center={pos ? [pos.lat, pos.lng] : NCR_CENTER}
            zoom={pos ? 16 : 11}
            style={{ height: '100%', width: '100%' }}
            zoomControl={false}
          >
            <TileLayer
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              attribution="&copy; OpenStreetMap"
            />
            <Recenter pos={pos} />
            <PickOnMap onPick={(p) => { if (!submitLock.current) pickPosition(p) }} />
            {pos && (
              <Marker
                position={[pos.lat, pos.lng]}
                draggable={!busy}
                icon={severityIcon('HIGH', true)}
                eventHandlers={{
                  dragend: (e) => {
                    const ll = e.target.getLatLng()
                    if (!submitLock.current) pickPosition({ lat: ll.lat, lng: ll.lng })
                  }
                }}
              />
            )}
          </MapContainer>
        </div>
        <div className="row gap between wrap">
          <button
            type="button"
            className="btn small ghost"
            onClick={locate}
            disabled={locating}
          >
            📍 {locating ? t('locating') : t('useMyLocation')}
          </button>
          <span className="small muted">{t('dragPin')}</span>
        </div>
        {pos && (
          <div className="small muted" style={{ margin: '4px 0 8px' }}>
            GPS: {pos.lat.toFixed(5)}, {pos.lng.toFixed(5)}
          </div>
        )}
        <div className="grid2">
          <label className="label">{t('latitudeLabel')}<input className="input" type="number" step="any" min="-90" max="90" value={manual.lat} onChange={(e) => setManual({ ...manual, lat: e.target.value })} /></label>
          <label className="label">{t('longitudeLabel')}<input className="input" type="number" step="any" min="-180" max="180" value={manual.lng} onChange={(e) => setManual({ ...manual, lng: e.target.value })} /></label>
        </div>
        <button type="button" className="btn small ghost" onClick={() => {
          const lat = Number(manual.lat), lng = Number(manual.lng)
          if (!manual.lat.trim() || !manual.lng.trim() || !Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return setError(t('invalidLocation'))
          setError(''); pickPosition({ lat, lng })
        }}>{t('manualLocation')}</button>
        <label className="label" htmlFor="report-place">{t('placeName')}</label>
        <input
          className="input"
          id="report-place"
          maxLength={200}
          value={place}
          onChange={(e) => { placeVersion.current++; setPlace(e.target.value) }}
          placeholder={t('placeName')}
        />
      </section>

      {/* Step 2: Photo */}
      <section className="card step">
        <h3>
          <span className="step-n">2</span>
          {t('step2')}
        </h3>
        <input
          ref={cameraInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          style={{ display: 'none' }}
          onChange={onPhotoSelected}
        />
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          style={{ display: 'none' }}
          onChange={onPhotoSelected}
        />

        {processing && <p role="status">{t('photoProcessing')}</p>}
        {preview ? (
          <div>
            <img
              src={preview}
              alt={t('photoAlt')}
              className="photo-preview"
            />
            <div className="row gap">
              <button
                type="button"
                className="btn small ghost"
                onClick={() => cameraInputRef.current?.click()}
              >
                📷 {t('changePhoto')}
              </button>
              <button
                type="button"
                className="btn small ghost"
                onClick={removePhoto}
              >
                🗑️ {t('removePhoto')}
              </button>
            </div>
          </div>
        ) : (
          <div className="photo-actions-grid">
            <button
              type="button"
              className="photo-drop"
              onClick={() => cameraInputRef.current?.click()}
            >
              <span className="big" aria-hidden>📸</span>
              <b>{t('takePhoto')}</b>
            </button>
            <button
              type="button"
              className="photo-drop"
              onClick={() => fileInputRef.current?.click()}
            >
              <span className="big" aria-hidden>🖼️</span>
              <b>{t('chooseGallery')}</b>
            </button>
          </div>
        )}
        <p className="small muted" style={{ marginTop: 8 }}>💡 {t('photoHint')}</p>
      </section>

      {/* Step 3: Depth Level */}
      <section className="card step">
        <h3>
          <span className="step-n">3</span>
          {t('step3')}
        </h3>
        <p className="small muted">{t('depthOptional')}</p>
        <div className="levels">
          {LEVELS.map((l) => (
            <button
              key={l}
              type="button"
              className={'level' + (level === l ? ' on' : '')}
              aria-pressed={level === l}
              onClick={() => setLevel(level === l ? null : l)}
            >
              <span className="level-fig" aria-hidden>
                <span className="level-person">🧍</span>
                <span className="level-water" style={{ height: LEVEL_FILL[l] + '%' }} />
              </span>
              <b>{t('lvl_' + l)}</b>
              <small>{t('lvl_' + l + '_d')}</small>
            </button>
          ))}
        </div>
        <label className="label" htmlFor="report-note">{t('note')}</label>
        <textarea
          className="input"
          rows={2}
          id="report-note"
          maxLength={500}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder={t('note')}
        />
      </section>

      </fieldset>
      {error && <div className="banner err" role="alert">⚠️ {error}</div>}
      {busy && <p role="status">{t('saveReport')}</p>}

      <button
        className="btn primary block"
        disabled={busy || processing}
        onClick={submit}
        style={{ fontSize: '1rem', padding: '14px 20px', minHeight: 50 }}
      >
        {busy ? (
          <>
            <span className="spinner" /> {t('sending')}
          </>
        ) : (
          `🚀 ${t('submit')}`
        )}
      </button>

      <p className="center small" style={{ marginTop: 14 }}>
        <Link to="/">← {t('nav_map')}</Link>
      </p>
    </div>
  )
}
