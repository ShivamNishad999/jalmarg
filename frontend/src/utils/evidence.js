export const validDepth = (value) => typeof value === 'number' && Number.isFinite(value) && value >= 0
export const validId = (value) => Number.isSafeInteger(value) && value > 0
export const confirmationCount = (r) => Number.isInteger(r?.confirmations) && r.confirmations >= 0 ? r.confirmations : null
export const lifecycleKey = (status) => ['ACTIVE', 'CLEARED', 'EXPIRED'].includes(status) ? `life_${status}` : 'unknownValue'
export const sourceKey = (r) => ({ AI: 'sourceAI', CITIZEN: 'sourceCitizen', AI_AND_CITIZEN: 'sourceCombined', DEMO: 'demoTag' }[r?.depthSource] || 'unknownValue')
export function assessmentKey(r) {
  if (r?.depthSource === 'DEMO') return 'demoTag'
  if (r?.depthSource === 'AI_AND_CITIZEN') return 'sourceCombined'
  if (r?.depthSource === 'AI') return 'sourceAI'
  if (r?.citizenDepthLevel && ['PATCHY', 'ANKLE', 'SHIN', 'KNEE', 'WAIST'].includes(r.citizenDepthLevel)) return 'citizenProvided'
  return 'aiStatusUnknown'
}
export function formatTimestamp(value, lang, fallback) {
  if (!value || !Number.isFinite(Date.parse(value))) return fallback
  return new Date(value).toLocaleString(lang === 'hi' ? 'hi-IN' : 'en-IN')
}
export function depthText(value, t) {
  return validDepth(value) ? `${value.toFixed(1)} ${t('ft')}` : t('depthUnavailable')
}

// Used by asynchronous UI tasks so only the most recent operation may commit.
export function latestTask() {
  let version = 0
  return { next: () => ++version, current: (token) => token === version, invalidate: () => { version++ } }
}
