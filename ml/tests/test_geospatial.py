"""Tests for geospatial terrain algorithms and corridor registry"""

import pytest
import numpy as np

from jalmarg_ml.geospatial.terrain import TerrainFeatureCalculator
from jalmarg_ml.geospatial.corridors import CorridorRegistry, haversine_distance_m


def test_terrain_slope_flat_surface():
    calc = TerrainFeatureCalculator(nominal_cell_size_m=30.0)
    flat_grid = np.full((3, 3), 210.0)
    slope = calc.compute_slope_degrees(flat_grid)
    assert abs(slope) < 1e-4


def test_terrain_slope_steep_plane():
    calc = TerrainFeatureCalculator(nominal_cell_size_m=30.0)
    # 30m vertical rise over 60m horizontal distance = 0.5 slope -> ~26.56 degrees
    steep_grid = np.array([
        [200.0, 200.0, 230.0],
        [200.0, 200.0, 230.0],
        [200.0, 200.0, 230.0]
    ])
    slope = calc.compute_slope_degrees(steep_grid)
    assert slope > 15.0


def test_relative_elevation_and_depression_index():
    calc = TerrainFeatureCalculator()
    target_elev = 210.0
    # Surrounding ridge at 218m -> target is in an 8m depression
    neighborhood = [218.0, 219.0, 217.5, 218.5]
    rel_elev = calc.compute_relative_elevation(target_elev, neighborhood)
    assert rel_elev < -5.0

    depression_idx = calc.compute_depression_index(rel_elev, slope_deg=2.0)
    assert depression_idx > 0.5 # Substantial bowl depression


def test_corridor_matching_and_distance():
    registry = CorridorRegistry()
    # Test coordinates near Minto Bridge (28.6347, 77.2263)
    match = registry.find_nearest_corridor(28.6349, 77.2265, max_dist_m=100.0)
    assert match is not None
    assert "Minto Bridge" in match["name"]
    assert match["distance_to_corridor_m"] < 50.0

    # Test coordinate far away (Dhaula Kuan vs Minto Bridge > 5km)
    dist = haversine_distance_m(28.6347, 77.2263, 28.5920, 77.1610)
    assert dist > 5000.0
