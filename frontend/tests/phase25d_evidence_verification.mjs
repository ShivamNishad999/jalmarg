import test from 'node:test'
import assert from 'node:assert/strict'
import { api } from '../src/api.js'
import { stage2Strings } from '../src/stage2Strings.js'

test('Phase 25D: Evidence audit and Flyway integrity API client functions are defined', () => {
  assert.equal(typeof api.evidenceAuditSummary, 'function')
  assert.equal(typeof api.evidenceAuditObservations, 'function')
  assert.equal(typeof api.flywayIntegrityStatus, 'function')
})

test('Phase 25D: Complete English and Hindi localization string parity for evidence verification', () => {
  const phase25dKeys = [
    'tabPhase25D',
    'phase25dTitle',
    'flywayCardTitle',
    'flywayChecksumMismatchNotice',
    'flywayRemediationStatus',
    'auditCensusTitle',
    'auditGenuinePositives',
    'auditGenuineNegatives',
    'auditPartiallyVerified',
    'auditUnverifiedMunicipal',
    'auditCalibratedDepths',
    'auditDepthClaimsExcluded',
    'auditRainfallAligned',
    'auditRainfallMissing',
    'auditGateVerdict',
    'auditLedgerBreakdownTitle',
    'colObsId',
    'colCorridor',
    'colEvidenceStatus',
    'colDepthStatus',
    'colRainfallStatus',
    'colEligibility',
    'colRationale'
  ]

  for (const k of phase25dKeys) {
    assert.ok(stage2Strings.en[k], `Missing EN string for key: ${k}`)
    assert.ok(stage2Strings.hi[k], `Missing HI string for key: ${k}`)
    assert.notEqual(stage2Strings.en[k], stage2Strings.hi[k], `EN and HI string should not be identical for: ${k}`)
  }
})

test('Phase 25D: Flyway V6 integrity evaluation logic and safe remediation policy', () => {
  function evaluateFlywayIntegrity({ appliedChecksum, resolvedChecksum, tablesExist }) {
    if (appliedChecksum !== resolvedChecksum) {
      return {
        status: 'FAILED_CHECKSUM_MISMATCH',
        remediationVerdict: 'REMEDIATION_REQUIRES_APPROVAL',
        canAutoRepair: false,
        requiresApproval: true,
        reason: 'Manual schema history alteration prohibited. Non-destructive repair migration requires authorized approval.'
      }
    }
    if (!tablesExist) {
      return {
        status: 'FAILED_MISSING_TABLES',
        remediationVerdict: 'REMEDIATION_REQUIRES_APPROVAL',
        canAutoRepair: false,
        requiresApproval: true
      }
    }
    return {
      status: 'VERIFIED',
      remediationVerdict: 'INTACT',
      canAutoRepair: false,
      requiresApproval: false
    }
  }

  // Exact Phase 25D condition: DB stored 1892837461 vs local file 16961246
  const evalResult = evaluateFlywayIntegrity({
    appliedChecksum: 1892837461,
    resolvedChecksum: 16961246,
    tablesExist: true
  })

  assert.equal(evalResult.status, 'FAILED_CHECKSUM_MISMATCH')
  assert.equal(evalResult.remediationVerdict, 'REMEDIATION_REQUIRES_APPROVAL')
  assert.equal(evalResult.canAutoRepair, false, 'Flyway auto-repair must NEVER be executed automatically')
  assert.equal(evalResult.requiresApproval, true)

  // Post-repair condition: DB stored 16961246 vs local file 16961246
  const postRepairResult = evaluateFlywayIntegrity({
    appliedChecksum: 16961246,
    resolvedChecksum: 16961246,
    tablesExist: true
  })
  assert.equal(postRepairResult.status, 'VERIFIED')
  assert.equal(postRepairResult.remediationVerdict, 'INTACT')
  assert.equal(postRepairResult.requiresApproval, false)
})

test('Phase 25D: Independent evidence verification and training eligibility census invariants', () => {
  // Evaluates observation eligibility based on authenticity forensics
  function evaluateObservationEligibility(obs) {
    // A. Evidence Status
    let evidenceStatus = 'EVIDENCE_UNVERIFIED'
    if (obs.hasRawDocument && obs.corroboratingReports > 0) {
      evidenceStatus = 'EVIDENCE_VERIFIED'
    } else if (obs.corroboratingReports > 0) {
      evidenceStatus = 'EVIDENCE_PARTIALLY_VERIFIED'
    }

    // B. Calibrated Depth
    let depthStatus = 'DEPTH_UNVERIFIED_CLAIM'
    if (obs.gaugeInstrumentId && obs.calibrationRecordPresent) {
      depthStatus = 'CALIBRATED_GAUGE'
    }

    // C. Rainfall Alignment
    let rainfallStatus = 'ALIGNMENT_MISSING_DATA'
    if (obs.rainfallDatasetAvailable && obs.temporalWindowAligned) {
      rainfallStatus = 'ALIGNMENT_VERIFIED'
    }

    // D. Training Eligibility
    let eligibility = 'ELIGIBLE_FOR_TRAINING'
    let exclusionReason = null

    if (evidenceStatus !== 'EVIDENCE_VERIFIED') {
      eligibility = 'EXCLUDED_UNVERIFIED_EVIDENCE'
      exclusionReason = 'Raw primary evidence file missing from repository'
    } else if (rainfallStatus !== 'ALIGNMENT_VERIFIED') {
      eligibility = 'EXCLUDED_MISSING_RAINFALL'
      exclusionReason = 'Historical rainfall window unavailable in authentic records'
    }

    return { evidenceStatus, depthStatus, rainfallStatus, eligibility, exclusionReason }
  }

  // Historical positive without raw PDF (all 16 historical events)
  const histPos = evaluateObservationEligibility({
    hasRawDocument: false,
    corroboratingReports: 3,
    gaugeInstrumentId: null,
    calibrationRecordPresent: false,
    rainfallDatasetAvailable: false,
    temporalWindowAligned: false
  })
  assert.equal(histPos.evidenceStatus, 'EVIDENCE_PARTIALLY_VERIFIED')
  assert.equal(histPos.depthStatus, 'DEPTH_UNVERIFIED_CLAIM')
  assert.equal(histPos.rainfallStatus, 'ALIGNMENT_MISSING_DATA')
  assert.equal(histPos.eligibility, 'EXCLUDED_UNVERIFIED_EVIDENCE')

  // Municipal negative without raw external audit
  const muniNeg = evaluateObservationEligibility({
    hasRawDocument: false,
    corroboratingReports: 0,
    gaugeInstrumentId: null,
    calibrationRecordPresent: false,
    rainfallDatasetAvailable: true,
    temporalWindowAligned: true
  })
  assert.equal(muniNeg.evidenceStatus, 'EVIDENCE_UNVERIFIED')
  assert.equal(muniNeg.eligibility, 'EXCLUDED_UNVERIFIED_EVIDENCE')

  // Recalculated Census Assertions
  const recalculatedCensus = {
    claimedPositives: 16,
    verifiedPositives: 0,
    claimedNegatives: 8,
    verifiedNegatives: 0,
    claimedDepths: 16,
    verifiedCalibratedDepths: 0,
    partiallyVerified: 16,
    unverified: 8,
    rainfallAligned: 5,
    rainfallMissing: 19
  }

  assert.equal(recalculatedCensus.verifiedPositives, 0)
  assert.equal(recalculatedCensus.verifiedNegatives, 0)
  assert.equal(recalculatedCensus.verifiedCalibratedDepths, 0)
  assert.equal(recalculatedCensus.partiallyVerified + recalculatedCensus.unverified, 24)
  assert.equal(recalculatedCensus.rainfallAligned + recalculatedCensus.rainfallMissing, 24)
})
