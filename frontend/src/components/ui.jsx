import { useState } from 'react'
import { AlertTriangle, CheckCircle2, Eye, EyeOff, Info, Loader2 } from 'lucide-react'
import { useLang } from '../i18n'

/** Labelled input with an inline error. */
export function Field({ label, error, hint, id, ...props }) {
  const fid = id || 'f-' + label?.replace(/\W+/g, '-').toLowerCase()
  return (
    <div className="field">
      <label htmlFor={fid}>{label}</label>
      <input id={fid} className={'input' + (error ? ' invalid' : '')} aria-invalid={!!error} {...props} />
      {error ? <div className="field-error" role="alert">{error}</div> : hint ? <div className="field-hint">{hint}</div> : null}
    </div>
  )
}

export function PasswordField({ label, error, hint, ...props }) {
  const { t } = useLang()
  const [show, setShow] = useState(false)
  const fid = 'f-' + label.replace(/\W+/g, '-').toLowerCase()
  return (
    <div className="field">
      <label htmlFor={fid}>{label}</label>
      <div className="input-wrap">
        <input id={fid} className={'input' + (error ? ' invalid' : '')} type={show ? 'text' : 'password'} aria-invalid={!!error} {...props} />
        <button type="button" className="input-icon" onClick={() => setShow(!show)} aria-label={show ? t('hide') : t('show')}>
          {show ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </div>
      {error ? <div className="field-error" role="alert">{error}</div> : hint ? <div className="field-hint">{hint}</div> : null}
    </div>
  )
}

/** Primary/secondary button with a built-in loading state (blocks double taps). */
export function Button({ loading, children, variant = 'primary', block, className = '', ...props }) {
  return (
    <button {...props} className={`btn ${variant} ${block ? 'block' : ''} ${className}`} disabled={!!(loading || props.disabled)} aria-busy={!!loading}>
      {loading && <Loader2 size={18} className="spin" aria-hidden />}
      {children}
    </button>
  )
}

const ICONS = { error: AlertTriangle, success: CheckCircle2, info: Info, warn: AlertTriangle }

export function Notice({ kind = 'info', children, action }) {
  if (!children) return null
  const Icon = ICONS[kind] || Info
  return (
    <div className={`notice ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <Icon size={18} aria-hidden />
      <div className="notice-body">{children}</div>
      {action}
    </div>
  )
}

/** Screen title block used by every page. */
export function PageHead({ icon: Icon, title, sub, right }) {
  return (
    <div className="page-head">
      <div className="page-head-text">
        <h1>{Icon && <Icon size={22} aria-hidden className="page-head-icon" />}{title}</h1>
        {sub && <p className="muted">{sub}</p>}
      </div>
      {right}
    </div>
  )
}

export function Skeleton({ h = 16, w = '100%' }) {
  return <span className="skeleton" style={{ height: h, width: w }} aria-hidden />
}
