import sys
print("Step 1: sys imported", flush=True)
sys.path.insert(0, "D:/jalmarg/jalmarg/ml/.venv/Lib/site-packages")
print("Step 2: sys.path modified", flush=True)
import yaml
print("Step 3: yaml imported", flush=True)
import requests
print("Step 4: requests imported", flush=True)
import pydantic
print("Step 5: pydantic imported", flush=True)
import sqlalchemy
print("Step 6: sqlalchemy imported", flush=True)
import psycopg
print("Step 7: psycopg imported", flush=True)
import numpy
print("Step 8: numpy imported", flush=True)
import pandas
print("Step 9: pandas imported", flush=True)
import pytest
print("Step 10: pytest imported", flush=True)
print("ALL DONE!", flush=True)
