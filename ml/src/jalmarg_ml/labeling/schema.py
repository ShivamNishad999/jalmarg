"""Training Label Schema, Ground-Truth States & Observation Models

Defines explicit waterlogging label states to prevent spurious negative assumptions,
separate verified authority decisions from crowd reporting, and maintain full
data provenance for historical event reconstruction.
"""

from enum import Enum
from typing import Optional, Dict, Any, List
from datetime import datetime, timezone
from pydantic import BaseModel, Field


class WaterloggingLabel(str, Enum):
    """Ground truth label classes adhering to Section 3 and Section 11."""
    VERIFIED_WATERLOGGING = "VERIFIED_WATERLOGGING"       # Positive: Confirmed physical standing water/flooding
    VERIFIED_NO_WATERLOGGING = "VERIFIED_NO_WATERLOGGING" # Negative: Explicitly inspected and verified clear/dry
    REPORTED_UNVERIFIED = "REPORTED_UNVERIFIED"           # Ambiguous: Citizen report pending municipal verification
    DISPUTED = "DISPUTED"                                 # Contested observation under authority review
    UNKNOWN = "UNKNOWN"                                   # Unassessed: No observation or telemetry recorded


class VerificationStatus(str, Enum):
    """Authority verification workflow states."""
    VERIFIED = "VERIFIED"
    UNVERIFIED = "UNVERIFIED"
    DISPUTED = "DISPUTED"
    REJECTED = "REJECTED"
    UNDER_REVIEW = "UNDER_REVIEW"


class EvidenceType(str, Enum):
    """Type of supporting physical or documentary evidence."""
    FIELD_INSPECTION = "FIELD_INSPECTION"
    OFFICIAL_TRAFFIC_BULLETIN = "OFFICIAL_TRAFFIC_BULLETIN"
    MUNICIPAL_DISASTER_LOG = "MUNICIPAL_DISASTER_LOG"
    CITIZEN_PHOTO_INSPECTED = "CITIZEN_PHOTO_INSPECTED"
    UNVERIFIED_REPORT = "UNVERIFIED_REPORT"


class ProvenanceClassification(str, Enum):
    """Origin and authority tier of the observation."""
    OFFICIAL_MUNICIPAL = "OFFICIAL_MUNICIPAL"
    TRAFFIC_POLICE_BULLETIN = "TRAFFIC_POLICE_BULLETIN"
    FIELD_INSPECTION = "FIELD_INSPECTION"
    CITIZEN_VERIFIED = "CITIZEN_VERIFIED"
    HISTORICAL_ARCHIVE = "HISTORICAL_ARCHIVE"


class GroundTruthObservation(BaseModel):
    """Comprehensive ground truth observation model conforming strictly to Section 3."""
    observation_id: str
    latitude: float
    longitude: float
    location_description: str
    observation_timestamp: datetime
    observation_timezone: str = "Asia/Kolkata"
    reported_at: Optional[datetime] = None
    event_start_time: Optional[datetime] = None
    event_end_time: Optional[datetime] = None
    publication_timestamp: Optional[datetime] = None

    waterlogging_status: WaterloggingLabel
    verification_status: VerificationStatus
    verification_authority: Optional[str] = None
    verified_by_user: Optional[str] = None
    verified_at: Optional[datetime] = None

    source_organization: str
    source_reference: str
    source_url: Optional[str] = None
    evidence_type: EvidenceType
    evidence_file_reference: Optional[str] = None

    license_code: str = "GOI_OPEN_DATA"
    permitted_usage: str = "RESEARCH_AND_MUNICIPAL_OPERATIONS"

    spatial_accuracy_m: float = 50.0
    is_approximate_location: bool = False
    temporal_accuracy_minutes: int = 60

    # Physical water depth - STRICTLY only when supported by measurement
    measured_water_depth_m: Optional[float] = None
    depth_measurement_method: Optional[str] = None # GAUGE_ROD, SENSOR_TELEMETRY, NONE

    verification_notes: Optional[str] = None
    provenance_classification: ProvenanceClassification

    # Hydrological & Terrain Alignment Features
    alignment_status: str = "PENDING" # ALIGNED, PARTIAL, BLOCKED, PENDING
    rainfall_1h_mm: Optional[float] = None
    rainfall_3h_mm: Optional[float] = None
    rainfall_6h_mm: Optional[float] = None
    rainfall_24h_mm: Optional[float] = None
    antecedent_rainfall_index: Optional[float] = None
    elevation_m: Optional[float] = None
    slope_degrees: Optional[float] = None
    relative_depression_m: Optional[float] = None
    nearest_drain_distance_m: Optional[float] = None
    corridor_name: Optional[str] = None

    review_queue_status: str = "RESOLVED" # IN_QUEUE, UNDER_INVESTIGATION, RESOLVED
    ingested_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

    def to_dict(self) -> Dict[str, Any]:
        data = self.model_dump()
        data["waterlogging_status"] = self.waterlogging_status.value
        data["verification_status"] = self.verification_status.value
        data["evidence_type"] = self.evidence_type.value
        data["provenance_classification"] = self.provenance_classification.value
        data["observation_timestamp"] = self.observation_timestamp.isoformat()
        if self.reported_at:
            data["reported_at"] = self.reported_at.isoformat()
        if self.event_start_time:
            data["event_start_time"] = self.event_start_time.isoformat()
        if self.event_end_time:
            data["event_end_time"] = self.event_end_time.isoformat()
        if self.publication_timestamp:
            data["publication_timestamp"] = self.publication_timestamp.isoformat()
        if self.verified_at:
            data["verified_at"] = self.verified_at.isoformat()
        data["ingested_at"] = self.ingested_at.isoformat()
        return data


class GroundTruthSample(BaseModel):
    """Structured training sample candidate (legacy Phase 25A compatibility)."""
    event_id: str
    latitude: float
    longitude: float
    timestamp_utc: str
    label: WaterloggingLabel
    depth_ft: Optional[float] = None
    depth_category: Optional[str] = None
    is_measured_depth: bool = False
    evidence_source: str = ""
    source_confidence: float = 1.0
    spatial_uncertainty_m: float = 50.0

    @classmethod
    def from_report(cls, report_dict: Dict[str, Any]) -> "GroundTruthSample":
        """Construct ground-truth sample from a PostGIS report record."""
        v_status = report_dict.get("verification_status", "UNVERIFIED")
        if v_status == "VERIFIED":
            label = WaterloggingLabel.VERIFIED_WATERLOGGING
            evidence = "MUNICIPAL_VERIFIED_REPORT"
            conf = 0.95
        elif v_status == "DISPUTED":
            label = WaterloggingLabel.DISPUTED
            evidence = "MUNICIPAL_DISPUTED_REPORT"
            conf = 0.50
        elif v_status == "REJECTED":
            # Rejection is NOT proof that road is dry; it is an unverified/rejected claim
            label = WaterloggingLabel.UNKNOWN
            evidence = "MUNICIPAL_REJECTED_REPORT"
            conf = 0.0
        elif v_status == "UNVERIFIED":
            label = WaterloggingLabel.REPORTED_UNVERIFIED
            evidence = "CITIZEN_UNVERIFIED_REPORT"
            conf = 0.40
        else:
            label = WaterloggingLabel.UNKNOWN
            evidence = "UNCLASSIFIED"
            conf = 0.0

        depth = report_dict.get("depth_ft")
        level = report_dict.get("citizen_depth_level")

        return cls(
            event_id=str(report_dict.get("id")),
            latitude=float(report_dict["latitude"]),
            longitude=float(report_dict["longitude"]),
            timestamp_utc=str(report_dict.get("created_at", "")),
            label=label,
            depth_ft=float(depth) if depth is not None else None,
            depth_category=str(level) if level else None,
            is_measured_depth=bool(depth is not None and depth > 0),
            evidence_source=evidence,
            source_confidence=conf
        )
