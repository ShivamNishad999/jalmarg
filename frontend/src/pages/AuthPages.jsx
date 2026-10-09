import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { CheckCircle2, Droplets, KeyRound, Mail, MapPinned, Route, BellRing, Server } from 'lucide-react'
import { api, serverConfigured } from '../api'
import { useAuth } from '../auth'
import { useLang, errorText } from '../i18n'
import { Button, Field, Notice, PasswordField } from '../components/ui'

const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/
const pwOk = (p) => p.length >= 8 && p.length <= 72 && /[A-Za-z]/.test(p) && /\d/.test(p)

function AuthShell({ children }) {
  return (
    <div className="auth-shell">
      <div className="auth-card">{children}</div>
    </div>
  )
}

function ServerHint() {
  const { t } = useLang()
  if (serverConfigured()) return null
  return (
    <Notice kind="warn" action={<Link to="/settings#server" className="btn small secondary">{t('setupServer')}</Link>}>
      {t('e_NOT_CONFIGURED')}
    </Notice>
  )
}

// ------------------------------------------------------------------ welcome

export function WelcomePage() {
  const { t } = useLang()
  const navigate = useNavigate()
  const done = () => { try { localStorage.setItem('jalmarg-welcomed', '1') } catch { /* ignore */ } }
  return (
    <div className="welcome">
      <div className="welcome-hero">
        <div className="welcome-logo"><Droplets size={34} aria-hidden /></div>
        <h1>{t('welcomeTitle')}</h1>
        <p>{t('welcomeSub')}</p>
      </div>
      <ul className="welcome-points">
        <li><MapPinned size={20} aria-hidden /><span>{t('nav_map')}</span></li>
        <li><Route size={20} aria-hidden /><span>{t('nav_route')}</span></li>
        <li><BellRing size={20} aria-hidden /><span>{t('nav_alerts')}</span></li>
      </ul>
      <div className="welcome-actions">
        <Button block onClick={() => { done(); navigate('/register') }}>{t('getStarted')}</Button>
        <Button block variant="secondary" onClick={() => { done(); navigate('/login') }}>{t('signIn')}</Button>
        <button className="link-btn" onClick={() => { done(); navigate('/') }}>{t('continueGuest')}</button>
      </div>
      {!serverConfigured() && (
        <Link to="/settings#server" className="welcome-server"><Server size={14} aria-hidden /> {t('setupServer')}</Link>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ login

export function LoginPage() {
  const { t } = useLang()
  const { login, notice, clearNotice } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const [email, setEmail] = useState(params.get('email') || '')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const next = location.state?.from || '/'

  const submit = async (e) => {
    e.preventDefault()
    const errs = {}
    if (!EMAIL_RE.test(email.trim())) errs.email = t('e_EMAIL_INVALID')
    if (!password) errs.password = t('password')
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    setError(null)
    try {
      await login(email.trim(), password)
      clearNotice()
      navigate(next, { replace: true })
    } catch (err) {
      if (err.code === 'EMAIL_NOT_VERIFIED') {
        navigate(`/verify?email=${encodeURIComponent(email.trim())}&resend=1`)
        return
      }
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell>
      <h1>{t('loginTitle')}</h1>
      <p className="muted">{t('loginSub')}</p>
      <ServerHint />
      {notice === 'SESSION_EXPIRED' && <Notice kind="info">{t('sessionExpired')}</Notice>}
      <form onSubmit={submit} noValidate className="stack">
        <Field label={t('email')} type="email" autoComplete="email" inputMode="email" value={email}
          onChange={(e) => setEmail(e.target.value)} error={errors.email} />
        <PasswordField label={t('password')} autoComplete="current-password" value={password}
          onChange={(e) => setPassword(e.target.value)} error={errors.password} />
        <div className="row end"><Link to={`/forgot?email=${encodeURIComponent(email)}`} className="small">{t('forgotLink')}</Link></div>
        {error && <Notice kind="error">{errorText(error, t)}</Notice>}
        <Button block loading={busy} type="submit">{t('signIn')}</Button>
      </form>
      <p className="center small">{t('noAccount')} <Link to="/register">{t('getStarted')}</Link></p>
    </AuthShell>
  )
}

// ------------------------------------------------------------------ register

export function RegisterPage() {
  const { t } = useLang()
  const navigate = useNavigate()
  const [f, setF] = useState({ name: '', email: '', password: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    const errs = {}
    if (f.name.trim().length < 2) errs.name = t('fullName')
    if (!EMAIL_RE.test(f.email.trim())) errs.email = t('e_EMAIL_INVALID')
    if (!pwOk(f.password)) errs.password = t('pwRules')
    if (f.password !== f.confirm) errs.confirm = t('e_PASSWORD_MISMATCH')
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    setError(null)
    try {
      await api.register({ name: f.name.trim(), email: f.email.trim(), password: f.password })
      navigate(`/verify?email=${encodeURIComponent(f.email.trim())}`)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell>
      <h1>{t('registerTitle')}</h1>
      <p className="muted">{t('registerSub')}</p>
      <ServerHint />
      <form onSubmit={submit} noValidate className="stack">
        <Field label={t('fullName')} autoComplete="name" value={f.name} onChange={set('name')} error={errors.name} maxLength={100} />
        <Field label={t('email')} type="email" autoComplete="email" inputMode="email" value={f.email} onChange={set('email')} error={errors.email} />
        <PasswordField label={t('password')} autoComplete="new-password" value={f.password} onChange={set('password')}
          error={errors.password} hint={t('pwRules')} maxLength={72} />
        <PasswordField label={t('confirmPassword')} autoComplete="new-password" value={f.confirm} onChange={set('confirm')}
          error={errors.confirm} maxLength={72} />
        {error && <Notice kind="error">{errorText(error, t)}</Notice>}
        <Button block loading={busy} type="submit">{t('createAccount')}</Button>
      </form>
      <p className="center small">{t('haveAccount')} <Link to="/login">{t('signIn')}</Link></p>
    </AuthShell>
  )
}

// ------------------------------------------------------------------ OTP

function useCooldown(seconds) {
  const [left, setLeft] = useState(seconds)
  useEffect(() => {
    if (left <= 0) return
    const id = setTimeout(() => setLeft(left - 1), 1000)
    return () => clearTimeout(id)
  }, [left])
  return [left, () => setLeft(seconds)]
}

function CodeInput({ value, onChange, autoFocus }) {
  return (
    <input className="input code-input" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
      value={value} autoFocus={autoFocus} aria-label="6-digit code" placeholder="••••••"
      onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, 6))} />
  )
}

export function VerifyPage() {
  const { t } = useLang()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const email = params.get('email') || ''
  const [code, setCode] = useState('')
  const [error, setError] = useState(null)
  const [info, setInfo] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [left, restart] = useCooldown(params.get('resend') ? 0 : 60)
  const sentOnce = useRef(false)

  const resend = async () => {
    setError(null)
    try {
      await api.resendCode(email, 'VERIFY_EMAIL')
      setInfo(t('verifyHint'))
      restart()
    } catch (err) {
      setError(err)
    }
  }

  // came from login with an unverified account: send a fresh code once
  useEffect(() => {
    if (params.get('resend') && email && !sentOnce.current) {
      sentOnce.current = true
      resend()
    }
  }, []) // eslint-disable-line

  const submit = async (e) => {
    e.preventDefault()
    if (code.length !== 6) return setError({ code: 'CODE_FORMAT' })
    setBusy(true)
    setError(null)
    try {
      await api.verifyEmail(email, code)
      setDone(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <AuthShell>
        <div className="done-icon"><CheckCircle2 size={44} aria-hidden /></div>
        <h1>{t('verifiedTitle')}</h1>
        <p className="muted">{t('verifiedSub')}</p>
        <Button block onClick={() => navigate(`/login?email=${encodeURIComponent(email)}`, { replace: true })}>{t('signIn')}</Button>
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <div className="done-icon soft"><Mail size={36} aria-hidden /></div>
      <h1>{t('verifyTitle')}</h1>
      <p className="muted">{t('verifySub')} <b>{email}</b></p>
      <form onSubmit={submit} className="stack">
        <CodeInput value={code} onChange={setCode} autoFocus />
        <p className="small muted">{info || t('verifyHint')}</p>
        {error && <Notice kind="error">{errorText(error, t)}</Notice>}
        <Button block loading={busy} type="submit">{t('verifyBtn')}</Button>
      </form>
      <p className="center small">
        {left > 0 ? `${t('resendIn')} ${left} ${t('sec')}` : <button className="link-btn" onClick={resend}>{t('resend')}</button>}
      </p>
    </AuthShell>
  )
}

// ------------------------------------------------------------------ forgot / reset

export function ForgotPage() {
  const { t } = useLang()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState(params.get('email') || '')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    if (!EMAIL_RE.test(email.trim())) return setError({ code: 'EMAIL_INVALID' })
    setBusy(true)
    setError(null)
    try {
      await api.forgot(email.trim())
      navigate(`/reset?email=${encodeURIComponent(email.trim())}`)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell>
      <div className="done-icon soft"><KeyRound size={36} aria-hidden /></div>
      <h1>{t('forgotTitle')}</h1>
      <p className="muted">{t('forgotSub')}</p>
      <form onSubmit={submit} noValidate className="stack">
        <Field label={t('email')} type="email" autoComplete="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        {error && <Notice kind="error">{errorText(error, t)}</Notice>}
        <Button block loading={busy} type="submit">{t('sendCode')}</Button>
      </form>
      <p className="center small"><Link to="/login">{t('signIn')}</Link></p>
    </AuthShell>
  )
}

export function ResetPage() {
  const { t } = useLang()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const email = params.get('email') || ''
  const [code, setCode] = useState('')
  const [pw, setPw] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [left, restart] = useCooldown(60)

  const submit = async (e) => {
    e.preventDefault()
    const errs = {}
    if (code.length !== 6) errs.code = t('e_CODE_FORMAT')
    if (!pwOk(pw)) errs.pw = t('pwRules')
    if (pw !== confirm) errs.confirm = t('e_PASSWORD_MISMATCH')
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    setError(null)
    try {
      await api.reset(email, code, pw)
      setDone(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  if (done) {
    return (
      <AuthShell>
        <div className="done-icon"><CheckCircle2 size={44} aria-hidden /></div>
        <h1>{t('resetDone')}</h1>
        <Button block onClick={() => navigate(`/login?email=${encodeURIComponent(email)}`, { replace: true })}>{t('signIn')}</Button>
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <h1>{t('resetTitle')}</h1>
      <p className="muted">{t('verifySub')} <b>{email}</b></p>
      <form onSubmit={submit} noValidate className="stack">
        <CodeInput value={code} onChange={setCode} autoFocus />
        {errors.code && <div className="field-error">{errors.code}</div>}
        <PasswordField label={t('newPassword')} autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} error={errors.pw} hint={t('pwRules')} maxLength={72} />
        <PasswordField label={t('confirmPassword')} autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} error={errors.confirm} maxLength={72} />
        {error && <Notice kind="error">{errorText(error, t)}</Notice>}
        <Button block loading={busy} type="submit">{t('resetBtn')}</Button>
      </form>
      <p className="center small">
        {left > 0 ? `${t('resendIn')} ${left} ${t('sec')}` : (
          <button className="link-btn" onClick={async () => { try { await api.resendCode(email, 'RESET_PASSWORD'); restart() } catch (err) { setError(err) } }}>{t('resend')}</button>
        )}
      </p>
    </AuthShell>
  )
}
