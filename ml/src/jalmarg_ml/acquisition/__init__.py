"""Dataset Acquisition Adapters for JalMarg ML Foundation"""

from .base import BaseSourceAdapter, AcquisitionResult
from .copernicus_dem import CopernicusDemAdapter
from .open_meteo_historical import OpenMeteoHistoricalAdapter
from .imd_rainfall import ImdRainfallAdapter
from .era5_land import Era5LandDirectAdapter
from .municipal_events import MunicipalEventsAdapter

__all__ = [
    "BaseSourceAdapter",
    "AcquisitionResult",
    "CopernicusDemAdapter",
    "OpenMeteoHistoricalAdapter",
    "ImdRainfallAdapter",
    "Era5LandDirectAdapter",
    "MunicipalEventsAdapter",
]
