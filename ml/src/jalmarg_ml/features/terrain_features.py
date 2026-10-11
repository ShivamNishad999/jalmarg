"""Terrain & Infrastructure Feature Assembler"""

from typing import Dict, Any, List, Optional
from ..geospatial.terrain import TerrainFeatureCalculator


class TerrainFeatureAssembler:
    """Combines elevation, slope, and topographic depression metrics into ML feature vectors."""

    def __init__(self, cell_size_m: float = 30.0):
        self.calculator = TerrainFeatureCalculator(nominal_cell_size_m=cell_size_m)

    def extract_features(
        self,
        elevation_m: float,
        neighborhood_elevations: Optional[List[float]] = None,
        slope_degrees: Optional[float] = None
    ) -> Dict[str, float]:
        """Compute structured terrain feature dictionary."""
        rel_elev = 0.0
        if neighborhood_elevations:
            rel_elev = self.calculator.compute_relative_elevation(elevation_m, neighborhood_elevations)

        slope = slope_degrees if slope_degrees is not None else 0.0
        depression_idx = self.calculator.compute_depression_index(rel_elev, slope)

        return {
            "elevation_m": round(elevation_m, 2),
            "relative_elevation_m": round(rel_elev, 2),
            "slope_degrees": round(slope, 2),
            "topographic_depression_index": round(depression_idx, 3)
        }
