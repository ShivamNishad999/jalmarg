"""Dataset Provenance Registry & Metadata Models

Defines strongly-typed schemas for tracking data origins, physical vs reanalysis status,
licensing, cryptographic integrity, and operational availability.
"""

from enum import Enum
from typing import Optional, Dict, Any
from datetime import datetime, timezone
from pydantic import BaseModel, Field


class DataSourceType(str, Enum):
    """Provenance categories compatible with JalMarg core domain enums."""
    OBSERVED = "OBSERVED"             # Physical ground rain gauges, water level sensors, verified human inspection
    FORECAST = "FORECAST"             # Numerical Weather Prediction model output for future horizon
    HISTORICAL_PRIOR = "HISTORICAL_PRIOR" # Historical incident frequency, antecedent static patterns
    REANALYSIS = "REANALYSIS"         # Atmospheric model backcasts blending observations with physics (e.g. ERA5)
    SIMULATED = "SIMULATED"           # Digital Twin What-If scenario projections (NOT ground truth)
    UNKNOWN = "UNKNOWN"               # Unclassified or unverified provenance


class VerificationStatus(str, Enum):
    """Operational and verification readiness state."""
    VERIFIED = "VERIFIED"             # Authenticated, validated, and byte-level confirmed
    PARTIAL = "PARTIAL"               # Partial data acquired with documented coverage/gap limitations
    BLOCKED = "BLOCKED"               # Inaccessible due to authentication, licensing, or missing credentials
    NOT_TESTED = "NOT_TESTED"         # Registered but not yet audited or fetched


class DatasetMetadata(BaseModel):
    """Machine-readable dataset provenance record conforming to Section 7."""
    dataset_code: str
    source_name: str
    source_organization: str
    official_source_url: str
    provenance_type: DataSourceType
    spatial_coverage: str
    temporal_coverage: str
    spatial_resolution: str
    temporal_resolution: str
    crs: str
    units: str
    license: str
    local_storage_path: Optional[str] = None
    file_size_bytes: Optional[int] = None
    sha256_checksum: Optional[str] = None
    validation_status: VerificationStatus = VerificationStatus.NOT_TESTED
    known_limitations: str = ""
    acquisition_timestamp: Optional[datetime] = None
    publication_timestamp: Optional[datetime] = None
    extra_attributes: Dict[str, Any] = Field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        data = self.model_dump()
        # Convert enums to string
        data["provenance_type"] = self.provenance_type.value
        data["validation_status"] = self.validation_status.value
        if self.acquisition_timestamp:
            data["acquisition_timestamp"] = self.acquisition_timestamp.isoformat()
        if self.publication_timestamp:
            data["publication_timestamp"] = self.publication_timestamp.isoformat()
        return data
