# Sécurité et traçabilité

[← Présentation du projet](../README.md) · [Exigences fonctionnelles](EXIGENCES_FONCTIONNELLES.md) · [Limites connues](TECHNICAL_DEBT_REGISTER.md)

Ce document présente les **contrôles réellement implémentés** dans GMLSHILD et leur périmètre. Il ne constitue pas un audit d'intrusion ou une certification de sécurité.

## 1. Authentification et sessions

| Mécanisme | Fonctionnement |
| --- | --- |
| **Mots de passe** | Hachage avec BCrypt avant stockage. |
| **Connexion** | Vérification des identifiants et contrôle du compte actif. |
| **JWT d'accès** | Jeton signé envoyé dans un cookie `HttpOnly`. |
| **Renouvellement** | Refresh token conservé en base, transmis dans un cookie `HttpOnly` limité au chemin `/api/auth`. La création d'une nouvelle session invalide l'ancien refresh token de l'utilisateur. |
| **Déconnexion ou désactivation** | Effacement des cookies à la déconnexion ; révocation du jeton de renouvellement. La désactivation interdit également l'accès des comptes inactifs. |
| **Protection CSRF** | Cookie `XSRF-TOKEN` et en-tête `X-XSRF-TOKEN` sur les requêtes qui modifient les données. |
| **Tentatives répétées** | Limitation des connexions, avec blocage lié aux échecs et filtre par adresse IP. |

Les cookies d'authentification utilisent les attributs `HttpOnly` et `SameSite=Lax`. L'attribut `Secure` est piloté par la configuration et doit être activé pour l'accès HTTPS en production. Le cookie CSRF est lisible par le frontend pour pouvoir renvoyer sa valeur dans l'en-tête prévu.

Le JWT n'est pas passé dans l'URL du flux SSE.

**Dans le code :** [`SecurityConfig.java`](../backend/src/main/java/com/gml/shield/config/SecurityConfig.java), [`AuthCookieService.java`](../backend/src/main/java/com/gml/shield/security/AuthCookieService.java), [`RefreshTokenService.java`](../backend/src/main/java/com/gml/shield/security/RefreshTokenService.java), [`LoginRateLimitFilter.java`](../backend/src/main/java/com/gml/shield/security/LoginRateLimitFilter.java).

## 2. Autorisations : rôle **et** propriété

Les contrôleurs restreignent les routes aux rôles autorisés. Les services vérifient ensuite que l'utilisateur peut accéder à la **mission demandée**.

Exemples :

- Un Dispatcher ne peut modifier, réaffecter ou annuler que ses missions autorisées.
- Un chauffeur ne peut démarrer, terminer, consulter le code ou déposer une preuve que sur une mission qui lui est assignée.
- Un Dispatcher ne peut télécharger le justificatif d'une mission attribuée à un autre Dispatcher.
- Une mission non visible est traitée comme introuvable pour ne pas révéler inutilement son existence.

Les droits sont appliqués dans le backend : les restrictions d'affichage dans React ne sont qu'une aide à l'utilisation.

Les routes et réponses sont décrites dans le [contrat API](API.md).

## 3. Codes secrets des missions

**Avant stockage :** chaque code est chiffré avec **AES-256-GCM**, à partir d'une clé fournie par l'environnement. Un vecteur d'initialisation aléatoire est généré pour chaque opération.

**Pendant l'utilisation :** le code n'est pas inclus dans les listes ni dans `MissionResponse`. Il est restitué par une route dédiée, après contrôle du rôle, du chauffeur affecté et du statut `IN_PROGRESS`.

**À l'écran :** le navigateur conserve temporairement le code en mémoire, puis le masque après **30 secondes** ; l'utilisateur peut aussi le masquer immédiatement. La réponse du serveur impose `Cache-Control: no-store`.

**Après clôture :** l'API refuse de révéler de nouveau le code.

**Dans le code :** [`SecretEncryptionService.java`](../backend/src/main/java/com/gml/shield/service/SecretEncryptionService.java), [`MissionService.java`](../backend/src/main/java/com/gml/shield/service/MissionService.java), [`MissionList.jsx`](../frontend/src/components/MissionList.jsx).

## 4. Preuves de livraison

Le serveur n'accepte que les fichiers **PDF, JPEG et PNG**, limités à **10 Mo**. Le type de fichier est identifié à partir du **contenu** par Apache Tika, plutôt qu'en faisant confiance à l'extension ou au type déclaré par le navigateur.

Le fichier est enregistré sous un nom généré avec un identifiant aléatoire. Les chemins de lecture restent contraints au répertoire autorisé ; l'API vérifie l'accès aux documents avant le téléchargement.

**Limite importante :** la détection du type par Tika **n'est pas une analyse antivirus**. L'absence de scanner de malwares figure dans le [registre de dette technique](TECHNICAL_DEBT_REGISTER.md).

**Dans le code :** [`DocumentService.java`](../backend/src/main/java/com/gml/shield/service/DocumentService.java) et [`FileStorageService.java`](../backend/src/main/java/com/gml/shield/service/FileStorageService.java).

## 5. Journal d'audit

L'application enregistre les requêtes modifiant des données (notamment `POST`, `PATCH`, `PUT`, `DELETE`) et **chaque consultation de code secret**, acceptée ou refusée. Les événements contiennent notamment l'action, le statut HTTP, l'utilisateur lorsqu'il est identifié et l'adresse IP connue du backend.

L'audit est exposé à l'administrateur dans l'interface. L'enregistrement est asynchrone, dans la base applicative : **ce n'est pas un journal inaltérable** et cela ne garantit pas à lui seul la conformité à une réglementation.

**Dans le code :** [`WriteRequestAuditInterceptor.java`](../backend/src/main/java/com/gml/shield/config/WriteRequestAuditInterceptor.java) et [`AuditLogService.java`](../backend/src/main/java/com/gml/shield/service/AuditLogService.java).

## 6. Tests d'injection et de rendu HTML

Quatre tests d'intégration, exécutés avec une base PostgreSQL temporaire, vérifient le comportement HTTP face à des entrées évoquant une injection SQL :

- E-mail de connexion contenant une expression SQL : **HTTP 400**, validation refusée.
- Mot de passe contenant une expression SQL : **HTTP 401**, authentification refusée.
- Titre de mission contenant `DROP TABLE` : **créé comme du texte**, table et mission conservées.
- Identifiant de mission non numérique : **HTTP 400** avec le code `BAD_REQUEST`, sans erreur serveur 500.

Ces scénarios se trouvent dans [`InjectionSecurityIntegrationTest.java`](../backend/src/test/java/com/gml/shield/InjectionSecurityIntegrationTest.java). Les tests ne sont **pas exécutés contre la production**.

Le test [`MissionList.test.js`](../frontend/test/MissionList.test.js) vérifie aussi qu'un titre contenant `<script>` est **échappé lors du rendu React**. Il ne constitue ni une simulation complète d'attaque XSS dans un navigateur, ni une preuve de politique CSP.

### Vérifications directement dans le navigateur

Le fichier [`e2e/tests/securite.spec.js`](../e2e/tests/securite.spec.js) utilise **Playwright/Chromium** : rejet de l'e-mail malformé par le formulaire, refus HTTP 401 pour un mot de passe ressemblant à une injection SQL, conservation d'une instruction SQL comme titre de mission et rendu inerte d'une balise `<script>` dans le titre.

Le refus de l'e-mail dans l'IHM est une **validation du navigateur**, différente du test Java qui contrôle la réponse HTTP 400 du backend. Les tests sont exécutés sur une installation locale ou la base de test isolée de GitHub Actions.

**Commande unique Windows :** `.\scripts\run-security-tests.ps1`, avec `-Visible` pour voir Chromium et `-Debug` pour le mode pas à pas. Elle regroupe les contrôles Java/PostgreSQL, React et Playwright dans un environnement de test isolé. Voir [Tests fonctionnels et de sécurité](TESTS_FONCTIONNELS.md) pour les prérequis, les commandes et les limites.

## 7. Limites à ne pas masquer

| Sujet | État actuel / point de vigilance |
| --- | --- |
| Refresh tokens | Valeur opaque conservée en base, non hachée. |
| Chiffrement | Pas de version de clé dans le format chiffré ; rotation progressive non prévue. |
| Protection des connexions | Compteurs et limiteurs locaux à l'instance. |
| Audit | Asynchrone, non inaltérable et couverture incomplète des lectures sensibles. |
| Documents | Pas de scan antivirus / quarantaine. |
| Exploitation | Vérifier les sauvegardes, la restauration, la configuration du proxy et le HTTPS. |
| Disponibilité | Pas de revendication de haute disponibilité ou de multi-instance. |

Les risques, priorités et déclencheurs d'évolution sont suivis dans [`TECHNICAL_DEBT_REGISTER.md`](TECHNICAL_DEBT_REGISTER.md). Les tests de sécurité sont expliqués dans [Qualité, tests et CI/CD](QUALITE_CICD.md).
