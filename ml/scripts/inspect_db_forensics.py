import psycopg
import json

conn = psycopg.connect('postgresql://postgres:postgres@127.0.0.1:5433/jalmarg_twin')
with conn.cursor() as cur:
    cur.execute("""
        SELECT source_code, source_title, publisher, licensing_status, acquisition_status, official_url, sha256_checksum, records_count 
        FROM ml_historical_source_registry 
        ORDER BY id;
    """)
    print("\n--- SOURCES IN DB ---")
    for s in cur.fetchall():
        print(f"  [{s[0]}] {s[1]} | Publisher: {s[2]} | License: {s[3]} | Status: {s[4]} | URL: {s[5]} | Checksum: {s[6]} | Records: {s[7]}")

    cur.execute("""
        SELECT observation_id, corridor_name, location_description, waterlogging_status, verification_status, 
               measured_water_depth_m, depth_measurement_method, observation_timestamp, event_start_time, 
               source_organization, source_reference, source_url, evidence_type, evidence_file_reference,
               spatial_accuracy_m, is_approximate_location, alignment_status, provenance_classification
        FROM ml_ground_truth_observations 
        ORDER BY id;
    """)
    obs = cur.fetchall()
    print(f"\n--- OBSERVATIONS IN DB (Total: {len(obs)}) ---")
    for o in obs:
        print(f"  {o[0]} | {o[1]} - {o[2]}")
        print(f"    Status: {o[3]} | Verif: {o[4]} | Depth: {o[5]}m ({o[6]})")
        print(f"    ObsTime: {o[7]} | Start: {o[8]}")
        print(f"    Source: {o[9]} | Ref: {o[10]} | URL: {o[11]}")
        print(f"    Evidence: {o[12]} | FileRef: {o[13]} | Accuracy: {o[14]}m (Approx: {o[15]})")
        print(f"    Alignment: {o[16]} | Provenance: {o[17]}")
        print()
