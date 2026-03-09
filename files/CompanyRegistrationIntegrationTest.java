package com.complianceos.registration;

import com.complianceos.registration.dto.RegistrationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CO-01 — Tests d'intégration avec Testcontainers
 * Couvre tous les critères d'acceptation de la user story.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CompanyRegistrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
        .withDatabaseName("complianceos_test")
        .withUsername("app")
        .withPassword("test");

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:25.0")
        .withRealmImportFile("keycloak/complianceos-realm-test.json");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("keycloak.auth-server-url", keycloak::getAuthServerUrl);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CompanyRepository companyRepository;

    // ── Critère : Inscription réussie ─────────────────────────────────────────

    @Test
    @DisplayName("CO-01 ✓ Registration creates company in PENDING status + returns 201")
    void givenValidRequest_whenRegister_thenCreated() throws Exception {
        var request = validRequest();

        mockMvc.perform(post("/api/v1/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("check your email")));
    }

    // ── Critère : Validation SIRET ────────────────────────────────────────────

    @Test
    @DisplayName("CO-01 ✓ Invalid SIRET (wrong length) returns 400")
    void givenInvalidSiret_whenRegister_thenBadRequest() throws Exception {
        var request = new RegistrationRequest(
            "admin@acme.fr", "ACME Corp", "1234567", "FINANCE", null, true
        );

        mockMvc.perform(post("/api/v1/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("siret"));
    }

    // ── Critère : E-mail professionnel ────────────────────────────────────────

    @Test
    @DisplayName("CO-01 ✓ Gmail address is rejected")
    void givenGmailAddress_whenRegister_thenBadRequest() throws Exception {
        var request = new RegistrationRequest(
            "admin@gmail.com", "ACME Corp", "73282932000074", "FINANCE", null, true
        );

        mockMvc.perform(post("/api/v1/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    // ── Critère : Unicité e-mail ──────────────────────────────────────────────

    @Test
    @DisplayName("CO-01 ✓ Duplicate email returns 409 Conflict")
    void givenDuplicateEmail_whenRegister_thenConflict() throws Exception {
        var request = validRequest();

        // Premier enregistrement
        mockMvc.perform(post("/api/v1/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Doublon
        var duplicate = new RegistrationRequest(
            request.email(), "Other Corp", "35600000000048", "SANTE", null, true
        );

        mockMvc.perform(post("/api/v1/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
            .andExpect(status().isConflict());
    }

    // ── Critère : Activation token ────────────────────────────────────────────

    @Test
    @DisplayName("CO-01 ✓ Activation with valid token sets status to ACTIVE")
    void givenValidActivationToken_whenActivate_thenAccountActive() throws Exception {
        // Register
        var request = new RegistrationRequest(
            "activate@test-company.fr", "Test Corp", "73282932000074", "FINANCE", null, true
        );

        var registerResult = mockMvc.perform(post("/api/v1/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        // Extraire le token depuis l'e-mail (simulé via EmailCaptor en test)
        String capturedToken = EmailCaptor.getCapturedToken(request.email());

        // Activer
        mockMvc.perform(post("/api/v1/registration/activate")
                .param("token", capturedToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("activated"));

        // Vérifier en BDD
        var company = companyRepository.findByEmail(request.email()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(company.getStatus())
            .isEqualTo(CompanyStatus.ACTIVE);
    }

    // ── Critère : Token expiré ────────────────────────────────────────────────

    @Test
    @DisplayName("CO-01 ✓ Expired activation token returns 400")
    void givenExpiredToken_whenActivate_thenBadRequest() throws Exception {
        String expiredToken = JwtTestHelper.generateExpiredActivationToken();

        mockMvc.perform(post("/api/v1/registration/activate")
                .param("token", expiredToken))
            .andExpect(status().isBadRequest());
    }

    private RegistrationRequest validRequest() {
        return new RegistrationRequest(
            "admin@acme-corporation.fr",
            "ACME Corporation SAS",
            "73282932000074",  // SIRET valide (Luhn OK)
            "FINANCE",
            "+33 1 23 45 67 89",
            true
        );
    }
}
