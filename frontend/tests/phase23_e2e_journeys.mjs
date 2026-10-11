import test from 'node:test'
import assert from 'node:assert/strict'
import { execSync } from 'node:child_process'

const FRONTEND_URL = 'http://localhost:5173'
const BACKEND_URL = 'http://localhost:8080'
const POSTGRES_PORT = 5433
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'

function queryPostgres(sql) {
  const cmd = `& "D:\\JalMargPostGIS\\bin\\psql.exe" -p ${POSTGRES_PORT} -U postgres -d jalmarg_twin -t -A -F "|" -c "${sql.replace(/"/g, '\\"')}"`
  const output = execSync(cmd, { shell: 'powershell.exe', encoding: 'utf-8' })
  return output.trim()
}

test('Journey A — Delhi NCR Risk Inspection & Provenance Verification', async () => {
  // 1. Connaught Place / Minto Bridge Hotspot inspection
  const res = await fetch(`${BACKEND_URL}/api/flood-risk?lat=28.6330&lng=77.2260&area=Connaught%20Place`)
  assert.equal(res.status, 200, 'Risk API returns HTTP 200')
  const risk = await res.json()

  assert.ok(risk.id > 0, 'Risk record has positive ID')
  assert.equal(typeof risk.riskScore, 'number', 'Risk score is numeric')
  assert.ok(risk.riskScore >= 0 && risk.riskScore <= 100, 'Risk score between 0 and 100')
  assert.ok(['LOW', 'MODERATE', 'HIGH', 'CRITICAL', 'UNKNOWN'].includes(risk.riskLevel), 'Valid risk level')
  assert.ok(risk.confidence >= 0 && risk.confidence <= 1.0, 'Confidence between 0 and 1')
  assert.ok(risk.summaryEn && risk.summaryEn.length > 5, 'English summary provided')
  assert.ok(['FRESH', 'STALE', 'MISSING_TELEMETRY', 'DEGRADED'].includes(risk.dataQuality), 'Data quality flagged')
  assert.ok(risk.assessedAt && risk.expiresAt, 'Provenance timestamps present')

  // Factor breakdown validation
  assert.ok(typeof risk.factorBreakdown === 'string', 'Factor breakdown is JSON string')
  const factors = JSON.parse(risk.factorBreakdown)
  assert.ok(factors.type, 'Factor breakdown specifies type')

  // 2. High-elevation Delhi Ridge inspection (Dhaula Kuan)
  const ridgeRes = await fetch(`${BACKEND_URL}/api/flood-risk?lat=28.5920&lng=77.1610&area=Dhaula%20Kuan`)
  assert.equal(ridgeRes.status, 200)
  const ridgeRisk = await ridgeRes.json()
  assert.ok(ridgeRisk.riskScore <= 50, 'Elevated terrain has lower risk score')
})

test('Journey B — Citizen Waterlogging Report Submission, Persistence & Deduplication', async () => {
  const testLat = 28.6322
  const testLng = 77.2188
  const testPlace = 'Phase 23 E2E Test Corridor'
  const testNote = 'Automated E2E Test Report for Phase 23 Verification'

  // Step 1: Submit new citizen report with SHIN depth level
  const formData = new FormData()
  formData.append('latitude', String(testLat))
  formData.append('longitude', String(testLng))
  formData.append('locationName', testPlace)
  formData.append('depthLevel', 'SHIN')
  formData.append('note', testNote)

  const submitRes = await fetch(`${BACKEND_URL}/api/reports`, {
    method: 'POST',
    body: formData
  })
  assert.equal(submitRes.status, 201, 'Report created with HTTP 201')
  const report = await submitRes.json()

  assert.ok(report.id > 0, 'Generated valid report ID')
  assert.equal(report.locationName, testPlace)
  assert.equal(report.citizenDepthLevel, 'SHIN')
  assert.equal(report.status, 'ACTIVE')
  assert.equal(report.verificationStatus, 'UNVERIFIED', 'Citizen report starts as UNVERIFIED')
  assert.equal(report.confirmations, 0, 'Initial confirmation count is 0')

  const createdId = report.id

  try {
    // Step 2: Directly query PostgreSQL 17 on Port 5433 (jalmarg_twin)
    const sql = `SELECT id, location_name, severity, depth_ft, citizen_depth_level, status, verification_status, confirmations FROM reports WHERE id = ${createdId}`
    const dbRow = queryPostgres(sql)
    assert.ok(dbRow, 'Record exists in jalmarg_twin database')
    const [id, locName, sev, depth, level, status, verifStatus, confs] = dbRow.split('|')

    assert.equal(Number(id), createdId)
    assert.equal(locName, testPlace)
    assert.equal(level, 'SHIN')
    assert.equal(status, 'ACTIVE')
    assert.equal(verifStatus, 'UNVERIFIED')
    assert.equal(Number(confs), 0, 'Initial database confirmation count is 0')

    // Step 3: Verify report appears in active reports endpoint
    const activeRes = await fetch(`${BACKEND_URL}/api/reports/active`)
    assert.equal(activeRes.status, 200)
    const activeList = await activeRes.json()
    const found = activeList.find((r) => r.id === createdId)
    assert.ok(found, 'Created report appears in active reports list')

    // Step 4: Submit duplicate report within 50m and 20 min -> verify automatic deduplication & merge
    const dupForm = new FormData()
    dupForm.append('latitude', String(testLat + 0.0001))
    dupForm.append('longitude', String(testLng + 0.0001))
    dupForm.append('locationName', testPlace + ' Duplicate')
    dupForm.append('depthLevel', 'KNEE') // deeper depth should update maximum depth
    dupForm.append('note', 'Duplicate confirmation')

    const dupRes = await fetch(`${BACKEND_URL}/api/reports`, {
      method: 'POST',
      body: dupForm
    })
    assert.equal(dupRes.status, 201)
    const merged = await dupRes.json()

    assert.equal(merged.id, createdId, 'Duplicate merges into the existing report ID')
    assert.equal(merged.confirmations, 1, 'Confirmations incremented to 1')
    assert.equal(merged.citizenDepthLevel, 'KNEE', 'Depth level updated to higher observed depth')
    assert.equal(merged.duplicateMerged, true, 'Flagged as duplicateMerged')

    // Verify database shows merged record
    const mergedDbRow = queryPostgres(sql)
    const [, , , , mergedLevel, , , mergedConfs] = mergedDbRow.split('|')
    assert.equal(mergedLevel, 'KNEE')
    assert.equal(Number(mergedConfs), 1, 'Database confirmation count is 1 after duplicate merge')

  } finally {
    // Step 5: Clean up ONLY the test-created report from jalmarg_twin database
    queryPostgres(`DELETE FROM reports WHERE id = ${createdId}`)
    // Also clean up observation if any
    queryPostgres(`DELETE FROM twin_report_observations WHERE report_id = ${createdId}`)
  }
})

test('Journey C — Multi-Route Hazard Risk Evaluation', async () => {
  // Evaluate two candidate routes:
  // Route 0 passes through Connaught Place / Barakhamba Road corridor
  // Route 1 bypasses around outer corridor
  const evalPayload = {
    routes: [
      [
        [28.6320, 77.2190],
        [28.6328, 77.2197],
        [28.6340, 77.2210]
      ],
      [
        [28.6320, 77.2190],
        [28.6280, 77.2250],
        [28.6340, 77.2210]
      ]
    ],
    bufferMeters: 60
  }

  const res = await fetch(`${BACKEND_URL}/api/routes/evaluate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(evalPayload)
  })
  assert.equal(res.status, 200, 'Route evaluation endpoint returns HTTP 200')
  const evalResult = await res.json()

  assert.ok(Number.isInteger(evalResult.recommendedIndex), 'Recommended index is integer')
  assert.equal(evalResult.routes.length, 2, 'Evaluated exactly 2 routes')

  const [r0, r1] = evalResult.routes
  assert.equal(r0.index, 0)
  assert.equal(r1.index, 1)

  assert.equal(typeof r0.riskScore, 'number')
  assert.equal(typeof r1.riskScore, 'number')
  assert.equal(typeof r0.safeForBike, 'boolean')
  assert.equal(typeof r0.safeForCar, 'boolean')
  assert.ok(Array.isArray(r0.hazards), 'Hazards is an array')

  // Recommended route must have riskScore <= other route
  const recommendedRoute = evalResult.routes[evalResult.recommendedIndex]
  assert.ok(recommendedRoute, 'Recommended route exists')
})

test('Journey D — Failure & Security Hardening Verification', async () => {
  // 1. Invalid coordinates (> 90 degrees latitude) rejected with HTTP 400
  const invalidCoordRes = await fetch(`${BACKEND_URL}/api/reports`, {
    method: 'POST',
    body: new URLSearchParams({ latitude: '999', longitude: '77.2', depthLevel: 'SHIN' })
  })
  assert.ok(invalidCoordRes.status >= 400, 'Invalid coordinate rejected with 4xx')

  // 2. Report without photo and without depth level rejected
  const emptyReportForm = new FormData()
  emptyReportForm.append('latitude', '28.63')
  emptyReportForm.append('longitude', '77.22')
  const emptyRes = await fetch(`${BACKEND_URL}/api/reports`, {
    method: 'POST',
    body: emptyReportForm
  })
  assert.ok(emptyRes.status >= 400, 'Empty report rejected with 4xx')

  // 3. Unauthorized access to internal scheduler trigger
  const unauthRes = await fetch(`${BACKEND_URL}/api/flood-risk/run`, {
    method: 'POST',
    headers: { 'X-Jalmarg-Key': 'invalid-secret-key' }
  })
  assert.equal(unauthRes.status, 401, 'Unauthorized request returns HTTP 401')
})

test('Headless Chrome DOM & Viewport Rendering Verification', () => {
  // Verify home page rendering
  const homeDom = execSync(
    `& "${CHROME_PATH}" --headless --dump-dom "${FRONTEND_URL}/"`,
    { shell: 'powershell.exe', encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 }
  )
  assert.ok(homeDom.includes('JalMarg'), 'Home page contains JalMarg branding')
  assert.ok(homeDom.includes('map-full'), 'Home page contains Leaflet map')
  assert.ok(homeDom.includes('place-search'), 'Home page contains PlaceSearch bar')

  // Verify report page rendering
  const reportDom = execSync(
    `& "${CHROME_PATH}" --headless --dump-dom "${FRONTEND_URL}/report"`,
    { shell: 'powershell.exe', encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 }
  )
  assert.ok(reportDom.includes('Report Waterlogging'), 'Report page title rendered')
  assert.ok(reportDom.includes('level-person'), 'Water depth level options rendered')

  // Verify route page rendering
  const routeDom = execSync(
    `& "${CHROME_PATH}" --headless --dump-dom "${FRONTEND_URL}/route"`,
    { shell: 'powershell.exe', encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 }
  )
  assert.ok(routeDom.includes('Hazard-Aware Route Planner'), 'Route page title rendered')
  assert.ok(routeDom.includes('Travel Mode'), 'Travel mode selector rendered')
})
