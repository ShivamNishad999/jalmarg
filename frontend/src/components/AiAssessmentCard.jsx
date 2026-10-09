import { useLang } from '../i18n'
import { assessmentKey, depthText, sourceKey } from '../utils/evidence'

export default function AiAssessmentCard({ report: r }) {
  const { t, lang } = useLang()
  if (!r) return null
  const summary = lang === 'hi' && typeof r.aiSummaryHi === 'string' ? r.aiSummaryHi : r.aiSummary
  const confidence = typeof r.aiConfidence === 'number' && Number.isFinite(r.aiConfidence) && r.aiConfidence >= 0 && r.aiConfidence <= 1
  return (
    <section className="bedrock-card" aria-label={t('aiEvidence')}>
      <h3>{t('aiEvidence')}</h3>
      <p><b>{t(assessmentKey(r))}</b></p>
      <p>{t('reportedDepth')}: <b>{depthText(r.depthFt, t)}</b></p>
      <p className="small">{t('sourceLabel')}: {t(sourceKey(r))}</p>
      {typeof summary === 'string' && summary.trim() && <p className="ai-summary">{summary}</p>}
      {confidence && <p className="small">{t('confidenceScore')}: {Math.round(r.aiConfidence * 100)}%</p>}
      <p className="small muted">{t('aiStatusUnknown')}</p>
      <p className="small muted">{t('aiLimitations')}</p>
      {r.depthSource === 'CITIZEN' && !r.citizenDepthLevel && <p className="small muted">{t('fallbackNotice')}</p>}
    </section>
  )
}
