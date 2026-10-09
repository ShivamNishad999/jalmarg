import { useEffect, useRef, useState } from 'react'
import { NavLink, Route, Routes, Link, useLocation, useNavigate } from 'react-router-dom'
import { Bell, Camera, Droplets, Landmark, Map as MapIcon, Navigation, UserRound, WifiOff } from 'lucide-react'
import { useLang } from './i18n'
import { useAuth } from './auth'
import MapPage from './pages/MapPage'
import ReportPage from './pages/ReportPage'
import RoutePage from './pages/RoutePage'
import AlertsPage from './pages/AlertsPage'
import AuthorityPage from './pages/AuthorityPage'
import AboutPage from './pages/AboutPage'
import ProfilePage from './pages/ProfilePage'
import { WelcomePage, LoginPage, RegisterPage, VerifyPage, ForgotPage, ResetPage } from './pages/AuthPages'
import { RequireAuth, RequireAuthority } from './components/Guards'
import { initNative, watchNetwork } from './native'

const NAV = [
  { to: '/', key: 'nav_map', icon: MapIcon, end: true },
  { to: '/report', key: 'nav_report', icon: Camera },
  { to: '/route', key: 'nav_route', icon: Navigation },
  { to: '/alerts', key: 'nav_alerts', icon: Bell },
  { to: '/settings', key: 'profile', icon: UserRound }
]

// screens without the app chrome (full-screen auth flow)
const BARE = ['/welcome', '/login', '/register', '/verify', '/forgot', '/reset']

export default function App() {
  const { t, lang, setLang } = useLang()
  const { user, isAuthority } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const pathRef = useRef(location.pathname)
  pathRef.current = location.pathname
  const [online, setOnline] = useState(true)

  // Android: status bar, splash, back button. Browser: nothing.
  useEffect(() => initNative(navigate, () => pathRef.current), [navigate])
  useEffect(() => watchNetwork(setOnline), [])
  // First start: welcome screen once
  useEffect(() => {
    let seen = false
    try { seen = !!localStorage.getItem('jalmarg-welcomed') } catch { /* ignore */ }
    if (!seen && location.pathname === '/') navigate('/welcome', { replace: true })
  }, []) // eslint-disable-line

  const bare = BARE.includes(location.pathname)

  return (
    <div className={'app' + (bare ? ' bare' : '')}>
      <header className="topbar">
        <Link to="/" className="brand" aria-label="JalMarg home">
          <span className="brand-mark"><Droplets size={18} aria-hidden /></span>
          <span className="brand-text">
            <b>JalMarg</b>
            <small>{t('appTagline')}</small>
          </span>
        </Link>
        {!bare && (
          <nav className="topnav" aria-label="Main">
            {NAV.slice(0, 4).map((n) => (
              <NavLink key={n.to} to={n.to} end={n.end}><n.icon size={16} aria-hidden /> {t(n.key)}</NavLink>
            ))}
            {isAuthority && <NavLink to="/authority"><Landmark size={16} aria-hidden /> {t('nav_authority')}</NavLink>}
            <NavLink to="/settings"><UserRound size={16} aria-hidden /> {user ? user.name.split(' ')[0] : t('signIn')}</NavLink>
          </nav>
        )}
        <button className="lang-toggle" onClick={() => setLang(lang === 'en' ? 'hi' : 'en')} aria-label="Switch language">
          {lang === 'en' ? 'हिं' : 'EN'}
        </button>
      </header>

      {!online && <div className="offline-bar" role="status"><WifiOff size={14} aria-hidden /> {t('offline')}</div>}

      <main className="content">
        <Routes>
          <Route path="/" element={<MapPage />} />
          <Route path="/report" element={<RequireAuth><ReportPage /></RequireAuth>} />
          <Route path="/route" element={<RoutePage />} />
          <Route path="/alerts" element={<RequireAuth><AlertsPage /></RequireAuth>} />
          <Route path="/authority" element={<RequireAuthority><AuthorityPage /></RequireAuthority>} />
          <Route path="/settings" element={<ProfilePage />} />
          <Route path="/server" element={<ProfilePage />} />
          <Route path="/about" element={<AboutPage />} />
          <Route path="/welcome" element={<WelcomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/verify" element={<VerifyPage />} />
          <Route path="/forgot" element={<ForgotPage />} />
          <Route path="/reset" element={<ResetPage />} />
          <Route path="*" element={<MapPage />} />
        </Routes>
      </main>

      {!bare && (
        <nav className="bottomnav" aria-label="Main">
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end}>
              <n.icon size={22} aria-hidden />
              <span>{n.to === '/settings' && user ? user.name.split(' ')[0] : t(n.key)}</span>
            </NavLink>
          ))}
        </nav>
      )}
    </div>
  )
}
