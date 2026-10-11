"""Database Connection & Session Factory for JalMarg PostGIS Cluster

Connects strictly to the dedicated JalMarg cluster on port 5433 (jalmarg_twin).
Protected CarSync database on port 5432 is never contacted.
"""

import os
from sqlalchemy import create_engine
from sqlalchemy.engine import Engine


def get_db_engine() -> Engine:
    """Create SQLAlchemy engine targeting PostGIS cluster on port 5433."""
    db_url = os.getenv("JALMARG_DB_URL")
    if not db_url:
        host = os.getenv("JALMARG_DB_HOST", "localhost")
        port = int(os.getenv("JALMARG_DB_PORT", "5433")) # STRICT JALMARG PORT 5433
        dbname = os.getenv("JALMARG_DB_NAME", "jalmarg_twin")
        user = os.getenv("JALMARG_DB_USER", "postgres")
        password = os.getenv("JALMARG_DB_PASSWORD", "postgres")

        # Explicit safety check: refuse connection if port 5432 is accidentally specified
        if port == 5432 or dbname == "carsync":
            raise ValueError(
                "CRITICAL SAFETY VIOLATION: Port 5432 / carsync database is strictly protected "
                "and must NEVER be connected to by JalMarg ML pipelines."
            )

        db_url = f"postgresql+psycopg://{user}:{password}@{host}:{port}/{dbname}"

    return create_engine(db_url, pool_pre_ping=True, echo=False)
