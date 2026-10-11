"""Base Source Adapter Interface & Checksum Utilities

Provides abstract contracts and forensic hashing tools for all authentic dataset adapters.
"""

import hashlib
import time
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Optional, Dict, Any
from pydantic import BaseModel

from ..provenance.registry import DatasetMetadata, VerificationStatus


class AcquisitionResult(BaseModel):
    """Result of an acquisition execution."""
    success: bool
    dataset_code: str
    status: VerificationStatus
    records_count: int = 0
    file_path: Optional[str] = None
    file_size_bytes: Optional[int] = None
    sha256_checksum: Optional[str] = None
    http_status_code: Optional[int] = None
    error_message: Optional[str] = None
    notes: Optional[str] = None
    metadata: Optional[DatasetMetadata] = None


class BaseSourceAdapter(ABC):
    """Abstract base class for all authentic dataset adapters."""

    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.dataset_code = config.get("dataset_code", "UNKNOWN")

    @abstractmethod
    def acquire(self) -> AcquisitionResult:
        """Execute acquisition of authentic data."""
        pass

    @abstractmethod
    def validate_connection(self) -> bool:
        """Verify endpoint connectivity and access credentials."""
        pass

    @staticmethod
    def calculate_file_sha256(file_path: Path) -> str:
        """Compute SHA-256 cryptographic digest of a local dataset file."""
        if not file_path.exists():
            raise FileNotFoundError(f"Cannot compute checksum: file {file_path} does not exist.")
        sha256_hash = hashlib.sha256()
        with open(file_path, "rb") as f:
            for byte_block in iter(lambda: f.read(65536), b""):
                sha256_hash.update(byte_block)
        return sha256_hash.hexdigest()
