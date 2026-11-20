# Outils d'exploitation de GMLSHILD

Ces commandes complètent l'exploitation de l'installation OVH existante.
Elles utilisent le fichier `compose.yaml` et le fichier privé `.env` du projet.
Elles ne changent ni la configuration Caddy, ni le déploiement automatique GitHub Actions.

## Vérification de production

Contrôle des trois conteneurs, de la santé du backend et de PostgreSQL, du
frontend et de l'API accessibles localement :

```bash
cd ~/GMLSHIELD
bash scripts/operations/check-production.sh --local-only
```

Le contrôle public vérifie également le HTTPS, le frontend React, le cookie
CSRF sécurisé et l'absence d'exposition du statut interne Actuator :

```bash
bash scripts/operations/check-production.sh
```

La commande est en lecture seule. Le domaine par défaut est
`gml-shield-demo.fr` ; pour un autre domaine, définir `GML_PUBLIC_HOST`.
Le contrôle du frontend accepte l'absence de healthcheck Docker (celui du
backend et de PostgreSQL est obligatoire).

## Sauvegarde vérifiable

```bash
cd ~/GMLSHIELD
bash scripts/operations/backup-production.sh
```

Le script crée un dossier de travail privé, puis enregistre :

- `postgres.dump` : sauvegarde PostgreSQL au format personnalisé, validée par `pg_restore --list` ;
- `documents.tar.gz` : documents des missions, validés par `tar -tzf` ;
- `SHA256SUMS` : empreintes des deux éléments ;
- une archive `GMLSHIELD-<date>.tar.gz` et son empreinte `.sha256`.

L'emplacement par défaut est `~/GMLSHIELD-backups`. Pour choisir un dossier :

```bash
bash scripts/operations/backup-production.sh --output-dir "$HOME/sauvegardes-gml"
```

La configuration `.env` contient les clés de chiffrement nécessaires à la
lecture des données existantes. Elle n'est **pas** incluse par défaut, afin
de limiter la diffusion des secrets. Pour préparer explicitement un jeu de
secours complet :

```bash
bash scripts/operations/backup-production.sh --with-env
```

Dans ce cas, le fichier `production.env` se trouve dans l'archive, qui
**contient des secrets**. Elle doit être chiffrée avant toute copie externe.
Les fichiers créés sont privés (`umask 077`). Ne jamais les committer dans Git.

Pour vérifier ultérieurement une archive **dans un répertoire privé** :

```bash
mkdir -p ~/controle-sauvegarde
chmod 700 ~/controle-sauvegarde
tar -xzf /chemin/GMLSHIELD-DATE.tar.gz -C ~/controle-sauvegarde
(cd ~/controle-sauvegarde && sha256sum -c SHA256SUMS)
```

Supprimer les fichiers extraits après vérification, notamment `production.env`
si l'option `--with-env` a été utilisée.

### Limites importantes

- Les écritures ne sont pas suspendues : la base et les fichiers sont capturés
  successivement. Pour une reprise strictement cohérente, prévoir une fenêtre
  de maintenance et tester une procédure de restauration.
- Le contrôle `pg_restore --list` prouve la lisibilité de l'archive, pas la
  réussite d'une restauration complète. Cette dernière doit être testée sur
  une base isolée, jamais sur la base de production.
- Le déploiement automatique actuel réalise déjà ses propres sauvegardes.
  Cet outil sert notamment aux sauvegardes ponctuelles et aux exercices de
  reprise ; il ne remplace pas les sauvegardes externes chiffrées.
- Ne pas exécuter `docker compose down -v` : cela supprimerait les volumes.

## Contrôle qualité

La CI contrôle la syntaxe Bash, puis exécute les scripts sur une application
Docker de test avec une base PostgreSQL temporaire. Elle vérifie la disponibilité
des services, la création d'une archive et son empreinte SHA-256.

Ce contrôle ne remplace pas un essai de restauration ni un diagnostic manuel sur
l'installation cible avant une première utilisation en exploitation.
