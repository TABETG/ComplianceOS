package com.complianceos.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * CO-01 — Provisionnement du tenant PostgreSQL isolé
 *
 * Stratégie : Row-Level Security (RLS) sur toutes les tables sensibles.
 * Chaque ligne porte un tenant_id. Une policy PostgreSQL native filtre
 * automatiquement selon la variable de session app.current_tenant.
 *
 * Avantages vs schema-per-tenant :
 *  - Pool de connexions partagé (scalabilité horizontale)
 *  - Migrations Flyway uniques (maintenabilité)
 *  - Isolation garantie par la BDD elle-même (pas par le code applicatif)
 *
 * Sécurité at-rest : chiffrement AES-256 via AWS KMS configuré au niveau
 * du volume RDS (Transparent Data Encryption) — pas de code applicatif requis.
 */
@Service
public class TenantProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

    private final JdbcTemplate jdbcTemplate;

    public TenantProvisioningService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Async pour ne pas bloquer la réponse HTTP (critère CO-01 : < 2 secondes).
     * La création RLS prend typiquement 200-500ms.
     */
    @Async("tenantProvisioningExecutor")
    public void provisionTenantAsync(UUID tenantId, UUID companyId) {
        log.info("Provisioning tenant: tenantId={}", tenantId);

        try {
            // 1. Vérifier que le tenant n'existe pas déjà (idempotence)
            boolean exists = Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM tenant_registry WHERE tenant_id = ?)",
                    Boolean.class, tenantId
                )
            );

            if (exists) {
                log.warn("Tenant already provisioned: {}", tenantId);
                return;
            }

            // 2. Enregistrer le tenant dans le registre
            jdbcTemplate.update(
                "INSERT INTO tenant_registry (tenant_id, company_id, created_at) VALUES (?, ?, NOW())",
                tenantId, companyId
            );

            // 3. Les policies RLS sont déjà en place globalement (via Flyway migration).
            //    On vérifie juste que les tables critiques ont bien leur policy active.
            validateRlsPolicies();

            // 4. Initialiser la configuration du tenant (quotas, features flags)
            jdbcTemplate.update(
                """
                INSERT INTO tenant_config (tenant_id, plan, max_audits_per_month, ai_analysis_enabled, created_at)
                VALUES (?, 'TRIAL', 3, true, NOW())
                """,
                tenantId
            );

            log.info("Tenant provisioned successfully: tenantId={}", tenantId);

        } catch (Exception e) {
            log.error("Failed to provision tenant: tenantId={}", tenantId, e);
            // TODO: publier un événement Kafka tenant.provisioning.failed pour retry
            throw new TenantProvisioningException("Failed to provision tenant: " + tenantId, e);
        }
    }

    /**
     * Validation que les policies RLS sont actives sur les tables sensibles.
     * Appelé lors du provisionnement comme filet de sécurité.
     */
    private void validateRlsPolicies() {
        var criticalTables = new String[]{"companies", "audits", "documents", "risk_scores", "agent_memories"};

        for (String table : criticalTables) {
            Boolean rlsEnabled = jdbcTemplate.queryForObject(
                "SELECT rowsecurity FROM pg_tables WHERE tablename = ? AND schemaname = 'public'",
                Boolean.class, table
            );

            if (!Boolean.TRUE.equals(rlsEnabled)) {
                throw new TenantProvisioningException(
                    "RLS not enabled on critical table: " + table + ". Check Flyway migrations."
                );
            }
        }
    }
}

// ─── Flyway migration V1__init_rls.sql (référence) ─────────────────────────
/*
-- Activation RLS sur toutes les tables métier
ALTER TABLE companies ENABLE ROW LEVEL SECURITY;
ALTER TABLE audits ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE risk_scores ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_memories ENABLE ROW LEVEL SECURITY;

-- Policy : chaque service applicatif (rôle app_user) ne voit
-- que les lignes de son tenant courant (variable de session)
CREATE POLICY tenant_isolation ON companies
    FOR ALL
    TO app_user
    USING (tenant_id = current_setting('app.current_tenant')::uuid);

CREATE POLICY tenant_isolation ON audits
    FOR ALL
    TO app_user
    USING (tenant_id = current_setting('app.current_tenant')::uuid);

-- Idem pour toutes les tables sensibles...

-- Le rôle admin_user bypass RLS (pour les migrations et opérations cross-tenant)
ALTER TABLE companies FORCE ROW LEVEL SECURITY;
*/
