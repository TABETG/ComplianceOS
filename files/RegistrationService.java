package com.complianceos.registration.service;

import com.complianceos.registration.dto.RegistrationRequest;
import com.complianceos.registration.dto.RegistrationResponse;
import com.complianceos.registration.exception.DuplicateRegistrationException;
import com.complianceos.registration.exception.InvalidActivationTokenException;
import com.complianceos.tenant.TenantProvisioningService;
import com.complianceos.keycloak.KeycloakProvisioningService;
import com.complianceos.security.JwtActivationTokenService;
import com.complianceos.email.EmailService;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final CompanyRepository companyRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final KeycloakProvisioningService keycloakProvisioningService;
    private final JwtActivationTokenService jwtActivationTokenService;
    private final EmailService emailService;

    public RegistrationService(
        CompanyRepository companyRepository,
        TenantProvisioningService tenantProvisioningService,
        KeycloakProvisioningService keycloakProvisioningService,
        JwtActivationTokenService jwtActivationTokenService,
        EmailService emailService
    ) {
        this.companyRepository = companyRepository;
        this.tenantProvisioningService = tenantProvisioningService;
        this.keycloakProvisioningService = keycloakProvisioningService;
        this.jwtActivationTokenService = jwtActivationTokenService;
        this.emailService = emailService;
    }

    /**
     * CO-01 — Processus d'inscription complet :
     * 1. Vérification unicité (email + SIRET)
     * 2. Provisionnement du tenant PostgreSQL (Row-Level Security)
     * 3. Création compte Keycloak avec rôle ADMIN
     * 4. Envoi e-mail de confirmation avec token JWT 24h
     * 5. Chiffrement AES-256 at-rest via KMS (géré au niveau PostgreSQL + S3)
     *
     * Critère CO-01 : réponse < 2 secondes → la création du tenant est async
     * (TenantProvisioningService utilise @Async sur la partie RLS heavy)
     */
    @Transactional
    @Timed(value = "registration.duration", description = "Company registration duration")
    public RegistrationResponse registerCompany(RegistrationRequest request) {
        log.info("Starting registration for email={}, siret={}", request.email(), request.siret());

        // Guard : unicité e-mail et SIRET
        if (companyRepository.existsByEmail(request.email())) {
            throw new DuplicateRegistrationException("Email already registered: " + request.email());
        }
        if (companyRepository.existsBySiret(request.siret())) {
            throw new DuplicateRegistrationException("SIRET already registered: " + request.siret());
        }

        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        // 1. Sauvegarder l'entreprise en état PENDING (avant activation e-mail)
        Company company = Company.builder()
            .id(companyId)
            .tenantId(tenantId)
            .email(request.email())
            .companyName(request.companyName())
            .siret(request.siret())
            .sector(request.sector())
            .status(CompanyStatus.PENDING_ACTIVATION)
            .registeredAt(Instant.now())
            .build();
        companyRepository.save(company);

        // 2. Provisionnement tenant PostgreSQL (Row-Level Security isolé)
        // → Async pour respecter le critère < 2s côté utilisateur
        tenantProvisioningService.provisionTenantAsync(tenantId, companyId);

        // 3. Compte Keycloak avec rôle ADMIN
        keycloakProvisioningService.createAdminUser(
            tenantId,
            request.email(),
            request.companyName()
        );

        // 4. Génération du token JWT d'activation (24h)
        String activationToken = jwtActivationTokenService.generateActivationToken(
            companyId,
            tenantId,
            request.email()
        );

        // 5. Envoi e-mail de confirmation (async, non bloquant)
        emailService.sendActivationEmail(
            request.email(),
            request.companyName(),
            activationToken
        );

        log.info("Registration successful: tenantId={}, companyId={}", tenantId, companyId);

        return new RegistrationResponse(
            companyId,
            tenantId,
            "Registration successful. Please check your email to activate your account.",
            request.email()
        );
    }

    /**
     * Activation du compte via le lien e-mail (token JWT signé, valable 24h)
     */
    @Transactional
    public void activateAccount(String activationToken) {
        var claims = jwtActivationTokenService.validateActivationToken(activationToken)
            .orElseThrow(() -> new InvalidActivationTokenException("Token is invalid or expired"));

        UUID companyId = UUID.fromString(claims.get("companyId", String.class));

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new InvalidActivationTokenException("Company not found"));

        if (company.getStatus() == CompanyStatus.ACTIVE) {
            return; // Idempotent : déjà activé, pas d'erreur
        }

        company.setStatus(CompanyStatus.ACTIVE);
        company.setActivatedAt(Instant.now());
        companyRepository.save(company);

        // Activer le compte Keycloak
        keycloakProvisioningService.enableUser(company.getTenantId(), company.getEmail());

        log.info("Account activated: companyId={}", companyId);
    }

    public void resendConfirmationEmail(String email) {
        Company company = companyRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("No account found for email: " + email));

        if (company.getStatus() == CompanyStatus.ACTIVE) {
            throw new IllegalStateException("Account is already active");
        }

        String newToken = jwtActivationTokenService.generateActivationToken(
            company.getId(),
            company.getTenantId(),
            email
        );

        emailService.sendActivationEmail(email, company.getCompanyName(), newToken);
    }
}
