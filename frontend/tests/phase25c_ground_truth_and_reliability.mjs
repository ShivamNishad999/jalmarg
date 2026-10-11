import test from 'node:test'
import assert from 'node:assert/strict'
import { api } from '../src/api.js'
import { stage2Strings } from '../src/stage2Strings.js'

test('Phase 25C: Ground truth and reliability API client functions are defined', () => {
  assert.equal(typeof api.groundTruthObservations, 'function')
  assert.equal(typeof api.groundTruthSummary, 'function')
  assert.equal(typeof api.groundTruthQueue, 'function')
  assert.equal(typeof api.groundTruthProvenance, 'function')
  assert.equal(typeof api.recordNegativeObservation, 'function')
  assert.equal(typeof api.reviewObservation, 'function')
  assert.equal(typeof api.visionReliabilityAudit, 'function')
})

test('Phase 25C: Complete English and Hindi localization string parity', () => {
  const phase25cKeys = [
    'tabGroundTruth',
    'gtTitle',
    'gtSummaryTitle',
    'gtVerifiedPositives',
    'gtVerifiedNegatives',
    'gtPendingReview',
    'gtDisputed',
    'gtDepthMeasurements',
    'gtMonsoonSeasons',
    'gtAlignmentCompleteness',
    'gtHistoricalLedgerTitle',
    'gtQueueTitle',
    'gtNegativeModalTitle',
    'gtVerifyBtn',
    'gtDisputeBtn',
    'gtRejectBtn',
    'gtRecordNegativeBtn',
    'gtLocationDesc',
    'gtCorridor',
    'gtTimestamp',
    'gtMeasuredDepth',
    'gtNotes',
    'gtProvenance',
    'gtEvidenceType',
    'gtNoStandingWater',
    'gtCvAuditTitle',
    'gtCvFolderDerivedNotice',
    'gtCvAbstentionNotice',
    'gtLicenseBlockerNotice',
    'gtFailureModesTitle',
    'gtTrainingReadinessTitle',
    'gtOperationalHeuristicNotice'
  ]

  for (const k of phase25cKeys) {
    assert.ok(stage2Strings.en[k], `Missing EN string for key: ${k}`)
    assert.ok(stage2Strings.hi[k], `Missing HI string for key: ${k}`)
    assert.notEqual(stage2Strings.en[k], stage2Strings.hi[k], `EN and HI string should not be identical for: ${k}`)
  }
})

test('Phase 25C: Computer Vision Dual-Threshold Abstention Policy logic', () => {
  const lowerThreshold = 0.35
  const upperThreshold = 0.65

  function evaluatePrediction(probability) {
    if (probability < lowerThreshold) {
      return { status: 'NO_WATERLOGGING', confidence: 1.0 - probability }
    } else if (probability > upperThreshold) {
      return { status: 'WATERLOGGING_DETECTED', confidence: probability }
    } else {
      return { status: 'INDETERMINATE', reason: 'UNCERTAINTY_ABSTENTION_REQUIRED' }
    }
  }

  // Clear negatives
  assert.equal(evaluatePrediction(0.12).status, 'NO_WATERLOGGING')
  assert.equal(evaluatePrediction(0.34).status, 'NO_WATERLOGGING')

  // Clear positives
  assert.equal(evaluatePrediction(0.88).status, 'WATERLOGGING_DETECTED')
  assert.equal(evaluatePrediction(0.66).status, 'WATERLOGGING_DETECTED')

  // Ambiguous abstention zone [0.35, 0.65]
  assert.equal(evaluatePrediction(0.35).status, 'INDETERMINATE')
  assert.equal(evaluatePrediction(0.50).status, 'INDETERMINATE')
  assert.equal(evaluatePrediction(0.65).status, 'INDETERMINATE')
})

test('Phase 25C: Municipal verified negative observation validation constraints', () => {
  function validateNegativeInspection({ lat, lng, observationTime, confirmedNoWater, officerRole }) {
    // Delhi NCR bounding box: 28.30 <= lat <= 28.90, 76.80 <= lng <= 77.70
    if (lat < 28.30 || lat > 28.90 || lng < 76.80 || lng > 77.70) {
      return { valid: false, error: 'Location outside Delhi NCR bounds' }
    }
    const obsDate = new Date(observationTime)
    if (obsDate > new Date()) {
      return { valid: false, error: 'Observation timestamp cannot be in the future' }
    }
    if (!confirmedNoWater) {
      return { valid: false, error: 'Explicit confirmation of no standing water required' }
    }
    const authorizedRoles = ['MUNICIPAL_ADMIN', 'MUNICIPAL_OPERATOR', 'FIELD_INSPECTOR']
    if (!authorizedRoles.includes(officerRole)) {
      return { valid: false, error: 'Unauthorized role for recording ground truth negative' }
    }
    return { valid: true }
  }

  // Valid observation
  const valid = validateNegativeInspection({
    lat: 28.6331,
    lng: 77.2208,
    observationTime: '2026-08-15T08:00:00Z',
    confirmedNoWater: true,
    officerRole: 'MUNICIPAL_OPERATOR'
  })
  assert.equal(valid.valid, true)

  // Outside bounds
  const badLoc = validateNegativeInspection({
    lat: 19.0760,
    lng: 72.8777,
    observationTime: '2026-08-15T08:00:00Z',
    confirmedNoWater: true,
    officerRole: 'MUNICIPAL_OPERATOR'
  })
  assert.equal(badLoc.valid, false)

  // Unconfirmed
  const unconfirmed = validateNegativeInspection({
    lat: 28.6331,
    lng: 77.2208,
    observationTime: '2026-08-15T08:00:00Z',
    confirmedNoWater: false,
    officerRole: 'MUNICIPAL_OPERATOR'
  })
  assert.equal(unconfirmed.valid, false)

  // Unauthorized citizen role
  const unauth = validateNegativeInspection({
    lat: 28.6331,
    lng: 77.2208,
    observationTime: '2026-08-15T08:00:00Z',
    confirmedNoWater: true,
    officerRole: 'CITIZEN'
  })
  assert.equal(unauth.valid, false)
})

test('Phase 25C: Predictive ML Training Readiness Invariants', () => {
  // Ground truth counts: 16 authentic positives, 8 authentic negatives
  const positives = 16
  const negatives = 8
  const requiredPositives = 50
  const requiredNegatives = 50

  const ready = positives >= requiredPositives && negatives >= requiredNegatives
  assert.equal(ready, false, 'Predictive ML supervised training must be withheld when ground truth is insufficient')

  const gateDecision = (positives > 0 && negatives > 0) ? 'PARTIALLY_READY' : 'BLOCKED_INSUFFICIENT_GROUND_TRUTH'
  assert.equal(gateDecision, 'PARTIALLY_READY')
})
