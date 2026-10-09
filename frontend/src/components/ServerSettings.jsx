import { useEffect, useState } from 'react'
import { CheckCircle2, CircleDashed, Server, XCircle } from 'lucide-react'
import { API_BASE, api, parseServerUrl, setApiBase, isNativeApp } from '../api'
import { useLang, errorText } from '../i18n'
import { Button, Notice } from './ui'

/**
 * Backend address + live diagnostics. "Save & test" validates the address, saves it, and calls
 * /api/health with an 8-second timeout; the result is always shown (never stuck on "Checking…").
 */
export default function ServerSettings({ compact = false }) {
  const { t } = useLang()
  const [url, setUrl] = useState(API_BASE)
  const [state, setState] = useState('idle') // idle | checking | ok | fail
  const [health, setHealth] = useState(null)
  const [error, setError] = useState(null)

  const test = async () => {
    setError(null)
    setHealth(null)
    if (isNativeApp || url.trim()) {
      const p = parseServerUrl(url)
      if (!p.ok) {
        setState('fail')
        setError({ message: t('s_' + p.error) })
        return
      }
      setUrl(p.url)
      setApiBase(p.url)
    } else {
      setApiBase('')
    }
    setState('checking')
    try {
      const h = await api.health()
      if (!h || !h.status) throw new Error('bad')
      setHealth(h)
      setState('ok')
    } catch (e) {
      setError(e)
      setState('fail')
    }
  }

  // show the status of an already saved address
  useEffect(() => {
    if (API_BASE || !isNativeApp) test()
  }, []) // eslint-disable-line

  const row = (label, value, good) => (
    <div className="diag-row">
      <span>{label}</span>
      <span className={good ? 'ok-text' : 'muted'}>{value}</span>
    </div>
  )

  return (
    <section className="card stack" id="server">
      <h3 className="card-title"><Server size={18} aria-hidden /> {t('serverAddress')}</h3>
      {!compact && <p className="small muted">{t('serverHelp')}</p>}
      <div className="row gap">
        <input className="input" value={url} onChange={(e) => setUrl(e.target.value)}
          placeholder="https://your-server  or  http://<laptop-IP>:8091" inputMode="url" autoCapitalize="off" autoCorrect="off" spellCheck={false} />
        <Button variant="secondary" loading={state === 'checking'} onClick={test}>{t('testConnection')}</Button>
      </div>

      {state === 'checking' && <Notice kind="info"><CircleDashed size={14} className="spin" aria-hidden /> {t('checking')}</Notice>}
      {state === 'fail' && <Notice kind="error"><XCircle size={14} aria-hidden /> {errorText(error, t)}</Notice>}
      {state === 'ok' && health && (
        <>
          <Notice kind="success"><CheckCircle2 size={14} aria-hidden /> {t('connected')} · {API_BASE || location.origin}</Notice>
          <div className="diag">
            {row(t('db'), health.database, health.database === 'UP')}
            {row(t('ai'), health.ai === 'ENABLED' ? 'Amazon Bedrock' : 'Off', health.ai === 'ENABLED')}
            {row(t('storage'), health.storage === 'S3' ? 'Amazon S3' : 'Server disk', health.storage === 'S3')}
            {row(t('alertsSvc'), health.alerts === 'SNS' ? 'Amazon SNS' : 'Log only', health.alerts === 'SNS')}
            {row(t('emailSvc'), health.email === 'smtp' ? 'Email (SMTP)' : health.email === 'dev-console' ? 'Dev console only' : 'Not configured', health.email === 'smtp')}
          </div>
        </>
      )}
    </section>
  )
}
