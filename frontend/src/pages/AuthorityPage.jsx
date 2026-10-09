import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, CircleMarker, Tooltip } from 'react-leaflet'
import { api, NCR_CENTER, TILE_URL, TILE_ATTRIBUTION } from '../api'
import { useLang } from '../i18n'
import { SEV_HEX } from '../components/shared'

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
  const [error, setError] = useState('')

  useEffect(() => {
    setS(null)
    api.summary(area, 30).then(setS).catch((e) => setError(e.message))
  }, [area])

  const fmt = (iso) => (iso ? new Date(iso).toLocaleDateString(lang === 'hi' ? 'hi-IN' : 'en-IN', { day: 'numeric', month: 'short' }) : '-')
  const sevTotal = s ? Object.values(s.bySeverity).reduce((a, b) => a + b, 0) || 1 : 1

  return (
    <div className="page wide">
      <div className="row between wrap">
        <div>
          <h1>{t('authTitle')}</h1>
          <p className="muted">{t('authSub')}</p>
        </div>
        <div className="row gap wrap">
          <select className="input" value={area} onChange={(e) => setArea(e.target.value)}>
            {AREAS.map((a) => <option key={a} value={a}>{a || t('allAreas')}</option>)}
          </select>
          <a className="btn primary" href={api.pdfUrl({ area })} target="_blank" rel="noreferrer">📄 {t('downloadPdf')}</a>
        </div>
      </div>

      {error && <div className="banner err">{error}</div>}
      {!s && !error && <p className="muted">{t('loading')}</p>}

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
              <div className="row between small muted"><span>{fmt(s.perDay[0]?.date)}</span><span>{fmt(s.perDay.at(-1)?.date)}</span></div>
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
                      <td><a href={api.pdfUrl({ hotspotId: h.id })} target="_blank" rel="noreferrer">{t('pdf')}</a></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </div>
  )
}
