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
      {r.imagePrediction && (
        <div style={{ marginTop: 8, padding: 8, background: 'rgba(2, 132, 199, 0.08)', borderRadius: 6, border: '1px solid rgba(2, 132, 199, 0.2)' }}>
          <p style={{ margin: 0, fontSize: '0.85rem' }}>
            <b>📸 AI Visual Assessment:</b>{' '}
            <span style={{ color: r.imagePrediction === 'VISIBLE_WATERLOGGING' ? '#0284C7' : '#64748B', fontWeight: 600 }}>
              {r.imagePrediction === 'VISIBLE_WATERLOGGING' ? 'Visible Waterlogging Detected' : 'No Visible Waterlogging Detected'}
            </span>
            {r.imageConfidence != null && ` (${Math.round(r.imageConfidence * 100)}% confidence)`}
          </p>
          <p className="small muted" style={{ margin: '4px 0 0', fontStyle: 'italic', fontSize: '0.75rem' }}>
            Assistive signal only · Municipal verification pending
          </p>
        </div>
      )}
      <p className="small muted">{t('aiStatusUnknown')}</p>
      <p className="small muted">{t('aiLimitations')}</p>
      {r.depthSource === 'CITIZEN' && !r.citizenDepthLevel && <p className="small muted">{t('fallbackNotice')}</p>}
    </section>
  )
}
