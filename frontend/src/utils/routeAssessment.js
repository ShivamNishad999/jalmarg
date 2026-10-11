import { validDepth, validId } from './evidence.js'

const limits = { bike: 0.5, car: 0.8, walk: 1.5 }
const severity = ['LOW', 'MODERATE', 'HIGH', 'SEVERE']
const validHazard = (h) => h && (h.reportId === null || validId(h.reportId)) && validDepth(h.depthFt) &&
  Number.isFinite(h.latitude) && Math.abs(h.latitude) <= 90 &&
  Number.isFinite(h.longitude) && Math.abs(h.longitude) <= 180 &&
  validDepth(h.distanceFromRouteM) && severity.includes(h.severity)

export function mapAssessments(candidates, response, mode) {
  const rows = Array.isArray(response?.routes) ? response.routes : []
  const indexCounts = new Map()
  rows.forEach((row) => indexCounts.set(row?.index, (indexCounts.get(row?.index) || 0) + 1))
  // Out-of-range indices make the response mapping ambiguous; fail closed.
  const badIndices = rows.some((row) => !Number.isInteger(row?.index) || row.index < 0 || row.index >= candidates.length)
  return candidates.map((candidate, index) => {
    const row = rows.find((entry) => entry?.index === index)
    const assessed = !badIndices && indexCounts.get(index) === 1 && row &&
      validDepth(row.riskScore) && Number.isInteger(row.hazardCount) && row.hazardCount >= 0 &&
      typeof row.safeForBike === 'boolean' && typeof row.safeForCar === 'boolean' &&
      Array.isArray(row.hazards) && row.hazardCount === row.hazards.length && row.hazards.every(validHazard)
    return {
      ...candidate, assessed: !!assessed,
      risk: assessed ? row.riskScore : null,
      hazards: assessed ? row.hazards : [],
      blocking: assessed ? row.hazards.filter((h) => h.depthFt >= limits[mode]).length : null,
      safeForBike: assessed ? row.safeForBike : null,
      safeForCar: assessed ? row.safeForCar : null
    }
  })
}
export function compareRoutes(a, b) {
  if (a.assessed !== b.assessed) return a.assessed ? -1 : 1
  return (a.assessed ? a.blocking - b.blocking || a.risk - b.risk : 0) || a.durationMin - b.durationMin
}
