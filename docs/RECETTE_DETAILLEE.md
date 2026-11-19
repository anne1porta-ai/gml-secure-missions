# Tableau de recette détaillée — GML-SHIELD

[← Accueil du projet](../README.md) · [Fiche technique](REFERENCE_TECHNIQUE.md) · [Recette fonctionnelle](RECETTE_FONCTIONNELLE.md) · [Sécurité](SECURITE.md)

Ce tableau relie les **exigences**, le **code réellement présent**, les **tests automatisés disponibles** et les **vérifications visibles**. Il reprend la répartition **S (sécurité), A (authentification), RG (règles de gestion), F (fonctionnalités)**, avec des références adaptées à cette version de GML-SHIELD.

La numérotation sert à naviguer dans cette recette ; elle ne constitue pas une déclaration de conformité à un référentiel réglementaire ou un renvoi vérifié aux annexes officielles d'un énoncé. Il faut également distinguer **code implémenté**, **test automatisé identifié** et **contrôle d'exploitation**.

**Application de démonstration :** https://gml-shield-demo.fr. Pour réaliser les scénarios qui créent ou modifient des données, utiliser de préférence un environnement local de test ou une base isolée ; ne pas effectuer de tests destructifs sur la production.

**Chemins :** dans les tableaux, les chemins Java courts commencent dans `backend/src/main/java/com/gml/shield/` ; les noms de tests Java se trouvent dans `backend/src/test/java/com/gml/shield/` sauf indication contraire.

**Lecture à l'écran :** les tableaux présentent une vue synthétique. Cliquer sur une preuve pour retrouver le code, les tests et les vérifications détaillées sans défilement horizontal.

## 1. Sécurité

| ID | Exigence | Preuve principale |
| --- | --- | --- |
| **S1** | Mot de passe non stocké en clair | [UserManagementServiceTest.java](#s1) |
| **S2** | Protection du transport | [SecurityIntegrationTest.java](#s2) |
| **S3** | Réduire le risque d'injection SQL | [InjectionSecurityIntegrationTest.java](#s3) |
| **S4** | Prévenir l'exécution de contenu saisi par un utilisateur | [MissionList.test.js](#s4) |
| **S5** | Protéger les jetons de session côté navigateur | [SecurityIntegrationTest.java](#s5) |
| **S6** | Protection CSRF | [SecurityIntegrationTest.java](#s6) |
| **S7** | Limiter les connexions répétées | [LoginAttemptServiceTest.java](#s7) |
| **S8** | Ne pas exposer d'erreurs techniques dans les réponses REST | [SecurityIntegrationTest.java](#s8) |
| **S9** | Chiffrer/authentifier les échanges vers une base externe | [Pas de test TLS identifié](#s9) |

### Détail des preuves — sécurité

#### S1

- **Où dans le code :** `config/SecurityConfig.java` (`passwordEncoder` : BCrypt), `service/UserManagementService.java` (`createUser`), `model/User.java`
- **Tests et preuves automatisées :** `service/UserManagementServiceTest.java` (`createsDriverWithNormalizedEmailAndHashedTemporaryPassword`) ; `SecurityIntegrationTest.java` (`adminCreatesDriverWhoCanThenLogIn`)
- **Vérification et limites :** En base de test, la colonne de mot de passe contient une empreinte. **Aucune contrainte SQL spécifique au format BCrypt n'est revendiquée.**

#### S2

- **Où dans le code :** `security/AuthCookieService.java` (cookie `Secure` configurable), `frontend/nginx.conf`, workflow `.github/workflows/deploy-ovh.yml`
- **Tests et preuves automatisées :** Le déploiement vérifie une réponse par **HTTPS** ; `SecurityIntegrationTest.java` (`loginCreatesHttpOnlyAccessAndRefreshCookies`) vérifie les cookies
- **Vérification et limites :** Contrôler le certificat HTTPS et la configuration des cookies sur l'installation. **TLS, HSTS et le reverse proxy ne font pas l'objet d'un test complet dans le dépôt.**

#### S3

- **Où dans le code :** `repository/MissionRepository.java` (requêtes JPQL à paramètres nommés), Spring Data JPA, validations DTO, `controller/ApiExceptionHandler.java`
- **Tests et preuves automatisées :** **`InjectionSecurityIntegrationTest.java`** : e-mail injecté refusé (400), mot de passe injecté refusé (401), titre SQL conservé comme texte (200), identifiant non numérique refusé (400) ; tests avec PostgreSQL Testcontainers
- **Vérification et limites :** **Quatre scénarios HTTP** complétés par [`e2e/tests/securite.spec.js`](../e2e/tests/securite.spec.js), qui saisit des injections depuis l'IHM. L'e-mail malformé est refusé par le navigateur, alors que le test Java vérifie le 400 API. Cela ne remplace pas un audit d'intrusion.

#### S4

- **Où dans le code :** `frontend/src/components/MissionList.jsx` (rendu JSX du titre et de la description)
- **Tests et preuves automatisées :** **`frontend/test/MissionList.test.js`** (`a mission title containing a script is displayed as text`) : contrôle l'échappement du titre `<script>` dans le rendu HTML React
- **Vérification et limites :** **Test de rendu React et test Chromium** dans [`e2e/tests/securite.spec.js`](../e2e/tests/securite.spec.js) : le titre `<script>` apparaît comme texte, sans balise ni exécution. Cela ne couvre pas tous les cas XSS ; aucune politique CSP stricte n'est revendiquée.

#### S5

- **Où dans le code :** `security/AuthCookieService.java`, `security/JwtAuthenticationFilter.java`
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`loginCreatesHttpOnlyAccessAndRefreshCookies`)
- **Vérification et limites :** Cookies d'authentification `HttpOnly` et `SameSite=Lax` ; cookie CSRF lisible volontairement par React. Vérifier l'attribut `Secure` en HTTPS.

#### S6

- **Où dans le code :** `config/SecurityConfig.java`, `config/SpaCsrfTokenRequestHandler.java`
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`loginWithoutCsrfTokenIsRejected`)
- **Vérification et limites :** Une requête d'écriture sans le jeton CSRF attendu doit être refusée. Le mécanisme est **cookie + en-tête**, pas un simple contrôle obligatoire de `Origin`.

#### S7

- **Où dans le code :** `security/LoginAttemptService.java`, `security/LoginRateLimitFilter.java`
- **Tests et preuves automatisées :** `security/LoginAttemptServiceTest.java` (`accountAndAddressPairIsBlockedAfterConfiguredFailures`) ; `security/LoginRateLimitFilterTest.java` (`repeatedLoginFromSameAddressIsRejected`)
- **Vérification et limites :** Tester les refus et le `429` sur un environnement de test. Les compteurs sont **locaux à l'instance**, sans stockage partagé.

#### S8

- **Où dans le code :** `controller/ApiExceptionHandler.java`, `security/RestAuthenticationEntryPoint.java`
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`invalidPasswordIsRejectedWithoutTechnicalDetail`) et `InjectionSecurityIntegrationTest.java` (identifiant invalide : erreur générique `BAD_REQUEST`, HTTP 400)
- **Vérification et limites :** Une entrée de route non numérique ne provoque plus un 500 technique. La couverture exhaustive des erreurs inattendues reste à vérifier.

#### S9

- **Où dans le code :** `compose.yaml` et `backend/src/main/resources/application.properties` (connexion PostgreSQL locale au réseau Docker)
- **Tests et preuves automatisées :** **Aucun test dédié de validation du certificat TLS PostgreSQL identifié**
- **Vérification et limites :** **Non démontré par ce dépôt**. Si la base est externalisée, définir et vérifier explicitement sa configuration TLS/certificats.


**Lecture des résultats :** S3 dispose désormais de quatre tests HTTP ciblés, et S4 d'un test d'échappement HTML dans React. S1, S5–S7 disposent également de tests spécialisés. Les contrôles S2, S8 et S9 restent partiels ; les tests S3/S4 ne prouvent pas une résistance exhaustive à toutes les attaques.

## 2. Authentification

| ID | Exigence | Preuve principale |
| --- | --- | --- |
| **A1** | Ouvrir une session et émettre des jetons | [SecurityIntegrationTest.java](#a1) |
| **A2** | Expiration courte du JWT d'accès | [JwtServiceTest.java](#a2) |
| **A3** | Déconnexion et révocation | [AuthControllerTest.java](#a3) |
| **A4** | Signature et vérification des JWT | [JwtServiceTest.java](#a4) |
| **A5** | Refuser un compte désactivé | [SecurityIntegrationTest.java](#a5) |

### Détail des preuves — authentification

#### A1

- **Où dans le code :** `controller/AuthController.java` (`login`, `issueTokens`), `security/JwtService.java`
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`loginCreatesHttpOnlyAccessAndRefreshCookies`) ; `controller/AuthControllerTest.java` (`successfulLoginClearsFailuresAndIssuesBothTokens`)
- **Vérification et limites :** Ouvrir une session, puis interroger `GET /api/auth/me`.

#### A2

- **Où dans le code :** `security/JwtService.java` ; `backend/src/main/resources/application.properties` (`JWT_EXPIRATION_MS`, valeur par défaut **900 000 ms = 15 min**)
- **Tests et preuves automatisées :** `security/JwtServiceTest.java` (`tokenBecomesInvalidWhenClockMovesPastExpiration`)
- **Vérification et limites :** Contrôle déterministe par horloge de test ; durée configurable, donc à vérifier dans l'environnement cible.

#### A3

- **Où dans le code :** `controller/AuthController.java` (`logout`), `security/RefreshTokenService.java` (`revoke`, `revokeAllFor`)
- **Tests et preuves automatisées :** `controller/AuthControllerTest.java` (`logoutWithRefreshCookieRevokesToken`) ; `SecurityIntegrationTest.java` (`logoutRevokesRefreshToken`)
- **Vérification et limites :** La déconnexion efface les cookies et révoque **le refresh token**. **Elle ne place pas immédiatement le JWT d'accès en liste noire :** un JWT encore valide peut subsister jusqu'à expiration, sauf si le compte est désactivé.

#### A4

- **Où dans le code :** `security/JwtService.java` (`generateToken` en HS256, `parseClaims`), `security/JwtAuthenticationFilter.java`
- **Tests et preuves automatisées :** `security/JwtServiceTest.java` (`generatedTokenContainsExpectedIdentityRoleAndExpiration`, expiration)
- **Vérification et limites :** Les signatures sont contrôlées par JJWT. **Test spécifique d'un JWT forgé ou d'un algorithme inattendu à compléter.**

#### A5

- **Où dans le code :** `security/JwtAuthenticationFilter.java` (relecture de l'utilisateur en base), `security/RefreshTokenService.java`
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`disabledUserWithOtherwiseValidJwtIsRejected`) ; `security/RefreshTokenServiceTest.java` (`tokenForDisabledUserIsRejectedAndDeleted`)
- **Vérification et limites :** Après désactivation, l'API refuse un JWT autrement valable et le renouvellement de session est interdit.


## 3. Règles de gestion

| ID | Exigence | Preuve principale |
| --- | --- | --- |
| **RG1** | Le chauffeur voit **uniquement ses missions** ; une mission d'autrui reste introuvable | [MissionServiceTest.java](#rg1) |
| **RG2** | Code d'entrepôt chiffré avant stockage et révélé sous conditions | [SecretEncryptionServiceTest.java](#rg2) |
| **RG3** | Cycle linéaire `PENDING` → `IN_PROGRESS` → `COMPLETED` | [MissionServiceTest.java](#rg3) |
| **RG4** | Dépôt de preuve réservé au chauffeur assigné et à une mission en cours | [DocumentSecurityIntegrationTest.java](#rg4) |

### Détail des preuves — règles de gestion

#### RG1

- **Où dans le code :** `repository/MissionRepository.java` (`findMissionsForDriver`, `findMissionForDriver`), `service/MissionService.java`
- **Tests et preuves automatisées :** `service/MissionServiceTest.java` (`driverGetsOnlyMissionAssignedToHim`, `otherDriverCannotRevealSecret`) ; `DocumentSecurityIntegrationTest.java`
- **Vérification et limites :** Se connecter avec deux chauffeurs **de test** affectés à des missions distinctes ; chacun ne voit que les siennes.

#### RG2

- **Où dans le code :** `service/SecretEncryptionService.java` (AES-256-GCM), `service/MissionService.java` (`revealSecretCode`)
- **Tests et preuves automatisées :** `service/SecretEncryptionServiceTest.java` (`sameSecretProducesDifferentCiphertextBecauseIvIsRandom`, `tamperedCiphertextIsRejectedByGcmIntegrityCheck`) ; `SecurityIntegrationTest.java` (`createdMissionStoresSecretEncryptedAndDoesNotExposeItToDispatcher`)
- **Vérification et limites :** En base de test, le code n'est pas en clair. Contrairement à certains modèles d'implémentation, **l'identifiant de mission n'est pas utilisé comme donnée associée GCM (AAD)** dans le code actuel.

#### RG3

- **Où dans le code :** `service/MissionService.java` (`validateStatusTransition`, `updateStatus`) ; `enums/MissionStatus.java`
- **Tests et preuves automatisées :** `service/MissionServiceTest.java` (`driverCannotSkipDirectlyFromPendingToCompleted`, `completedMissionCannotMoveBackToInProgress`)
- **Vérification et limites :** Les actions proposées suivent le statut. **Pas de verrou optimiste `@Version` ni de déclencheur SQL dédié** ; voir la dette technique TD-07.

#### RG4

- **Où dans le code :** `controller/DocumentController.java`, `service/DocumentService.java` (`upload`)
- **Tests et preuves automatisées :** `DocumentSecurityIntegrationTest.java` (`driverCannotUploadDocumentForAnotherDriversMission`, `driverCannotUploadProofBeforeMissionStarts`, `driverCannotUploadProofAfterMissionEnds`)
- **Vérification et limites :** Un autre chauffeur ou une mission non démarrée ne doit pas permettre le dépôt.


## 4. Fonctionnalités

| ID | Exigence | Preuve principale |
| --- | --- | --- |
| **F1** | Modèles utilisateurs, missions, justificatifs et audit ; schéma versionné | [SecurityIntegrationTest.java](#f1) |
| **F2** | Création de mission par Dispatcher avec code chiffré avant enregistrement | [SecurityIntegrationTest.java](#f2) |
| **F3** | Liste selon le rôle, **sans code secret pour aucun rôle** | [SecurityIntegrationTest.java](#f3) |
| **F4** | Changement d'état réservé au chauffeur ; clôture avec preuve | [DocumentSecurityIntegrationTest.java](#f4) |
| **F5** | Validation du contenu et de la taille des preuves | [FileStorageServiceTest.java](#f5) |
| **F6** | Stockage privé et accès contrôlé aux documents | [FileStorageServiceTest.java](#f6) |
| **F7** | Téléchargement par le Dispatcher propriétaire | [DocumentSecurityIntegrationTest.java](#f7) |
| **F8** | Notifications de création et de changements de mission au chauffeur concerné | [MissionTransactionIntegrationTest.java](#f8) |
| **F9** | Journal d'audit des actions et des lectures sensibles | [WriteRequestAuditInterceptorTest.java](#f9) |
| **F10** | Client web par rôle, formulaires et notification visuelle | [parcours.spec.js](#f10) |
| **F11** | Application livrée sur un serveur et contrôlée après publication | [Contrôles CI et déploiement](#f11) |

### Détail des preuves — fonctionnalités

#### F1

- **Où dans le code :** `model/User.java`, `model/Mission.java`, `model/Document.java`, `model/AuditLog.java` ; `backend/src/main/resources/db/migration/`
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`integrationSuiteUsesPostgresqlAndFlyway`) et autres tests PostgreSQL
- **Vérification et limites :** Vérifier les tables et migrations Flyway sur la **base de test**.

#### F2

- **Où dans le code :** `controller/MissionController.java` (`createMission`) ; `service/MissionService.java` (`createMission`)
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`dispatcherCanCreateMission`, `createdMissionStoresSecretEncryptedAndDoesNotExposeItToDispatcher`)
- **Vérification et limites :** Formulaire « Nouvelle mission » et présence de la mission sans code dans la liste.

#### F3

- **Où dans le code :** `service/MissionService.java` (`listMissions`), `dto/MissionResponse.java`
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`missionListNeverContainsSecretEvenForAssignedDriver`) ; `service/MissionServiceTest.java` (`driverListNeverDecryptsSecret`)
- **Vérification et limites :** Inspecter la réponse `GET /api/missions` : aucun champ `secretCode`. L'accès au code se fait uniquement par `GET /api/missions/{id}/secret-code`.

#### F4

- **Où dans le code :** `controller/MissionController.java` (`updateStatus`), `service/MissionService.java`
- **Tests et preuves automatisées :** `DocumentSecurityIntegrationTest.java` (`missionCannotBeCompletedWithoutProof`, `missionCanBeCompletedAfterProofUpload`)
- **Vérification et limites :** Démarrer une mission, tenter la clôture sans preuve (refus), déposer une preuve, puis clôturer.

#### F5

- **Où dans le code :** `service/FileStorageService.java` (`validateMimeType`, `validateFile`), Apache Tika
- **Tests et preuves automatisées :** `service/FileStorageServiceTest.java` (`unsupportedFileTypeIsRejectedFromItsContent`, `fileOneByteAboveSizeLimitIsRejected`)
- **Vérification et limites :** Un fichier texte renommé en PDF est refusé. **Cette validation n'est pas un antivirus.**

#### F6

- **Où dans le code :** `service/FileStorageService.java` (`createStoredFilename`, `validateStoredPath`), `service/DocumentService.java`
- **Tests et preuves automatisées :** `service/FileStorageServiceTest.java` (`readOutsideStorageDirectoryIsRejected`) ; `DocumentSecurityIntegrationTest.java`
- **Vérification et limites :** Les documents ne sont pas servis comme fichiers publics ; l'API contrôle l'accès. Stockage local au serveur, pas de compartiment Supabase.

#### F7

- **Où dans le code :** `controller/DocumentController.java` (`download`), `repository/DocumentRepository.java`
- **Tests et preuves automatisées :** `DocumentSecurityIntegrationTest.java` (`dispatcherCannotDownloadDocumentFromAnotherDispatchersMission`, `owningDispatcherCanDownloadDocument`)
- **Vérification et limites :** Un Dispatcher peut télécharger les preuves de **ses propres missions** et ne peut pas accéder à celles d'un autre.

#### F8

- **Où dans le code :** `event/MissionNotificationListener.java` (`onMissionCreated`, `onMissionChanged`) ; `service/NotificationService.java` ; `controller/NotificationController.java`
- **Tests et preuves automatisées :** `MissionTransactionIntegrationTest.java` (création, modification, annulation, réaffectation et absence de notification après rollback) ; `frontend/test/missionNotifications.test.js` ; `e2e/tests/parcours.spec.js` ; `service/NotificationServiceTest.java`
- **Vérification et limites :** Connexion d'un chauffeur de test : réception d'une notification **SSE**, pas WebSocket. Une reconnexion recharge les missions par REST.

#### F9

- **Où dans le code :** `config/WriteRequestAuditInterceptor.java` ; `service/AuditLogService.java` ; `model/AuditLog.java`
- **Tests et preuves automatisées :** `config/WriteRequestAuditInterceptorTest.java` (`revealedSecretCodeIsAudited`, `mutatingRequestIsStillAudited`, `ordinaryReadIsNotAudited`)
- **Vérification et limites :** Vue « Journal d'audit » du compte Admin. **Écriture asynchrone en base, non inaltérable et sans couverture générale des lectures.**

#### F10

- **Où dans le code :** `frontend/src/pages/` ; `frontend/src/components/` ; `frontend/src/hooks/useMissionNotifications.js`
- **Tests et preuves automatisées :** `e2e/tests/parcours.spec.js` (ADMIN, Dispatcher et livraison), `frontend/test/MissionList.test.js`
- **Vérification et limites :** Parcours dans l'application de test : connexion, création, attribution et clôture de mission avec preuve.

#### F11

- **Où dans le code :** `compose.yaml` ; `.github/workflows/ci.yml` ; `.github/workflows/deploy-ovh.yml`
- **Tests et preuves automatisées :** Workflow CI : 6 jobs ; workflow OVH : sauvegardes et contrôle HTTPS après déploiement
- **Vérification et limites :** Consulter les [exécutions GitHub Actions](https://github.com/anne1porta-ai/gml-secure-missions/actions) et https://gml-shield-demo.fr. L'état courant doit être vérifié à chaque livraison.


## 5. Capacités supplémentaires de ce dépôt

Elles sont intégrées à l'application actuelle et complètent les exigences ci-dessus :

| ID | Exigence | Preuve principale |
| --- | --- | --- |
| **M1** | Modifier une mission en attente | [SecurityIntegrationTest.java](#m1) |
| **M2** | Réaffecter une mission en attente à un chauffeur actif | [SecurityIntegrationTest.java](#m2) |
| **M3** | Annuler une mission autorisée en attente ou en cours | [SecurityIntegrationTest.java](#m3) |
| **M4** | Administrer les comptes Dispatcher et Chauffeur | [UserManagementServiceTest.java](#m4) |

### Détail des preuves — capacités supplémentaires

#### M1

- **Où dans le code :** `service/MissionService.java` (`updateMission`), `frontend/src/components/EditMissionForm.jsx`
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`owningDispatcherCanEditPendingMission`, `dispatcherCannotEditStartedMission`) ; `e2e/tests/parcours.spec.js`
- **Vérification et limites :** Modifier avant démarrage ; l'action disparaît après.

#### M2

- **Où dans le code :** `service/MissionService.java` (`reassignMission`), `frontend/src/components/ReassignMissionForm.jsx`
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`owningDispatcherCanReassignPendingMission`) ; `e2e/tests/parcours.spec.js`
- **Vérification et limites :** Vérifier le nouveau chauffeur et la perte d'accès de l'ancien.

#### M3

- **Où dans le code :** `service/MissionService.java` (`cancelMission`), `controller/MissionController.java`
- **Tests et preuves automatisées :** `SecurityIntegrationTest.java` (`dispatcherCanCancelOwnPendingMission`, `adminCanCancelStartedMission`, `completedMissionCannotBeCancelled`)
- **Vérification et limites :** Vérifier la transition vers `CANCELLED` et l'absence de retour possible.

#### M4

- **Où dans le code :** `service/UserManagementService.java` (`createUser`, `updateActive`), `frontend/src/components/UsersPanel.jsx`
- **Tests et preuves automatisées :** `service/UserManagementServiceTest.java` ; `e2e/tests/parcours.spec.js`
- **Vérification et limites :** Créer et désactiver un compte dans l'interface Admin sur la base de test.


## 6. Mode d'emploi des preuves

- **Automatisé :** chaque test cité existe dans le dépôt ; les jobs de CI exécutent les suites applicables. Un nom de test ne doit pas être présenté comme une preuve plus large que ses assertions réelles.
- **Visible :** les contrôles manuels sont des scénarios proposés pour une installation de test ; ils ne prétendent pas tous avoir été exécutés sur la production.
- **À compléter :** S2, S3, S4, S8, S9 et A4 comportent des limites ou des tests spécialisés manquants. A3 et RG3 ont des limites structurelles explicites. Ces écarts ne doivent pas être masqués par la réussite globale de la CI.
- **Exploitation :** une CI verte, une sauvegarde vérifiée et une réponse HTTPS ne remplacent ni un test de restauration, ni un audit d'intrusion, ni une démonstration de haute disponibilité.

Pour aller plus loin : [contrat API](API.md), [exigences fonctionnelles](EXIGENCES_FONCTIONNELLES.md), [sécurité](SECURITE.md), [tests et CI/CD](QUALITE_CICD.md), [dette technique](TECHNICAL_DEBT_REGISTER.md) et [recette fonctionnelle synthétique](RECETTE_FONCTIONNELLE.md).
