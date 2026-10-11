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

test('Journey 1 — RBAC & Authentication Security Hardening', async () => {
  // 1. Unauthenticated request to /api/v1/authority/reports must be rejected with 401
  const unauthReports = await fetch(`${BACKEND_URL}/api/v1/authority/reports`)
  assert.equal(unauthReports.status, 401, 'Unauthenticated report list returns HTTP 401')

  // 2. Unauthenticated request to /api/v1/authority/incidents must be rejected with 401
  const unauthIncidents = await fetch(`${BACKEND_URL}/api/v1/authority/incidents`)
  assert.equal(unauthIncidents.status, 401, 'Unauthenticated incident list returns HTTP 401')

  // 3. Unauthenticated request to /api/v1/twin/scenarios (POST) must be rejected with 401
  const unauthScenario = await fetch(`${BACKEND_URL}/api/v1/twin/scenarios`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      scenarioName: 'Unauthenticated Test Simulation',
      rainfallTotalMm: 50.0,
      rainfallIntensityMmPerHour: 25.0,
      durationHours: 2
    })
  })
  assert.equal(unauthScenario.status, 401, 'Unauthenticated scenario execution returns HTTP 401')

  // 4. Unauthenticated review decision must be rejected with 401
  const unauthReview = await fetch(`${BACKEND_URL}/api/v1/authority/reports/1/review`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ decision: 'VERIFIED' })
  })
  assert.equal(unauthReview.status, 401, 'Unauthenticated review submission returns HTTP 401')

  // 5. Invalid credentials must be rejected with 401
  const invalidLogin = await fetch(`${BACKEND_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'operator_pwd', password: 'WrongPassword#2026' })
  })
  assert.equal(invalidLogin.status, 401, 'Invalid password returns HTTP 401')

  // 6. Valid municipal officer login must succeed with session token and role
  const validLogin = await fetch(`${BACKEND_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'operator_pwd', password: 'PwdOperator#2026' })
  })
  assert.equal(validLogin.status, 200, 'Valid officer login returns HTTP 200')
  const authData = await validLogin.json()
  assert.ok(authData.token, 'Issued bearer session token')
  assert.equal(authData.username, 'operator_pwd')
  assert.equal(authData.role, 'MUNICIPAL_OPERATOR')

  // 7. Verify token via /api/v1/auth/me
  const meRes = await fetch(`${BACKEND_URL}/api/v1/auth/me`, {
    headers: { 'Authorization': `Bearer ${authData.token}` }
  })
  assert.equal(meRes.status, 200, '/auth/me returns HTTP 200')
  const meData = await meRes.json()
  assert.equal(meData.username, 'operator_pwd')
  assert.equal(meData.role, 'MUNICIPAL_OPERATOR')
})

test('Journey 2 — Authority Dashboard Operations & Computed Statistics', async () => {
  // Login as operator
  const loginRes = await fetch(`${BACKEND_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'operator_pwd', password: 'PwdOperator#2026' })
  })
  const { token } = await loginRes.json()
  const authHeader = { 'Authorization': `Bearer ${token}` }

  // 1. Fetch municipal summary statistics (calculated from live PostGIS database)
  const summaryRes = await fetch(`${BACKEND_URL}/api/authority/summary?days=30`)
  assert.equal(summaryRes.status, 200, 'Authority summary endpoint returns HTTP 200')
  const summary = await summaryRes.json()
  assert.equal(typeof summary.totalReports, 'number', 'totalReports is numeric')
  assert.equal(typeof summary.activeNow, 'number', 'activeNow is numeric')
  assert.equal(typeof summary.hotspots, 'number', 'hotspots count is numeric')
  assert.ok(Array.isArray(summary.perDay), 'perDay trend is an array')
  assert.ok(summary.perDay.length > 0, 'Trend data populated')
  assert.ok(typeof summary.bySeverity === 'object', 'bySeverity breakdown present')

  // 2. Fetch authenticated reports queue
  const reportsRes = await fetch(`${BACKEND_URL}/api/v1/authority/reports`, { headers: authHeader })
  assert.equal(reportsRes.status, 200, 'Authority reports returns HTTP 200')
  const reports = await reportsRes.json()
  assert.ok(Array.isArray(reports), 'Reports is an array')

  // 3. Fetch authenticated incidents list
  const incidentsRes = await fetch(`${BACKEND_URL}/api/v1/authority/incidents`, { headers: authHeader })
  assert.equal(incidentsRes.status, 200, 'Authority incidents returns HTTP 200')
  const incidents = await incidentsRes.json()
  assert.ok(Array.isArray(incidents), 'Incidents is an array')

  // 4. Fetch authenticated audit logs
  const auditRes = await fetch(`${BACKEND_URL}/api/v1/authority/audit-logs`, { headers: authHeader })
  assert.equal(auditRes.status, 200, 'Authority audit-logs returns HTTP 200')
  const logs = await auditRes.json()
  assert.ok(Array.isArray(logs), 'Audit logs is an array')
  if (logs.length > 0) {
    assert.ok(logs[0].actorRole, 'Audit log contains actorRole')
    assert.ok(logs[0].actionType, 'Audit log contains actionType')
    assert.ok(logs[0].eventTimestamp, 'Audit log contains eventTimestamp')
  }
})

test('Journey 3 — Report Review Decision, Deduplication Observation Ledger & Incident Lifecycle', async () => {
  // Login as operator
  const loginRes = await fetch(`${BACKEND_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'operator_pwd', password: 'PwdOperator#2026' })
  })
  const { token } = await loginRes.json()
  const authHeaders = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }

  let createdReportId = null
  let promotedIncidentId = null

  try {
    // Step 1: Submit a new citizen report
    const form = new FormData()
    form.append('latitude', '28.6145')
    form.append('longitude', '77.2085')
    form.append('locationName', 'Phase 24 Authority Verification Corridor')
    form.append('depthLevel', 'WAIST')
    form.append('note', 'Critical waterlogging at India Gate circle')

    const citizenRes = await fetch(`${BACKEND_URL}/api/reports`, {
      method: 'POST',
      body: form
    })
    assert.equal(citizenRes.status, 201, 'Citizen report created with HTTP 201')
    const createdReport = await citizenRes.json()
    createdReportId = createdReport.id
    assert.ok(createdReportId > 0, 'Valid report ID generated')
    assert.equal(createdReport.verificationStatus, 'UNVERIFIED', 'Starts as UNVERIFIED')

    // Step 2: Query report detail via Authority endpoint
    const detailRes = await fetch(`${BACKEND_URL}/api/v1/authority/reports/${createdReportId}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    assert.equal(detailRes.status, 200, 'Report detail returned')
    const detail = await detailRes.json()
    assert.equal(detail.report.id, createdReportId, 'Detail report ID matches')
    assert.equal(detail.report.locationName, 'Phase 24 Authority Verification Corridor')
    assert.ok(Array.isArray(detail.observations), 'Observations ledger array present')
    assert.ok(detail.observations.length >= 1, 'Raw citizen observation preserved')

    // Step 3: Municipal Officer verifies report and promotes to incident
    const reviewRes = await fetch(`${BACKEND_URL}/api/v1/authority/reports/${createdReportId}/review`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({
        decision: 'VERIFIED',
        operatorNotes: 'Verified via CCTV feed: water level waist high',
        promoteToIncident: true,
        incidentType: 'WATERLOGGING_CHOKE',
        zoneId: 1
      })
    })
    assert.equal(reviewRes.status, 200, 'Report review decision returned HTTP 200')
    const reviewResult = await reviewRes.json()
    assert.equal(reviewResult.report.verificationStatus, 'VERIFIED', 'Report transitioned to VERIFIED')
    assert.ok(reviewResult.linkedIncidentId, 'Incident was created and linked')
    promotedIncidentId = reviewResult.linkedIncidentId
    assert.ok(promotedIncidentId > 0, 'Incident has valid ID')

    // Step 4: Verify PostGIS database records in jalmarg_twin
    const dbReport = queryPostgres(`SELECT id, status, verification_status FROM reports WHERE id = ${createdReportId}`)
    const [, , verifStatus] = dbReport.split('|')
    assert.equal(verifStatus, 'VERIFIED', 'Database report record marked VERIFIED')

    const dbIncident = queryPostgres(
      `SELECT id, incident_number, related_report_id, status, severity FROM twin_municipal_incidents WHERE id = ${promotedIncidentId}`
    )
    const [incId, , relRepId, incStatus] = dbIncident.split('|')
    assert.equal(Number(incId), promotedIncidentId)
    assert.equal(Number(relRepId), createdReportId, 'Incident foreign key links to source report')
    assert.equal(incStatus, 'NEW')

    // Step 5: Update Incident status and team assignment
    const assignRes = await fetch(`${BACKEND_URL}/api/v1/authority/incidents/${promotedIncidentId}/assign`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({ team: 'NDMC Quick Response Unit 2' })
    })
    assert.equal(assignRes.status, 200, 'Incident team reassigned')

    const statusRes = await fetch(`${BACKEND_URL}/api/v1/authority/incidents/${promotedIncidentId}/status`, {
      method: 'PATCH',
      headers: authHeaders,
      body: JSON.stringify({
        status: 'IN_PROGRESS',
        notes: 'Dewatering pumps deployed at location'
      })
    })
    assert.equal(statusRes.status, 200, 'Incident status updated to IN_PROGRESS')
    const updatedInc = await statusRes.json()
    assert.equal(updatedInc.status, 'IN_PROGRESS')

    // Step 6: Verify audit events recorded in twin_audit_events
    const dbAudit = queryPostgres(
      `SELECT action_type, actor_id, actor_role FROM twin_audit_events WHERE target_entity_id = '${createdReportId}' AND action_type = 'REPORT_VERIFY'`
    )
    assert.ok(dbAudit.includes('REPORT_VERIFY'), 'REPORT_VERIFY logged in twin_audit_events')
    assert.ok(dbAudit.includes('operator_pwd'), 'Operator identity preserved in audit log')

  } finally {
    // Clean up created test records in jalmarg_twin
    if (promotedIncidentId) {
      queryPostgres(`DELETE FROM twin_municipal_incidents WHERE id = ${promotedIncidentId}`)
    }
    if (createdReportId) {
      queryPostgres(`DELETE FROM twin_report_observations WHERE report_id = ${createdReportId}`)
      queryPostgres(`DELETE FROM reports WHERE id = ${createdReportId}`)
      queryPostgres(`DELETE FROM twin_audit_events WHERE target_entity_id IN ('${createdReportId}', '${promotedIncidentId || 0}')`)
    }
  }
})

test('Journey 4 — Authorized Digital Twin What-If Scenario Execution & State Isolation', async () => {
  // Login as operator
  const loginRes = await fetch(`${BACKEND_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'operator_pwd', password: 'PwdOperator#2026' })
  })
  const { token } = await loginRes.json()

  let scenarioRunId = null

  try {
    // Step 1: Run What-If simulation with valid operator credentials
    const runRes = await fetch(`${BACKEND_URL}/api/v1/twin/scenarios`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        scenarioName: 'Phase 24 Authorized E2E Simulation',
        rainfallTotalMm: 45.0,
        rainfallIntensityMmPerHour: 22.5,
        durationHours: 2,
        targetZoneId: 1,
        assumedDrainageCapacityPercent: 70.0
      })
    })
    assert.equal(runRes.status, 201, 'Scenario execution returns HTTP 201')
    const scenario = await runRes.json()
    scenarioRunId = scenario.id
    assert.ok(scenarioRunId > 0, 'Scenario run generated valid ID')
    assert.equal(scenario.scenarioName, 'Phase 24 Authorized E2E Simulation')
    assert.equal(scenario.status, 'COMPLETED')
    assert.equal(typeof scenario.affectedRoadsCount, 'number', 'affectedRoadsCount is numeric')
    assert.equal(typeof scenario.deltaRiskScore, 'number', 'deltaRiskScore is numeric')

    // Step 2: Verify audit log recorded for scenario run
    const dbAudit = queryPostgres(
      `SELECT action_type, actor_id, actor_role FROM twin_audit_events WHERE target_entity_id = '${scenarioRunId}' AND action_type = 'SCENARIO_RUN'`
    )
    assert.ok(dbAudit.includes('SCENARIO_RUN'), 'SCENARIO_RUN logged in twin_audit_events')
    assert.ok(dbAudit.includes('operator_pwd'), 'Operator identity captured in audit')

    // Step 3: Verify operational isolation — physical assets have not been modified by simulation
    const dbAsset = queryPostgres(`SELECT status FROM twin_drainage_assets WHERE id = 1`)
    assert.ok(!dbAsset.includes('SIMULATED_FAILURE'), 'Operational drainage asset status isolated from scenario simulation')

  } finally {
    if (scenarioRunId) {
      queryPostgres(`DELETE FROM twin_scenario_runs WHERE id = ${scenarioRunId}`)
      queryPostgres(`DELETE FROM twin_audit_events WHERE target_entity_id = '${scenarioRunId}'`)
    }
  }
})

test('Journey 5 — Headless Chrome Authority Console Visual Inspection', () => {
  // Verify /authority renders Authority Console interface
  const authDom = execSync(
    `& "${CHROME_PATH}" --headless --dump-dom "${FRONTEND_URL}/authority"`,
    { shell: 'powershell.exe', encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 }
  )

  // Verify key components and branding rendered in DOM
  assert.ok(authDom.includes('Municipal Authority Waterlogging Dashboard'), 'Dashboard title rendered')
  assert.ok(authDom.includes('Municipal Officer Authentication'), 'Authentication card rendered')
  assert.ok(authDom.includes('Overview &amp; Analytics') || authDom.includes('Overview & Analytics'), 'Overview tab rendered')
  assert.ok(authDom.includes('Citizen Report Review Queue'), 'Review queue tab rendered')
  assert.ok(authDom.includes('Operational Incidents'), 'Incidents tab rendered')
  assert.ok(authDom.includes('Audit Trail &amp; Compliance') || authDom.includes('Audit Trail & Compliance'), 'Audit trail tab rendered')
  assert.ok(authDom.includes('Download Area Complaint PDF'), 'PDF download action rendered')
})
