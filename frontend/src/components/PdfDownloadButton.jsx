import { useEffect, useRef, useState } from 'react'
import { requestPdf } from '../api'
import { useLang } from '../i18n'
import { safePdfFilename, validatePdfBlob } from '../utils/pdf'

export default function PdfDownloadButton({ path, filename, enabled = false }) {
  const { t } = useLang()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(false)
  const pending = useRef(null)
  const downloads = useRef(new Map())
  useEffect(() => () => {
    pending.current?.abort()
    downloads.current.forEach((timer, url) => { clearTimeout(timer); URL.revokeObjectURL(url) })
    downloads.current.clear()
  }, [])
  const download = async () => {
    if (!enabled || !path || pending.current) return
    const controller = new AbortController()
    pending.current = controller
    setBusy(true)
    setError(false)
    try {
      const blob = await validatePdfBlob(await requestPdf(path, { signal: controller.signal }))
      if (controller.signal.aborted) return
      const url = URL.createObjectURL(blob)
      // Allow browsers time to consume the URL; also revoke on unmount.
      const timer = setTimeout(() => { URL.revokeObjectURL(url); downloads.current.delete(url) }, 60000)
      downloads.current.set(url, timer)
      const link = document.createElement('a')
      link.href = url
      link.download = safePdfFilename(filename)
      document.body.appendChild(link)
      try { link.click() } finally { link.remove() }
    } catch {
      if (!controller.signal.aborted) setError(true)
    } finally {
      pending.current = null
      if (!controller.signal.aborted) setBusy(false)
    }
  }
  return <div className="stack">
    <button className="btn ghost" type="button" disabled={!enabled || !path || busy} onClick={download}>{t(busy ? 'pdfLoading' : 'pdfDownload')}</button>
    {!enabled && <p className="small muted">{t('pdfUnavailable')}</p>}
    {busy && <p role="status">{t('pdfLoading')}</p>}
    {error && <p className="banner err" role="alert">{t('pdfError')}</p>}
  </div>
}
