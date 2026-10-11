"""Topographic & Terrain Feature Engineering

Computes elevation, local slope, relative elevation, and topographic depression indices
from DEM elevation grids while ensuring DEM depressions are never misrepresented as measured flood depths.
"""

import math
from typing import Dict, Any, List, Optional
import numpy as np


class TerrainFeatureCalculator:
    """Computes terrain features from elevation measurements and spatial neighborhoods."""

    def __init__(self, nominal_cell_size_m: float = 30.0):
        self.cell_size_m = nominal_cell_size_m

    def compute_slope_degrees(self, grid_3x3: np.ndarray) -> float:
        """Compute local slope in degrees using Horn's 3x3 finite-difference algorithm.

        grid_3x3 layout:
        [ [z_tl, z_tm, z_tr],
          [z_ml, z_mm, z_mr],
          [z_bl, z_bm, z_br] ]
        """
        if grid_3x3.shape != (3, 3):
            raise ValueError("Expected 3x3 numpy array for local slope computation.")

        # Check for NoData contamination
        if np.any(grid_3x3 < -100.0) or np.any(np.isnan(grid_3x3)):
            return 0.0

        dz_dx = ((grid_3x3[0, 2] + 2.0 * grid_3x3[1, 2] + grid_3x3[2, 2]) -
                 (grid_3x3[0, 0] + 2.0 * grid_3x3[1, 0] + grid_3x3[2, 0])) / (8.0 * self.cell_size_m)

        dz_dy = ((grid_3x3[2, 0] + 2.0 * grid_3x3[2, 1] + grid_3x3[2, 2]) -
                 (grid_3x3[0, 0] + 2.0 * grid_3x3[0, 1] + grid_3x3[0, 2])) / (8.0 * self.cell_size_m)

        slope_rad = math.atan(math.sqrt(dz_dx**2 + dz_dy**2))
        return math.degrees(slope_rad)

    def compute_relative_elevation(self, target_elevation: float, neighborhood: List[float]) -> float:
        """Compute relative elevation: target elevation minus mean surrounding elevation.

        Negative values indicate a local low point / depression relative to surroundings.
        """
        valid_neighbors = [z for z in neighborhood if z > 0 and not math.isnan(z)]
        if not valid_neighbors:
            return 0.0
        mean_elev = sum(valid_neighbors) / len(valid_neighbors)
        return target_elevation - mean_elev

    def compute_depression_index(self, relative_elevation_m: float, slope_deg: float) -> float:
        """Compute a bounded topographic depression index [0.0 to 1.0].

        Higher values indicate a bowl-shaped low point with steep or inward surrounding relief.
        Note: This is an indicator of geometric accumulation potential, NOT measured flood depth.
        """
        if relative_elevation_m >= 0.0:
            return 0.0 # Elevated terrain or flat summit
        
        # Invert relative elevation: deeper depression gives higher index
        depth_penalty = min(1.0, abs(relative_elevation_m) / 10.0) # Saturation at 10m depression
        slope_damping = max(0.2, 1.0 - min(1.0, slope_deg / 45.0)) # Very steep slopes drain faster
        return float(depth_penalty * slope_damping)
