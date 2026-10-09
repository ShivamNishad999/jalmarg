import { useEffect, useRef, useState } from 'react'
import { NavLink, Route, Routes, Link, useLocation, useNavigate } from 'react-router-dom'
import { useLang } from './i18n'
import MapPage from './pages/MapPage'
import ReportPage from './pages/ReportPage'
import RoutePage from './pages/RoutePage'
import AlertsPage from './pages/AlertsPage'
import AuthorityPage from './pages/AuthorityPage'
import AboutPage from './pages/AboutPage'
import ServerSettings from './components/ServerSettings'
import { initNative, watchNetwork, isNative } from './native'
import { API_BASE } from './api'

const NAV = [
  { to: '/', key: 'nav_map', icon: '🗺️', end: true },
  { to: '/report', key: 'nav_report', icon: '📷' },
  { to: '/route', key: 'nav_route', icon: '🧭' },
  { to: '/alerts', key: 'nav_alerts', icon: '🔔' },
  { to: '/authority', key: 'nav_authority', icon: '🏛️' }
]

export default function App() {
  const { t, lang, setLang } = useLang()
  const navigate = useNavigate()
  const location = useLocation()
  const pathRef = useRef(location.pathname)
  pathRef.current = location.pathname
  const [online, setOnline] = useState(true)

  // Android: status bar, splash, back button. Browser: nothing.
  useEffect(() => initNative(navigate, () => pathRef.current), [navigate])
  useEffect(() => watchNetwork(setOnline), [])
  // First launch of the Android app: ask for the server address before anything else
  useEffect(() => {
    if (isNative && !API_BASE && location.pathname !== '/server') navigate('/server', { replace: true })
  }, []) // eslint-disable-line

  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="brand">
          <img src="/icon.svg" alt="" width="30" height="30" />
          <span>
            <b>JalMarg</b>
            <small>{t('appTagline')}</small>
          </span>
        </Link>
        <nav className="topnav">
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end}>{t(n.key)}</NavLink>
          ))}
          <NavLink to="/about">{t('nav_about')}</NavLink>
        </nav>
        <button
          className="lang-toggle"
          onClick={() => setLang(lang === 'en' ? 'hi' : 'en')}
          aria-label="Switch language"
        >
          {lang === 'en' ? 'हिंदी' : 'English'}
        </button>
      </header>

      {!online && <div className="offline-bar" role="status">{t('offline')}</div>}

      <main className="content">
        <Routes>
          <Route path="/" element={<MapPage />} />
          <Route path="/report" element={<ReportPage />} />
          <Route path="/route" element={<RoutePage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/authority" element={<AuthorityPage />} />
          <Route path="/about" element={<AboutPage />} />
          <Route path="/server" element={<div className="page narrow"><ServerSettings /></div>} />
          <Route path="*" element={<MapPage />} />
        </Routes>
      </main>

      <nav className="bottomnav">
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end}>
            <span className="ico" aria-hidden>{n.icon}</span>
            <span>{t(n.key)}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
