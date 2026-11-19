# GML-SHIELD - démarrage Docker simplifié
# Usage : .\scripts\start-docker.ps1
#
# - conserve le .env s'il existe déjà ;
# - sinon génère les secrets locaux nécessaires ;
# - construit et demarre les services Docker en arriere-plan ;
# - verifie le frontend sur http://localhost:8088.

$ErrorActionPreference = "Stop"

$RootDir = Resolve-Path "$PSScriptRoot\.."
$EnvFile = Join-Path $RootDir ".env"

Set-Location $RootDir

# Vérifie que Docker est installé.
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker n'est pas installé ou n'est pas présent dans le PATH."
    exit 1
}

# Vérifie que Docker est démarré.
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker est installé mais ne semble pas démarré. Démarrez Docker Desktop puis relancez ce script."
    exit 1
}

# Crée le .env uniquement au premier lancement.
if (-not (Test-Path $EnvFile)) {
    Write-Host ".env absent : génération des secrets locaux..."
    & (Join-Path $PSScriptRoot "create-local-env.ps1")
    if ($LASTEXITCODE -ne 0) {
        Write-Error "La création du fichier .env a échoué."
        exit $LASTEXITCODE
    }
}
else {
    Write-Host ".env déjà présent : il est conservé."
}

Write-Host ""
Write-Host "Construction et démarrage de GML-SHIELD..."
docker compose up -d --build --wait --wait-timeout 300

if ($LASTEXITCODE -ne 0) {
    Write-Error "Le démarrage Docker a échoué."
    exit $LASTEXITCODE
}

$ready = $false
for ($attempt = 1; $attempt -le 15; $attempt++) {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:8088/" -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            $ready = $true
            break
        }
    }
    catch {
        # Le frontend peut encore terminer son demarrage.
    }
    Start-Sleep -Seconds 2
}

if (-not $ready) {
    throw "Le frontend ne repond pas. Consultez : docker compose logs."
}

docker compose ps
Write-Host ""
Write-Host "Application disponible : http://localhost:8088" -ForegroundColor Green
Write-Host "Arreter sans supprimer les donnees : docker compose down"
