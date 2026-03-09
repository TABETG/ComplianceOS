-- ─────────────────────────────────────────────────────────────────────────────
--  ComplianceOS — Initialisation PostgreSQL
--  Exécuté automatiquement au 1er démarrage du container
-- ─────────────────────────────────────────────────────────────────────────────

-- Base de données pour Keycloak (séparée de l'app)
CREATE DATABASE keycloak
    WITH OWNER = complianceos_app
    ENCODING = 'UTF8';

-- Extensions sur la DB principale
\c complianceos;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Registre des tenants
CREATE TABLE IF NOT EXISTS tenant_registry (
    tenant_id   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id  UUID NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status      VARCHAR(50) NOT NULL DEFAULT 'PROVISIONING'
);

-- Configuration par tenant
CREATE TABLE IF NOT EXISTS tenant_config (
    tenant_id            UUID PRIMARY KEY REFERENCES tenant_registry(tenant_id),
    plan                 VARCHAR(50)  NOT NULL DEFAULT 'TRIAL',
    max_audits_per_month INT          NOT NULL DEFAULT 3,
    ai_analysis_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Table companies avec Row-Level Security
CREATE TABLE IF NOT EXISTS companies (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id     UUID        NOT NULL REFERENCES tenant_registry(tenant_id),
    email         VARCHAR(255) NOT NULL UNIQUE,
    company_name  VARCHAR(255) NOT NULL,
    siret         VARCHAR(14)  NOT NULL UNIQUE,
    sector        VARCHAR(100) NOT NULL,
    phone         VARCHAR(50),
    status        VARCHAR(50)  NOT NULL DEFAULT 'PENDING_ACTIVATION',
    registered_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    activated_at  TIMESTAMPTZ
);

-- Activation du Row-Level Security (CO-01)
ALTER TABLE companies ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON companies
    FOR ALL
    TO complianceos_app
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

CREATE INDEX IF NOT EXISTS idx_companies_tenant_id ON companies(tenant_id);
CREATE INDEX IF NOT EXISTS idx_companies_email     ON companies(email);
CREATE INDEX IF NOT EXISTS idx_companies_siret     ON companies(siret);

DO $$ BEGIN
    RAISE NOTICE 'ComplianceOS DB initialized. Extensions: uuid-ossp, vector, pgcrypto. RLS: ON';
END $$;
