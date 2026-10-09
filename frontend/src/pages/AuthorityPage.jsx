import { useEffect, useState, useCallback } from 'react'
import { MapContainer, TileLayer, CircleMarker, Tooltip } from 'react-leaflet'
import { api, NCR_CENTER } from '../api'
import { useLang } from '../i18n'
import { SEV_HEX, timeAgo } from '../components/shared'

const AREAS = ['', 'Delhi', 'Noida', 'Greater Noida', 'Ghaziabad', 'Gurugram']

function Bars({ data = [] }) {
  if (!data || data.length === 0) return null
  const max = Math.max(1, ...data.map((d) => d.count || 0))
  return (
    <div className="bars" role="img" aria-label="Reports per day chart">
      {data.map((d) => (
        <div key={d.date} className="bar-col" title={`${d.date}: ${d.count} report(s)`}>
          <div
            className="bar"
            style={{
              height: `${Math.max(4, (d.count / max) * 100)}%`,
              background: d.count > 0 ? 'var(--brand)' : 'var(--line)'
            }}
          />
        </div>
      ))}
    </div>
  )
}

export default function AuthorityPage() {
  const { t, lang } = useLang()
  const [area, setArea] = useState('')
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const loadData = useCallback(() => {
    setLoading(true)
    setError('')
    api.summary(area, 30)
      .then((data) => {
        setSummary(data)
      })
      .catch((e) => setError(e.message || 'Failed to load authority summary.'))
      .finally(() => setLoading(false))
  }, [area])

  useEffect(() => {
    loadData()
  }, [loadData])

  const fmt = (iso) => {
    if (!iso) return '-'
    try {
      return new Date(iso).toLocaleDateString(lang === 'hi' ? 'hi-IN' : 'en-IN', {
        day: 'numeric',
        month: 'short',
        year: 'numeric'
      })
    } catch {
      return iso
    }
  }

  const sevTotal = summary?.bySeverity
    ? Object.values(summary.bySeverity).reduce((a, b) => a + b, 0) || 1
    : 1

  return (
    <div className="page wide">
      {/* Dashboard Header with Area Filter & PDF Download */}
      <div className="row between wrap" style={{ marginBottom: 12 }}>
        <div>
          <h1>🏛️ {t('authTitle')}</h1>
          <p className="muted">{t('authSub')}</p>
        </div>
        <div className="row gap wrap">
          <select
            className="input"
            value={area}
            onChange={(e) => setArea(e.target.value)}
            aria-label={t('area')}
          >
            {AREAS.map((a) => (
              <option key={a} value={a}>
                {a || t('allAreas')}
              </option>
            ))}
          </select>
          <a
            className="btn primary"
            href={api.pdfUrl({ area })}
            target="_blank"
            rel="noreferrer"
            aria-label={t('downloadPdf')}
          >
            📄 {t('downloadPdf')}
          </a>
        </div>
      </div>

      <div className="banner info small" style={{ margin: '10px 0 16px' }}>
        🏛️ {t('pdfDisclaimer')}
      </div>

      {error && (
        <div className="banner err">
          <span>⚠️ {error}</span>
          <button
            className="btn small ghost"
            onClick={loadData}
            style={{ marginLeft: 10, padding: '2px 8px', minHeight: 28 }}
          >
            {t('retry')}
          </button>
        </div>
      )}

      {loading && !summary && (
        <div className="card center" style={{ padding: 40 }}>
          <span className="spinner" style={{ borderColor: 'rgba(0,0,0,0.2)', borderTopColor: 'var(--brand)', width: 28, height: 28 }} />
          <p className="muted" style={{ marginTop: 12 }}>{t('loading')}</p>
        </div>
      )}

      {summary && (
        <>
          {/* Key Metric KPI Cards */}
          <div className="kpis">
            <div className="kpi">
              <span>{t('totalReports')}</span>
              <b>{summary.totalReports ?? 0}</b>
            </div>
            <div className="kpi hot">
              <span>{t('activeNow')}</span>
              <b>{summary.activeNow ?? 0}</b>
            </div>
            <div className="kpi">
              <span>{t('hotspots')}</span>
              <b>{summary.hotspots ?? 0}</b>
            </div>
            <div className="kpi">
              <span>{t('last7')}</span>
              <b>{summary.reportsLast7Days ?? 0}</b>
            </div>
          </div>

          {/* Analytical Charts & Hotspot Map Grid */}
          <div className="grid-dash">
            {/* Daily Trend Chart */}
            <section className="card">
              <h3>📈 {t('perDay')}</h3>
              <Bars data={summary.perDay} />
              <div className="row between small muted" style={{ marginTop: 6 }}>
                <span>{fmt(summary.perDay?.[0]?.date)}</span>
                <span>{fmt(summary.perDay?.at(-1)?.date)}</span>
              </div>
            </section>

            {/* Severity Distribution */}
            <section className="card">
              <h3>📊 {t('bySeverity')}</h3>
              <div className="sev-stack">
                {Object.entries(summary.bySeverity || {}).map(([k, v]) => (
                  v > 0 && (
                    <div
                      key={k}
                      style={{
                        width: `${(v / sevTotal) * 100}%`,
                        background: SEV_HEX[k] || '#64748B'
                      }}
                      title={`${k}: ${v} incidents`}
                    />
                  )
                ))}
              </div>
              <ul className="legend">
                {Object.entries(summary.bySeverity || {}).map(([k, v]) => (
                  <li key={k}>
                    <span className="dot" style={{ background: SEV_HEX[k] || '#64748B' }} />
                    {t('sev_' + k)}: <b>{v}</b>
                  </li>
                ))}
              </ul>
            </section>

            {/* Hotspots Geospatial Map */}
            <section className="card map-card">
              <MapContainer
                center={NCR_CENTER}
                zoom={10}
                style={{ height: '100%', minHeight: 280, width: '100%' }}
                scrollWheelZoom={false}
                zoomControl={false}
              >
                <TileLayer
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                  attribution="&copy; OpenStreetMap"
                />
                {(summary.topHotspots || []).map((h) => (
                  <CircleMarker
                    key={h.id}
                    center={[h.latitude, h.longitude]}
                    radius={Math.min(22, 6 + (h.totalReports || 1))}
                    pathOptions={{
                      color: h.activeNow ? SEV_HEX.SEVERE : SEV_HEX.HIGH,
                      fillColor: h.activeNow ? SEV_HEX.SEVERE : SEV_HEX.HIGH,
                      fillOpacity: 0.55,
                      weight: 2
                    }}
                  >
                    <Tooltip>
                      <div style={{ fontSize: '0.85rem' }}>
                        <b>{h.name}</b> ({h.area})<br />
                        {h.totalReports} total reports · Max {h.maxDepthFt?.toFixed(1) || 0} ft
                      </div>
                    </Tooltip>
                  </CircleMarker>
                ))}
              </MapContainer>
            </section>
          </div>

          {/* Top Hotspots Action Table */}
          <section className="card" style={{ marginTop: 16 }}>
            <div className="row between wrap" style={{ marginBottom: 10 }}>
              <h3>🔥 {t('topHotspots')}</h3>
              <span className="small muted">Ranked by historical frequency & citizen report density</span>
            </div>

            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>{t('location')}</th>
                    <th>{t('area')}</th>
                    <th>{t('reports')}</th>
                    <th>{t('last30')}</th>
                    <th>{t('maxDepth')}</th>
                    <th>{t('lastSeen')}</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {(summary.topHotspots || []).map((h, i) => (
                    <tr key={h.id}>
                      <td>{i + 1}</td>
                      <td>
                        {h.activeNow && (
                          <span
                            className="live-dot"
                            title="Waterlogging currently active"
                            aria-label="Active now"
                          />
                        )}
                        <b>{h.name}</b>
                      </td>
                      <td>{h.area || 'NCR'}</td>
                      <td><b>{h.totalReports}</b></td>
                      <td>{h.reportsLast30Days ?? 0}</td>
                      <td>{(h.maxDepthFt || 0).toFixed(1)} ft</td>
                      <td>
                        <div>{fmt(h.lastSeen)}</div>
                        {h.lastSeen && <div className="small muted">({timeAgo(h.lastSeen, lang)} {t('ago')})</div>}
                      </td>
                      <td>
                        <a
                          className="btn small ghost"
                          href={api.pdfUrl({ hotspotId: h.id })}
                          target="_blank"
                          rel="noreferrer"
                          style={{ padding: '4px 10px', minHeight: 30, fontSize: '0.8rem' }}
                        >
                          📄 {t('pdf')}
                        </a>
                      </td>
                    </tr>
                  ))}
                  {(!summary.topHotspots || summary.topHotspots.length === 0) && (
                    <tr>
                      <td colSpan={8} className="center muted" style={{ padding: 24 }}>
                        No hotspots reported in this area.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </div>
  )
}
