"""JalMarg ML Environment Bootstrap

Ensures that the dedicated virtual environment packages and package source
directories are properly attached to sys.path across all execution contexts.
"""

import sys
from pathlib import Path

# Locate workspace root and venv site-packages
CURRENT_FILE = Path(__file__).resolve()
SRC_DIR = CURRENT_FILE.parents[1]
ML_ROOT = CURRENT_FILE.parents[2]
VENV_SITE_PACKAGES = ML_ROOT / ".venv" / "Lib" / "site-packages"

if str(VENV_SITE_PACKAGES) not in sys.path and VENV_SITE_PACKAGES.exists():
    sys.path.insert(0, str(VENV_SITE_PACKAGES))

if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))
