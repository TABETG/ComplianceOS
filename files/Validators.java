package com.complianceos.registration.validation;

// ─── @Siret ──────────────────────────────────────────────────────────────────

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.util.Set;

/**
 * CO-01 critère : "Le formulaire valide le format SIRET (14 chiffres)"
 * Validation : format numérique 14 chiffres + algorithme de Luhn
 */
@Documented
@Constraint(validatedBy = SiretValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Siret {
    String message() default "Invalid SIRET: must be 14 digits and pass Luhn check";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

class SiretValidator implements ConstraintValidator<Siret, String> {

    @Override
    public boolean isValid(String siret, ConstraintValidatorContext context) {
        if (siret == null || siret.isBlank()) return false;

        // Nettoyer (espaces éventuels)
        String cleaned = siret.replaceAll("\\s", "");

        // 1. Format : exactement 14 chiffres
        if (!cleaned.matches("\\d{14}")) return false;

        // 2. Algorithme de Luhn (même logique que la carte bancaire)
        return luhnCheck(cleaned);
    }

    private boolean luhnCheck(String number) {
        int sum = 0;
        boolean alternate = false;

        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(number.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }

            sum += digit;
            alternate = !alternate;
        }

        return (sum % 10 == 0);
    }
}


// ─── @ProfessionalEmail ──────────────────────────────────────────────────────

/**
 * CO-01 critère : "valide le format e-mail et le domaine professionnel"
 * Rejette les domaines gratuits courants (gmail, yahoo, hotmail, etc.)
 */
@Documented
@Constraint(validatedBy = ProfessionalEmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@interface ProfessionalEmail {
    String message() default "Please use a professional email address (no gmail, yahoo, hotmail, etc.)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

class ProfessionalEmailValidator implements ConstraintValidator<ProfessionalEmail, String> {

    // Liste maintenue et extensible (peut être chargée depuis la BDD ou un fichier)
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
        "gmail.com", "googlemail.com",
        "yahoo.com", "yahoo.fr", "yahoo.co.uk",
        "hotmail.com", "hotmail.fr", "live.com", "outlook.com",
        "wanadoo.fr", "orange.fr", "free.fr", "sfr.fr", "laposte.net",
        "icloud.com", "me.com", "mac.com",
        "aol.com", "protonmail.com", "proton.me",
        "tutanota.com", "guerrillamail.com", "mailinator.com", "yopmail.com",
        "temp-mail.org", "throwam.com", "sharklasers.com"
    );

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || !email.contains("@")) return false;

        // Validation format basique
        if (!email.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
            return false;
        }

        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        return !BLOCKED_DOMAINS.contains(domain);
    }
}
