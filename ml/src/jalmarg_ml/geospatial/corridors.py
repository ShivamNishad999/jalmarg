"""Delhi NCR Corridor Matching & Geodesic Distance Utilities

Manages monitored waterlogging corridors and provides rigorous spatial distance
computations in meters between point coordinates.
"""

import math
from typing import Dict, Any, List, Optional, Tuple


def haversine_distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Compute great-circle distance between two GPS coordinates in meters."""
    r = 6371000.0  # Earth radius in meters
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (math.sin(delta_phi / 2.0) ** 2 +
         math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2)
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r * c


class CorridorRegistry:
    """Registry of documented historical waterlogging corridors in Delhi NCR."""

    DEFAULT_CORRIDORS = [
        {"id": 1, "name": "Minto Bridge underpass, Connaught Place", "area": "Central Delhi", "lat": 28.6347, "lng": 77.2263, "base_elev": 214.2, "category": "UNDERPASS"},
        {"id": 2, "name": "Pul Prahladpur underpass, MB Road", "area": "South Delhi", "lat": 28.5019, "lng": 77.2873, "base_elev": 218.4, "category": "UNDERPASS"},
        {"id": 3, "name": "Hero Honda Chowk, NH-48", "area": "Gurugram", "lat": 28.4296, "lng": 77.0118, "base_elev": 226.5, "category": "HIGHWAY_CHOKE"},
        {"id": 4, "name": "Zakhira underpass, Rohtak Road", "area": "West Delhi", "lat": 28.6612, "lng": 77.1455, "base_elev": 212.8, "category": "UNDERPASS"},
        {"id": 5, "name": "Azad Market underpass", "area": "North Delhi", "lat": 28.6667, "lng": 77.2064, "base_elev": 215.1, "category": "UNDERPASS"},
        {"id": 6, "name": "NH-9 underpass near Sector 62", "area": "Noida", "lat": 28.6280, "lng": 77.3690, "base_elev": 204.6, "category": "UNDERPASS"},
        {"id": 7, "name": "ITO crossing, Vikas Marg", "area": "East Delhi", "lat": 28.6289, "lng": 77.2410, "base_elev": 209.0, "category": "INTERSECTION"},
        {"id": 8, "name": "Dhaula Kuan Ridge Junction", "area": "New Delhi", "lat": 28.5920, "lng": 77.1610, "base_elev": 242.0, "category": "RIDGE_BENCHMARK"},
        {"id": 9, "name": "Mohan Nagar / GT Road", "area": "Ghaziabad", "lat": 28.6791, "lng": 77.3912, "base_elev": 206.0, "category": "HIGHWAY_CHOKE"},
        {"id": 10, "name": "Badarpur Border / NH-19", "area": "Faridabad", "lat": 28.4900, "lng": 77.3000, "base_elev": 217.0, "category": "HIGHWAY_CHOKE"},
        {"id": 11, "name": "Pari Chowk", "area": "Greater Noida", "lat": 28.4650, "lng": 77.5110, "base_elev": 197.0, "category": "INTERSECTION"}
    ]

    def __init__(self, corridors: Optional[List[Dict[str, Any]]] = None):
        self.corridors = corridors or self.DEFAULT_CORRIDORS

    def find_nearest_corridor(self, lat: float, lng: float, max_dist_m: float = 200.0) -> Optional[Dict[str, Any]]:
        """Find the nearest monitored corridor within a maximum threshold in meters."""
        best_match = None
        min_dist = float("inf")

        for c in self.corridors:
            dist = haversine_distance_m(lat, lng, c["lat"], c["lng"])
            if dist <= max_dist_m and dist < min_dist:
                min_dist = dist
                best_match = {**c, "distance_to_corridor_m": round(dist, 1)}

        return best_match
