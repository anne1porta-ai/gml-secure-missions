# Tests fonctionnels et de sécurité avec Chromium

Ces tests utilisent **Playwright** pour ouvrir l'interface React dans un vrai navigateur Chromium. Ils vérifient les actions à l'écran, en complément des tests Java et des tests d'API.

## Parcours existants

Le fichier [e2e/tests/parcours.spec.js](../e2e/tests/parcours.spec.js) réalise neuf scénarios :

- Administrateur : créer puis désactiver un chauffeur.
- Dispatcher : modifier une mission en attente.
- Dispatcher : réaffecter une mission.
- Dispatcher : annuler une mission.
- Annulation : le chauffeur est averti sans actualiser sa page.
- Mission : démarrer, consulter le code, déposer une preuve et clôturer.
- Preuve : un fichier non autorisé est refusé et la mission reste en cours.
- Administrateur : un compte désactivé ne peut plus se connecter.
- Accès : sans connexion, les missions ne sont pas accessibles.

### Lancer les neuf parcours : une seule commande (Windows)

Depuis **PowerShell, à la racine du projet** :

| Commande | Résultat |
| --- | --- |
| `.\scripts\run-functional-tests.ps1` | Les neuf parcours, sans fenêtre |
| `.\scripts\run-functional-tests.ps1 -Visible` | Les mêmes parcours, avec **Chromium visible** |
| `.\scripts\run-functional-tests.ps1 -Debug` | Les mêmes parcours, avec **Playwright pas à pas** |
| `.\scripts\run-functional-tests.ps1 -Help` | Affiche l'aide sans exécuter de tests |

Le [script PowerShell](../scripts/run-functional-tests.ps1) démarre une application et une base PostgreSQL de test, crée le compte administrateur de test, installe Playwright et Chromium, puis lance les parcours.

**Prérequis :** Docker Desktop démarré et Node.js 24+. Les ports **8080 et 8088 doivent être libres** : arrêter l'application locale habituelle si elle les utilise.

**Isolation :** comme pour les tests de sécurité, les conteneurs, la base et les secrets sont temporaires. Le fichier `.env` local n'est pas modifié et tout est supprimé à la fin, même si un test échoue.

## Tests de sécurité depuis l'IHM

Le fichier [e2e/tests/securite.spec.js](../e2e/tests/securite.spec.js) contient **quatre scénarios supplémentaires** :

| Scénario | Ce que Chromium fait | Résultat vérifié |
| --- | --- | --- |
| Injection SQL dans l'e-mail | Saisit `' OR '1'='1' --` dans le formulaire de connexion | L'e-mail invalide est **refusé par le navigateur** ; aucune requête de connexion n'est envoyée |
| Injection SQL dans le mot de passe | Saisit `' OR 1=1 --` et clique sur « Se connecter » | L'API retourne **401** et l'interface affiche un refus |
| Instruction SQL dans un titre | Crée une mission avec `DROP TABLE mission` dans le titre | Le texte est enregistré et affiché ; une seconde mission peut encore être créée |
| Balise HTML/script dans un titre | Crée une mission avec une balise `<script>` dans le titre | La balise s'affiche comme du texte ; **aucun élément script n'est créé ni exécuté** |

**Important :** le premier scénario est bloqué par la validation HTML du champ e-mail. Le test Java [InjectionSecurityIntegrationTest.java](../backend/src/test/java/com/gml/shield/InjectionSecurityIntegrationTest.java) vérifie séparément le refus **HTTP 400 côté API**. Ces tests ciblés ne remplacent pas un audit d'intrusion ni un scanner XSS complet.

## Tous les tests de sécurité : une seule commande (Windows)

Depuis **PowerShell, à la racine du projet** :

| Commande | Résultat |
| --- | --- |
| `.\scripts\run-security-tests.ps1` | Tests de sécurité Java/PostgreSQL, tests React et quatre simulations Playwright |
| `.\scripts\run-security-tests.ps1 -Visible` | Les mêmes tests, avec **Chromium visible** pour les injections |
| `.\scripts\run-security-tests.ps1 -Debug` | Les mêmes tests, puis **Playwright pas à pas** |
| `.\scripts\run-security-tests.ps1 -Help` | Affiche l'aide sans exécuter de tests |

Le [script PowerShell](../scripts/run-security-tests.ps1) vérifie :

1. **Java et PostgreSQL Testcontainers** : authentification, JWT, droits, injections SQL, documents, stockage, chiffrement et audit.
2. **React** : tests de composants, dont l'échappement des titres contenant une balise HTML.
3. **Chromium/Playwright** : quatre essais d'injection par les formulaires.

**Prérequis :** Docker Desktop démarré, Java 17, Maven 3.9+ et Node.js 24+. Le script installe lui-même les dépendances npm et Chromium nécessaires. Les ports **8080 et 8088 doivent être libres** : arrêter l'application locale habituelle si elle les utilise.

**Isolation :** le lanceur crée une application, une base PostgreSQL et des volumes **temporaires et séparés** avec un nom Docker spécifique. Les secrets de test sont générés en mémoire ; le fichier `.env` local n'est pas modifié. À la fin, les conteneurs et leurs volumes de test sont supprimés, même si un test échoue. **Ne jamais utiliser la production OVH pour ces scénarios.**

Ce script rassemble les tests applicatifs de sécurité. Les scans **Gitleaks** et **OWASP Dependency-Check** restent exécutés dans GitHub Actions ; ils ne sont pas lancés localement par cette commande.

## Lancer uniquement Playwright sur l'application locale

Dans PowerShell, avec **Node.js** et **Docker Desktop** installés, démarrer d'abord l'application locale sur **http://localhost:8088** :

```powershell
.\scripts\start-docker.ps1
```

Puis depuis la racine du dépôt :

```powershell
cd e2e
npm install
npx playwright install chromium
npm run test:securite:visible
```

Chromium s'ouvre et saisit les données dans les formulaires. Pour une démonstration **étape par étape**, utiliser :

```powershell
npm run test:securite -- --debug
```

Pour exécuter ces quatre scénarios sans fenêtre :

```powershell
npm run test:securite
```

Pour lancer **tous les scénarios Playwright** :

```powershell
npm test
```

Ce dernier lancement nécessite aussi le compte administrateur de test et sa variable `E2E_ADMIN_PASSWORD` pour les scénarios d'administration. Les tests de sécurité seuls utilisent les comptes de démonstration prévus dans le `.env` local.

**Précaution :** ces tests créent des missions. Utiliser une installation locale de démonstration, avec `DEMO_SEED_ENABLED=true`, et **jamais une base métier ou la production OVH**. La configuration Playwright cible uniquement `127.0.0.1:8088`.

## Exécution automatique dans GitHub Actions

Le workflow [CI](../.github/workflows/ci.yml) lance `npm test` dans le dossier `e2e`. Cela comprend les **neuf parcours métier et les quatre scénarios de sécurité**, sans nouveau job.

GitHub crée un fichier `.env` temporaire, démarre une application et une base PostgreSQL **isolées** sous le projet Docker `gmlshield-e2e`, installe Chromium puis exécute Playwright. Les conteneurs et leurs volumes de test sont supprimés après les contrôles.

Pour consulter les résultats : **Actions → CI → Parcours fonctionnels Chromium → Vérifier les parcours utilisateur**. Un échec rend ce job rouge et empêche le déploiement automatique OVH depuis `main`.

## Portée

Ces contrôles apportent des preuves de **non-régression** : ils vérifient quatre tentatives représentatives dans l'IHM. Ils ne prouvent pas l'absence de toute injection SQL, de tout XSS ou de toute vulnérabilité. Les sauvegardes et la restauration doivent être testées séparément.
