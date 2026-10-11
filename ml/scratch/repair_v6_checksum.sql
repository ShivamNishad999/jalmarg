BEGIN;

DO $$
DECLARE
    matched_rows integer;
    changed_rows integer;
BEGIN
    IF current_database() <> 'jalmarg_twin'
       OR inet_server_port() <> 5433 THEN
        RAISE EXCEPTION 'JalMarg database identity check failed';
    END IF;

    SELECT COUNT(*)
      INTO matched_rows
      FROM flyway_schema_history
     WHERE version = '6'
       AND script = 'V6__ground_truth_and_ml_reliability.sql'
       AND checksum = 1892837461
       AND success = true;

    IF matched_rows <> 1 THEN
        RAISE EXCEPTION
          'Expected exactly one eligible V6 row; found %',
          matched_rows;
    END IF;

    UPDATE flyway_schema_history
       SET checksum = 16961246
     WHERE version = '6'
       AND script = 'V6__ground_truth_and_ml_reliability.sql'
       AND checksum = 1892837461
       AND success = true;

    GET DIAGNOSTICS changed_rows = ROW_COUNT;

    IF changed_rows <> 1 THEN
        RAISE EXCEPTION
          'Expected exactly one updated V6 row; updated %',
          changed_rows;
    END IF;
END
$$;

COMMIT;
