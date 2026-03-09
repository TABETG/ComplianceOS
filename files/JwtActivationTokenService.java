package com.complianceos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * CO-01 critère : "L'e-mail de confirmation contient un token JWT signé, expirant dans 24h"
 *
 * Token séparé du token d'authentification Keycloak :
 * - Usage unique : activation du compte uniquement
 * - Signé avec une clé dédiée (rotation indépendante)
 * - Claims : companyId, tenantId, email, purpose=ACCOUNT_ACTIVATION
 */
@Service
public class JwtActivationTokenService {

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String ACTIVATION_PURPOSE = "ACCOUNT_ACTIVATION";
    private static final String COMPANY_ID_CLAIM = "companyId";
    private static final String TENANT_ID_CLAIM = "tenantId";
    private static final long EXPIRATION_HOURS = 24L;

    private final SecretKey signingKey;

    public JwtActivationTokenService(
        @Value("${complianceos.activation.jwt.secret}") String secret
    ) {
        // Clé HMAC-SHA256 — en prod, récupérer depuis Vault (pas en config file)
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Génère un token JWT signé valable 24h
     */
    public String generateActivationToken(UUID companyId, UUID tenantId, String email) {
        Instant now = Instant.now();
        Instant expiration = now.plus(EXPIRATION_HOURS, ChronoUnit.HOURS);

        return Jwts.builder()
            .id(UUID.randomUUID().toString())        // jti : unicité du token
            .subject(email)
            .claim(PURPOSE_CLAIM, ACTIVATION_PURPOSE)
            .claim(COMPANY_ID_CLAIM, companyId.toString())
            .claim(TENANT_ID_CLAIM, tenantId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(signingKey)
            .compact();
    }

    /**
     * Valide le token et retourne les claims si valide, Optional.empty() sinon
     * Vérifie : signature, expiration, et purpose=ACCOUNT_ACTIVATION
     */
    public Optional<Claims> validateActivationToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            // Vérification du purpose : ne pas accepter d'autres types de tokens JWT
            if (!ACTIVATION_PURPOSE.equals(claims.get(PURPOSE_CLAIM, String.class))) {
                return Optional.empty();
            }

            return Optional.of(claims);

        } catch (JwtException | IllegalArgumentException e) {
            // Token expiré, malformé, signature invalide → Optional.empty()
            return Optional.empty();
        }
    }
}
