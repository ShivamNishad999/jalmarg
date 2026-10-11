"""Tests for authentic dataset acquisition adapters"""

import pytest
from pathlib import Path

from jalmarg_ml.acquisition.copernicus_dem import CopernicusDemAdapter
from jalmarg_ml.acquisition.imd_rainfall import ImdRainfallAdapter
from jalmarg_ml.acquisition.era5_land import Era5LandDirectAdapter
from jalmarg_ml.provenance.registry import VerificationStatus


def test_copernicus_dem_asset_validation():
    project_root = Path(__file__).resolve().parents[2]
    config = {
        "dataset_code": "COP_DEM_GLO30",
        "local_path": "backend/data/dem/Copernicus_DSM_COG_10_N28_00_E077_00_DEM.tif"
    }
    adapter = CopernicusDemAdapter(config, project_root)
    assert adapter.validate_connection() is True

    result = adapter.acquire()
    assert result.success is True
    assert result.status == VerificationStatus.VERIFIED
    assert result.records_count == 3600 * 3600
    assert result.file_size_bytes == 41680243
    assert len(result.sha256_checksum) == 64
    assert result.metadata is not None
    assert result.metadata.crs == "EPSG:4326 (EGM2008 vertical datum)"


def test_imd_rainfall_fails_closed_when_unauthenticated(monkeypatch):
    monkeypatch.delenv("IMD_API_KEY", raising=False)
    monkeypatch.delenv("IMD_CLIENT_ID", raising=False)

    config = {"dataset_code": "IMD_OBSERVED_RAINFALL"}
    adapter = ImdRainfallAdapter(config)
    assert adapter.validate_connection() is False

    result = adapter.acquire()
    assert result.success is False
    assert result.status == VerificationStatus.BLOCKED
    assert "IMD institutional API credentials missing" in result.error_message
    assert result.metadata.validation_status == VerificationStatus.BLOCKED


def test_era5_land_direct_fails_closed_when_unauthenticated(monkeypatch, tmp_path):
    monkeypatch.delenv("CDSAPI_KEY", raising=False)
    # Ensure fake home without ~/.cdsapirc
    fake_home = tmp_path / "fake_home"
    fake_home.mkdir()
    monkeypatch.setattr(Path, "home", lambda: fake_home)

    config = {"dataset_code": "ERA5_LAND_CDS"}
    adapter = Era5LandDirectAdapter(config)
    assert adapter.validate_connection() is False

    result = adapter.acquire()
    assert result.success is False
    assert result.status == VerificationStatus.BLOCKED
    assert "Copernicus CDS API credentials not found" in result.error_message
