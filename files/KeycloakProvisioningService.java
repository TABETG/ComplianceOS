package com.complianceos.keycloak;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CO-01 — Provisionnement Keycloak 25
 *
 * Architecture multi-tenant Keycloak :
 * - Un seul Realm "complianceos" (tous les tenants partagent le même realm)
 * - Le tenant_id est stocké en attribut utilisateur pour l'isolation
 * - Les groupes Keycloak représentent les organisations (un groupe = un tenant)
 *
 * Rôles disponibles : ADMIN | AUDITOR | VIEWER | EXTERNAL_AUDITOR
 * Le premier compte créé à l'inscription reçoit ADMIN par défaut.
 */
@Service
public class KeycloakProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakProvisioningService.class);
    private static final String REALM = "complianceos";
    private static final String ADMIN_ROLE = "ADMIN";

    private final Keycloak keycloakAdminClient;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    public KeycloakProvisioningService(Keycloak keycloakAdminClient) {
        this.keycloakAdminClient = keycloakAdminClient;
    }

    /**
     * CO-01 : Crée un compte Keycloak DÉSACTIVÉ (activation via e-mail)
     * avec le rôle ADMIN et les attributs de tenant.
     */
    public String createAdminUser(UUID tenantId, String email, String companyName) {
        RealmResource realm = keycloakAdminClient.realm(REALM);

        UserRepresentation user = buildUserRepresentation(tenantId, email, companyName);

        try (Response response = realm.users().create(user)) {
            if (response.getStatus() != 201) {
                String errorBody = response.readEntity(String.class);
                throw new KeycloakProvisioningException(
                    "Failed to create Keycloak user. Status: " + response.getStatus() + ", Body: " + errorBody
                );
            }

            // Extraire l'ID Keycloak depuis le header Location
            String locationHeader = response.getHeaderString("Location");
            String keycloakUserId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);

            // Assigner le rôle ADMIN au niveau du client (pas realm-level)
            assignClientRole(realm, keycloakUserId, ADMIN_ROLE);

            // Créer le groupe tenant et y ajouter l'utilisateur
            ensureTenantGroupExists(realm, tenantId, companyName);
            addUserToTenantGroup(realm, keycloakUserId, tenantId);

            log.info("Keycloak user created: email={}, tenantId={}, keycloakId={}", email, tenantId, keycloakUserId);
            return keycloakUserId;

        } catch (KeycloakProvisioningException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakProvisioningException("Unexpected error creating Keycloak user: " + email, e);
        }
    }

    /**
     * Activé après validation de l'e-mail (CO-01 critère : token 24h)
     */
    public void enableUser(UUID tenantId, String email) {
        RealmResource realm = keycloakAdminClient.realm(REALM);

        var users = realm.users().searchByEmail(email, true);
        if (users.isEmpty()) {
            throw new KeycloakProvisioningException("User not found in Keycloak: " + email);
        }

        UserRepresentation user = users.get(0);
        user.setEnabled(true);
        realm.users().get(user.getId()).update(user);

        log.info("Keycloak user enabled: email={}, tenantId={}", email, tenantId);
    }

    private UserRepresentation buildUserRepresentation(UUID tenantId, String email, String companyName) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(email);
        user.setEmail(email);
        user.setEnabled(false); // Désactivé jusqu'à confirmation e-mail
        user.setEmailVerified(false);

        // Attributs tenant pour le JWT claim custom
        user.setAttributes(Map.of(
            "tenant_id", List.of(tenantId.toString()),
            "company_name", List.of(companyName),
            "registration_source", List.of("self_service")
        ));

        // Credential temporaire (l'utilisateur définira son mot de passe via Keycloak)
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setTemporary(true);
        credential.setValue(UUID.randomUUID().toString()); // Temporaire, remplacé via reset link
        user.setCredentials(List.of(credential));

        return user;
    }

    private void assignClientRole(RealmResource realm, String keycloakUserId, String roleName) {
        var clientResource = realm.clients().findByClientId(clientId).get(0);
        var role = realm.clients().get(clientResource.getId()).roles().get(roleName).toRepresentation();
        realm.users().get(keycloakUserId).roles().clientLevel(clientResource.getId()).add(List.of(role));
    }

    private void ensureTenantGroupExists(RealmResource realm, UUID tenantId, String companyName) {
        String groupName = "tenant-" + tenantId;
        boolean exists = realm.groups().groups().stream()
            .anyMatch(g -> g.getName().equals(groupName));

        if (!exists) {
            var group = new org.keycloak.representations.idm.GroupRepresentation();
            group.setName(groupName);
            group.setAttributes(Map.of("company_name", List.of(companyName)));
            realm.groups().add(group);
        }
    }

    private void addUserToTenantGroup(RealmResource realm, String keycloakUserId, UUID tenantId) {
        String groupName = "tenant-" + tenantId;
        var group = realm.groups().groups().stream()
            .filter(g -> g.getName().equals(groupName))
            .findFirst()
            .orElseThrow();
        realm.users().get(keycloakUserId).joinGroup(group.getId());
    }
}
