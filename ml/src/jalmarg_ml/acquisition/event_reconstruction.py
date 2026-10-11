"""Historical Event Reconstruction, Georesolution & Spatiotemporal Deduplication Engine

Reconstructs authentic historical waterlogging events from official traffic advisories,
municipal vulnerable spot logs, and disaster situation reports.
Enforces coordinate resolution with uncertainty preservation, temporal distinction
(start vs end vs publication), and strict deduplication.
"""

from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime, timezone, timedelta
import math

from .historical_sources import DELHI_NCR_HOTSPOT_COORDINATES
from ..labeling.schema import (
    GroundTruthObservation,
    WaterloggingLabel,
    VerificationStatus,
    EvidenceType,
    ProvenanceClassification
)


def haversine_distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Computes great-circle distance in meters between two WGS-84 coordinate points."""
    r_earth = 6371000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (math.sin(delta_phi / 2.0) ** 2 +
         math.cos(phi1) * math.cos(phi2) * (math.sin(delta_lambda / 2.0) ** 2))
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r_earth * c


class HistoricalEventReconstructor:
    """Reconstructs, deduplicates, and validates historical waterlogging incidents."""

    SPATIAL_DEDUP_THRESHOLD_M = 300.0 # 300 meters
    TEMPORAL_DEDUP_WINDOW_HOURS = 6.0 # 6 hours

    def __init__(self):
        self.hotspots = DELHI_NCR_HOTSPOT_COORDINATES

    def resolve_location(self, hotspot_key: str, fallback_lat: Optional[float] = None, fallback_lng: Optional[float] = None) -> Tuple[float, float, str, str, float, bool]:
        """Resolves coordinates from authentic Delhi gazetteer or flags uncertainty."""
        if hotspot_key in self.hotspots:
            spot = self.hotspots[hotspot_key]
            return (
                spot["lat"],
                spot["lng"],
                spot["name"],
                spot["corridor"],
                spot["spatial_accuracy_m"],
                False # exact supported coordinate
            )
        elif fallback_lat is not None and fallback_lng is not None:
            # Approximate fallback coordinate with preserved uncertainty
            return (
                fallback_lat,
                fallback_lng,
                f"Approximate corridor vicinity ({hotspot_key})",
                "Unclassified Corridor",
                250.0, # Approximate spatial accuracy
                True # Flagged as approximate
            )
        else:
            raise ValueError(f"Unresolvable location key '{hotspot_key}' with no geographic coordinates.")

    def build_observation(
        self,
        observation_id: str,
        hotspot_key: str,
        event_start_utc: str,
        event_end_utc: Optional[str],
        publication_utc: str,
        source_code: str,
        source_reference: str,
        source_url: str,
        evidence_type: EvidenceType,
        provenance: ProvenanceClassification,
        measured_depth_m: Optional[float] = None,
        depth_method: Optional[str] = None,
        verification_notes: str = "",
        is_negative: bool = False,
        verification_authority: str = "DELHI_TRAFFIC_POLICE",
        verified_by: str = "OFFICIAL_POLICE_BULLETIN",
        fallback_lat: Optional[float] = None,
        fallback_lng: Optional[float] = None
    ) -> GroundTruthObservation:
        """Constructs an authentic observation with temporal distinction and spatial uncertainty."""
        lat, lng, desc, corridor, accuracy_m, is_approx = self.resolve_location(
            hotspot_key, fallback_lat, fallback_lng
        )

        t_start = datetime.fromisoformat(event_start_utc.replace("Z", "+00:00"))
        t_end = datetime.fromisoformat(event_end_utc.replace("Z", "+00:00")) if event_end_utc else None
        t_pub = datetime.fromisoformat(publication_utc.replace("Z", "+00:00"))

        if is_negative:
            label = WaterloggingLabel.VERIFIED_NO_WATERLOGGING
            v_status = VerificationStatus.VERIFIED
        else:
            label = WaterloggingLabel.VERIFIED_WATERLOGGING
            v_status = VerificationStatus.VERIFIED

        return GroundTruthObservation(
            observation_id=observation_id,
            latitude=lat,
            longitude=lng,
            location_description=desc,
            observation_timestamp=t_start,
            reported_at=t_start,
            event_start_time=t_start,
            event_end_time=t_end,
            publication_timestamp=t_pub,
            waterlogging_status=label,
            verification_status=v_status,
            verification_authority=verification_authority,
            verified_by_user=verified_by,
            verified_at=t_pub,
            source_organization=source_code,
            source_reference=source_reference,
            source_url=source_url,
            evidence_type=evidence_type,
            license_code="GOI_OPEN_DATA",
            permitted_usage="RESEARCH_AND_MUNICIPAL_OPERATIONS",
            spatial_accuracy_m=accuracy_m,
            is_approximate_location=is_approx,
            temporal_accuracy_minutes=30,
            measured_water_depth_m=measured_depth_m,
            depth_measurement_method=depth_method if measured_depth_m is not None else None,
            verification_notes=verification_notes,
            provenance_classification=provenance,
            corridor_name=corridor,
            alignment_status="PENDING",
            review_queue_status="RESOLVED" if not is_approx else "UNDER_INVESTIGATION"
        )

    def deduplicate_observations(
        self,
        observations: List[GroundTruthObservation]
    ) -> Tuple[List[GroundTruthObservation], List[Dict[str, Any]]]:
        """Deduplicates observations within spatial and temporal proximity thresholds.

        Returns: (deduplicated_observations, deduplication_audit_log)
        """
        deduped: List[GroundTruthObservation] = []
        audit_log: List[Dict[str, Any]] = []

        for obs in observations:
            duplicate_found = False
            for existing in deduped:
                # 1. Check spatial distance
                dist = haversine_distance_m(obs.latitude, obs.longitude, existing.latitude, existing.longitude)
                # 2. Check temporal difference between start timestamps
                time_diff = abs((obs.observation_timestamp - existing.observation_timestamp).total_seconds()) / 3600.0

                if dist <= self.SPATIAL_DEDUP_THRESHOLD_M and time_diff <= self.TEMPORAL_DEDUP_WINDOW_HOURS:
                    # Same physical event detected from overlapping reports
                    duplicate_found = True
                    # Append source reference to existing observation to preserve provenance
                    combined_notes = (
                        f"{existing.verification_notes} | Corroborating Report: "
                        f"[{obs.observation_id}] {obs.source_reference}"
                    )
                    existing.verification_notes = combined_notes
                    # If incoming observation has a measured depth and existing didn't, preserve it
                    if obs.measured_water_depth_m and not existing.measured_water_depth_m:
                        existing.measured_water_depth_m = obs.measured_water_depth_m
                        existing.depth_measurement_method = obs.depth_measurement_method

                    audit_log.append({
                        "retained_id": existing.observation_id,
                        "merged_id": obs.observation_id,
                        "distance_m": round(dist, 1),
                        "time_difference_hours": round(time_diff, 2),
                        "reason": "Within 300m spatial and 6h temporal threshold of existing verified event."
                    })
                    break

            if not duplicate_found:
                deduped.append(obs)

        return deduped, audit_log
