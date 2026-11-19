param(
    [switch]$Visible,
    [switch]$Debug,
    [switch]$Help
)

if ($Help) {
    Write-Host "Tests fonctionnels GML-SHIELD dans Chromium (depuis la racine du projet)"
    Write-Host ".\scripts\run-functional-tests.ps1           : les neuf parcours utilisateur"
    Write-Host ".\scripts\run-functional-tests.ps1 -Visible  : montrer les parcours dans Chromium"
    Write-Host ".\scripts\run-functional-tests.ps1 -Debug    : execution pas a pas avec Playwright"
    Write-Host "Docker Desktop et Node.js 24 sont necessaires."
    return
}

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if ($Visible -and $Debug) {
    throw "Choisir -Visible ou -Debug, pas les deux."
}

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$E2eDir = Join-Path $ProjectRoot "e2e"
$ComposeProject = "gmlshield-functional-$PID"
$ComposeFiles = @("-f", "compose.yaml", "-f", "compose.test.yaml")

function Run-Step {
    param(
        [string]$Title,
        [string]$Folder,
        [string]$Command,
        [string[]]$Arguments
    )

    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
    Push-Location $Folder
    try {
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Echec : $Title (code $LASTEXITCODE)."
        }
    }
    finally {
        Pop-Location
    }
}

function New-RandomKey {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    }
    finally {
        $rng.Dispose()
    }

    return [Convert]::ToBase64String($bytes)
}

$names = @(
    "DB_PASSWORD", "JWT_SECRET", "ENCRYPTION_KEY",
    "ADMIN_EMAIL", "ADMIN_PASSWORD", "DEMO_SEED_ENABLED",
    "COOKIE_SECURE", "FRONTEND_ORIGIN", "COMPOSE_PROJECT_NAME",
    "E2E_ADMIN_PASSWORD"
)
$original = @{}
$environmentChanged = $false
$dockerStarted = $false
$failed = $false

try {
    foreach ($name in @("docker", "npm.cmd", "npx.cmd")) {
        if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
            throw "Commande '$name' introuvable. Verifier les prerequis."
        }
    }

    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Demarrer Docker Desktop avant les tests."
    }

    # Les ports doivent rester libres. On ne ferme jamais l'application de l'utilisateur.
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        $usedPorts = @(Get-NetTCPConnection -LocalPort 8080, 8088 -State Listen -ErrorAction SilentlyContinue)
        if ($usedPorts.Count -gt 0) {
            throw "Les ports 8080 ou 8088 sont occupes. Arreter l'application locale avant les tests."
        }
    }

    # L'application de test a sa propre base et ses propres volumes Docker.
    # Les secrets sont temporaires et le fichier .env local n'est pas modifie.
    foreach ($name in $names) {
        $original[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    }
    $environmentChanged = $true

    # Les parcours se connectent avec ce compte administrateur de test.
    $adminPassword = New-RandomKey

    $env:DB_PASSWORD = New-RandomKey
    $env:JWT_SECRET = New-RandomKey
    $env:ENCRYPTION_KEY = New-RandomKey
    $env:ADMIN_EMAIL = "admin.e2e@example.test"
    $env:ADMIN_PASSWORD = $adminPassword
    $env:E2E_ADMIN_PASSWORD = $adminPassword
    $env:DEMO_SEED_ENABLED = "true"
    $env:COOKIE_SECURE = "false"
    $env:FRONTEND_ORIGIN = "http://127.0.0.1:8088"
    $env:COMPOSE_PROJECT_NAME = $ComposeProject

    $composeArgs = @("compose", "-p", $ComposeProject) + $ComposeFiles + @("up", "-d", "--build", "--wait", "--wait-timeout", "300")
    $dockerStarted = $true
    Run-Step "1/4 - Demarrer l'application et la base de test" $ProjectRoot "docker" $composeArgs

    Run-Step "2/4 - Installer Playwright" $E2eDir "npm.cmd" @("install", "--no-audit", "--no-fund")
    Run-Step "3/4 - Installer Chromium" $E2eDir "npx.cmd" @("playwright", "install", "chromium")

    $playwrightArgs = @("run", "test:parcours")
    if ($Visible) {
        $playwrightArgs = @("run", "test:parcours:visible")
    }
    if ($Debug) {
        $playwrightArgs = @("run", "test:parcours", "--", "--debug")
    }

    Run-Step "4/4 - Parcours utilisateur dans Chromium" $E2eDir "npm.cmd" $playwrightArgs
}
catch {
    $failed = $true
    Write-Host ""
    Write-Host "TESTS FONCTIONNELS EN ECHEC : $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    if ($dockerStarted) {
        Write-Host ""
        Write-Host "Nettoyage de l'application et de la base de test..." -ForegroundColor Cyan
        Push-Location $ProjectRoot
        try {
            # Sous Windows PowerShell, Docker peut afficher sa progression sur stderr.
            # Seul le code de retour doit déterminer si le nettoyage a échoué.
            $ErrorActionPreference = "Continue"
            & docker compose -p $ComposeProject @ComposeFiles down -v --remove-orphans *> $null
            if ($LASTEXITCODE -ne 0) {
                $failed = $true
                Write-Warning "Le nettoyage Docker n'a pas pu etre termine."
            }
        }
        catch {
            $failed = $true
            Write-Warning "Nettoyage Docker incomplet : $($_.Exception.Message)"
        }
        finally {
            Pop-Location
        }
    }

    if ($environmentChanged) {
        foreach ($name in $names) {
            [Environment]::SetEnvironmentVariable($name, $original[$name], "Process")
        }
    }
}

if ($failed) {
    exit 1
}

Write-Host ""
Write-Host "TOUS LES TESTS FONCTIONNELS SONT REUSSIS." -ForegroundColor Green
