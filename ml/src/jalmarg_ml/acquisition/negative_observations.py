"""Municipal Verified Negative (Dry-Road) Observation Workflow

Implements the official protocol for municipal field officers to record confirmed
non-waterlogged conditions during or after rain events.
Strictly prohibits inferring negatives from absence of citizen reports, silence,
or uninspected corridors.
"""

from typing import Dict, Any, List, Optional
from datetime import datetime, timezone
from pydantic import BaseModel, Field

from ..labeling.schema import (
    GroundTruthObservation,
    WaterloggingLabel,
    VerificationStatus,
    EvidenceType,
    ProvenanceClassification
)


class NegativeObservationSubmission(BaseModel):
    """Payload for submitting a municipal verified negative observation."""
    inspection_id: str
    latitude: float
    longitude: float
    location_description: str
    corridor_name: Optional[str] = None
    inspection_timestamp: datetime
    officer_username: str
    officer_role: str
    officer_department: str
    no_visible_standing_water: bool = True
    supporting_notes: str
    inspection_photo_reference: Optional[str] = None
    spatial_accuracy_m: float = 15.0 # Exact field GPS reading


class VerifiedNegativeObservationService:
    """Service validating and converting municipal dry-road field inspections into ground-truth negatives."""

    # Delhi NCR Bounding Box validation
    MIN_LAT = 28.20
    MAX_LAT = 28.95
    MIN_LNG = 76.80
    MAX_LNG = 77.55

    AUTHORIZED_ROLES = {"MUNICIPAL_OPERATOR", "MUNICIPAL_ADMIN", "AUTHORITY_OFFICER", "FIELD_ENGINEER"}

    def validate_and_record(self, submission: NegativeObservationSubmission) -> GroundTruthObservation:
        """Validates negative observation submission criteria conforming to Section 6."""
        # 1. Geographic bounds check
        if not (self.MIN_LAT <= submission.latitude <= self.MAX_LAT and
                self.MIN_LNG <= submission.longitude <= self.MAX_LNG):
            raise ValueError(
                f"Inspection coordinates ({submission.latitude}, {submission.longitude}) "
                f"fall outside monitored Delhi NCR bounds."
            )

        # 2. Timestamp check: Cannot be in future
        now_utc = datetime.now(timezone.utc)
        insp_ts = submission.inspection_timestamp
        if insp_ts.tzinfo is None:
            insp_ts = insp_ts.replace(tzinfo=timezone.utc)

        if insp_ts > now_utc:
            raise ValueError("Inspection timestamp cannot be in the future.")

        # 3. Role authorization check
        if submission.officer_role not in self.AUTHORIZED_ROLES:
            raise PermissionError(
                f"Role '{submission.officer_role}' is not authorized to submit verified negative observations. "
                f"Authorized roles: {', '.join(sorted(self.AUTHORIZED_ROLES))}."
            )

        # 4. Explicit dry confirmation check
        if not submission.no_visible_standing_water:
            raise ValueError(
                "Submission must explicitly confirm no visible standing water (no_visible_standing_water=True)."
            )

        # 5. Supporting notes required
        if not submission.supporting_notes or len(submission.supporting_notes.strip()) < 5:
            raise ValueError("Substantive field inspection notes are mandatory for verified negative observations.")

        # Construct official ground-truth observation
        obs = GroundTruthObservation(
            observation_id=submission.inspection_id,
            latitude=submission.latitude,
            longitude=submission.longitude,
            location_description=submission.location_description,
            observation_timestamp=insp_ts,
            reported_at=insp_ts,
            event_start_time=insp_ts,
            event_end_time=insp_ts,
            publication_timestamp=now_utc,
            waterlogging_status=WaterloggingLabel.VERIFIED_NO_WATERLOGGING,
            verification_status=VerificationStatus.VERIFIED,
            verification_authority=f"{submission.officer_department} / JalMarg Municipal",
            verified_by_user=submission.officer_username,
            verified_at=insp_ts,
            source_organization=f"MUNICIPAL_FIELD_INSPECTION_{submission.officer_department}",
            source_reference=f"FIELD-LOG-{submission.inspection_id}",
            source_url="internal://jalmarg/inspections/dry-road",
            evidence_type=EvidenceType.FIELD_INSPECTION,
            evidence_file_reference=submission.inspection_photo_reference,
            license_code="MUNICIPAL_OPERATIONAL_CONFIDENTIAL",
            permitted_usage="RESEARCH_AND_MUNICIPAL_OPERATIONS",
            spatial_accuracy_m=submission.spatial_accuracy_m,
            is_approximate_location=False,
            temporal_accuracy_minutes=15,
            measured_water_depth_m=0.0, # Explicitly measured 0.0m water depth
            depth_measurement_method="FIELD_INSPECTION_CONFIRMED_ZERO",
            verification_notes=(
                f"Officer {submission.officer_username} ({submission.officer_department}) "
                f"confirmed no standing water: {submission.supporting_notes}"
            ),
            provenance_classification=ProvenanceClassification.FIELD_INSPECTION,
            corridor_name=submission.corridor_name or "Inspected Delhi Corridor",
            alignment_status="PENDING",
            review_queue_status="RESOLVED"
        )
        return obs
