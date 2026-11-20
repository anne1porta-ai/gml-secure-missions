# Contrat API GMLSHILD

> Pour retrouver les parcours de l'interface et leurs vérifications, voir la [matrice de recette fonctionnelle](RECETTE_FONCTIONNELLE.md).

## 1. Principes

- Base URL locale backend : `http://localhost:8080`.
- Base URL Docker via frontend : `http://localhost:8088`.
- Authentification : cookies HttpOnly contenant le JWT d'accès et le refresh token.
- Requêtes mutantes : protection CSRF via cookie `XSRF-TOKEN` et header `X-XSRF-TOKEN`.
- Les entités JPA ne sont pas exposées directement sur les endpoints Mission/Document.

## 2. Format d'erreur

Les erreurs applicatives utilisent un contrat commun :

```json
{
  "code": "VALIDATION_ERROR",
  "error": "Requête invalide",
  "details": [
    "driverEmail : doit être une adresse e-mail syntaxiquement correcte"
  ],
  "timestamp": "2026-09-05T14:00:00Z"
}
```

Le champ `details` est absent lorsqu'il n'apporte rien. Le champ `error` est conservé pour la compatibilité avec le frontend.

Codes courants :

| Code | HTTP | Sens |
|---|---:|---|
| `UNAUTHENTICATED` | 401 | Session absente ou JWT invalide |
| `FORBIDDEN` | 403 | Rôle/permission insuffisant |
| `AUTH_INVALID_CREDENTIALS` | 401 | Email ou mot de passe invalide |
| `AUTH_LOCKED` | 429 | Couple email/IP temporairement bloqué |
| `AUTH_RATE_LIMITED` | 429 | Trop de POST login depuis la même IP |
| `VALIDATION_ERROR` | 400 | Validation DTO échouée |
| `BAD_REQUEST` | 400 | Paramètre de route invalide, notamment identifiant non numérique |
| `MISSION_BAD_REQUEST` | 400 | Règle métier mission invalide |
| `MISSION_NOT_FOUND` | 404 | Mission non visible/inexistante |
| `FILE_REJECTED` | 400 | Fichier vide, trop gros ou MIME interdit |
| `INTERNAL_ERROR` | 500 | Erreur serveur non exposée au client |

Sur une réponse `429`, le serveur fournit un header `Retry-After`.

## 3. Authentification

### `GET /api/auth/csrf`

Public. Initialise la protection CSRF.

Réponse :

```json
{ "token": "..." }
```

### `POST /api/auth/login`

Public + CSRF.

```json
{
  "email": "driver1@gml.local",
  "password": "driver123"
}
```

Réponse 200 :

```json
{
  "email": "driver1@gml.local",
  "role": "DRIVER"
}
```

Effets :

- cookie HttpOnly `gml_token` ;
- cookie HttpOnly `gml_refresh_token` ;
- compte inactif refusé ;
- BCrypt vérifie le mot de passe ;
- limitation par IP et verrouillage sur échecs répétés.

### `POST /api/auth/refresh`

Public + CSRF. Utilise le cookie refresh, vérifie son expiration et le compte actif, puis effectue une rotation du refresh token.

### `POST /api/auth/logout`

Public + CSRF. Révoque le refresh token en base et expire les deux cookies.

### `GET /api/auth/me`

Retourne l'utilisateur courant.

## 4. Missions

### `POST /api/missions`

Rôle : `DISPATCHER`.

```json
{
  "title": "Livraison matériel médical",
  "destination": "Lyon - Entrepôt A",
  "secretCode": "WH-42-SECRET",
  "driverEmail": "driver1@gml.local",
  "description": "Quai 4, sonner à l'accueil",
  "scheduledAt": "2026-11-02T08:30"
}
```

`description` et `scheduledAt` sont facultatifs. Le service vérifie que le Driver existe, possède le rôle `DRIVER` et est actif. Le `secretCode` est chiffré avant l'INSERT.

Aucune réponse de mission n'expose le secret, quel que soit le rôle.

### `GET /api/missions/assignable-drivers`

Rôle : `DISPATCHER`. Liste des chauffeurs **actifs**, triée par e-mail (`[{ "email": "driver1@gml.local" }]`), utilisée par le formulaire de création. Même règle qu'à la création : un chauffeur inactif ne peut pas recevoir de mission.

### `GET /api/missions`

Rôles : `DISPATCHER`, `DRIVER`.

- Dispatcher : uniquement les missions qu'il a créées.
- Driver : uniquement ses missions assignées.

Aucune mission listée ne contient le code d'accès : le Driver le demande via `GET /api/missions/{id}/secret-code`.

### `PATCH /api/missions/{id}/status`

Rôle : `DRIVER` assigné.

```json
{ "status": "IN_PROGRESS" }
```

Transitions autorisées uniquement :

```text
PENDING -> IN_PROGRESS -> COMPLETED
```

Aucun saut ou retour arrière n'est accepté. Une transition invalide retourne actuellement `400` avec le code applicatif `MISSION_BAD_REQUEST`. Le contrat actuel ne prévoit pas de verrou optimiste ni de réponse `409`/`422` pour ces transitions ; cette limite est suivie sous `TD-07` dans le [registre de dette technique](TECHNICAL_DEBT_REGISTER.md).

### `GET /api/missions/{id}/secret-code`

Rôle : `DRIVER` assigné, mission `IN_PROGRESS` (RG2).

```json
{ "secretCode": "WH-42-SECRET" }
```

- la réponse porte `Cache-Control: no-store` ;
- mission d'un autre Driver : `404 MISSION_NOT_FOUND`, sans révéler son existence ;
- mission `PENDING` ou `COMPLETED` : `409 SECRET_CODE_UNAVAILABLE` ;
- autre rôle : `403 FORBIDDEN` ;
- chaque appel, accepté ou refusé, est enregistré dans le journal d'audit avec son statut HTTP.

## 5. Documents

### `POST /api/missions/{missionId}/documents`

Rôle : `DRIVER` assigné. Multipart `file`.

Contrôles :

- 10 Mo maximum ;
- PDF/JPEG/PNG ;
- type MIME réel détecté avec Apache Tika ;
- nom physique généré par UUID ;
- stockage hors contenu web public.

### `GET /api/missions/{missionId}/documents`

Rôles : Dispatcher propriétaire ou Driver assigné.

### `GET /api/missions/{missionId}/documents/{docId}`

Rôle : Dispatcher propriétaire uniquement.

La réponse force le téléchargement et ajoute `X-Content-Type-Options: nosniff`.

## 6. Temps réel SSE

### `GET /api/notifications/stream`

Rôle : `DRIVER`.

Content-Type : `text/event-stream`.

Événements :

- `CONNECTED` à l'ouverture ;
- `NEW_MISSION` après le commit d'une mission assignée.

Exemple de payload `NEW_MISSION` : les données viennent de `MissionCreatedEvent` et ne contiennent aucun secret d'authentification.

REST reste la source de vérité : après reconnexion SSE, le frontend recharge `GET /api/missions`.

## 7. Audit

### `GET /api/audit-logs`

Rôle : `ADMIN`.

Retourne les traces récentes enregistrées pour les opérations mutantes interceptées par le backend, ainsi que les lectures du code d'accès. Elles sont consultables dans l'onglet « Journal d'audit » de la vue Admin.

## 7 bis. Comptes (Admin)

Toutes ces routes exigent le rôle `ADMIN` ; chaque création ou changement d'état est audité.

### `GET /api/users`

Liste des comptes, triée par e-mail : `id`, `email`, `role`, `active`. Aucun mot de passe ni hash n'est renvoyé.

### `POST /api/users`

```json
{ "email": "chauffeur@gml.local", "temporaryPassword": "<mot-de-passe-temporaire>", "role": "DRIVER" }
```

- rôles autorisés : `DISPATCHER` ou `DRIVER` ; `ADMIN` est refusé (`400 USER_MANAGEMENT_REJECTED`) ;
- e-mail normalisé en minuscules, unique (`409 EMAIL_ALREADY_USED`) ;
- mot de passe temporaire de 12 à 64 caractères, haché avec BCrypt ;
- réponse `201` avec le compte créé.

### `PATCH /api/users/{id}/active`

```json
{ "active": false }
```

Active ou désactive un compte Dispatcher ou Driver. Un compte administrateur n'est pas modifiable (`400 USER_MANAGEMENT_REJECTED`) ; un identifiant inconnu renvoie `404 USER_NOT_FOUND`. La désactivation supprime aussi le refresh token du compte ; son JWT éventuel est déjà refusé par le filtre d'authentification.

### Compte administrateur initial

Il n'existe aucun mot de passe administrateur dans le code. Au démarrage, si aucun administrateur n'existe, le backend crée celui défini par `ADMIN_EMAIL` et `ADMIN_PASSWORD` (12 caractères minimum). Un compte existant n'est jamais promu administrateur.

## 8. Matrice d’accès exhaustive

Cette matrice distingue **accessibilité HTTP**, **authentification effective**, **rôle** et **ownership**. Les routes `/api/auth/**` sont `permitAll` au niveau de la chaîne Spring Security afin que login/refresh puissent fonctionner sans access token ; certaines appliquent néanmoins leurs propres contrôles applicatifs.

| Méthode | Endpoint | Accès effectif | Contrôle ressource / remarque |
|---|---|---|---|
| `GET` | `/api/auth/csrf` | Public | initialise le token CSRF |
| `POST` | `/api/auth/login` | Public + CSRF | rate limit IP + échecs email/IP |
| `POST` | `/api/auth/refresh` | Public + CSRF | exige un refresh cookie valide côté application |
| `POST` | `/api/auth/logout` | Public + CSRF | révoque le refresh s’il existe puis efface les cookies |
| `GET` | `/api/auth/me` | identité effective requise | route techniquement `permitAll`, mais le contrôleur renvoie `401` sans principal actif |
| `POST` | `/api/missions` | `DISPATCHER` | Driver cible doit exister, être actif et avoir le rôle `DRIVER` |
| `GET` | `/api/missions/assignable-drivers` | `DISPATCHER` | chauffeurs actifs uniquement, e-mails seuls |
| `GET` | `/api/missions` | `DISPATCHER` ou `DRIVER` | Dispatcher : créées par lui ; Driver : assignées à lui |
| `PATCH` | `/api/missions/{id}/status` | `DRIVER` | Driver **assigné** + transition linéaire valide |
| `POST` | `/api/missions/{missionId}/documents` | `DRIVER` | Driver **assigné** à la mission |
| `GET` | `/api/missions/{missionId}/documents` | `DISPATCHER` ou `DRIVER` | Dispatcher propriétaire ou Driver assigné |
| `GET` | `/api/missions/{missionId}/documents/{docId}` | `DISPATCHER` | Dispatcher **propriétaire** de la mission |
| `GET` | `/api/notifications/stream` | `DRIVER` | flux lié à l’identité du principal ; aucun ID Driver dans l’URL |
| `GET` | `/api/audit-logs` | `ADMIN` | consultation des traces prévues par le MVP |
| `GET` | `/api/users` | `ADMIN` | liste des comptes, sans mot de passe |
| `POST` | `/api/users` | `ADMIN` | création de comptes `DISPATCHER` ou `DRIVER` uniquement |
| `PATCH` | `/api/users/{id}/active` | `ADMIN` | activation/désactivation, comptes `ADMIN` exclus |
| `GET` | `/api/missions/{id}/secret-code` | `DRIVER` | Driver **assigné**, mission `IN_PROGRESS`, réponse `no-store`, appel audité |
| `GET` | `/actuator/health` | Public | état technique minimal |
| `GET` | `/actuator/info` | Public | informations Actuator configurées |
| `GET` | `/swagger-ui/**`, `/v3/api-docs/**` | Public | documentation technique ; à restreindre selon politique de production |

### Lecture 401 / 403 / 404

- `401` : identité absente/invalide quand elle est nécessaire ;
- `403` : identité valide mais rôle insuffisant ;
- `404` peut être utilisé par les services lorsqu’une ressource n’est pas visible pour l’utilisateur courant, afin de ne pas élargir inutilement la visibilité sur son existence.

Le frontend ne constitue jamais une frontière de sécurité : masquer un bouton par rôle améliore l’UX, mais seul le backend fait foi.

## 9. Contrats de données principaux

### CreateMissionRequest

| Champ | Contraintes | Sensibilité |
|---|---|---|
| `title` | obligatoire, max 150 | métier |
| `destination` | obligatoire, max 500 | sensible |
| `secretCode` | obligatoire, max 200 | **critique** |
| `driverEmail` | obligatoire, email valide, max 254 | identité métier |
| `description` | facultatif, max 1000 ; vide = absent | métier |
| `scheduledAt` | facultatif, date-heure locale sans fuseau (`2026-11-02T08:30`) | métier |

Le `dispatcherEmail` n’est pas fourni par le client : il est dérivé du principal authentifié.

### MissionResponse

| Champ | Dispatcher | Driver |
|---|:---:|:---:|
| `id` | ✓ | ✓ |
| `title` | ✓ | ✓ |
| `destination` | ✓ | ✓ |
| `description` | ✓ | ✓ |
| `scheduledAt` | ✓ | ✓ |
| `status` | ✓ | ✓ |
| `driverEmail` | ✓ | ✓ |
| `dispatcherEmail` | ✓ | ✓ |
| `createdAt` | ✓ | ✓ |

### DocumentResponse

Le contrat ne renvoie pas le chemin physique de stockage :

```json
{
  "id": 42,
  "originalFilename": "proof.pdf",
  "mimeType": "application/pdf",
  "uploadedAt": "2026-09-05T14:00:00"
}
```

### AuditLogResponse

Le contrat d’audit est également séparé de l’entité JPA et expose uniquement les champs nécessaires à l’Admin.

## 10. Sémantique d’autorisation

Le projet combine deux niveaux :

```text
RBAC       : ai-je le bon rôle ?
Ownership  : cette ressource est-elle réellement à moi / assignée à moi ?
```

Un endpoint protégé par rôle n’est pas considéré comme suffisamment sécurisé si la ressource appartient à un utilisateur spécifique.

## 11. Catalogue d’erreurs complémentaire

| Code | HTTP | Cas typique |
|---|---:|---|
| `MALFORMED_JSON` | 400 | JSON impossible à parser |
| `BAD_REQUEST` | 400 | argument invalide générique |
| `FILE_TOO_LARGE` | 400 | dépassement multipart 10 Mo |
| `DOCUMENT_NOT_FOUND` | 404 | preuve absente/non visible |
| `ROUTE_NOT_FOUND` | 404 | route inconnue |
| `METHOD_NOT_ALLOWED` | 405 | méthode HTTP non autorisée |
| `REFRESH_TOKEN_MISSING` | 401 | cookie refresh absent |
| `REFRESH_TOKEN_INVALID` | 401 | refresh expiré/révoqué/invalide |
| `FILE_STORAGE_ERROR` | 500 | échec écriture stockage |
| `FILE_READ_ERROR` | 500 | échec lecture preuve |

## 12. Ordre recommandé côté frontend lors du démarrage

```text
GET /api/auth/csrf
       |
       v
GET /api/auth/me
       |
       +-- 200 --> session restaurée
       |
       +-- 401 --> écran login
```

Après un `401` sur une requête métier, l’intercepteur Axios tente une seule fois `POST /api/auth/refresh`, puis rejoue la requête initiale si le renouvellement réussit.

## 13. Règle de compatibilité

Une évolution API doit être considérée comme cassante si elle :

- renomme un champ consommé par React ;
- modifie le sens d’un code d’erreur ;
- expose soudainement un champ sensible ;
- change le cycle de statuts ;
- transforme une ressource cachée en `403`/`404` avec une sémantique différente sans mise à jour du client.


## 14. Versionnement externe futur

Le MVP utilise `/api/...` sans version majeure dans le chemin car le frontend et le backend sont livrés ensemble. Avant exposition à des applications partenaires, une stratégie de compatibilité devra être figée (par exemple `/api/v1/...`) afin qu'une évolution backend ne casse pas silencieusement un client tiers. Ce point appartient à la phase 2 et n'est pas présenté comme déjà implémenté.
