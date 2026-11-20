# Fiche technique — GMLSHILD

[← Accueil du projet](../README.md) · [Recette détaillée](RECETTE_DETAILLEE.md) · [Architecture](ARCHITECTURE.md) · [Contrat API](API.md)

**GMLSHILD** est une application de gestion sécurisée des missions de transport de GreenMove Logistics. Elle permet aux Dispatchers de transmettre et de suivre des missions attribuées à des chauffeurs, avec **un code d'accès chiffré**, des preuves de livraison et un journal d'audit.

Elle fournit une alternative structurée aux échanges de données opérationnelles sensibles par messages ou courriels. Le projet est réalisé dans le cadre du **Mastère CTO & Tech Lead — Studi**.

**Application déployée :** https://gml-shield-demo.fr  
**Dépôt :** [gml-secure-missions](https://github.com/anne1porta-ai/gml-secure-missions)

## 1. Recette et preuves

La [recette détaillée par exigence](RECETTE_DETAILLEE.md) relie, pour chaque contrôle :

- le comportement attendu et réellement implémenté ;
- le **fichier et, si possible, la méthode** qui le réalisent ;
- le **test automatisé** qui apporte une preuve ;
- la **vérification manuelle** possible et les limites connues.

La [recette fonctionnelle](RECETTE_FONCTIONNELLE.md) reste la matrice synthétique destinée à vérifier les exigences fonctionnelles. Les deux documents sont complémentaires.

## 2. Application et rôles

| Rôle | Fonctions principales |
| --- | --- |
| **ADMIN** | Gestion des comptes et du journal d'audit, annulation des missions autorisées |
| **DISPATCHER** | Création, attribution, modification et réaffectation de missions en attente, consultation des justificatifs |
| **DRIVER** | Consultation de ses missions, démarrage, révélation du code pendant la livraison, dépôt de preuve, clôture |

**Cycle de vie :** `PENDING` → `IN_PROGRESS` → `COMPLETED`. L'annulation autorisée conduit à `CANCELLED`. La clôture est refusée si aucun justificatif n'a été déposé.

Le **code secret n'est jamais présent dans `GET /api/missions`**, pour aucun rôle. Le chauffeur assigné le demande avec une route dédiée pendant `IN_PROGRESS` ; il est masqué dans l'interface au bout de 30 secondes.

## 3. API — principales routes

Les contrôles d'accès s'appliquent **côté Spring Boot**, indépendamment de l'affichage React.

| Méthode et route | Accès | Fonction |
| --- | --- | --- |
| `GET /api/auth/csrf` | Public | Prépare le jeton CSRF |
| `POST /api/auth/login` | Public | Ouvre la session, cookies JWT et renouvellement |
| `GET /api/auth/me` | Session valide | Identité et rôle de l'utilisateur |
| `POST /api/auth/refresh` | Cookie de renouvellement valide | Fait tourner les jetons |
| `POST /api/auth/logout` | Session / cookie | Efface les cookies et révoque le refresh token présenté |
| `GET /api/missions/assignable-drivers` | DISPATCHER | Chauffeurs actifs pouvant recevoir une mission |
| `POST /api/missions` | DISPATCHER | Crée une mission et chiffre le code avant stockage |
| `GET /api/missions` | DISPATCHER, DRIVER | Missions visibles pour l'utilisateur, sans code secret |
| `PATCH /api/missions/{id}` | DISPATCHER propriétaire | Modifie une mission en attente |
| `PATCH /api/missions/{id}/driver` | DISPATCHER propriétaire | Réaffecte une mission en attente |
| `PATCH /api/missions/{id}/cancel` | ADMIN ou DISPATCHER propriétaire | Annule une mission admissible |
| `PATCH /api/missions/{id}/status` | DRIVER assigné | Démarre ou termine la mission, avec preuve exigée pour clôturer |
| `GET /api/missions/{id}/secret-code` | DRIVER assigné, mission en cours | Révèle le code à la demande, réponse `no-store` |
| `POST /api/missions/{missionId}/documents` | DRIVER assigné | Dépose un PDF, JPEG ou PNG de 10 Mo maximum |
| `GET /api/missions/{missionId}/documents` | DISPATCHER ou DRIVER concerné | Liste les preuves |
| `GET /api/missions/{missionId}/documents/{docId}` | DISPATCHER propriétaire | Télécharge une preuve |
| `GET /api/notifications/stream` | DRIVER | Notifications de création, modification, annulation et réaffectation en **SSE** |
| `GET /api/audit-logs` | ADMIN | Journal des opérations suivies |
| `GET /api/users` / `POST /api/users` / `PATCH /api/users/{id}/active` | ADMIN | Consultation, création et activation de comptes |

Sur le backend directement accessible, `GET /actuator/health` renvoie l'état de santé. Les routes OpenAPI sont `/v3/api-docs` et `/swagger-ui/index.html` ; leur accessibilité depuis Internet dépend du proxy déployé.

**Contrat complet, réponses d'erreur et limites de visibilité :** [`API.md`](API.md).

## 4. Déploiement et stockage

| Élément | Implantation actuelle | Mesures et limites |
| --- | --- | --- |
| **Interface** | React et Nginx dans un conteneur Docker | Proxy vers les routes `/api/` |
| **API** | Java 17 / Spring Boot 4 dans un conteneur Docker sur OVH | Spring Security, rôle + propriété, cookies et CSRF |
| **Données** | PostgreSQL **16**, service `database` et volume persistant | Migrations Flyway ; sauvegarde avant déploiement |
| **Justificatifs** | Volume privé associé au backend | Contrôle de taille/type, téléchargement par API ; pas d'antivirus intégré |
| **Notifications** | Server-Sent Events (SSE) dans le backend | Diffusion ciblée ; connexions conservées en mémoire, non persistantes |
| **Publication** | GitHub Actions → SSH OVH → Docker Compose | Déclenchement après CI réussie sur `main` ; vérification HTTPS |

Les secrets de connexion et la clé AES proviennent de l'environnement. La déconnexion révoque le **refresh token**, mais ne met pas immédiatement en liste noire un JWT d'accès déjà émis.

Le workflow vérifie les sauvegardes PostgreSQL et documents avant la mise à jour. Il ne réalise **ni restauration automatique ni rollback automatique**.

Voir [l'architecture](ARCHITECTURE.md), [la sécurité](SECURITE.md) et [l'exploitation](PRODUCTION_TOOLS.md).

## 5. Commandes et contrôles qualité

| Lieu | Commande | Vérification |
| --- | --- | --- |
| Dossier `backend/` | `mvn -B -ntp spotless:check` | Formatage Java |
| Dossier `backend/` | `mvn -B -ntp verify` | Tests unitaires/intégration PostgreSQL, rapport JaCoCo et seuils de couverture |
| Dossier `backend/` | `mvn -B -ntp spotbugs:check` | Analyse statique |
| Dossier `frontend/` | `npm ci` puis `npm run quality` | ESLint, Prettier, tests JavaScript et compilation |
| Racine, PowerShell | `.\scripts\run-all-tests.ps1` | Contrôles locaux et parcours API sur base isolée |
| Racine, PowerShell | `.\scripts\run-security-tests.ps1` | Tests de sécurité Java, React et Chromium sur base isolée |
| Racine, PowerShell | `.\scripts\run-functional-tests.ps1` | Neuf parcours utilisateur dans Chromium sur base isolée |
| GitHub | [Workflow CI](../.github/workflows/ci.yml) | Six jobs, dont sécurité et Chromium |
| GitHub | [Workflow périodique de sécurité](../.github/workflows/security.yml) | Dépendances Java et frontend |

Le lancement local, la gestion du fichier `.env` et l'arrêt des conteneurs sont expliqués dans [`DEMARRAGE_LOCAL.md`](DEMARRAGE_LOCAL.md) ; ces opérations ne sont pas détaillées ici.

**Résultats actuels :** [GitHub Actions — CI](https://github.com/anne1porta-ai/gml-secure-missions/actions/workflows/ci.yml) et [Déploiement OVH](https://github.com/anne1porta-ai/gml-secure-missions/actions/workflows/deploy-ovh.yml). Le résultat d'une exécution n'est pas une certification de sécurité.

## 6. Organisation du code

```text
gml-secure-missions/
├── backend/
│   ├── src/main/java/com/gml/shield/
│   │   ├── config/          Configuration HTTP, sécurité, audit, initialisation
│   │   ├── controller/      Routes REST
│   │   ├── dto/             Contrats de requête et de réponse
│   │   ├── enums/           Rôles et statuts
│   │   ├── event/           Événement de mission et envoi après commit
│   │   ├── mapper/          Conversion entre modèles et DTO
│   │   ├── model/           Entités persistées
│   │   ├── repository/      Requêtes PostgreSQL via JPA
│   │   ├── security/        JWT, cookies, filtres et sessions
│   │   └── service/         Règles métier et stockage privé
│   ├── src/main/resources/db/migration/  Migrations Flyway
│   └── src/test/                        Tests Java et PostgreSQL
├── frontend/
│   ├── src/pages/          Pages par rôle
│   ├── src/components/     Missions, formulaires, comptes, audit, documents
│   ├── src/hooks/          Notifications SSE
│   └── test/               Tests JavaScript
├── e2e/tests/              Scénarios Playwright / Chromium
├── scripts/                Démarrage et contrôles locaux
├── scripts/operations/     Diagnostic et sauvegardes
├── docs/                   Architecture, API, sécurité, recettes, exploitation
└── .github/workflows/      Intégration, sécurité et déploiement
```

Pour une lecture métier, commencer par [les exigences fonctionnelles](EXIGENCES_FONCTIONNELLES.md). Pour la preuve de chaque exigence, ouvrir la [recette détaillée](RECETTE_DETAILLEE.md).

---

**Périmètre :** logiciel pédagogique et déployé, sans revendication d'audit d'intrusion, de conformité réglementaire certifiée ou de haute disponibilité.
