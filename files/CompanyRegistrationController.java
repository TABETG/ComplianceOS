package com.complianceos.registration;

import com.complianceos.registration.dto.RegistrationRequest;
import com.complianceos.registration.dto.RegistrationResponse;
import com.complianceos.registration.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/registration")
@Tag(name = "Registration", description = "Company account registration - CO-01")
public class CompanyRegistrationController {

    private final RegistrationService registrationService;

    public CompanyRegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * CO-01 — Inscription et création de compte entreprise
     * Crée un tenant isolé PostgreSQL + compte Keycloak ADMIN + e-mail de confirmation
     */
    @PostMapping
    @Operation(summary = "Register a new company", description = "Creates isolated tenant, Keycloak admin account, and sends 24h confirmation email")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        RegistrationResponse response = registrationService.registerCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Activation du compte via le token JWT envoyé par e-mail (valable 24h)
     */
    @PostMapping("/activate")
    @Operation(summary = "Activate account via email token")
    public ResponseEntity<Map<String, String>> activate(@RequestParam String token) {
        registrationService.activateAccount(token);
        return ResponseEntity.ok(Map.of("status", "activated", "message", "Account successfully activated"));
    }

    /**
     * Renvoi de l'e-mail de confirmation (si le token a expiré)
     */
    @PostMapping("/resend-confirmation")
    public ResponseEntity<Void> resendConfirmation(@RequestParam String email) {
        registrationService.resendConfirmationEmail(email);
        return ResponseEntity.noContent().build();
    }
}
