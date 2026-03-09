-- ComplianceOS -- Initialisation PostgreSQL
-- Execute automatiquement au 1er demarrage

CREATE DATABASE keycloak
    WITH OWNER = complianceos_app ENCODING = 'UTF8';

\c complianceos;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tenant_registry (
    tenant_id  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status     VARCHAR(50) NOT NULL DEFAULT 'PROVISIONING'
);

CREATE TABLE IF NOT EXISTS tenant_config (
    tenant_id            UUID PRIMARY KEY REFERENCES tenant_registry(tenant_id),
    plan                 VARCHAR(50) NOT NULL DEFAULT 'TRIAL',
    max_audits_per_month INT         NOT NULL DEFAULT 3,
    ai_analysis_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$ BEGIN
    RAISE NOTICE 'ComplianceOS DB initialized. Extensions: uuid-ossp, vector, pgcrypto. OK';
END $$;