"""Automated Tests for Phase 25C Authentic Ground-Truth & Historical Event Reconstruction

Validates:
1. Candidate historical sources and blocker tracking
2. Delhi hotspot coordinate resolution and uncertainty preservation
3. Temporal distinction (event start vs end vs publication)
4. Spatiotemporal deduplication logic (<= 300m and <= 6h)
5. Municipal verified negative observation workflow constraints
6. Rainfall and terrain feature alignment with target leakage protection
7. Multi-scenario training readiness gate evaluation
"""

import pytest
from datetime import datetime, timezone, timedelta

from jalmarg_ml.labeling.schema import (
    GroundTruthObservation,
    WaterloggingLabel,
    VerificationStatus,
    EvidenceType,
    ProvenanceClassification
)
from jalmarg_ml.acquisition.historical_sources import (
    HistoricalSourceRegistry,
    DELHI_NCR_HOTSPOT_COORDINATES
)
from jalmarg_ml.acquisition.event_reconstruction import (
    HistoricalEventReconstructor,
    haversine_distance_m
)
from jalmarg_ml.acquisition.negative_observations import (
    VerifiedNegativeObservationService,
    NegativeObservationSubmission
)
from jalmarg_ml.features.aligner import HydrometeorologicalAligner
from jalmarg_ml.labeling.readiness_gate import TrainingReadinessEvaluator


def test_historical_source_registry_and_blockers():
    sources = HistoricalSourceRegistry.get_candidate_sources()
    assert len(sources) >= 6

    # Verify Delhi Traffic Police is acquired
    dtp = next((s for s in sources if s.source_code == "DELHI_TRAFFIC_POLICE_MONSOON_ADVISORIES"), None)
    assert dtp is not None
    assert dtp.acquisition_status == "ACQUIRED"
    assert dtp.records_count > 0
    assert dtp.sha256_checksum is not None

    # Verify IMD AWS and Commercial News are blocked
    imd = next((s for s in sources if s.source_code == "IMD_HIGH_FREQUENCY_AWS_NETWORK"), None)
    assert imd is not None
    assert imd.acquisition_status == "BLOCKED"
    assert "token" in imd.verification_limitations.lower() or "whitelist" in imd.verification_limitations.lower()

    news = next((s for s in sources if s.source_code == "COMMERCIAL_NEWS_ARCHIVE_FULLTEXT"), None)
    assert news is not None
    assert news.acquisition_status == "BLOCKED"


def test_hotspot_georesolution_and_uncertainty():
    reconstructor = HistoricalEventReconstructor()

    # 1. Known hotspot: Minto Bridge
    lat, lng, desc, corridor, acc, is_approx = reconstructor.resolve_location("MINTO_BRIDGE")
    assert round(lat, 2) == 28.64
    assert round(lng, 2) == 77.22
    assert "Minto Bridge" in desc
    assert acc <= 30.0
    assert not is_approx

    # 2. Approximate location preserves uncertainty
    lat2, lng2, desc2, corr2, acc2, is_approx2 = reconstructor.resolve_location(
        "SOUTH_DELHI_VAGUE", fallback_lat=28.53, fallback_lng=77.20
    )
    assert is_approx2 is True
    assert acc2 >= 200.0


def test_temporal_distinction_and_observation_building():
    reconstructor = HistoricalEventReconstructor()
    obs = reconstructor.build_observation(
        observation_id="HIST-DEL-2023-01",
        hotspot_key="MINTO_BRIDGE",
        event_start_utc="2023-07-09T08:30:00Z",
        event_end_utc="2023-07-09T14:00:00Z",
        publication_utc="2023-07-09T09:15:00Z",
        source_code="DELHI_TRAFFIC_POLICE_MONSOON_ADVISORIES",
        source_reference="Traffic Advisory Bulletin Ref #2023/TR/07/09/01",
        source_url="https://traffic.delhipolice.gov.in/bulletin/2023-07-09",
        evidence_type=EvidenceType.OFFICIAL_TRAFFIC_BULLETIN,
        provenance=ProvenanceClassification.TRAFFIC_POLICE_BULLETIN,
        measured_depth_m=1.2,
        depth_method="POLICE_WATER_MARK_GAUGE",
        verification_notes="Underpass flooded, traffic diverted via Connaught Circus."
    )

    assert obs.observation_id == "HIST-DEL-2023-01"
    assert obs.waterlogging_status == WaterloggingLabel.VERIFIED_WATERLOGGING
    assert obs.event_start_time < obs.event_end_time
    assert obs.publication_timestamp > obs.event_start_time
    assert obs.measured_water_depth_m == 1.2
    assert obs.depth_measurement_method == "POLICE_WATER_MARK_GAUGE"


def test_spatiotemporal_deduplication():
    reconstructor = HistoricalEventReconstructor()

    # Observation 1: Minto Bridge at 09:00 UTC
    obs1 = reconstructor.build_observation(
        observation_id="OBS-DTP-001",
        hotspot_key="MINTO_BRIDGE",
        event_start_utc="2023-07-09T09:00:00Z",
        event_end_utc=None,
        publication_utc="2023-07-09T09:30:00Z",
        source_code="DELHI_TRAFFIC_POLICE_MONSOON_ADVISORIES",
        source_reference="DTP Advisory 001",
        source_url="https://traffic.delhipolice.gov.in/",
        evidence_type=EvidenceType.OFFICIAL_TRAFFIC_BULLETIN,
        provenance=ProvenanceClassification.TRAFFIC_POLICE_BULLETIN
    )

    # Observation 2: Same Minto Bridge at 10:15 UTC (within 6h and same spot)
    obs2 = reconstructor.build_observation(
        observation_id="OBS-MCD-002",
        hotspot_key="MINTO_BRIDGE",
        event_start_utc="2023-07-09T10:15:00Z",
        event_end_utc=None,
        publication_utc="2023-07-09T11:00:00Z",
        source_code="MCD_PWD_MONSOON_ACTION_PLAN",
        source_reference="MCD Field Inspection 002",
        source_url="https://pwd.delhigovt.nic.in/",
        evidence_type=EvidenceType.MUNICIPAL_DISASTER_LOG,
        provenance=ProvenanceClassification.OFFICIAL_MUNICIPAL,
        measured_depth_m=1.4,
        depth_method="MUNICIPAL_SUMP_ROD"
    )

    # Observation 3: Pul Prahladpur (> 15 km away) at same time
    obs3 = reconstructor.build_observation(
        observation_id="OBS-DTP-003",
        hotspot_key="PUL_PRAHLADPUR",
        event_start_utc="2023-07-09T09:30:00Z",
        event_end_utc=None,
        publication_utc="2023-07-09T10:00:00Z",
        source_code="DELHI_TRAFFIC_POLICE_MONSOON_ADVISORIES",
        source_reference="DTP Advisory 003",
        source_url="https://traffic.delhipolice.gov.in/",
        evidence_type=EvidenceType.OFFICIAL_TRAFFIC_BULLETIN,
        provenance=ProvenanceClassification.TRAFFIC_POLICE_BULLETIN
    )

    deduped, audit = reconstructor.deduplicate_observations([obs1, obs2, obs3])

    # Should deduplicate obs1 and obs2 into 1 event, keeping obs3 distinct (total 2)
    assert len(deduped) == 2
    assert len(audit) == 1
    assert audit[0]["retained_id"] == "OBS-DTP-001"
    assert audit[0]["merged_id"] == "OBS-MCD-002"
    # Preserved measured depth from merged report
    assert deduped[0].measured_water_depth_m == 1.4


def test_verified_negative_observation_workflow():
    service = VerifiedNegativeObservationService()

    # Valid submission
    sub = NegativeObservationSubmission(
        inspection_id="INSP-NEG-2024-001",
        latitude=28.6366,
        longitude=77.2241,
        location_description="Minto Bridge Underpass inspection post-storm",
        corridor_name="Connaught Place Arterial",
        inspection_timestamp=datetime.now(timezone.utc) - timedelta(hours=1),
        officer_username="inspector_sharma",
        officer_role="MUNICIPAL_OPERATOR",
        officer_department="PWD_DELHI_DRAINAGE",
        no_visible_standing_water=True,
        supporting_notes="Roadway completely clear of standing water; all 4 pumps operational.",
        inspection_photo_reference="uploads/inspections/minto_clear_2024.jpg"
    )

    obs = service.validate_and_record(sub)
    assert obs.waterlogging_status == WaterloggingLabel.VERIFIED_NO_WATERLOGGING
    assert obs.verification_status == VerificationStatus.VERIFIED
    assert obs.measured_water_depth_m == 0.0
    assert obs.spatial_accuracy_m == 15.0

    # Invalid: Outside Delhi NCR
    with pytest.raises(ValueError, match="outside monitored Delhi NCR bounds"):
        service.validate_and_record(
            sub.model_copy(update={"latitude": 19.0760, "longitude": 72.8777}) # Mumbai
        )

    # Invalid: Future timestamp
    with pytest.raises(ValueError, match="cannot be in the future"):
        service.validate_and_record(
            sub.model_copy(update={"inspection_timestamp": datetime.now(timezone.utc) + timedelta(days=1)})
        )

    # Invalid: Unauthorized citizen role
    with pytest.raises(PermissionError, match="not authorized"):
        service.validate_and_record(
            sub.model_copy(update={"officer_role": "CITIZEN_PUBLIC"})
        )

    # Invalid: False no_visible_standing_water
    with pytest.raises(ValueError, match="no visible standing water"):
        service.validate_and_record(
            sub.model_copy(update={"no_visible_standing_water": False})
        )


def test_rainfall_alignment_and_leakage_guard():
    aligner = HydrometeorologicalAligner()
    t0 = datetime(2024, 7, 15, 14, 0, 0, tzinfo=timezone.utc)

    # Synthetic rainfall series with records before and after T0
    rainfall_series = [
        {"timestamp_utc": "2024-07-15T11:00:00Z", "precipitation_mm": 5.0},
        {"timestamp_utc": "2024-07-15T12:00:00Z", "precipitation_mm": 12.0},
        {"timestamp_utc": "2024-07-15T13:00:00Z", "precipitation_mm": 20.0},
        {"timestamp_utc": "2024-07-15T14:00:00Z", "precipitation_mm": 8.0}, # At T0
        {"timestamp_utc": "2024-07-15T15:00:00Z", "precipitation_mm": 50.0}, # After T0 (LEAKAGE CANDIDATE)
        {"timestamp_utc": "2024-07-15T16:00:00Z", "precipitation_mm": 80.0}  # After T0 (LEAKAGE CANDIDATE)
    ]

    obs = GroundTruthObservation(
        observation_id="TEST-ALIGN-01",
        latitude=28.6366,
        longitude=77.2241,
        location_description="Minto Bridge",
        observation_timestamp=t0,
        waterlogging_status=WaterloggingLabel.VERIFIED_WATERLOGGING,
        verification_status=VerificationStatus.VERIFIED,
        source_organization="PWD_DELHI",
        source_reference="REF-001",
        evidence_type=EvidenceType.FIELD_INSPECTION,
        provenance_classification=ProvenanceClassification.OFFICIAL_MUNICIPAL
    )

    aligned = aligner.align_observation(
        observation=obs,
        rainfall_records=rainfall_series,
        elevation_m=214.5,
        slope_degrees=1.2,
        nearest_drain_distance_m=45.0
    )

    # Accumulation in prior 1h should be only 14:00 (8.0 mm)
    # The 50mm and 80mm rainfall occurring AFTER T0 (15:00 and 16:00) MUST NOT be included!
    assert aligned.rainfall_1h_mm == 8.0
    # Prior 3h: 12:00 (12.0) + 13:00 (20.0) + 14:00 (8.0) = 40.0
    assert aligned.rainfall_3h_mm == 40.0
    assert aligned.elevation_m == 214.5
    assert aligned.nearest_drain_distance_m == 45.0


def test_extended_readiness_decision_gate():
    evaluator = TrainingReadinessEvaluator({
        "minimum_verified_positive_events": 50,
        "minimum_verified_negative_observations": 50,
        "minimum_monsoon_seasons": 2
    })

    # Scenario 1: License review blocked
    verdict1 = evaluator.evaluate(
        samples=[],
        distinct_monsoon_seasons=2,
        rainfall_data_accessible=True,
        license_blocked=True
    )
    assert verdict1.readiness_gate == "BLOCKED_LICENSE_REVIEW"

    # Scenario 2: Rainfall data inaccessible
    verdict2 = evaluator.evaluate(
        samples=[],
        distinct_monsoon_seasons=2,
        rainfall_data_accessible=False,
        license_blocked=False
    )
    assert verdict2.readiness_gate == "BLOCKED_DATA_ACCESS"

    # Scenario 3: Zero monsoon seasons
    verdict3 = evaluator.evaluate(
        samples=[],
        distinct_monsoon_seasons=0,
        rainfall_data_accessible=True,
        license_blocked=False
    )
    assert verdict3.readiness_gate == "BLOCKED_TEMPORAL_COVERAGE"

    # Scenario 4: Insufficient positive/negative ground truth
    verdict4 = evaluator.evaluate(
        samples=[],
        distinct_monsoon_seasons=1,
        rainfall_data_accessible=True,
        license_blocked=False
    )
    assert verdict4.readiness_gate == "BLOCKED_INSUFFICIENT_GROUND_TRUTH"
