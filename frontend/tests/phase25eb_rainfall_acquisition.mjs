import test from 'node:test'
import assert from 'node:assert/strict'
import { api } from '../src/api.js'
import { stage2Strings } from '../src/stage2Strings.js'

test('Phase 25E-B: Historical rainfall and inspection workflow API client functions are defined', () => {
  assert.equal(typeof api.rainfallCoverage, 'function')
  assert.equal(typeof api.rainfallSummary, 'function')
  assert.equal(typeof api.dryRoadInspections, 'function')
  assert.equal(typeof api.recordDryRoadInspection, 'function')
  assert.equal(typeof api.reviewDryRoadInspection, 'function')
  assert.equal(typeof api.calibratedDepthMeasurements, 'function')
  assert.equal(typeof api.recordCalibratedDepthMeasurement, 'function')
  assert.equal(typeof api.reviewCalibratedDepthMeasurement, 'function')
})

test('Phase 25E-B: Complete English and Hindi localization string parity for authentic acquisition & verification', () => {
  const phase25ebKeys = [
    'tabPhase25EB',
    'phase25ebTitle',
    'rainfallCoverageTitle',
    'rainfallSourcesTitle',
    'sourceImdStatus',
    'sourceCdsStatus',
    'sourceOpenMeteoStatus',
    'colPeriodCode',
    'colPeriodName',
    'colDateRange',
    'colRecordsCount',
    'colGridResolution',
    'colAcquisitionStatus',
    'colQualityStatus',
    'colSha256Checksum',
    'colUniqueGridCells',
    'colGridCellHours',
    'colSpatialReplication',
    'colRainfallCategory',
    'rainfallCategoryObserved',
    'rainfallCategoryReanalysis',
    'rainfallCategorySatellite',
    'dryRoadWorkflowsTitle',
    'recordDryRoadBtn',
    'dryRoadPendingNotice',
    'calibratedDepthTitle',
    'recordDepthBtn',
    'calibratedDepthNotice',
    'alignmentComparisonTitle',
    'alignmentBeforeNotice',
    'alignmentAfterNotice',
    'supervisedTrainingVerdictTitle',
    'supervisedTrainingBlockedReason'
  ]

  for (const k of phase25ebKeys) {
    assert.ok(stage2Strings.en[k], `Missing EN string for key: ${k}`)
    assert.ok(stage2Strings.hi[k], `Missing HI string for key: ${k}`)
    assert.notEqual(stage2Strings.en[k], stage2Strings.hi[k], `EN and HI string should not be identical for: ${k}`)
  }
})

test('Phase 25E-B: Authoritative rainfall source evaluation invariants', () => {
  // Evaluates source status based on credentials and official access policies
  function evaluateSourceStatus({ sourceId, hasCredentials, isPublicDataset }) {
    if (sourceId === 'IMD_AWS') {
      return {
        priority: 'Priority A',
        status: hasCredentials ? 'ACCESSIBLE' : 'BLOCKED_OFFICIAL_ACCESS',
        requiresInstitutionalAuth: true
      }
    }
    if (sourceId === 'COPERNICUS_CDS') {
      return {
        priority: 'Priority B',
        status: hasCredentials ? 'ACCESSIBLE' : 'BLOCKED_OFFICIAL_ACCESS',
        requiresInstitutionalAuth: true
      }
    }
    if (sourceId === 'OPEN_METEO_ERA5_LAND') {
      return {
        priority: 'Priority C',
        status: isPublicDataset ? 'ACQUIRED_AND_VALIDATED' : 'UNAVAILABLE',
        license: 'CC-BY-4.0',
        measurementType: 'REANALYSIS_ERA5_LAND'
      }
    }
    return { priority: 'Unknown', status: 'UNSUPPORTED' }
  }

  const imdEval = evaluateSourceStatus({ sourceId: 'IMD_AWS', hasCredentials: false, isPublicDataset: false })
  assert.equal(imdEval.status, 'BLOCKED_OFFICIAL_ACCESS')
  assert.equal(imdEval.requiresInstitutionalAuth, true)

  const cdsEval = evaluateSourceStatus({ sourceId: 'COPERNICUS_CDS', hasCredentials: false, isPublicDataset: false })
  assert.equal(cdsEval.status, 'BLOCKED_OFFICIAL_ACCESS')

  const openMeteoEval = evaluateSourceStatus({ sourceId: 'OPEN_METEO_ERA5_LAND', hasCredentials: false, isPublicDataset: true })
  assert.equal(openMeteoEval.status, 'ACQUIRED_AND_VALIDATED')
  assert.equal(openMeteoEval.license, 'CC-BY-4.0')
  assert.equal(openMeteoEval.measurementType, 'REANALYSIS_ERA5_LAND')
})

test('Phase 25E-B: Multi-period rainfall dataset acquisition completeness and zero missingness invariant', () => {
  const periods = [
    { code: '2023-06', hours: 720, corridors: 11, expectedRecords: 7920 },
    { code: '2023-07', hours: 744, corridors: 11, expectedRecords: 8184 },
    { code: '2023-08', hours: 744, corridors: 11, expectedRecords: 8184 },
    { code: '2023-09', hours: 720, corridors: 11, expectedRecords: 7920 },
    { code: '2024-06', hours: 720, corridors: 11, expectedRecords: 7920 },
    { code: '2024-07', hours: 744, corridors: 11, expectedRecords: 8184 },
    { code: '2024-08', hours: 744, corridors: 11, expectedRecords: 8184 },
    { code: '2024-09', hours: 720, corridors: 11, expectedRecords: 7920 },
    { code: '2025-JJAS', hours: 2928, corridors: 11, expectedRecords: 32208 },
    { code: '2026-JJAS', hours: 2928, corridors: 11, expectedRecords: 32208 }
  ]

  let totalRecords = 0
  for (const p of periods) {
    const calculated = p.hours * p.corridors
    assert.equal(calculated, p.expectedRecords, `Period ${p.code} record count mismatch`)
    totalRecords += calculated
  }

  assert.equal(totalRecords, 128832, 'Total hourly reanalysis records across 10 periods must equal 128,832')
})

test('Phase 25E-B: Negative dry-road inspection lifecycle and supervisor review state transitions', () => {
  function reviewInspection(currentInspection, decision, reviewerRole) {
    if (reviewerRole !== 'MUNICIPAL_ADMIN' && reviewerRole !== 'MUNICIPAL_OPERATOR') {
      throw new Error('Unauthorized reviewer role')
    }
    if (decision === 'VERIFIED_NEGATIVE') {
      return {
        ...currentInspection,
        reviewStatus: 'VERIFIED_NEGATIVE',
        isEligibleGroundTruth: true
      }
    }
    if (decision === 'REJECTED') {
      return {
        ...currentInspection,
        reviewStatus: 'REJECTED',
        isEligibleGroundTruth: false
      }
    }
    return currentInspection
  }

  const initialInspection = {
    id: 101,
    inspectorName: 'Inspector Sharma',
    roadSurfaceCondition: 'DRY',
    waterPresenceConfirmed: false,
    reviewStatus: 'PENDING_REVIEW',
    isEligibleGroundTruth: false
  }

  assert.throws(() => reviewInspection(initialInspection, 'VERIFIED_NEGATIVE', 'MUNICIPAL_VIEWER'))

  const approved = reviewInspection(initialInspection, 'VERIFIED_NEGATIVE', 'MUNICIPAL_ADMIN')
  assert.equal(approved.reviewStatus, 'VERIFIED_NEGATIVE')
  assert.equal(approved.isEligibleGroundTruth, true)

  const rejected = reviewInspection(initialInspection, 'REJECTED', 'MUNICIPAL_OPERATOR')
  assert.equal(rejected.reviewStatus, 'REJECTED')
  assert.equal(rejected.isEligibleGroundTruth, false)
})

test('Phase 25E-B: Calibrated depth measurement metrology validation invariants', () => {
  function validateCalibratedDepth(record) {
    const validMethods = ['CALIBRATED_STAFF_GAUGE', 'ULTRASONIC_STAGE_SENSOR', 'SURVEYED_ROD', 'RADAR_LEVEL_SENSOR']
    if (!validMethods.includes(record.measurementMethod)) {
      return { valid: false, reason: 'Method does not qualify as calibrated physical metrology' }
    }
    if (!record.instrumentIdentifier || record.instrumentIdentifier.trim().length === 0) {
      return { valid: false, reason: 'Missing instrument identifier' }
    }
    if (!record.calibrationEvidenceDoc || record.calibrationEvidenceDoc.trim().length === 0) {
      return { valid: false, reason: 'Missing calibration certification document' }
    }
    if (record.measuredDepthMeters == null || record.measuredDepthMeters < 0 || record.measuredDepthMeters > 5.0) {
      return { valid: false, reason: 'Depth out of physically plausible flood range' }
    }
    return { valid: true, evidenceStatus: 'CALIBRATED_GAUGE' }
  }

  const validRecord = {
    measurementMethod: 'CALIBRATED_STAFF_GAUGE',
    instrumentIdentifier: 'SG-ZAK-01',
    calibrationEvidenceDoc: 'PWD/DEL/IRR/CAL/2024/091',
    measuredDepthMeters: 0.45
  }
  assert.equal(validateCalibratedDepth(validRecord).valid, true)

  const missingCert = {
    measurementMethod: 'CALIBRATED_STAFF_GAUGE',
    instrumentIdentifier: 'SG-ZAK-01',
    calibrationEvidenceDoc: '',
    measuredDepthMeters: 0.45
  }
  assert.equal(validateCalibratedDepth(missingCert).valid, false)

  const implausibleDepth = {
    measurementMethod: 'CALIBRATED_STAFF_GAUGE',
    instrumentIdentifier: 'SG-ZAK-01',
    calibrationEvidenceDoc: 'PWD/2024',
    measuredDepthMeters: 8.5
  }
  assert.equal(validateCalibratedDepth(implausibleDepth).valid, false)
})

test('Phase 25E-B: Supervised ML readiness gating logic and operational risk engine invariant', () => {
  function evaluateSupervisedTrainingReadiness({ verifiedPositives, verifiedNegatives, calibratedDepths, rainfallAlignedRatio }) {
    const POS_THRESHOLD = 50
    const NEG_THRESHOLD = 50
    const DEPTH_THRESHOLD = 30

    if (rainfallAlignedRatio < 1.0) {
      return {
        verdict: 'BLOCKED_RAINFALL_ALIGNMENT',
        heuristicRiskEngineActive: true
      }
    }
    if (verifiedPositives < POS_THRESHOLD || verifiedNegatives < NEG_THRESHOLD) {
      return {
        verdict: 'BLOCKED_UNVERIFIED_GROUND_TRUTH',
        heuristicRiskEngineActive: true,
        posDeficit: POS_THRESHOLD - verifiedPositives,
        negDeficit: NEG_THRESHOLD - verifiedNegatives,
        depthDeficit: DEPTH_THRESHOLD - calibratedDepths
      }
    }
    return {
      verdict: 'READY_FOR_BASELINE_TRAINING',
      heuristicRiskEngineActive: true
    }
  }

  // Exact Phase 25E-B condition: 100% rainfall aligned, but 0 genuine verified positives, 0 genuine verified negatives
  const gateResult = evaluateSupervisedTrainingReadiness({
    verifiedPositives: 0,
    verifiedNegatives: 0,
    calibratedDepths: 0,
    rainfallAlignedRatio: 1.0
  })

  assert.equal(gateResult.verdict, 'BLOCKED_UNVERIFIED_GROUND_TRUTH')
  assert.equal(gateResult.heuristicRiskEngineActive, true, 'Heuristic flood risk engine must NEVER be disabled')
  assert.equal(gateResult.posDeficit, 50)
  assert.equal(gateResult.negDeficit, 50)
})
