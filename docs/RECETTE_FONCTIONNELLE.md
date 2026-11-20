# Recette fonctionnelle — GMLSHILD

> **Objectif.** Relier chaque règle de l'application au code qui l'implémente, aux tests automatisés et à la vérification visible dans l'interface.

> **Environnement de recette.** Les tests automatiques utilisent des comptes et une base PostgreSQL temporaires. La recette manuelle peut être effectuée sur une installation de démonstration avec des comptes et des données de test.

**Accès rapide :** [Exigences](#vue-densemble) · [Sécurité et comptes](#1-sécurité-et-gestion-des-comptes) · [Missions](#2-cycle-de-vie-des-missions) · [Documents](#3-documents-et-justificatifs) · [Notifications et audit](#4-notifications-et-traçabilité) · [Exploitation](#5-qualité-sauvegarde-et-déploiement) · [Scénarios](#6-scénarios-de-recette) · [Validation](#7-validation)

Pour une analyse plus complète, organisée en sécurité (S), authentification (A), règles de gestion (RG) et fonctionnalités (F), voir le **[tableau de recette détaillée](RECETTE_DETAILLEE.md)**, notamment les contrôles encore à compléter.

**Repère pour les chemins Java :** `backend/.../` correspond à `backend/src/main/java/com/gml/shield/`. Les tests Java se trouvent dans `backend/src/test/java/com/gml/shield/` et ses sous-dossiers.

## Vue d'ensemble

Cette première vue permet de trouver rapidement un contrôle. Les détails du code, des tests et des vérifications sont présentés sous chaque domaine.

| Domaine | Exigences | Accéder aux preuves |
| --- | ---: | --- |
| Authentification, confidentialité et administration | 5 | [Sécurité et gestion des comptes](#1-sécurité-et-gestion-des-comptes) |
| Création, affectation, modification, annulation et clôture | 7 | [Cycle de vie des missions](#2-cycle-de-vie-des-missions) |
| Dépôt, consultation et contrôle des fichiers | 3 | [Documents et justificatifs](#3-documents-et-justificatifs) |
| Notifications et journal d'audit | 2 | [Notifications et traçabilité](#4-notifications-et-traçabilité) |
| Contrôles CI, diagnostic, sauvegarde et OVH | 3 | [Qualité, sauvegarde et déploiement](#5-qualité-sauvegarde-et-déploiement) |

## 1. Sécurité et gestion des comptes

| ID | Résultat attendu | Détail des preuves |
| --- | --- | --- |
| **SEC-01** | Un utilisateur se connecte avec son rôle ; les accès non autorisés sont refusés. | [Voir le code et les tests](#sec-01) |
| **SEC-02** | Les mots de passe et les codes secrets ne sont pas enregistrés en clair. | [Voir le code et les tests](#sec-02) |
| **SEC-03** | Le code secret n'est révélé qu'au chauffeur assigné pendant IN_PROGRESS. | [Voir le code et les tests](#sec-03) |
| **SEC-04** | Après révélation, le code secret se masque automatiquement après 30 secondes. | [Voir le code et les tests](#sec-04) |
| **ADM-01** | L'administrateur crée, désactive et réactive des comptes Chauffeur ou Dispatcher. | [Voir le code et les tests](#adm-01) |

**Preuves et vérifications**

### SEC-01

**Dans le code :** `backend/.../controller/AuthController.java`, `backend/.../security/JwtAuthenticationFilter.java`

**Tests automatisés :** `SecurityIntegrationTest.java`

**Vérification dans l'application :** Un chauffeur ne voit pas l'écran ADMIN.

### SEC-02

**Dans le code :** `backend/.../service/SecretEncryptionService.java`, `backend/.../service/UserManagementService.java`

**Tests automatisés :** `SecurityIntegrationTest.java`

**Vérification dans l'application :** Le code secret n'apparaît jamais dans les listes de missions.

### SEC-03

**Dans le code :** `backend/.../service/MissionService.java`, `frontend/src/components/MissionList.jsx`

**Tests automatisés :** `SecurityIntegrationTest.java`, `e2e/tests/parcours.spec.js`

**Vérification dans l'application :** Avant le démarrage et après la clôture, le code n'est pas accessible.

### SEC-04

**Dans le code :** `frontend/src/components/MissionList.jsx`

**Tests automatisés :** `e2e/tests/parcours.spec.js`

**Vérification dans l'application :** Révéler le code puis attendre 30 secondes : il redevient masqué.

### ADM-01

**Dans le code :** `frontend/src/components/UsersPanel.jsx`, `backend/.../service/UserManagementService.java`

**Tests automatisés :** `SecurityIntegrationTest.java`, `e2e/tests/parcours.spec.js`

**Vérification dans l'application :** L'écran ADMIN affiche les comptes et leur statut.

## 2. Cycle de vie des missions

| ID | Résultat attendu | Détail des preuves |
| --- | --- | --- |
| **MIS-01** | Un Dispatcher crée une mission pour un chauffeur actif. | [Voir le code et les tests](#mis-01) |
| **MIS-02** | Un chauffeur consulte et modifie uniquement les missions qui lui sont assignées. | [Voir le code et les tests](#mis-02) |
| **MIS-03** | Le parcours suit PENDING → IN_PROGRESS → COMPLETED, sans retour en arrière. | [Voir le code et les tests](#mis-03) |
| **MIS-04** | La clôture est refusée sans preuve de livraison et acceptée après dépôt. | [Voir le code et les tests](#mis-04) |
| **MIS-05** | Le Dispatcher peut modifier le titre, la destination, la description et la date d'une mission en attente uniquement. | [Voir le code et les tests](#mis-05) |
| **MIS-06** | Le Dispatcher peut réaffecter une mission en attente à un chauffeur actif ; l'ancien chauffeur perd l'accès et le nouveau est notifié. | [Voir le code et les tests](#mis-06) |
| **MIS-07** | Le Dispatcher propriétaire ou l'ADMIN peut annuler une mission en attente ou en cours ; une mission terminée ou annulée est définitive. | [Voir le code et les tests](#mis-07) |

**Preuves et vérifications**

### MIS-01

**Dans le code :** `frontend/src/pages/DispatcherPage.jsx`, `backend/.../service/MissionService.java`

**Tests automatisés :** `MissionServiceTest.java`, `SecurityIntegrationTest.java`, `e2e/tests/parcours.spec.js`

**Vérification dans l'application :** La mission figure dans la liste du Dispatcher, puis dans celle du chauffeur.

### MIS-02

**Dans le code :** `backend/.../repository/MissionRepository.java`, `backend/.../service/MissionService.java`

**Tests automatisés :** `SecurityIntegrationTest.java`, `DocumentSecurityIntegrationTest.java`

**Vérification dans l'application :** Un autre chauffeur ne peut pas accéder à cette mission.

### MIS-03

**Dans le code :** `backend/.../service/MissionService.java`

**Tests automatisés :** `MissionServiceTest.java`

**Vérification dans l'application :** Seule l'étape suivante est proposée.

### MIS-04

**Dans le code :** `backend/.../service/MissionService.java`, `backend/.../repository/DocumentRepository.java`

**Tests automatisés :** `MissionServiceTest.java`, `DocumentSecurityIntegrationTest.java`, `e2e/tests/parcours.spec.js`

**Vérification dans l'application :** Tenter « Terminée » sans fichier : message de refus. Déposer un fichier : clôture possible.

### MIS-05

**Dans le code :** `backend/.../service/MissionService.java`, `frontend/src/components/EditMissionForm.jsx`

**Tests automatisés :** `MissionServiceTest.java`, `SecurityIntegrationTest.java`, `frontend/test/MissionList.test.js`, `e2e/tests/parcours.spec.js`

**Vérification dans l'application :** Modifier une mission avant démarrage ; vérifier que le bouton disparaît ensuite.

### MIS-06

**Dans le code :** `backend/.../service/MissionService.java`, `frontend/src/components/ReassignMissionForm.jsx`

**Tests automatisés :** `MissionServiceTest.java`, `SecurityIntegrationTest.java`, `frontend/test/MissionList.test.js`, `e2e/tests/parcours.spec.js`

**Vérification dans l'application :** Réaffecter une mission avant démarrage, puis vérifier sa présence avec le nouveau chauffeur.

### MIS-07

**Dans le code :** `backend/.../service/MissionService.java`, `frontend/src/pages/DispatcherPage.jsx`

**Tests automatisés :** `MissionServiceTest.java`, `SecurityIntegrationTest.java`, `frontend/test/MissionList.test.js`, `e2e/tests/parcours.spec.js`

**Vérification dans l'application :** Annuler une mission et vérifier le statut « Annulée » côté chauffeur.

## 3. Documents et justificatifs

| ID | Résultat attendu | Détail des preuves |
| --- | --- | --- |
| **DOC-01** | Seul le chauffeur assigné dépose la preuve ; le Dispatcher concerné peut la télécharger. | [Voir le code et les tests](#doc-01) |
| **DOC-02** | Les fichiers sont contrôlés avant stockage ; un faux PDF est rejeté. | [Voir le code et les tests](#doc-02) |
| **DOC-03** | Le dépôt d'une preuve n'est permis qu'au chauffeur assigné pendant IN_PROGRESS ; les documents déjà déposés restent consultables après clôture. | [Voir le code et les tests](#doc-03) |

**Preuves et vérifications**

### DOC-01

**Dans le code :** `backend/.../service/DocumentService.java`, `frontend/src/components/DocumentsSection.jsx`

**Tests automatisés :** `DocumentSecurityIntegrationTest.java`, `e2e/tests/parcours.spec.js`

**Vérification dans l'application :** Déposer une preuve PNG/PDF puis vérifier sa présence dans la mission.

### DOC-02

**Dans le code :** `backend/.../service/FileStorageService.java`

**Tests automatisés :** `DocumentSecurityIntegrationTest.java`

**Vérification dans l'application :** Le serveur refuse un fichier dont le contenu ne correspond pas au type annoncé.

### DOC-03

**Dans le code :** `backend/.../service/DocumentService.java`, `frontend/src/components/DocumentsSection.jsx`

**Tests automatisés :** `DocumentSecurityIntegrationTest.java`, `frontend/test/MissionList.test.js`, `e2e/tests/parcours.spec.js`

**Vérification dans l'application :** En attente et terminée : pas de sélection de fichier ; en cours : dépôt possible.

## 4. Notifications et traçabilité

| ID | Résultat attendu | Détail des preuves |
| --- | --- | --- |
| **TEMPS-01** | La création, la modification, l'annulation ou la réaffectation d'une mission actualise automatiquement l'écran du chauffeur concerné après enregistrement. | [Voir le code et les tests](#temps-01) |
| **AUD-01** | Les actions sensibles sont journalisées et consultables par l'administrateur. | [Voir le code et les tests](#aud-01) |

**Preuves et vérifications**

### TEMPS-01

**Dans le code :** `backend/.../service/MissionService.java`, `frontend/src/hooks/useMissionNotifications.js`

**Tests automatisés :** `MissionTransactionIntegrationTest.java`, `frontend/test/missionNotifications.test.js`

**Vérification dans l'application :** Avec deux sessions ouvertes, créer puis annuler une mission : le chauffeur voit le changement sans actualiser sa page. Vérifier aussi les modifications et les réaffectations.

### AUD-01

**Dans le code :** `backend/.../config/WriteRequestAuditInterceptor.java`, `frontend/src/components/AuditLogPanel.jsx`

**Tests automatisés :** `WriteRequestAuditInterceptorTest.java`, `frontend/test/audit.test.js`

**Vérification dans l'application :** L'écran ADMIN donne accès au journal d'audit.

## 5. Qualité, sauvegarde et déploiement

| ID | Résultat attendu | Détail des preuves |
| --- | --- | --- |
| **OPS-01** | Les contrôles qualité s'exécutent avant intégration. | [Voir le code et les tests](#ops-01) |
| **OPS-02** | Le diagnostic et la sauvegarde peuvent être vérifiés sans données de production. | [Voir le code et les tests](#ops-02) |
| **OPS-03** | Après CI verte sur main, le déploiement OVH est lancé automatiquement. | [Voir le code et les tests](#ops-03) |

**Preuves et vérifications**

### OPS-01

**Dans le code :** `.github/workflows/ci.yml`

**Tests automatisés :** Java, tests React, Playwright, contrôles de sécurité

**Vérification dans l'application :** Les jobs GitHub Actions doivent être verts.

### OPS-02

**Dans le code :** `scripts/operations/check-production.sh`, `scripts/operations/backup-production.sh`

**Tests automatisés :** Job `Parcours fonctionnels Chromium` dans `.github/workflows/ci.yml`

**Vérification dans l'application :** Une archive et son empreinte SHA-256 sont produites sur l'environnement de test.

### OPS-03

**Dans le code :** `.github/workflows/deploy-ovh.yml`

**Tests automatisés :** Contrôles du workflow de déploiement

**Vérification dans l'application :** GitHub Actions affiche `Déploiement OVH` en réussite.

## 6. Scénarios de recette

Deux parcours à effectuer avec des données de test :

### 6.1. Clôture avec preuve

1. Un Dispatcher crée une mission pour un chauffeur.
2. Le chauffeur ouvre sa session et démarre la mission.
3. Il vérifie que le dépôt de document est disponible uniquement lorsque la mission est en cours.
4. Il tente de passer à « Terminée » sans document : le serveur refuse.
5. Il dépose une preuve de livraison valide.
6. Il passe la mission à « Terminée » : le statut est enregistré.
7. Le dépôt d'un nouveau document est bloqué ; les documents enregistrés restent consultables.
8. Le code secret ne peut plus être révélé après la clôture.

### 6.2. Consultation du code

1. Une mission est en cours.
2. Le chauffeur clique sur « Révéler » : le code est affiché.
3. Après 30 secondes, l'interface masque automatiquement le code.
4. Le chauffeur peut demander à nouveau le code pendant la mission.
5. Le bouton « Masquer » reste utilisable pour cacher le code immédiatement.

Le masquage automatique concerne l'affichage dans le navigateur. Les
autorisations du serveur continuent de s'appliquer à chaque demande de code.

## 7. Validation

La recette est validée lorsque les tests automatisés passent et que les
scénarios manuels attendus sont observés sans erreur. Une réussite de la CI
ne remplace pas un essai complet de restauration des sauvegardes.

