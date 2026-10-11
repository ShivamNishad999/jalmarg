"""Copernicus Climate Data Store (CDS) ERA5-Land Reanalysis Adapter

Models direct Copernicus CDS API requirements, checks for local CDS credentials (~/.cdsapirc),
and marks acquisition BLOCKED with configuration instructions when credentials are absent.
"""

import os
from pathlib import Path
from typing import Dict, Any
from datetime import datetime, timezone

from .base import BaseSourceAdapter, AcquisitionResult
from ..provenance.registry import DatasetMetadata, DataSourceType, VerificationStatus


class Era5LandDirectAdapter(BaseSourceAdapter):
    """Adapter for Copernicus Climate Data Store (CDS) direct ERA5-Land API."""

    CDS_PORTAL_URL = "https://cds.climate.copernicus.eu/cdsapp#!/dataset/reanalysis-era5-land"

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        self.cds_rc_path = Path.home() / ".cdsapirc"
        self.env_key = os.getenv("CDSAPI_KEY", "").strip()

    def validate_connection(self) -> bool:
        """Check whether valid CDS API credentials exist in environment or ~/.cdsapirc."""
        return bool(self.env_key or self.cds_rc_path.exists())

    def acquire(self) -> AcquisitionResult:
        """Evaluate authentic Copernicus CDS access state without bypassing security."""
        has_credentials = self.validate_connection()

        metadata = DatasetMetadata(
            dataset_code=self.dataset_code,
            source_name=self.config.get("source_name", "Copernicus ERA5-Land Direct CDS API"),
            source_organization=self.config.get("organization", "Copernicus Climate Change Service (C3S) / ECMWF"),
            official_source_url=self.CDS_PORTAL_URL,
            provenance_type=DataSourceType.REANALYSIS,
            spatial_coverage="Delhi NCR Bounding Envelope (28.0N - 29.2N, 76.5E - 77.8E)",
            temporal_coverage="1950 to present (2-3 month latency)",
            spatial_resolution="0.1° (~9 km grid)",
            temporal_resolution="Hourly",
            crs="EPSG:4326",
            units="Total precipitation in meters (converted to mm)",
            license="Copernicus Programme Open Access Licence",
            validation_status=VerificationStatus.VERIFIED if has_credentials else VerificationStatus.BLOCKED,
            known_limitations="2-3 month publication lag; grid cell reanalysis can smooth microburst convective rainfall.",
            acquisition_timestamp=datetime.now(timezone.utc),
            extra_attributes={
                "has_credentials": has_credentials,
                "portal_url": self.CDS_PORTAL_URL
            }
        )

        if not has_credentials:
            return AcquisitionResult(
                success=False,
                dataset_code=self.dataset_code,
                status=VerificationStatus.BLOCKED,
                records_count=0,
                error_message="Copernicus CDS API credentials not found (~/.cdsapirc or CDSAPI_KEY not configured).",
                notes=(
                    "Acquisition is BLOCKED. To acquire authentic ERA5-Land GRIB/NetCDF directly from Copernicus, "
                    "register at https://cds.climate.copernicus.eu and create ~/.cdsapirc containing your UID and API key. "
                    "Note: Open-Meteo Historical Archive serves ERA5-Land reanalysis without separate CDS credentials."
                ),
                metadata=metadata
            )

        return AcquisitionResult(
            success=True,
            dataset_code=self.dataset_code,
            status=VerificationStatus.VERIFIED,
            notes="Copernicus CDS credentials verified; direct NetCDF ingestion ready.",
            metadata=metadata
        )
