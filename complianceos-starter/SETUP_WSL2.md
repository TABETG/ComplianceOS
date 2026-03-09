# ComplianceOS -- Setup from scratch (Windows + WSL2)

## Etape 0 -- Verifier les prerequis

Ouvre **WSL2 (Ubuntu)** et verifie chaque outil :

```bash
java -version      # doit afficher Java 21 ou superieur
mvn -version       # doit afficher Maven 3.9.x
docker --version   # doit afficher Docker 24+
docker compose version  # doit afficher Compose v2.x
git --version      # doit afficher git 2.x
```

> Si Java n'est pas installe :
> ```bash
> sudo apt update && sudo apt install -y openjdk-21-jdk
> ```

---

## Etape 1 -- Placer les fichiers

Deplace le dossier `complianceos/` dans ton home WSL2 :

```bash
# Depuis PowerShell Windows, copie le dossier dezipe dans WSL :
# Tu peux aussi faire glisser dans l'explorateur Windows -> \\wsl.localhost\Ubuntu\home\<ton_user>\

cd ~
ls complianceos/    # doit lister : pom.xml  compliance-api/  docker/
```

---

## Etape 2 -- Demarrer les services Docker

```bash
cd ~/complianceos/docker

# Lance PostgreSQL + Keycloak + MailHog + Redis
docker compose up -d

# Attendre 60-90 secondes, puis verifier :
docker compose ps
```

Resultat attendu -- tous `running` :
```
complianceos-postgres   running (healthy)
complianceos-keycloak   running (healthy)
complianceos-mailhog    running
complianceos-redis      running (healthy)
```

---

## Etape 3 -- Verifier dans le navigateur

| Service | URL | Login |
|---------|-----|-------|
| Keycloak | http://localhost:8180 | admin / admin |
| MailHog | http://localhost:8025 | aucun |
| RedisInsight | http://localhost:8001 | aucun |

Dans Keycloak : Administration Console -> tu dois voir le realm **complianceos**

---

## Etape 4 -- Verifier PostgreSQL

```bash
docker exec -it complianceos-postgres psql -U complianceos_app -d complianceos -c "\\dt"
# Doit lister : tenant_registry, tenant_config
```

---

## Etape 5 -- Compiler le projet

```bash
cd ~/complianceos

# 1ere compilation (3-5 min, telecharge les dependances Maven)
mvn clean install -DskipTests

# Resultat attendu : BUILD SUCCESS
```

---

## Etape 6 -- Demarrer Spring Boot

```bash
cd ~/complianceos/compliance-api
mvn spring-boot:run
```

Verifier que l'API repond :
```bash
# Dans un 2e terminal WSL2
curl http://localhost:8080/actuator/health
# {"status":"UP",...}
```

Swagger UI : http://localhost:8080/swagger-ui.html

---

## Etape 7 -- Ouvrir dans IntelliJ IDEA

- File -> Open -> Naviguer vers : `\\\\wsl.localhost\\Ubuntu\\home\\<user>\\complianceos`
- Selectionner le **pom.xml racine** -> "Open as Project"
- IntelliJ detecte automatiquement Maven et synchronise

---

## Commandes du quotidien

```bash
# Demarrer les services (debut de session)
cd ~/complianceos/docker && docker compose up -d

# Arreter les services (fin de journee)
docker compose down

# Logs d'un service
docker compose logs -f keycloak
docker compose logs -f postgres

# Tout reset (efface les donnees)
docker compose down -v
```

---

## Ports utilises

| Port | Service |
|------|---------|
| 8080 | Spring Boot API |
| 5432 | PostgreSQL |
| 8180 | Keycloak (admin: admin/admin) |
| 1025 | MailHog SMTP |
| 8025 | MailHog UI (voir les emails) |
| 6379 | Redis |
| 8001 | RedisInsight UI |

---

## Problemes frequents

**Port 5432 deja utilise**
```bash
sudo service postgresql stop
```

**Keycloak ne demarre pas**
```bash
docker compose logs postgres   # verifier "database system is ready"
docker compose restart keycloak
```

**Docker non accessible depuis WSL2**
Docker Desktop Windows -> Settings -> Resources -> WSL Integration -> Ubuntu -> ON
