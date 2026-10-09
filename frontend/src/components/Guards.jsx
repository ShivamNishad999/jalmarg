import { Link, useLocation } from 'react-router-dom'
import { Landmark, LogIn, Loader2 } from 'lucide-react'
import { useAuth } from '../auth'
import { useLang } from '../i18n'

function Gate({ icon: Icon, title, sub, children }) {
  return (
    <div className="page narrow">
      <div className="gate card">
        <Icon size={36} aria-hidden className="gate-icon" />
        <h2>{title}</h2>
        <p className="muted">{sub}</p>
        {children}
      </div>
    </div>
  )
}

/** Shows the page only to signed-in users; others get a clear sign-in prompt (not a broken form). */
export function RequireAuth({ children }) {
  const { user, ready } = useAuth()
  const { t } = useLang()
  const location = useLocation()
  if (!ready) return <div className="page narrow center"><Loader2 className="spin" aria-label="Loading" /></div>
  if (user) return children
  return (
    <Gate icon={LogIn} title={t('needSignIn')} sub={t('needSignInSub')}>
      <div className="row gap center-row">
        <Link to="/login" state={{ from: location.pathname }} className="btn primary">{t('signIn')}</Link>
        <Link to="/register" className="btn secondary">{t('getStarted')}</Link>
      </div>
    </Gate>
  )
}

/** AUTHORITY / ADMIN only. The server enforces this too; this just explains it. */
export function RequireAuthority({ children }) {
  const { user, ready, isAuthority } = useAuth()
  const { t } = useLang()
  if (!ready) return <div className="page narrow center"><Loader2 className="spin" aria-label="Loading" /></div>
  if (!user) return <RequireAuth>{children}</RequireAuth>
  if (isAuthority) return children
  return <Gate icon={Landmark} title={t('authorityOnly')} sub={t('authorityOnlySub')} />
}
