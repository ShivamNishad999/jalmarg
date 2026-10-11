import { useLang } from '../i18n'
import { ReportCard } from './shared'
import PdfDownloadButton from './PdfDownloadButton'
import { confirmationCount, formatTimestamp, lifecycleKey, validId } from '../utils/evidence'

// Deployment operator must verify the endpoint before explicitly setting this flag.
const incidentPdfVerified = import.meta.env.VITE_INCIDENT_PDF_VERIFIED === 'true'

export default function ReportEvidencePanel({ report: r }) {
  const { t, lang } = useLang()
  if (!r) return null
  const count = confirmationCount(r)
  return <section className="card evidence-panel" aria-label={t('evidenceTitle')}>
    <h2>{t('evidenceTitle')}</h2>
    <ReportCard r={r} showTimeline />
    <dl className="evidence-details">
      <dt>{t('reportId')}</dt><dd>{validId(r.id) ? r.id : t('unknownValue')}</dd>
      <dt>{t('submittedAt')}</dt><dd>{formatTimestamp(r.createdAt, lang, t('unknownValue'))}</dd>
      <dt>{t('citizenCategory')}</dt><dd>{['PATCHY', 'ANKLE', 'SHIN', 'KNEE', 'WAIST'].includes(r.citizenDepthLevel) ? t('lvl_' + r.citizenDepthLevel) : t('unknownValue')}</dd>
      <dt>{t('lifecycle')}</dt><dd>{t(lifecycleKey(r.status))}</dd>
      <dt>{t('lastConfirmation')}</dt><dd>{count > 0 ? formatTimestamp(r.lastConfirmedAt, lang, t('unknownValue')) : t(count === 0 ? 'noConfirmation' : 'unknownValue')}</dd>
    </dl>
    <p className="small muted">{t('verificationNotice')}</p>
    <PdfDownloadButton enabled={incidentPdfVerified && validId(r.id)} path={validId(r.id) ? `/api/reports/${r.id}/evidence.pdf` : null} filename={`jalmarg-report-${r.id}`} />
  </section>
}
