import { NavLink, Route, Routes, Link } from 'react-router-dom'
import { useLang } from './i18n'
import MapPage from './pages/MapPage'
import ReportPage from './pages/ReportPage'
import RoutePage from './pages/RoutePage'
import AlertsPage from './pages/AlertsPage'
import AuthorityPage from './pages/AuthorityPage'
import AboutPage from './pages/AboutPage'

const NAV = [
  { to: '/', key: 'nav_map', icon: '🗺️', end: true },
  { to: '/report', key: 'nav_report', icon: '📷' },
  { to: '/route', key: 'nav_route', icon: '🧭' },
  { to: '/alerts', key: 'nav_alerts', icon: '🔔' },
  { to: '/authority', key: 'nav_authority', icon: '🏛️' }
]

export default function App() {
  const { t, lang, setLang } = useLang()
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

      <main className="content">
        <Routes>
          <Route path="/" element={<MapPage />} />
          <Route path="/report" element={<ReportPage />} />
          <Route path="/route" element={<RoutePage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/authority" element={<AuthorityPage />} />
          <Route path="/about" element={<AboutPage />} />
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
