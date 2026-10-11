"""Spatiotemporal Feature Builder

Constructs unified machine learning training instances by joining temporal rainfall series,
static terrain attributes, and prior historical event densities strictly before timestamp T0.
"""

from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

from .rainfall_features import RainfallFeatureCalculator
from .terrain_features import TerrainFeatureAssembler
from ..geospatial.corridors import CorridorRegistry, haversine_distance_m


class FeatureBuilder:
    """Builds tabular feature vectors for Delhi NCR points without temporal or spatial leakage."""

    def __init__(self):
        self.rainfall_calc = RainfallFeatureCalculator()
        self.terrain_assembler = TerrainFeatureAssembler()
        self.corridor_registry = CorridorRegistry()

    def build_sample_features(
        self,
        latitude: float,
        longitude: float,
        prediction_timestamp: datetime,
        rainfall_observations: List[Dict[str, Any]],
        elevation_m: float,
        neighborhood_elevations: Optional[List[float]] = None,
        slope_degrees: Optional[float] = None,
        historical_incidents: Optional[List[Dict[str, Any]]] = None,
        spatial_radius_m: float = 500.0
    ) -> Dict[str, Any]:
        """Assemble all features strictly available at or prior to prediction_timestamp."""
        if prediction_timestamp.tzinfo is None:
            prediction_timestamp = prediction_timestamp.replace(tzinfo=timezone.utc)

        # 1. Dynamic rainfall features up to T0
        rain_feats = self.rainfall_calc.compute_windowed_aggregates(
            observations=rainfall_observations,
            prediction_timestamp=prediction_timestamp
        )

        # 2. Topographic & terrain features
        terrain_feats = self.terrain_assembler.extract_features(
            elevation_m=elevation_m,
            neighborhood_elevations=neighborhood_elevations,
            slope_degrees=slope_degrees
        )

        # 3. Spatial corridor context
        nearest_corridor = self.corridor_registry.find_nearest_corridor(latitude, longitude, max_dist_m=1000.0)
        dist_to_corridor_m = nearest_corridor["distance_to_corridor_m"] if nearest_corridor else 1000.0
        is_known_hotspot = 1 if (nearest_corridor and nearest_corridor["category"] == "UNDERPASS") else 0

        # 4. Prior historical event density (strictly events created before T0 within spatial radius)
        prior_event_count = 0
        if historical_incidents:
            for inc in historical_incidents:
                created_str = inc.get("created_at")
                if not created_str:
                    continue
                try:
                    inc_ts = datetime.fromisoformat(created_str.replace("Z", "+00:00"))
                    if inc_ts.tzinfo is None:
                        inc_ts = inc_ts.replace(tzinfo=timezone.utc)

                    # LEAKAGE GUARD: Only count events strictly before prediction timestamp
                    if inc_ts < prediction_timestamp:
                        inc_lat = inc.get("latitude")
                        inc_lng = inc.get("longitude")
                        if inc_lat is not None and inc_lng is not None:
                            d = haversine_distance_m(latitude, longitude, inc_lat, inc_lng)
                            if d <= spatial_radius_m:
                                prior_event_count += 1
                except Exception:
                    continue

        return {
            "latitude": latitude,
            "longitude": longitude,
            "prediction_timestamp_utc": prediction_timestamp.isoformat(),
            **rain_feats,
            **terrain_feats,
            "distance_to_corridor_m": dist_to_corridor_m,
            "is_known_hotspot_corridor": is_known_hotspot,
            "prior_verified_incidents_500m": prior_event_count,
            "temporal_completeness_flag": 1 if rain_feats["observations_count_24h"] >= 20 else 0
        }
