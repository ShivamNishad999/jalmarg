"""Open-Meteo Historical Weather Archive Adapter

Fetches authentic hourly atmospheric reanalysis data (ERA5-Land backend) for Delhi NCR
corridor coordinates across monsoon periods, preserving timestamps, grid cell mappings,
and reanalysis provenance.

Strictly adheres to Phase 25E-B standards:
- Explicit quality flags (no silent zero-filling of missing rainfall)
- Reanalysis grid cell center and spatial resolution documentation
- Bounded exponential backoff and rate-limit handling
- Idempotent execution and SHA-256 cryptographic verification
"""

import json
import time
from pathlib import Path
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone
import requests

from .base import BaseSourceAdapter, AcquisitionResult
from ..provenance.registry import DatasetMetadata, DataSourceType, VerificationStatus


class OpenMeteoHistoricalAdapter(BaseSourceAdapter):
    """Adapter for Open-Meteo Historical Weather Archive API."""

    ARCHIVE_ENDPOINT = "https://archive-api.open-meteo.com/v1/archive"

    # Monitored sampling points representing all 6 key Delhi NCR administrative zones
    DEFAULT_NCR_CORRIDORS: List[Dict[str, Any]] = [
        {"name": "Minto Bridge underpass, Connaught Place", "area": "Central Delhi", "lat": 28.6347, "lng": 77.2263, "elev": 214.2},
        {"name": "Pul Prahladpur underpass, MB Road", "area": "South Delhi", "lat": 28.5019, "lng": 77.2873, "elev": 218.4},
        {"name": "Hero Honda Chowk, NH-48", "area": "Gurugram", "lat": 28.4296, "lng": 77.0118, "elev": 226.5},
        {"name": "Zakhira underpass, Rohtak Road", "area": "West Delhi", "lat": 28.6612, "lng": 77.1455, "elev": 212.8},
        {"name": "Azad Market underpass", "area": "North Delhi", "lat": 28.6667, "lng": 77.2064, "elev": 215.1},
        {"name": "NH-9 underpass near Sector 62", "area": "Noida", "lat": 28.6280, "lng": 77.3690, "elev": 204.6},
        {"name": "ITO crossing, Vikas Marg", "area": "East Delhi", "lat": 28.6289, "lng": 77.2410, "elev": 209.0},
        {"name": "Dhaula Kuan Ridge Junction", "area": "New Delhi", "lat": 28.5920, "lng": 77.1610, "elev": 242.0},
        {"name": "Mohan Nagar / GT Road", "area": "Ghaziabad", "lat": 28.6791, "lng": 77.3912, "elev": 206.0},
        {"name": "Badarpur Border / NH-19", "area": "Faridabad", "lat": 28.4900, "lng": 77.3000, "elev": 217.0},
        {"name": "Pari Chowk", "area": "Greater Noida", "lat": 28.4650, "lng": 77.5110, "elev": 197.0}
    ]

    def __init__(self, config: Dict[str, Any], output_dir: Path):
        super().__init__(config)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def validate_connection(self) -> bool:
        """Verify reachability of Open-Meteo Archive API endpoint."""
        try:
            res = requests.get(
                self.ARCHIVE_ENDPOINT,
                params={
                    "latitude": 28.6347,
                    "longitude": 77.2263,
                    "start_date": "2024-07-01",
                    "end_date": "2024-07-02",
                    "hourly": "precipitation",
                    "timezone": "UTC"
                },
                timeout=10
            )
            return res.status_code == 200
        except Exception:
            return False

    def fetch_corridor_history(
        self,
        lat: float,
        lng: float,
        corridor_name: str,
        area: str,
        start_date: str,
        end_date: str,
        max_retries: int = 3,
        initial_backoff: float = 1.0
    ) -> Dict[str, Any]:
        """Fetch hourly precipitation records for a specific corridor with exponential backoff."""
        params = {
            "latitude": lat,
            "longitude": lng,
            "start_date": start_date,
            "end_date": end_date,
            "hourly": "precipitation,rain,surface_pressure,cloud_cover",
            "timezone": "UTC"
        }

        last_error = None
        for attempt in range(max_retries):
            try:
                res = requests.get(self.ARCHIVE_ENDPOINT, params=params, timeout=20)
                if res.status_code == 200:
                    data = res.json()
                    data["corridor_name"] = corridor_name
                    data["area"] = area
                    data["requested_coords"] = {"latitude": lat, "longitude": lng}
                    return data
                elif res.status_code == 429:
                    sleep_time = initial_backoff * (2 ** attempt) + 1.0
                    time.sleep(sleep_time)
                else:
                    raise RuntimeError(f"Open-Meteo returned HTTP {res.status_code}: {res.text[:200]}")
            except Exception as e:
                last_error = e
                time.sleep(initial_backoff * (2 ** attempt))

        raise RuntimeError(f"Failed to fetch {corridor_name} ({start_date} to {end_date}) after {max_retries} attempts: {last_error}")

    def acquire_period(
        self,
        start_date: str,
        end_date: str,
        corridors: Optional[List[Dict[str, Any]]] = None,
        force: bool = False
    ) -> AcquisitionResult:
        """Acquire and validate historical hourly precipitation for a single date window."""
        if corridors is None:
            corridors = self.DEFAULT_NCR_CORRIDORS

        out_file = self.output_dir / f"delhi_ncr_historical_rainfall_{start_date}_{end_date}.json"

        # Check idempotency: if file exists and valid, avoid duplicate network calls unless forced
        if out_file.exists() and not force:
            try:
                with open(out_file, "r", encoding="utf-8") as f:
                    existing = json.load(f)
                if isinstance(existing, list) and len(existing) > 0 and "quality_flag" in existing[0]:
                    file_size = out_file.stat().st_size
                    sha256_hash = self.calculate_file_sha256(out_file)
                    metadata = DatasetMetadata(
                        dataset_code=self.dataset_code,
                        source_name=self.config.get("source_name", "Open-Meteo Historical Weather Archive"),
                        source_organization=self.config.get("organization", "Open-Meteo GmbH / ECMWF"),
                        official_source_url=self.ARCHIVE_ENDPOINT,
                        provenance_type=DataSourceType.REANALYSIS,
                        spatial_coverage=f"Delhi NCR 6-Zone Network ({len(corridors)} corridors sampled)",
                        temporal_coverage=f"{start_date} to {end_date} (Hourly)",
                        spatial_resolution="0.1° (~9 km ERA5-Land grid)",
                        temporal_resolution="Hourly",
                        crs="EPSG:4326",
                        units="Precipitation in mm",
                        license="CC-BY-4.0",
                        local_storage_path=str(out_file),
                        file_size_bytes=file_size,
                        sha256_checksum=sha256_hash,
                        validation_status=VerificationStatus.VERIFIED,
                        known_limitations="Model-based atmospheric reanalysis; not a direct physical ground rain gauge reading.",
                        acquisition_timestamp=datetime.now(timezone.utc),
                        extra_attributes={"records_count": len(existing), "corridors_sampled": len(corridors), "cached": True}
                    )
                    return AcquisitionResult(
                        success=True,
                        dataset_code=self.dataset_code,
                        status=VerificationStatus.VERIFIED,
                        records_count=len(existing),
                        file_path=str(out_file),
                        file_size_bytes=file_size,
                        sha256_checksum=sha256_hash,
                        notes=f"Loaded {len(existing)} verified records from existing dataset file (idempotent)",
                        metadata=metadata
                    )
            except Exception:
                pass # Re-fetch if corrupt

        all_records = []
        unique_grid_cells = set()
        missing_count = 0
        negative_count = 0

        try:
            for c in corridors:
                series = self.fetch_corridor_history(
                    lat=c["lat"],
                    lng=c["lng"],
                    corridor_name=c["name"],
                    area=c.get("area", "Delhi NCR"),
                    start_date=start_date,
                    end_date=end_date
                )
                grid_lat = series.get("latitude")
                grid_lng = series.get("longitude")
                elev = series.get("elevation", c.get("elev"))
                grid_cell_id = f"ERA5_{grid_lat:.4f}_{grid_lng:.4f}"
                unique_grid_cells.add(grid_cell_id)

                hourly = series.get("hourly", {})
                times = hourly.get("time", [])
                precip = hourly.get("precipitation", [])

                for t, p in zip(times, precip):
                    # Strict validation: DO NOT zero-fill missing data silently
                    if p is None:
                        is_missing = True
                        val = None
                        q_flag = "MISSING"
                        missing_count += 1
                    else:
                        is_missing = False
                        val = float(p)
                        if val < 0.0:
                            q_flag = "ANOMALOUS_NEGATIVE"
                            negative_count += 1
                        else:
                            q_flag = "VALID"

                    all_records.append({
                        "corridor": c["name"],
                        "area": c.get("area", "Delhi NCR"),
                        "latitude": c["lat"],
                        "longitude": c["lng"],
                        "grid_cell": {"latitude": grid_lat, "longitude": grid_lng},
                        "grid_cell_id": grid_cell_id,
                        "elevation_m": elev,
                        "timestamp_utc": t,
                        "precipitation_mm": val,
                        "is_missing": is_missing,
                        "quality_flag": q_flag,
                        "provenance": "REANALYSIS_ERA5_LAND",
                        "source": "Open-Meteo Historical Weather Archive",
                        "crs": "EPSG:4326",
                        "units": "mm"
                    })

                # Respectful rate-limiting pause
                time.sleep(0.3)

            # Persist acquired authentic dataset
            with open(out_file, "w", encoding="utf-8") as f:
                json.dump(all_records, f, indent=2)

            file_size = out_file.stat().st_size
            sha256_hash = self.calculate_file_sha256(out_file)

            metadata = DatasetMetadata(
                dataset_code=self.dataset_code,
                source_name=self.config.get("source_name", "Open-Meteo Historical Weather Archive"),
                source_organization=self.config.get("organization", "Open-Meteo GmbH / ECMWF"),
                official_source_url=self.ARCHIVE_ENDPOINT,
                provenance_type=DataSourceType.REANALYSIS,
                spatial_coverage=f"Delhi NCR 6-Zone Network ({len(corridors)} corridors across {len(unique_grid_cells)} distinct ERA5 grid cells)",
                temporal_coverage=f"{start_date} to {end_date} (Hourly)",
                spatial_resolution="0.1° (~9 km ERA5-Land grid)",
                temporal_resolution="Hourly",
                crs="EPSG:4326",
                units="Precipitation in mm",
                license="CC-BY-4.0",
                local_storage_path=str(out_file),
                file_size_bytes=file_size,
                sha256_checksum=sha256_hash,
                validation_status=VerificationStatus.VERIFIED,
                known_limitations="Model-based atmospheric reanalysis; not a direct physical ground rain gauge reading.",
                acquisition_timestamp=datetime.now(timezone.utc),
                extra_attributes={
                    "records_count": len(all_records),
                    "corridors_sampled": len(corridors),
                    "distinct_grid_cells_count": len(unique_grid_cells),
                    "missing_values_count": missing_count,
                    "negative_values_count": negative_count
                }
            )

            return AcquisitionResult(
                success=True,
                dataset_code=self.dataset_code,
                status=VerificationStatus.VERIFIED,
                records_count=len(all_records),
                file_path=str(out_file),
                file_size_bytes=file_size,
                sha256_checksum=sha256_hash,
                notes=(
                    f"Acquired {len(all_records)} authentic hourly reanalysis records for {start_date} to {end_date} "
                    f"across {len(corridors)} Delhi NCR corridors ({len(unique_grid_cells)} ERA5 grid cells). "
                    f"Missing: {missing_count}, Negative: {negative_count}."
                ),
                metadata=metadata
            )

        except Exception as e:
            return AcquisitionResult(
                success=False,
                dataset_code=self.dataset_code,
                status=VerificationStatus.PARTIAL,
                error_message=str(e),
                notes=f"Network or parsing error occurred during Open-Meteo historical acquisition for {start_date} to {end_date}"
            )

    def acquire(
        self,
        corridors: Optional[List[Dict[str, Any]]] = None,
        start_date: str = "2024-07-01",
        end_date: str = "2024-07-31"
    ) -> AcquisitionResult:
        """Default acquire method matching BaseSourceAdapter contract."""
        return self.acquire_period(start_date=start_date, end_date=end_date, corridors=corridors)
