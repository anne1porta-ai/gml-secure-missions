# Registre de dette technique et travaux différés

## 1. Objectif

Ce document distingue une **dette connue et acceptée** d'une fonctionnalité oubliée. Chaque élément possède un impact, une raison de report et surtout un **déclencheur** : le moment où il cesse d'être acceptable de le différer.

Priorité : `P1` = avant exposition/production critique, `P2` = prochaine stabilisation utile, `P3` = lisibilité/optimisation non bloquante.

## 2. Registre

| ID | Sujet | Priorité MVP | Impact actuel | Pourquoi différé | Déclencheur de traitement | Cible |
|---|---|---:|---|---|---|---|
| TD-01 | package Java racine renommé en `com.gml.shield` | P3 | résolu | ancien héritage du POC supprimé | réalisé | `com.gml.shield` |
| TD-02 | refresh token stocké sous valeur opaque brute en DB | P2 | fuite DB = token réutilisable jusqu'à expiration/révocation | MVP interne ; rotation/révocation déjà présentes | exposition partenaires / durcissement auth | stocker une empreinte du refresh token |
| TD-03 | clé AES sans `key_version` dans le ciphertext/modèle | P2 | rotation progressive difficile | une seule clé MVP | rotation réelle ou KMS | format/version de clé + procédure de re-chiffrement |
| TD-04 | état SSE en mémoire locale | P2 | connexions non partagées entre instances | mono-instance | scaling horizontal / HA | backplane/broker si besoin mesuré |
| TD-05 | rate limiting local à l'instance | P2 | seuil contournable en multi-instance | mono-instance | plusieurs instances / exposition Internet | stockage partagé / gateway rate limit |
| TD-06 | preuves de livraison sur stockage local | P2 | dépendance au nœud/volume | MVP mono-instance | multi-instance, sauvegarde ou volumétrie | stockage objet privé |
| TD-07 | absence de verrou optimiste sur `Mission` | P2 | lost update possible sous concurrence réelle | un seul Driver et faible contention | clients concurrents / réaffectation / automatisation | `@Version` + contrat de conflit |
| TD-08 | absence d'idempotency key sur `POST /api/missions` | P2 | retry réseau peut créer un doublon | faible fréquence, UI interactive | clients mobiles instables / API partenaire / retry automatique | clé d'idempotence + fingerprint |
| TD-09 | audit async en base applicative, non inaltérable | P1 phase 2 | fenêtre de perte + modification possible par accès DB | l'énoncé demande d'auditer cette limite | exigence NIS2/partenaires | journal durable, append-only/WORM/SIEM |
| TD-10 | lectures sensibles non auditées systématiquement | P1 phase 2 | traçabilité incomplète pour NIS2 | MVP centré sur mutations | conformité NIS2 | tracer lecture/création/suppression sensible |
| TD-11 | API métier non préfixée `/api/v1` | P3 | futur breaking change moins explicite | un seul client maîtrisé | première API partenaire ou changement incompatible | politique de version API |
| TD-12 | absence de correlation/request ID | P2 | diagnostic distribué limité | une seule application, logs simples | exploitation multi-service / incidents récurrents | correlation ID propagé/loggé |
| TD-13 | pas d'antivirus/sandbox de fichiers | P1 si Internet | Tika valide le type, pas l'innocuité | usage interne MVP | fichiers externes/non maîtrisés | scan malware + quarantaine |
| TD-14 | IP d'audit dépend de la confiance proxy | P2 | spoofing possible si mauvaise topologie | déploiement final non figé | exposition publique | proxy trusted + stratégie header documentée |
| TD-15 | pas de métriques/SLO métier formalisés | P2 | performance/fiabilité non mesurées | pas de SLA demandé au MVP | production durable | métriques, seuils, SLO et alertes |
| TD-16 | CVE DOMPurify (CVSS ≥ 7) dans Swagger UI embarqué par springdoc | P2 | outil de documentation non exposé en production (nginx ne relaie que `/api/`) | aucune version de Swagger UI avec DOMPurify ≥ 3.4.17 publiée à date | échéance de l'exception (31/12/2026) ou version corrigée disponible | monter springdoc/Swagger UI puis retirer l'exception de `backend/dependency-check-suppressions.xml` |

## 3. Dette vs phase 2

Certaines lignes deviennent **obligatoires** dans le scénario partenaires/NIS2 mais ne sont pas des prérequis pour démontrer le MVP interne. Les évolutions liées à l'audit figurent sous `TD-09` et `TD-10` ; l'identité externe et la gestion des tenants nécessiteraient un cadrage dédié.

## 4. Règle de traitement

Une dette doit être remontée dans la roadmap lorsque :

- son déclencheur est atteint ;
- elle bloque une exigence réglementaire ou contractuelle ;
- elle crée un incident réel ;
- son coût de contournement devient supérieur au coût de correction.

La priorité ne doit pas être augmentée simplement parce qu'une technologie plus sophistiquée existe dans un projet de référence.

## 5. Règle de fermeture

Pour fermer une dette :

1. modifier le code/configuration ;
2. ajouter la preuve appropriée ;
3. mettre à jour ce registre, la recette fonctionnelle et toute documentation concernée ;
4. conserver l'entrée ici avec le statut `CLOSED` ou la déplacer dans un changelog de décisions.
