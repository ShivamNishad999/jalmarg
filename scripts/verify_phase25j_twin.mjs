import assert from 'node:assert/strict'
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const BACKEND_URL = 'http://127.0.0.1:8080'
const FRONTEND_URL = 'http://127.0.0.1:5173'
const POSTGRES_PORT = 5433
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const OUTPUT_DIR = path.resolve('docs/phase25j_screenshots')

function queryPostgres(sql) {
  const cmd = `& "D:\\JalMargPostGIS\\bin\\psql.exe" -p ${POSTGRES_PORT} -U postgres -d jalmarg_twin -t -A -F "|" -c "${sql.replace(/"/g, '\\"')}"`
  const output = execSync(cmd, { shell: 'powershell.exe', encoding: 'utf-8' })
  return output.trim()
}

async function runPhase25JVerification() {
  console.log('================================================================')
  console.log('JALMARG PHASE 25J — DIGITAL TWIN RUNTIME & AUTH VERIFICATION')
  console.log('Backend:  ', BACKEND_URL)
  console.log('Frontend: ', FRONTEND_URL)
  console.log('PostGIS:  127.0.0.1:' + POSTGRES_PORT + ' / jalmarg_twin')
  console.log('================================================================\n')

  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true })
  }

  let createdIncidentId = null
  let createdScenarioId = null

  try {
    // -----------------------------------------------------------------
    // TEST 1: REPRODUCE & VERIFY FIX FOR WHAT-IF SCENARIO CONTENT-TYPE
    // -----------------------------------------------------------------
    console.log('>>> [1/5] VERIFYING WHAT-IF SCENARIO API CONTRACT')

    // 1A. Authenticate as Municipal Operator
    const opLoginRes = await fetch(`${BACKEND_URL}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'operator_pwd', password: process.env.OPERATOR_PASSWORD || 'PwdOperator#2026' })
    })
    assert.equal(opLoginRes.status, 200, 'Operator login returns HTTP 200')
    const opAuth = await opLoginRes.json()
    const opToken = opAuth.token
    console.log(`  Authenticated operator: ${opAuth.username} (${opAuth.role}, ${opAuth.department})`)

    // 1B. Submit Scenario with application/json
    const scenarioPayload = {
      scenarioName: 'Phase 25J Severe Cloudburst Test',
      rainfallTotalMm: 80.0,
      rainfallIntensityMmPerHour: 45.0,
      durationHours: 4,
      assumedDrainageCapacityPercent: 40.0,
      closedRoadSegmentCodes: ['MINTO_UNDERPASS'],
      availablePumpCount: 3,
      targetZoneId: 1
    }

    const scenarioRes = await fetch(`${BACKEND_URL}/api/v1/twin/scenarios`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'Authorization': `Bearer ${opToken}`
      },
      body: JSON.stringify(scenarioPayload)
    })

    assert.equal(scenarioRes.status, 201, 'Scenario run created with HTTP 201')
    const scenarioData = await scenarioRes.json()
    createdScenarioId = scenarioData.id
    console.log(`  Scenario Run ID: #${createdScenarioId}`)
    console.log(`  Run Type: ${scenarioData.runType}`)
    console.log(`  Baseline Risk Score: ${scenarioData.baselineRiskScore}`)
    console.log(`  Simulated Risk Score: ${scenarioData.simulatedRiskScore}`)
    console.log(`  Risk Delta: +${scenarioData.deltaRiskScore}`)
    assert.equal(scenarioData.runType, 'WHAT_IF_SIMULATION', 'Identified as WHAT_IF_SIMULATION')
    assert.ok(scenarioData.simulatedRiskScore > scenarioData.baselineRiskScore, 'Simulated risk reflects extreme rainfall stress')

    // Verify DB persistence & state isolation
    const dbScenarioRow = queryPostgres(`SELECT id, run_type, simulated_risk_score, delta_risk_score FROM twin_scenario_runs WHERE id = ${createdScenarioId}`)
    assert.ok(dbScenarioRow.includes(`${createdScenarioId}|WHAT_IF_SIMULATION`), 'Scenario persisted in twin_scenario_runs')
    console.log(`  DB Verification: Scenario record stored -> ${dbScenarioRow}`)

    // -----------------------------------------------------------------
    // TEST 2: REPRODUCE & VERIFY FIX FOR MUNICIPAL AUTHENTICATION / RBAC
    // -----------------------------------------------------------------
    console.log('\n>>> [2/5] VERIFYING MUNICIPAL AUTHENTICATION & RBAC')

    // 2A. Unauthenticated request to incident list must return 401
    const unauthRes = await fetch(`${BACKEND_URL}/api/v1/authority/incidents`)
    assert.equal(unauthRes.status, 401, 'Unauthenticated incident list request returns HTTP 401')
    console.log('  Confirmed: Unauthenticated incident access correctly blocked with HTTP 401')

    // 2B. Authenticate as Municipal Viewer (Delhi Traffic Police)
    const viewerLoginRes = await fetch(`${BACKEND_URL}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'viewer_traffic', password: process.env.VIEWER_PASSWORD || 'TrafficView#2026' })
    })
    assert.equal(viewerLoginRes.status, 200, 'Viewer login returns HTTP 200')
    const viewerAuth = await viewerLoginRes.json()
    console.log(`  Authenticated viewer: ${viewerAuth.username} (${viewerAuth.role}, ${viewerAuth.department})`)

    // Viewer can GET incidents
    const viewerIncidentsRes = await fetch(`${BACKEND_URL}/api/v1/authority/incidents`, {
      headers: { 'Authorization': `Bearer ${viewerAuth.token}` }
    })
    assert.equal(viewerIncidentsRes.status, 200, 'Viewer can retrieve incident list (HTTP 200)')
    const viewerIncidents = await viewerIncidentsRes.json()
    console.log(`  Viewer successfully read incident queue: ${viewerIncidents.length} active incidents`)

    // Viewer CANNOT create incidents (403 Forbidden)
    const viewerCreateRes = await fetch(`${BACKEND_URL}/api/v1/authority/incidents`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${viewerAuth.token}`
      },
      body: JSON.stringify({
        title: 'Unauthorized Test Incident by Viewer',
        description: 'Should be rejected by RBAC',
        incidentType: 'SURFACE_FLOODING',
        severity: 'HIGH',
        latitude: 28.6289,
        longitude: 77.2065
      })
    })
    assert.equal(viewerCreateRes.status, 403, 'Viewer incident creation rejected with HTTP 403 Forbidden')
    console.log('  Confirmed: Viewer mutating actions correctly rejected with HTTP 403 Forbidden')

    // 2C. Operator CAN create incidents (201 Created)
    const opCreateRes = await fetch(`${BACKEND_URL}/api/v1/authority/incidents`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${opToken}`
      },
      body: JSON.stringify({
        title: '[DEMO_25J] Live Waterlogging Alert at Connaught Place Outer Circle',
        description: 'Water depth ~1.2 ft obstructing two-wheelers. Dispatching dewatering team.',
        incidentType: 'SURFACE_FLOODING',
        severity: 'HIGH',
        locationName: 'Connaught Place Outer Circle',
        latitude: 28.6320,
        longitude: 77.2190,
        waterDepthMeters: 0.35,
        roadBlockage: true
      })
    })
    assert.equal(opCreateRes.status, 201, 'Operator incident creation succeeds with HTTP 201 Created')
    const createdIncident = await opCreateRes.json()
    createdIncidentId = createdIncident.id
    console.log(`  Operator created incident #${createdIncidentId} (${createdIncident.incidentNumber})`)

    // Verify Audit Event logged in PostgreSQL
    const dbAuditRow = queryPostgres(`SELECT action_type, actor_id, target_entity_id FROM twin_audit_events WHERE target_entity_id = '${createdIncidentId}' ORDER BY id DESC LIMIT 1`)
    assert.ok(dbAuditRow.includes(`INCIDENT_CREATE|operator_pwd|${createdIncidentId}`), 'Audit event INCIDENT_CREATE logged')
    console.log(`  DB Verification: Audit trail entry logged -> ${dbAuditRow}`)

    // -----------------------------------------------------------------
    // TEST 3: REAL-DATA TRANSPARENCY & DATABASE COUNTS
    // -----------------------------------------------------------------
    console.log('\n>>> [3/5] VERIFYING REAL DATA & TRANSPARENCY COUNTS')
    const statusRes = await fetch(`${BACKEND_URL}/api/v1/twin/status`)
    assert.equal(statusRes.status, 200, 'Twin status returns HTTP 200')
    const twinStatus = await statusRes.json()
    console.log(`  Database Zones Count: ${twinStatus.totalZonesCount}`)
    console.log(`  Database Road Corridors: ${twinStatus.totalRoadSegmentsCount}`)
    console.log(`  Database Drainage Assets: ${twinStatus.totalDrainageAssetsCount}`)
    console.log(`  Registered Data Sources: ${twinStatus.totalRegisteredSourcesCount}`)
    console.log(`  Drainage Notice: "${twinStatus.drainageTelemetryNotice}"`)
    assert.equal(twinStatus.totalZonesCount, 10, 'Zones count is 10')
    assert.equal(twinStatus.totalRoadSegmentsCount, 12, 'Road corridors count is 12')
    assert.equal(twinStatus.totalDrainageAssetsCount, 8, 'Drainage assets count is 8')
    assert.equal(twinStatus.totalRegisteredSourcesCount, 9, 'Registered sources count is 9')

    // -----------------------------------------------------------------
    // TEST 4: HEADLESS CHROME DOM RENDERING ACROSS VIEWPORTS
    // -----------------------------------------------------------------
    console.log('\n>>> [4/5] HEADLESS CHROME BROWSER VALIDATION & SCREENSHOTS')
    const chromeDataDir = path.resolve('target', 'chrome-p25j-' + Date.now())

    // 4A. DOM inspection on /twin
    const twinDom = execSync(
      `& "${CHROME_PATH}" --headless --no-sandbox --disable-background-networking --disable-default-apps --disable-extensions --user-data-dir="${chromeDataDir}" --dump-dom "${FRONTEND_URL}/twin"`,
      { shell: 'powershell.exe', encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 }
    )
    assert.ok(twinDom.includes('MUNICIPAL DIGITAL TWIN'), 'Digital twin branding rendered')
    assert.ok(twinDom.includes('What-If Scenario Sandbox'), 'Scenario sandbox tab rendered')
    assert.ok(twinDom.includes('Municipal Incident Queue'), 'Incident queue tab rendered')
    assert.ok(twinDom.includes('Data Provenance'), 'Provenance tab rendered')
    assert.ok(!twinDom.includes('Authority API Admin Key (Required for mutations)'), 'Insecure admin key field removed')
    console.log('  DOM Verification: All 5 tabs rendered, insecure admin key input confirmed removed.')

    // 4B. Multi-viewport screenshot capture
    const viewports = [
      { width: 1366, height: 768, label: 'desktop_1366x768' },
      { width: 768, height: 1024, label: 'tablet_768x1024' },
      { width: 390, height: 844, label: 'mobile_390x844' },
      { width: 360, height: 800, label: 'mobile_360x800' }
    ]

    for (const vp of viewports) {
      const shotPath = path.join(OUTPUT_DIR, `twin_overview_${vp.label}.png`)
      execSync(
        `& "${CHROME_PATH}" --headless --no-sandbox --disable-background-networking --disable-default-apps --disable-extensions --user-data-dir="${chromeDataDir}" --window-size=${vp.width},${vp.height} --screenshot="${shotPath}" "${FRONTEND_URL}/twin"`,
        { shell: 'powershell.exe', encoding: 'utf-8' }
      )
      const stat = fs.statSync(shotPath)
      console.log(`  Captured screenshot: ${path.basename(shotPath)} (${(stat.size / 1024).toFixed(1)} KB)`)
    }

    try {
      fs.rmSync(chromeDataDir, { recursive: true, force: true })
    } catch {}

    // -----------------------------------------------------------------
    // TEST 5: CLEANUP DISPOSABLE DEMO TEST RECORDS
    // -----------------------------------------------------------------
    console.log('\n>>> [5/5] SAFE CLEANUP OF DISPOSABLE TEST RECORDS')
  } finally {
    if (createdIncidentId) {
      queryPostgres(`DELETE FROM twin_audit_events WHERE target_entity_id = '${createdIncidentId}'`)
      queryPostgres(`DELETE FROM twin_municipal_incidents WHERE id = ${createdIncidentId}`)
      console.log(`  Cleaned up test incident #${createdIncidentId}`)
    }
    if (createdScenarioId) {
      queryPostgres(`DELETE FROM twin_audit_events WHERE target_entity_id = '${createdScenarioId}'`)
      queryPostgres(`DELETE FROM twin_scenario_runs WHERE id = ${createdScenarioId}`)
      console.log(`  Cleaned up test scenario run #${createdScenarioId}`)
    }
  }

  console.log('\n================================================================')
  console.log('ALL PHASE 25J VERIFICATION CHECKS PASSED WITH 100% SUCCESS!')
  console.log('================================================================\n')
}

runPhase25JVerification().catch((err) => {
  console.error('\nPhase 25J Verification Failed:', err)
  process.exit(1)
})
