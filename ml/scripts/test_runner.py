#!/usr/bin/env python3
"""JalMarg ML Test Suite Runner

Configures sys.path and executes all pytest test cases in ml/tests.
"""

import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
ML_ROOT = REPO_ROOT / "ml"
SRC_DIR = ML_ROOT / "src"
VENV_PACKAGES = ML_ROOT / ".venv" / "Lib" / "site-packages"

if str(VENV_PACKAGES) not in sys.path and VENV_PACKAGES.exists():
    sys.path.insert(0, str(VENV_PACKAGES))
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

import pytest

if __name__ == "__main__":
    test_dir = str(ML_ROOT / "tests")
    exit_code = pytest.main(["-v", test_dir])
    sys.exit(exit_code)
