import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { validDepth, depthText, assessmentKey, confirmationCount, lifecycleKey, latestTask } from '../src/utils/evidence.js'
import { mapAssessments, compareRoutes } from '../src/utils/routeAssessment.js'
import { safePdfFilename, validatePdfBlob } from '../src/utils/pdf.js'
import { stage2Strings } from '../src/stage2Strings.js'

const t = (key) => ({ ft: 'ft', depthUnavailable: 'Depth unavailable' }[key] || key)
// Contract fixtures only; never imported by application code.
const candidates = [
  { coords: [[28.6, 77.2], [28.61, 77.21]], durationMin: 12, distanceKm: 4 },
  { coords: [[28.6, 77.2], [28.62, 77.23]], durationMin: 14, distanceKm: 5 }
]
const row = (index, overrides = {}) => ({ index, riskScore: 0, hazardCount: 0, hazards: [], safeForBike: true, safeForCar: true, ...overrides })
const hazard = { reportId: 42, latitude: 28.6, longitude: 77.2, depthFt: 0.8, distanceFromRouteM: 20, severity: 'HIGH' }

test('genuine zero depth remains zero in cards and share formatting', () => {
  assert.equal(validDepth(0), true)
  assert.equal(depthText(0, t), '0.0 ft')
})
test('missing or invalid depth never becomes a numeric estimate', () => {
  for (const value of [undefined, null, NaN, Infinity, -1, '0', false]) assert.equal(depthText(value, t), 'Depth unavailable')
})
test('AI unavailable, citizen and combined provenance remain distinct', () => {
  assert.equal(assessmentKey({ depthSource: 'CITIZEN', depthFt: 0.3 }), 'aiStatusUnknown')
  assert.equal(assessmentKey({ depthSource: 'CITIZEN', citizenDepthLevel: 'ANKLE' }), 'citizenProvided')
  assert.equal(assessmentKey({ depthSource: 'AI_AND_CITIZEN' }), 'sourceCombined')
  assert.equal(assessmentKey({ depthSource: 'AI' }), 'sourceAI')
  assert.equal(assessmentKey({ depthSource: 'DEMO' }), 'demoTag')
  assert.equal(assessmentKey({ aiEnabled: true }), 'aiStatusUnknown')
})
test('lifecycle and zero confirmations never imply verified people', () => {
  assert.equal(confirmationCount({ confirmations: 0 }), 0)
  assert.equal(confirmationCount({ confirmations: -1 }), null)
  assert.equal(lifecycleKey('ACTIVE'), 'life_ACTIVE')
  assert.equal(lifecycleKey('VERIFIED'), 'unknownValue')
  assert.equal(lifecycleKey('EXPIRED'), 'life_EXPIRED')
})
test('route failure preserves every geometry and unknown risk', () => {
  const mapped = mapAssessments(candidates, null, 'bike')
  assert.equal(mapped.length, candidates.length)
  mapped.forEach((r, i) => {
    assert.equal(r.coords, candidates[i].coords)
    assert.equal(r.assessed, false)
    assert.equal(r.risk, null)
    assert.equal(r.blocking, null)
    assert.equal(r.safeForBike, null)
  })
})
test('out-of-order backend rows map by explicit index', () => {
  const mapped = mapAssessments(candidates, { routes: [row(1), row(0, { riskScore: 8, hazardCount: 1, hazards: [hazard], safeForBike: false, safeForCar: false })] }, 'car')
  assert.equal(mapped[0].risk, 8)
  assert.equal(mapped[0].blocking, 1)
  assert.equal(mapped[1].risk, 0)
})
test('partial evaluation keeps missing route unassessed and ranks it last', () => {
  const mapped = mapAssessments(candidates, { routes: [row(1, { riskScore: 8, hazardCount: 1, hazards: [hazard], safeForBike: false })] }, 'bike')
  assert.equal(mapped[0].assessed, false)
  assert.equal(mapped[1].assessed, true)
  assert.equal([...mapped].sort(compareRoutes)[0].coords, candidates[1].coords)
})
test('duplicate, negative and out-of-range indices fail closed', () => {
  for (const rows of [[row(0), row(0)], [row(-1)], [row(2)], [row('0')]]) {
    assert.ok(mapAssessments(candidates, { routes: rows }, 'car').every((r) => !r.assessed))
  }
})
test('malformed risk, counts, flags and hazards are never zero-risk assessments', () => {
  for (const bad of [{ riskScore: null }, { riskScore: '0' }, { riskScore: NaN }, { hazards: null }, { hazardCount: 2 }, { safeForBike: null }, { hazardCount: 1, hazards: [{ ...hazard, depthFt: null }] }, { hazardCount: 1, hazards: [{ ...hazard, latitude: 200 }] }]) {
    const [r] = mapAssessments(candidates, { routes: [row(0, bad)] }, 'bike')
    assert.equal(r.assessed, false)
    assert.equal(r.risk, null)
  }
})
test('mode thresholds differ and assessed ranking retains blocking/risk/time order', () => {
  const response = { routes: [row(0, { riskScore: 8, hazardCount: 1, hazards: [hazard] })] }
  assert.equal(mapAssessments(candidates, response, 'walk')[0].blocking, 0)
  assert.equal(mapAssessments(candidates, response, 'car')[0].blocking, 1)
  assert.ok(compareRoutes({ assessed: true, blocking: 0, risk: 3, durationMin: 20 }, { assessed: true, blocking: 1, risk: 8, durationMin: 10 }) < 0)
})
test('PDF validation rejects empty, HTML, wrong MIME and fake PDF content', async () => {
  for (const blob of [null, new Blob([], { type: 'application/pdf' }), new Blob(['<html>'], { type: 'application/pdf' }), new Blob(['%PDF-1.4'], { type: 'text/html' })]) await assert.rejects(validatePdfBlob(blob))
  const pdf = new Blob(['%PDF-1.4\n'], { type: 'application/pdf' })
  assert.equal(await validatePdfBlob(pdf), pdf)
})
test('download filenames cannot contain paths or control characters', () => {
  assert.match(safePdfFilename('../../evil\r\n.pdf'), /^[a-zA-Z0-9_-]+\.pdf$/)
  assert.equal(safePdfFilename('jalmarg-report-42'), 'jalmarg-report-42.pdf')
})
test('late async completion cannot overwrite a newer photo or search', async () => {
  const task = latestTask()
  let resolveOld
  let state
  const old = task.next()
  const delayed = new Promise((resolve) => { resolveOld = resolve }).then((value) => { if (task.current(old)) state = value })
  const newer = task.next()
  if (task.current(newer)) state = 'new selection'
  resolveOld('old selection')
  await delayed
  assert.equal(state, 'new selection')
  task.invalidate()
  assert.equal(task.current(newer), false)
})
test('all Stage 2 strings have English/Hindi parity', () => {
  assert.deepEqual(Object.keys(stage2Strings.en).sort(), Object.keys(stage2Strings.hi).sort())
  for (const text of Object.values(stage2Strings.hi)) assert.ok(text.trim())
})

// Vite env syntax is replaced only in this test process, without a bundler or dependencies.
// The api.js transport tests from the web build were removed: the app's api.js (accounts, native
// camera, server address) replaced that module. Its behaviour is covered by the Playwright flow tests.
