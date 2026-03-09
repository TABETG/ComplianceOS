-- V1__init_companies.sql
-- Flyway migration initiale -- CO-01

CREATE TYPE company_status AS ENUM (
    'PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'DELETED'
);

CREATE TYPE nis2_sector AS ENUM (
    'ENERGIE', 'TRANSPORT', 'SANTE', 'FINANCE',
    'EAU', 'NUMERIQUE', 'ESPACE', 'ADMINISTRATION', 'AUTRE'
);

CREATE TABLE companies (
    id            UUID           NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id     UUID           NOT NULL,
    email         VARCHAR(255)   NOT NULL,
    company_name  VARCHAR(255)   NOT NULL,
    siret         CHAR(14)       NOT NULL,
    sector        nis2_sector    NOT NULL,
    phone         VARCHAR(50),
    status        company_status NOT NULL DEFAULT 'PENDING_ACTIVATION',
    registered_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    activated_at  TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT companies_pkey      PRIMARY KEY (id),
    CONSTRAINT companies_email_uq  UNIQUE (email),
    CONSTRAINT companies_siret_uq  UNIQUE (siret),
    CONSTRAINT companies_tenant_uq UNIQUE (tenant_id)
);

CREATE INDEX idx_companies_tenant_id ON companies(tenant_id);
CREATE INDEX idx_companies_email     ON companies(email);
CREATE INDEX idx_companies_status    ON companies(status);

ALTER TABLE companies ENABLE ROW LEVEL SECURITY;

CREATE POLICY companies_tenant_isolation
    ON companies FOR ALL TO complianceos_app
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $func$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$func$ LANGUAGE plpgsql;

CREATE TRIGGER companies_updated_at
    BEFORE UPDATE ON companies
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

COMMENT ON TABLE companies IS 'Entreprises inscrites sur ComplianceOS';
COMMENT ON COLUMN companies.siret IS 'SIRET INSEE 14 chiffres valide par Luhn';
COMMENT ON COLUMN companies.status IS 'PENDING_ACTIVATION jusqu au clic sur le lien email';