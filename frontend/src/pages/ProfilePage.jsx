import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BadgeCheck, ChevronRight, Info, Landmark, Languages, LogIn, LogOut, ShieldAlert, UserRound } from 'lucide-react'
import { useAuth } from '../auth'
import { useLang } from '../i18n'
import { Button, Notice, PageHead } from '../components/ui'
import ServerSettings from '../components/ServerSettings'

export default function ProfilePage() {
  const { t, lang, setLang } = useLang()
  const { user, logout, isAuthority } = useAuth()
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState('')

  const doLogout = async () => {
    setBusy(true)
    const revoked = await logout()
    setBusy(false)
    setConfirming(false)
    setMsg(revoked ? t('signedOutServer') : t('signedOutLocal'))
  }

  return (
    <div className="page narrow">
      <PageHead icon={UserRound} title={user ? user.name : t('profile')} sub={user ? user.email : t('needSignInSub')} />
      {msg && <Notice kind="success">{msg}</Notice>}

      <section className="card">
        <h3 className="card-title"><UserRound size={18} aria-hidden /> {t('account')}</h3>
        {user ? (
          <div className="diag">
            <div className="diag-row"><span>{t('email')}</span><span>{user.email}</span></div>
            <div className="diag-row"><span>{t('role')}</span><span className="pill neutral">{user.role}</span></div>
            <div className="diag-row"><span>{t('verified')}</span>
              <span className={user.emailVerified ? 'ok-text' : 'muted'}><BadgeCheck size={14} aria-hidden /> {user.emailVerified ? t('verified') : t('notVerified')}</span>
            </div>
            <div className="diag-row"><span>{t('memberSince')}</span><span>{user.createdAt ? new Date(user.createdAt).toLocaleDateString() : '-'}</span></div>
          </div>
        ) : (
          <div className="row gap wrap">
            <Button onClick={() => navigate('/login')}><LogIn size={18} aria-hidden /> {t('signIn')}</Button>
            <Button variant="secondary" onClick={() => navigate('/register')}>{t('getStarted')}</Button>
          </div>
        )}
      </section>

      {isAuthority && (
        <Link to="/authority" className="card link-card">
          <Landmark size={20} aria-hidden /> <span>{t('authorityTools')}</span> <ChevronRight size={18} aria-hidden />
        </Link>
      )}

      <section className="card">
        <h3 className="card-title"><Languages size={18} aria-hidden /> {t('languageLabel')}</h3>
        <div className="seg">
          <button className={lang === 'en' ? 'on' : ''} onClick={() => setLang('en')}>English</button>
          <button className={lang === 'hi' ? 'on' : ''} onClick={() => setLang('hi')}>हिंदी</button>
        </div>
      </section>

      <ServerSettings />

      <Link to="/about" className="card link-card">
        <Info size={20} aria-hidden /> <span>{t('appInfo')}</span> <ChevronRight size={18} aria-hidden />
      </Link>

      {user && !confirming && (
        <Button block variant="danger" onClick={() => setConfirming(true)}><LogOut size={18} aria-hidden /> {t('signOut')}</Button>
      )}
      {user && confirming && (
        <section className="card stack">
          <p><ShieldAlert size={18} aria-hidden /> {t('signOutConfirm')}</p>
          <div className="row gap">
            <Button variant="danger" loading={busy} onClick={doLogout}>{t('signOut')}</Button>
            <Button variant="secondary" onClick={() => setConfirming(false)}>{t('cancel')}</Button>
          </div>
        </section>
      )}
    </div>
  )
}
