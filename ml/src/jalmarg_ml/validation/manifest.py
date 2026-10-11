"""Acquisition Manifest Generator & Audit Ledger

Tracks and persists machine-readable manifests of all evaluated, acquired, and blocked datasets.
"""

import json
from pathlib import Path
from typing import Dict, Any, List
from datetime import datetime, timezone
from pydantic import BaseModel, Field

from ..provenance.registry import DatasetMetadata, VerificationStatus


class DatasetManifestEntry(BaseModel):
    """Manifest record for an individual dataset."""
    dataset_code: str
    source_name: str
    provenance_type: str
    verification_status: VerificationStatus
    file_path: str = ""
    file_size_bytes: int = 0
    sha256_checksum: str = ""
    records_count: int = 0
    last_verified: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    notes: str = ""


class AcquisitionManifest(BaseModel):
    """Complete collection manifest."""
    manifest_version: str = "1.0.0"
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    total_datasets: int = 0
    verified_datasets_count: int = 0
    blocked_datasets_count: int = 0
    entries: List[DatasetManifestEntry] = Field(default_factory=list)

    def save(self, output_path: Path):
        output_path.parent.mkdir(parents=True, exist_ok=True)
        data = self.model_dump()
        data["created_at"] = self.created_at.isoformat()
        for e in data["entries"]:
            e["verification_status"] = e["verification_status"].value if hasattr(e["verification_status"], "value") else str(e["verification_status"])
            e["last_verified"] = e["last_verified"].isoformat() if hasattr(e["last_verified"], "isoformat") else str(e["last_verified"])
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
