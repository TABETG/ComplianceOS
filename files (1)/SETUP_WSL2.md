# ComplianceOS — Setup from scratch (Windows + WSL2)

## Avant de commencer — Vérifier les prérequis

Ouvre **WSL2** (Ubuntu) et lance ces commandes une par une.  
Chaque commande affiche une version → si tu vois une erreur, dis-le moi.

```bash
# Java — doit afficher "openjdk 21" ou "openjdk 24"
java -version

# Maven — doit afficher "Apache Maven 3.9.x"
mvn -version

# Docker — doit afficher "Docker version 2x.x"
docker --version

# Docker Compose — doit afficher "Docker Compose version v2.x"
docker compose version

# Git — doit afficher "git version 2.x"
git --version
```

> ⚠️ **Java 21 est OK pour commencer.** Java 24 est recommandé par le projet
> mais Spring Boot 3.4 tourne très bien sur Java 21. On upgradera plus tard.

---

## Étape 1 — Créer la structure du projet

```bash
# Va dans ton répertoire de travail habituel
# (adapte le chemin à ta situation)
cd ~

# Clone le projet (si tu as déjà git init)
# OU crée le dossier manuellement :
mkdir -p complianceos
cd complianceos

# Colle les fichiers que Claude a générés dans ce dossier.
# Structure attendue :
# complianceos/
# ├── pom.xml                          ← le parent Maven
# ├── compliance-api/
# │   ├── pom.xml
# │   └── src/main/
# │       ├── java/com/complianceos/
# │       │   └── ComplianceOsApplication.java
# │       └── resources/
# │           ├── application.yml
# │           └── db/migration/
# │               └── V1__init_companies.sql
# └── docker/
#     ├── docker-compose.yml
#     ├── postgres/
#     │   └── init.sql
#     └── keycloak/
#         └── complianceos-realm.json
```

---

## Étape 2 — Démarrer les services Docker

```bash
# Va dans le dossier docker
cd ~/complianceos/docker

# Lance tous les services en arrière-plan (-d = detached)
docker compose up -d

# ─── Ce que Docker va télécharger et démarrer ───────────────────────────────
# postgres:17-alpine       → PostgreSQL 17 sur port 5432
# keycloak:25.0            → Keycloak sur port 8180 (≈ 60s à démarrer)
# mailhog:v1.0.1           → Serveur e-mail de test sur port 8025
# redis/redis-stack:7.4    → Redis + RedisInsight sur port 8001
# ────────────────────────────────────────────────────────────────────────────

# Vérifie que tout démarre correctement (attendre 60-90 secondes)
docker compose ps
```

**Résultat attendu** — tous les services doivent être `running` :
```
NAME                        STATUS
complianceos-postgres       running (healthy)
complianceos-keycloak       running (healthy)
complianceos-mailhog        running
complianceos-redis          running (healthy)
```

---

## Étape 3 — Vérifier les services dans le navigateur

Ouvre ces URLs dans ton navigateur Windows :

| Service | URL | Login |
|---------|-----|-------|
| **Keycloak** | http://localhost:8180 | admin / admin |
| **MailHog** (e-mails de test) | http://localhost:8025 | aucun |
| **RedisInsight** | http://localhost:8001 | aucun |

> Keycloak met **60-90 secondes** à démarrer. Normal.  
> Si la page ne s'affiche pas, attends et rafraîchis.

**Vérifier le realm ComplianceOS dans Keycloak :**
1. Va sur http://localhost:8180
2. Clique "Administration Console"
3. Login : admin / admin
4. Tu dois voir "complianceos" dans la liste des realms → ✅

---

## Étape 4 — Vérifier PostgreSQL

```bash
# Se connecter à PostgreSQL dans le container
docker exec -it complianceos-postgres psql -U complianceos_app -d complianceos

# Dans le prompt psql, tape :
\dt                    -- liste les tables (tenant_registry, companies, etc.)
\dx                    -- liste les extensions (uuid-ossp, vector, pgcrypto)
\q                     -- quitter
```

**Résultat attendu :**
```
         List of relations
 Schema |      Name       | Type  |
--------+-----------------+-------+
 public | companies       | table |
 public | tenant_config   | table |
 public | tenant_registry | table |
```

---

## Étape 5 — Compiler le projet Spring Boot

```bash
# Retourne à la racine du projet
cd ~/complianceos

# 1ère compilation (Maven va télécharger les dépendances → 3-5 minutes)
mvn clean install -DskipTests

# Résultat attendu : BUILD SUCCESS
```

> La 1ère fois, Maven télécharge ~200 Mo de dépendances. C'est normal.
> Les fois suivantes, ce sera instantané (cache local ~/.m2).

---

## Étape 6 — Démarrer Spring Boot

```bash
cd ~/complianceos/compliance-api

# Démarrer l'application
mvn spring-boot:run
```

**Résultat attendu dans les logs :**
```
Started ComplianceOsApplication in 3.4 seconds
Tomcat started on port 8080
```

**Vérifier que l'API répond :**
```bash
# Dans un autre terminal WSL2
curl http://localhost:8080/actuator/health

# Résultat attendu :
# {"status":"UP","components":{"db":{"status":"UP"},...}}
```

**Swagger UI :** http://localhost:8080/swagger-ui.html

---

## Étape 7 — Ouvrir dans IntelliJ IDEA

```bash
# Depuis WSL2, ouvre le projet dans IntelliJ
# (IntelliJ doit être installé sur Windows)
explorer.exe .

# OU directement depuis PowerShell / Git Bash Windows :
# File → Open → Naviguer vers \\wsl.localhost\Ubuntu\home\TON_USER\complianceos
# Sélectionner le pom.xml racine → "Open as Project"
```

**Dans IntelliJ :**
- Aller dans `File → Project Structure → SDK` → sélectionner Java 21 ou 24
- Maven doit se resynchroniser automatiquement (barre de progression en bas)

---

## Commandes du quotidien

```bash
# Démarrer les services Docker (à chaque session de travail)
cd ~/complianceos/docker && docker compose up -d

# Arrêter les services (fin de journée)
docker compose down

# Voir les logs d'un service
docker compose logs -f keycloak
docker compose logs -f postgres

# Redémarrer un service
docker compose restart keycloak

# Supprimer tout et repartir de zéro (⚠️ efface les données)
docker compose down -v
```

---

## Problèmes fréquents

**"Port 5432 already in use"**
```bash
# Un PostgreSQL local tourne peut-être déjà
sudo service postgresql stop
# Ou changer le port dans docker-compose.yml : "5433:5432"
```

**"Keycloak ne démarre pas" (erreur DB)**
```bash
# Attendre que PostgreSQL soit healthy avant Keycloak
docker compose logs postgres   # Vérifier "database system is ready"
docker compose restart keycloak
```

**"Cannot connect to Docker daemon"**
```bash
# Docker Desktop doit être démarré sur Windows
# ET l'intégration WSL2 doit être activée :
# Docker Desktop → Settings → Resources → WSL Integration → Ubuntu → ON
```

**Maven ne compile pas (Java 24 not found)**
```bash
# Vérifier la version Java dans WSL2
java -version
# Si < 21 : installer via SDKMAN
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh
sdk install java 21.0.5-tem
sdk use java 21.0.5-tem
```

---

## Résumé des ports

| Port | Service | Usage |
|------|---------|-------|
| 8080 | Spring Boot API | Ton application |
| 5432 | PostgreSQL | Base de données |
| 8180 | Keycloak | Auth (admin: admin/admin) |
| 1025 | MailHog SMTP | Envoi e-mails (dev) |
| 8025 | MailHog UI | Voir les e-mails envoyés |
| 6379 | Redis | Cache + JWT blocklist |
| 8001 | RedisInsight | Interface Redis |
