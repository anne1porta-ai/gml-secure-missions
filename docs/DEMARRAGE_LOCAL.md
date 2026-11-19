# Démarrage et tests en local

Ce guide décrit le fonctionnement local de **GML-SHIELD** sous Windows, ainsi que les tests réalisés dans un environnement séparé. Il ne concerne pas le déploiement OVH.

## 1. Prérequis

- Git
- Docker Desktop démarré, avec Docker Compose
- PowerShell

Pour exécuter la suite complète de tests localement, prévoir aussi **Java 17**, **Maven 3.9+** et **Node.js 24+**.

## 2. Démarrer le logiciel

Dans PowerShell, depuis le dossier du dépôt :

```powershell
git switch main
git pull origin main
.\scripts\start-docker.ps1
```

Le script crée le fichier `.env` au premier lancement (s'il est absent), construit les images Docker et démarre les services `database`, `backend` et `frontend`. Les redémarrages suivants conservent la configuration et les données.

**Interface :** http://localhost:8088

Au premier lancement, le compte administrateur local est `admin@gml.local`. Le mot de passe généré est fourni dans le fichier privé `.env`, variable `ADMIN_PASSWORD`.

Les comptes de démonstration locaux, activés par `DEMO_SEED_ENABLED=true`, sont :

| Rôle | E-mail | Mot de passe de démonstration |
| --- | --- | --- |
| Dispatcher | `dispatcher1@gml.local` | `dispatch123` |
| Chauffeur | `driver1@gml.local` | `driver123` |

Ces identifiants sont uniquement destinés aux tests locaux. Ne pas activer les comptes de démonstration en production.

## 3. Vérifier, arrêter et redémarrer

Pour consulter l'état des conteneurs :

```powershell
docker compose ps
```

Pour consulter les journaux :

```powershell
docker compose logs --tail=100
```

Pour arrêter les services sans effacer les données :

```powershell
docker compose down
```

Pour les redémarrer :

```powershell
.\scripts\start-docker.ps1
```

**Important :** ne pas utiliser `docker compose down -v` pour un arrêt normal : cette option supprime les volumes de données. Conserver la valeur `ENCRYPTION_KEY` du fichier `.env` ; la régénérer rendrait illisibles les anciens codes déjà chiffrés.

## 4. Tester le projet sur le PC

Depuis la racine du dépôt :

```powershell
.\scripts\run-all-tests.ps1
```

Le script effectue les contrôles Java et React, puis construit une application de test Docker avec une **base PostgreSQL indépendante**. Il vérifie la création d'une mission, les restrictions d'accès au code secret, le refus de clôture sans preuve, le dépôt d'un PDF et la clôture autorisée.

Avant de lancer ces tests, arrêter l'application locale si elle utilise déjà les ports `8088` ou `8080`. Le fichier `compose.test.yaml` expose le backend de test uniquement sur `127.0.0.1:8080`.

Les fichiers de diagnostic du test sont enregistrés dans `artifacts/run-all-tests/`. Le script supprime ses propres conteneurs et volumes de test à la fin ; il ne doit pas être exécuté contre la production.

Pour consulter le détail des scénarios et la recette métier, voir :

- [Tests fonctionnels](TESTS_FONCTIONNELS.md)
- [Recette fonctionnelle](RECETTE_FONCTIONNELLE.md)
- [Contrat API](API.md)

## 5. Lancer les tests de sécurité

Une commande unique permet de vérifier les tests de sécurité Java, React et Playwright, sans modifier la base locale habituelle. Fermer l'application locale si elle utilise les ports 8080 ou 8088.

Depuis la racine du dépôt, dans PowerShell :

```powershell
.\scripts\run-security-tests.ps1
```

Pour **voir les tentatives d'injection dans Chromium** :

```powershell
.\scripts\run-security-tests.ps1 -Visible
```

Pour exécuter les scénarios **pas à pas** :

```powershell
.\scripts\run-security-tests.ps1 -Debug
```

Le script lance une application et une base PostgreSQL de test isolées, puis supprime les conteneurs et volumes temporaires à la fin. Le fichier local `.env` n'est pas modifié.

Voir le [guide des tests fonctionnels et de sécurité](TESTS_FONCTIONNELS.md) pour le détail des contrôles et les commandes permettant de lancer uniquement Playwright.

## 6. Lancer les tests fonctionnels

Les neuf parcours utilisateur (administrateur, dispatcher, chauffeur) sont rejoués dans Chromium. Fermer l'application locale si elle utilise les ports 8080 ou 8088.

Depuis la racine du dépôt, dans PowerShell :

```powershell
.\scripts\run-functional-tests.ps1
```

Pour **voir les parcours dans Chromium** :

```powershell
.\scripts\run-functional-tests.ps1 -Visible
```

Le script utilise lui aussi une application et une base de test isolées, supprimées à la fin. Le fichier local `.env` n'est pas modifié.
