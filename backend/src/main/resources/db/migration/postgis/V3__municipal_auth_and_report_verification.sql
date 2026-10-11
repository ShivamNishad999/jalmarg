-- ==================================================================================
-- JALMARG MUNICIPAL FLOOD DIGITAL TWIN — MIGRATION V3
-- Municipal Authentication, Report Verification & Observation Traceability
-- ==================================================================================

-- 1. Municipal Users & Role-Based Access Control
CREATE TABLE IF NOT EXISTS twin_municipal_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    email VARCHAR(128) NOT NULL,
    department VARCHAR(128) NOT NULL,
    role VARCHAR(64) NOT NULL, -- MUNICIPAL_VIEWER, MUNICIPAL_OPERATOR, MUNICIPAL_ADMIN
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_municipal_user_role ON twin_municipal_users(role);

-- Seed default provisioned municipal accounts (Passwords hashed with SHA-256 + salt)
-- 1) operator_pwd : PwdOperator#2026 -> Salt: salt_pwd_01 -> Hash: 7b31497a760de8f5c9e29a9eebeabfa7b2faec1f40773f3248386f5c8723cefa
-- 2) admin_ndmc   : NdmcAdmin#2026   -> Salt: salt_ndmc_02 -> Hash: 8b067f938f32289c09c9f28c2e646279f176ae3ea119859f518e9526e036e8aa
-- 3) viewer_traffic : TrafficView#2026 -> Salt: salt_traf_03 -> Hash: 3e83915bc56bfd387fbf3811802b545d654f5c9d64670c5e7ae9b7e7352caee9
INSERT INTO twin_municipal_users (username, password_hash, salt, full_name, email, department, role, is_active)
VALUES
    ('operator_pwd', '7b31497a760de8f5c9e29a9eebeabfa7b2faec1f40773f3248386f5c8723cefa', 'salt_pwd_01', 'PWD Drainage Field Operator', 'pwd.operator@delhi.gov.in', 'PWD Central Drainage', 'MUNICIPAL_OPERATOR', TRUE),
    ('admin_ndmc', '8b067f938f32289c09c9f28c2e646279f176ae3ea119859f518e9526e036e8aa', 'salt_ndmc_02', 'NDMC Emergency Center Admin', 'ndmc.admin@delhi.gov.in', 'NDMC Control Room', 'MUNICIPAL_ADMIN', TRUE),
    ('viewer_traffic', '3e83915bc56bfd387fbf3811802b545d654f5c9d64670c5e7ae9b7e7352caee9', 'salt_traf_03', 'Delhi Traffic Police Duty Officer', 'traffic.duty@delhipolice.gov.in', 'Delhi Traffic Police', 'MUNICIPAL_VIEWER', TRUE)
ON CONFLICT (username) DO NOTHING;

-- 2. Citizen Report Review & Verification Fields on 'reports'
ALTER TABLE reports
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(64) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN IF NOT EXISTS verified_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS verification_notes VARCHAR(500),
    ADD COLUMN IF NOT EXISTS parent_report_id BIGINT REFERENCES reports(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS is_duplicate_merged BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_report_verification_status ON reports(verification_status);

-- 3. Individual Observation Traceability Ledger (Preserving every submission, photo & depth note)
CREATE TABLE IF NOT EXISTS twin_report_observations (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    citizen_depth_level VARCHAR(32),
    depth_ft DOUBLE PRECISION,
    photo_key VARCHAR(256),
    client_ip VARCHAR(64),
    note VARCHAR(500),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_observation_report_id ON twin_report_observations(report_id);
CREATE INDEX IF NOT EXISTS idx_observation_submitted_at ON twin_report_observations(submitted_at DESC);
