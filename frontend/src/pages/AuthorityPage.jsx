import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, CircleMarker, Tooltip } from 'react-leaflet'
import { CheckCircle2, FileDown, Landmark, Radar, RotateCcw } from 'lucide-react'
import { api, NCR_CENTER, TILE_URL, TILE_ATTRIBUTION, isNativeApp } from '../api'
import { useLang, errorText } from '../i18n'
import { SEV_HEX, SeverityPill, timeAgo } from '../components/shared'
import { Button, Notice, PageHead, Skeleton } from '../components/ui'
import { RISK_HEX } from '../components/risk'

const AREAS = ['', 'Delhi', 'Noida', 'Greater Noida', 'Ghaziabad', 'Gurugram']

function Bars({ data }) {
  const max = Math.max(1, ...data.map((d) => d.count))
  return (
    <div className="bars" role="img" aria-label="Reports per day">
      {data.map((d) => (
        <div key={d.date} className="bar-col" title={`${d.date}: ${d.count}`}>
          <div className="bar" style={{ height: (d.count / max) * 100 + '%' }} />
        </div>
      ))}
    </div>
  )
}

export default function AuthorityPage() {
  const { t, lang } = useLang()
  const [area, setArea] = useState('')
  const [s, setS] = useState(null)
  const [error, setError] = useState(null)
  const [pdfBusy, setPdfBusy] = useState(null)
  const [recent, setRecent] = useState(null)
  const [forecast, setForecast] = useState(null)
  const [monitor, setMonitor] = useState(null)

  useEffect(() => {
    setS(null)
    setError(null)
    api.summary(area, 30).then(setS).catch(setError)
  }, [area])

  const loadRecent = () => api.recentReports(3).then((r) => setRecent(Array.isArray(r) ? r : [])).catch((e) => { setError(e); setRecent([]) })
  useEffect(() => { loadRecent() }, [])
  useEffect(() => {
    api.floodRiskHotspots().then((f) => setForecast(Array.isArray(f) ? f : [])).catch(() => setForecast([]))
    api.floodRiskStatus().then(setMonitor).catch(() => {})
  }, [])

  /** PDF needs the sign-in token, so fetch it as a blob instead of a plain link. */
  const downloadPdf = async (opts, key) => {
    setPdfBusy(key)
    setError(null)
    try {
      const blob = await api.pdf(opts)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `jalmarg-${opts.hotspotId ? 'hotspot-' + opts.hotspotId : (opts.area || 'ncr').toLowerCase().replace(/\s+/g, '-')}.pdf`
      document.body.appendChild(a)
      a.click()
      a.remove()
      setTimeout(() => URL.revokeObjectURL(url), 30_000)
    } catch (e) {
      setError(e)
    } finally {
      setPdfBusy(null)
    }
  }

  const fmt = (iso) => (iso ? new Date(iso).toLocaleDateString(lang === 'hi' ? 'hi-IN' : 'en-IN', { day: 'numeric', month: 'short' }) : '-')
  const sevTotal = s ? Object.values(s.bySeverity).reduce((a, b) => a + b, 0) || 1 : 1

  return (
    <div className="page wide">
      <div className="row between wrap">
        <PageHead icon={Landmark} title={t('authTitle')} sub={t('authSub')} />
        <div className="row gap wrap">
          <select className="input" value={area} onChange={(e) => setArea(e.target.value)}>
            {AREAS.map((a) => <option key={a} value={a}>{a || t('allAreas')}</option>)}
          </select>
          <Button loading={pdfBusy === 'area'} onClick={() => downloadPdf({ area }, 'area')}><FileDown size={18} aria-hidden /> {t('downloadPdf')}</Button>
        </div>
      </div>

      {isNativeApp && <Notice kind="info">{t('pdfWebHint')}</Notice>}
      {error && <Notice kind="error">{errorText(error, t)}</Notice>}
      {!s && !error && <div className="kpis">{[0, 1, 2, 3].map((i) => <Skeleton key={i} h={72} />)}</div>}

      {s && (
        <>
          <div className="kpis">
            <div className="kpi"><span>{t('totalReports')}</span><b>{s.totalReports}</b></div>
            <div className="kpi hot"><span>{t('activeNow')}</span><b>{s.activeNow}</b></div>
            <div className="kpi"><span>{t('hotspots')}</span><b>{s.hotspots}</b></div>
            <div className="kpi"><span>{t('last7')}</span><b>{s.reportsLast7Days}</b></div>
          </div>

          <div className="grid-dash">
            <section className="card">
              <h3>{t('perDay')}</h3>
              <Bars data={s.perDay} />
              <div className="row between small muted"><span>{fmt(s.perDay[0]?.date)}</span><span>{fmt(s.perDay[s.perDay.length - 1]?.date)}</span></div>
            </section>
            <section className="card">
              <h3>{t('bySeverity')}</h3>
              <div className="sev-stack">
                {Object.entries(s.bySeverity).map(([k, v]) => v > 0 && (
                  <div key={k} style={{ width: (v / sevTotal) * 100 + '%', background: SEV_HEX[k] }} title={`${k}: ${v}`} />
                ))}
              </div>
              <ul className="legend">
                {Object.entries(s.bySeverity).map(([k, v]) => (
                  <li key={k}><span className="dot" style={{ background: SEV_HEX[k] }} />{t('sev_' + k)} <b>{v}</b></li>
                ))}
              </ul>
            </section>
            <section className="card map-card">
              <MapContainer center={NCR_CENTER} zoom={10} style={{ height: '100%' }} scrollWheelZoom={false}>
                <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} />
                {s.topHotspots.map((h) => (
                  <CircleMarker key={h.id} center={[h.latitude, h.longitude]} radius={6 + h.totalReports}
                    pathOptions={{ color: h.activeNow ? SEV_HEX.SEVERE : SEV_HEX.HIGH, fillOpacity: 0.45, weight: 1 }}>
                    <Tooltip>{h.name}: {h.totalReports}</Tooltip>
                  </CircleMarker>
                ))}
              </MapContainer>
            </section>
          </div>

          <section className="card">
            <h3>{t('topHotspots')}</h3>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>#</th><th>{t('location')}</th><th>{t('area')}</th><th>{t('reports')}</th>
                    <th>{t('last30')}</th><th>{t('maxDepth')}</th><th>{t('lastSeen')}</th><th></th>
                  </tr>
                </thead>
                <tbody>
                  {s.topHotspots.map((h, i) => (
                    <tr key={h.id}>
                      <td>{i + 1}</td>
                      <td>{h.activeNow && <span className="live-dot" title="flooded now" />} {h.name}</td>
                      <td>{h.area}</td>
                      <td><b>{h.totalReports}</b></td>
                      <td>{h.reportsLast30Days}</td>
                      <td>{h.maxDepthFt.toFixed(1)} ft</td>
                      <td>{fmt(h.lastSeen)}</td>
                      <td><button className="link-btn" disabled={pdfBusy === h.id} onClick={() => downloadPdf({ hotspotId: h.id }, h.id)}>{t('pdf')}</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}

      <section className="card stack">
        <h3 className="card-title"><Radar size={18} aria-hidden /> {t('forecastTitle')}</h3>
        <p className="small muted">{t('forecastSub')}{monitor ? ` · ${monitor.totalCycles ?? 0} ${t('monitorCycles')}` : ''}</p>
        {forecast === null && <><Skeleton h={44} /><Skeleton h={44} /></>}
        {forecast && forecast.filter((f) => f.status !== 'NO_IMMEDIATE_RISK').length === 0 && <p className="muted small">{t('noForecast')}</p>}
        {forecast && forecast.filter((f) => f.status !== 'NO_IMMEDIATE_RISK').slice(0, 10).map((f) => (
          <div key={f.id} className="forecast-row">
            <div>
              <b>{f.hotspotName}</b>
              <div className="small muted">{f.area || '-'} · {(f.rainfallNext6hMm ?? 0).toFixed(1)} mm / 6 h{f.activeReportCount ? ` · ${f.activeReportCount} ${t('liveReportsNear')}` : ''}</div>
            </div>
            <span className="pill" style={{ background: RISK_HEX[f.riskLevel] || RISK_HEX.UNKNOWN }}>{t('risk_' + (f.riskLevel || 'UNKNOWN'))} · {f.riskScore}</span>
          </div>
        ))}
        <p className="disclaimer">{t('predictiveDisclaimer')}</p>
      </section>

      <section className="card stack">
        <h3 className="card-title">{t('reportsToReview')}</h3>
        {recent === null && <><Skeleton h={56} /><Skeleton h={56} /></>}
        {recent && recent.length === 0 && <p className="muted small">{t('noAlerts')}</p>}
        {recent && recent.slice(0, 30).map((r) => (
          <ReviewRow key={r.id} r={r} lang={lang} t={t} onSaved={(u) => setRecent((rs) => rs.map((x) => (x.id === u.id ? u : x)))} />
        ))}
      </section>
    </div>
  )
}

function ReviewRow({ r, lang, t, onSaved }) {
  const [note, setNote] = useState(r.authorityNote || '')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState(null)
  const save = async (status) => {
    setBusy(true)
    setMsg(null)
    try {
      const u = await api.updateStatus(r.id, status, note.trim().slice(0, 500))
      onSaved(u)
      setMsg({ kind: 'success', text: t('statusUpdated') })
    } catch (e) {
      setMsg({ kind: 'error', text: errorText(e, t) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="review-row">
      <div className="row between gap">
        <div>
          <b>{r.locationName}</b>
          <div className="small muted">#{r.id} · {r.area || '-'} · ≈{r.depthFt.toFixed(1)} {t('ft')} · {timeAgo(r.createdAt, lang)} {t('ago')}{r.demo ? ` · ${t('demoTag')}` : ''}</div>
        </div>
        <div className="row gap">
          <SeverityPill sev={r.severity} />
          <span className={'pill ' + (r.status === 'ACTIVE' ? 'sev-severe' : 'neutral')}>{r.status}</span>
        </div>
      </div>
      <div className="row gap wrap">
        <input className="input grow" value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} placeholder={t('notePh')} />
        {r.status === 'ACTIVE'
          ? <Button className="small" loading={busy} onClick={() => save('CLEARED')}><CheckCircle2 size={16} aria-hidden /> {t('markResolved')}</Button>
          : <Button className="small" variant="secondary" loading={busy} onClick={() => save('ACTIVE')}><RotateCcw size={16} aria-hidden /> {t('reopen')}</Button>}
      </div>
      {msg && <Notice kind={msg.kind}>{msg.text}</Notice>}
    </div>
  )
}
