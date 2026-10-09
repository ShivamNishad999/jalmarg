import { useState } from 'react'
import { API_BASE, api, setApiBase } from '../api'
import { useLang } from '../i18n'

/** Lets the Android app (or a tester) point JalMarg at a backend: AWS URL or laptop IP on Wi-Fi. */
export default function ServerSettings() {
  const { lang } = useLang()
  const [url, setUrl] = useState(API_BASE)
  const [status, setStatus] = useState('')

  const save = async () => {
    setApiBase(url)
    setStatus(lang === 'hi' ? 'जाँच रहे हैं…' : 'Checking…')
    try {
      const m = await api.meta()
      setStatus((lang === 'hi' ? '✓ जुड़ गया' : '✓ Connected') + (m.aiEnabled ? ' · AI on' : ''))
    } catch (e) {
      setStatus('✗ ' + e.message)
    }
  }

  return (
    <section className="card stack" id="server">
      <h3>{lang === 'hi' ? 'सर्वर' : 'Server'}</h3>
      <p className="small muted">
        {lang === 'hi'
          ? 'बैकएंड का पता, जैसे https://xxxx.cloudfront.net या Wi-Fi पर http://192.168.1.5:8091'
          : 'Backend address, e.g. https://xxxx.cloudfront.net or on Wi-Fi http://192.168.1.5:8091'}
      </p>
      <div className="row gap">
        <input className="input" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://..." inputMode="url" />
        <button className="btn small" onClick={save}>{lang === 'hi' ? 'सेव' : 'Save'}</button>
      </div>
      {status && <div className="small">{status}</div>}
    </section>
  )
}
