import assert from 'node:assert/strict'
import { execSync } from 'node:child_process'

const BACKEND_URL = 'http://127.0.0.1:8080'
const POSTGRES_PORT = 5433

function queryPostgres(sql) {
  const cmd = `& "D:\\JalMargPostGIS\\bin\\psql.exe" -p ${POSTGRES_PORT} -U postgres -d jalmarg_twin -t -A -F "|" -c "${sql.replace(/"/g, '\\"')}"`
  const output = execSync(cmd, { shell: 'powershell.exe', encoding: 'utf-8' })
  return output.trim()
}

async function runDemoWorkflow() {
  console.log('================================================================')
  console.log('JALMARG LIVE CITIZEN-TO-MUNICIPAL DEMO WORKFLOW EXECUTION')
  console.log('Backend Target: ', BACKEND_URL)
  console.log('PostgreSQL Target: 127.0.0.1:' + POSTGRES_PORT + ' / jalmarg_twin')
  console.log('================================================================\n')

  let createdReportId = null

  try {
    // -------------------------------------------------------------
    // STEP 1: CITIZEN WATERLOGGING REPORT SUBMISSION
    // -------------------------------------------------------------
    console.log('>>> [1/4] CITIZEN REPORT SUBMISSION')
    const reportForm = new FormData()
    reportForm.append('latitude', '28.6347')
    reportForm.append('longitude', '77.2263')
    reportForm.append('locationName', 'Minto Bridge Underpass')
    reportForm.append('area', 'Central Delhi')
    reportForm.append('depthLevel', 'KNEE')
    reportForm.append('note', '[DEMO_PILOT_25H] Live hackathon validation observation at Minto Bridge Underpass')

    const submitRes = await fetch(`${BACKEND_URL}/api/reports`, {
      method: 'POST',
      body: reportForm
    })

    assert.equal(submitRes.status, 201, 'Citizen report submission returns HTTP 201')
    const reportData = await submitRes.json()
    createdReportId = reportData.id
    console.log(`  Report successfully created with ID: #${createdReportId}`)
    console.log(`  Status: ${reportData.status}, Initial Verification: ${reportData.verificationStatus}`)

    // Verify DB persistence
    const dbReportRow = queryPostgres(`SELECT id, status, verification_status, depth_ft FROM reports WHERE id = ${createdReportId}`)
    assert.ok(dbReportRow.includes(`${createdReportId}|ACTIVE|UNVERIFIED`), 'Report persisted in PostgreSQL reports table')
    console.log(`  PostgreSQL Verification: Row persisted -> ${dbReportRow}`)

    // -------------------------------------------------------------
    // STEP 2: MUNICIPAL AUTHENTICATION & REPORT REVIEW
    // -------------------------------------------------------------
    console.log('\n>>> [2/4] MUNICIPAL AUTHENTICATION & REPORT REVIEW')
    const operatorPassword = process.env.OPERATOR_PASSWORD || 'PwdOperator#2026'
    const loginRes = await fetch(`${BACKEND_URL}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'operator_pwd', password: operatorPassword })
    })

    assert.equal(loginRes.status, 200, 'Municipal login returns HTTP 200')
    const loginData = await loginRes.json()
    const sessionToken = loginData.token
    console.log(`  Authenticated as: ${loginData.username} (${loginData.role}, ${loginData.department})`)

    // Fetch review queue
    const queueRes = await fetch(`${BACKEND_URL}/api/v1/authority/reports?status=ACTIVE`, {
      headers: { Authorization: `Bearer ${sessionToken}` }
    })
    assert.equal(queueRes.status, 200, 'Review queue endpoint returns HTTP 200')
    const queue = await queueRes.json()
    const foundInQueue = queue.some(r => r.id === createdReportId)
    assert.ok(foundInQueue, 'New citizen report is visible in municipal review queue')
    console.log(`  Review Queue Verified: Report #${createdReportId} visible among ${queue.length} active items`)

    // Submit operator review decision: VERIFIED
    const reviewRes = await fetch(`${BACKEND_URL}/api/v1/authority/reports/${createdReportId}/review`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${sessionToken}`
      },
      body: JSON.stringify({
        decision: 'VERIFIED',
        notes: '[DEMO_PILOT_25H] Verified waterlogging depth of ~2.0 ft confirmed by PWD drainage operator.'
      })
    })

    assert.equal(reviewRes.status, 200, 'Report review decision returns HTTP 200')
    const reviewData = await reviewRes.json()
    assert.equal(reviewData.verificationStatus, 'VERIFIED', 'Verification status updated to VERIFIED')
    assert.equal(reviewData.verifiedBy, 'operator_pwd', 'VerifiedBy attributed to operator_pwd')
    console.log(`  Review Decision Applied: verificationStatus = ${reviewData.verificationStatus}, verifiedBy = ${reviewData.verifiedBy}`)

    // Verify audit event in PostgreSQL
    const dbAuditRow = queryPostgres(`SELECT action_type, actor_id, target_entity_id FROM twin_audit_events WHERE target_entity_id = '${createdReportId}' ORDER BY id DESC LIMIT 1`)
    assert.ok(dbAuditRow.includes(`REPORT_VERIFY|operator_pwd|${createdReportId}`), 'Audit event REPORT_VERIFY logged')
    console.log(`  Audit Event Verified: ${dbAuditRow}`)

    // -------------------------------------------------------------
    // STEP 3: HEURISTIC RISK ENGINE ASSESSMENT
    // -------------------------------------------------------------
    console.log('\n>>> [3/4] HEURISTIC RISK ASSESSMENT')
    const riskRes = await fetch(`${BACKEND_URL}/api/flood-risk?lat=28.6347&lng=77.2263&area=Minto%20Bridge`)
    assert.equal(riskRes.status, 200, 'Risk API returns HTTP 200')
    const risk = await riskRes.json()
    assert.ok(risk.riskScore >= 0 && risk.riskScore <= 100, 'Risk score between 0 and 100')
    console.log(`  Location: Minto Bridge Underpass (28.6347, 77.2263)`)
    console.log(`  Heuristic Risk Score: ${risk.riskScore} / 100 (${risk.riskLevel})`)
    console.log(`  Rainfall Forecast (Next 6h): ${risk.rainfallNext6hMm} mm (Max hourly: ${risk.maxHourlyMm} mm)`)
    console.log(`  Active Corroborating Reports: ${risk.activeReportCount} (Max Depth: ${risk.maxObservedDepthFt} ft)`)
    console.log(`  Historical Proneness: ${risk.historicalProneness} / 100`)
    console.log(`  Summary: "${risk.summaryEn}"`)

    // -------------------------------------------------------------
    // STEP 4: HAZARD-AWARE ROUTING EVALUATION
    // -------------------------------------------------------------
    console.log('\n>>> [4/4] HAZARD-AWARE ROUTE EVALUATION')
    // Route 0 passes directly through Minto Bridge underpass hazard
    // Route 1 bypasses Minto Bridge via outer Connaught Circus
    const candidateRoutes = [
      [
        [28.6347, 77.2263],
        [28.6330, 77.2250],
        [28.6310, 77.2240]
      ],
      [
        [28.6420, 77.2180],
        [28.6400, 77.2200],
        [28.6380, 77.2220]
      ]
    ]

    const routeRes = await fetch(`${BACKEND_URL}/api/routes/evaluate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        routes: candidateRoutes,
        bufferMeters: 60.0
      })
    })

    assert.equal(routeRes.status, 200, 'Route evaluate API returns HTTP 200')
    const routeData = await routeRes.json()
    assert.ok(Array.isArray(routeData.routes), 'Routes array returned')
    assert.equal(routeData.routes.length, 2, 'Evaluated exactly 2 candidate routes')
    console.log(`  Candidate Routes Evaluated: ${routeData.routes.length}`)
    console.log(`  Recommended Route Index: #${routeData.recommendedIndex}`)

    const hazardousRoute = routeData.routes[0]
    const alternativeRoute = routeData.routes[1]
    console.log(`  Route #0 (Direct via Minto Bridge): Risk Score = ${hazardousRoute.riskScore}, Hazards Detected = ${hazardousRoute.hazardCount}, Safe for Bike = ${hazardousRoute.safeForBike}`)
    console.log(`  Route #1 (Bypass via Outer Ring): Risk Score = ${alternativeRoute.riskScore}, Hazards Detected = ${alternativeRoute.hazardCount}, Safe for Bike = ${alternativeRoute.safeForBike}`)
    assert.equal(routeData.recommendedIndex, 1, 'Engine recommends safer Route #1 with lower waterlogging exposure')
    console.log(`  Guidance: "Estimated lower waterlogging exposure. Follow official Delhi Traffic Police advisories."`)

    console.log('\n================================================================')
    console.log('ALL DEMO WORKFLOW PHASES COMPLETED WITH 100% SUCCESS!')
    console.log('================================================================')

  } finally {
    // Safe cleanup of disposable demo test report
    if (createdReportId) {
      queryPostgres(`DELETE FROM twin_report_observations WHERE report_id = ${createdReportId}`)
      queryPostgres(`DELETE FROM twin_audit_events WHERE target_entity_id = '${createdReportId}'`)
      queryPostgres(`DELETE FROM reports WHERE id = ${createdReportId}`)
      console.log(`\nDisposable demo test report #${createdReportId} cleanly removed from database.`)
    }
  }
}

runDemoWorkflow().catch(err => {
  console.error('\nDemo workflow execution failed:', err)
  process.exit(1)
})
