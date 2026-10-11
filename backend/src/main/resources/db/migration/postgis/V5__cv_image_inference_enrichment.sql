-- ==============================================================================
-- JalMarg Flyway Migration: V5__cv_image_inference_enrichment.sql
-- Phase 25B: IRDID Computer Vision Image Inference Enrichment & Verification
-- PostGIS Cluster: Port 5433 (jalmarg_twin)
-- Strictly Non-Destructive: Preserves all V1, V2, V3, V4 tables and citizen reports
-- ==============================================================================

-- 1. Optional CV Inference Summary Columns on 'reports' Table
ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS image_prediction VARCHAR(64),
    ADD COLUMN IF NOT EXISTS image_confidence DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS image_inference_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_reports_image_prediction ON reports(image_prediction);

-- 2. Audit-Grade Vision Inference Records Table
CREATE TABLE IF NOT EXISTS twin_report_image_inference (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    model_version VARCHAR(64) NOT NULL,
    prediction VARCHAR(64) NOT NULL, -- VISIBLE_WATERLOGGING, NO_VISIBLE_WATERLOGGING, INDETERMINATE
    confidence_score DOUBLE PRECISION,
    raw_probability DOUBLE PRECISION,
    inference_status VARCHAR(32) NOT NULL DEFAULT 'INFERENCE_COMPLETED', -- INFERENCE_COMPLETED, MODEL_UNAVAILABLE, FAILED
    review_required BOOLEAN NOT NULL DEFAULT TRUE,
    image_sha256 VARCHAR(64),
    failure_reason VARCHAR(256),
    model_provenance VARCHAR(128) DEFAULT 'IRDID_BASELINE_LIGHTROADWATERNET',
    limitations_json TEXT,
    inference_duration_ms DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_image_inference_report_id ON twin_report_image_inference(report_id);
CREATE INDEX IF NOT EXISTS idx_image_inference_prediction ON twin_report_image_inference(prediction);
CREATE INDEX IF NOT EXISTS idx_image_inference_status ON twin_report_image_inference(inference_status);
CREATE INDEX IF NOT EXISTS idx_image_inference_sha256 ON twin_report_image_inference(image_sha256);
