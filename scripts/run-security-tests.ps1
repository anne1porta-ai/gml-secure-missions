param(
    [switch]$Visible,
    [switch]$Debug,
    [switch]$Help
)

if ($Help) {
    Write-Host "Tests de securite GML-SHIELD (depuis la racine du projet)"
    Write-Host ".\scripts\run-security-tests.ps1           : tests Java, React et Chromium"
    Write-Host ".\scripts\run-security-tests.ps1 -Visible  : montrer les injections dans Chromium"
    Write-Host ".\scripts\run-security-tests.ps1 -Debug    : execution pas a pas avec Playwright"
    Write-Host "Docker Desktop, Java 17, Maven et Node.js 24 sont necessaires."
    return
}

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if ($Visible -and $Debug) {
    throw "Choisir -Visible ou -Debug, pas les deux."
}

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ComposeProject = "gmlshield-security-$PID"
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
    "COOKIE_SECURE", "FRONTEND_ORIGIN", "COMPOSE_PROJECT_NAME"
)
$original = @{}
$environmentChanged = $false
$dockerStarted = $false
$failed = $false

try {
    foreach ($name in @("docker", "mvn", "npm.cmd", "npx.cmd")) {
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

    # Cote Java : authentification, droits, fichiers, injections et chiffrement.
    $unitTests = "JwtServiceTest,RefreshTokenServiceTest,LoginAttemptServiceTest,LoginRateLimitFilterTest,SecretEncryptionServiceTest,FileStorageServiceTest,WriteRequestAuditInterceptorTest,AuthControllerTest,UserManagementServiceTest"
    $integrationTests = "SecurityIntegrationTest,DocumentSecurityIntegrationTest,InjectionSecurityIntegrationTest"
    $javaArgs = @("-B", "-ntp", "-Dtest=$unitTests", "-Dit.test=$integrationTests", "verify")

    Run-Step "1/5 - Tests de securite Java et PostgreSQL" (Join-Path $ProjectRoot "backend") "mvn" $javaArgs

    # Cote React : dont la verification du titre contenant une balise script.
    Run-Step "2/5 - Installer les dependances React" (Join-Path $ProjectRoot "frontend") "npm.cmd" @("ci")
    Run-Step "3/5 - Tests React, dont l'echappement HTML" (Join-Path $ProjectRoot "frontend") "npm.cmd" @("test")

    # L'application de test a sa propre base et ses propres volumes Docker.
    # Les secrets sont temporaires et le fichier .env local n'est pas modifie.
    foreach ($name in $names) {
        $original[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    }
    $environmentChanged = $true

    $env:DB_PASSWORD = New-RandomKey
    $env:JWT_SECRET = New-RandomKey
    $env:ENCRYPTION_KEY = New-RandomKey
    $env:ADMIN_EMAIL = "admin.security@example.test"
    $env:ADMIN_PASSWORD = New-RandomKey
    $env:DEMO_SEED_ENABLED = "true"
    $env:COOKIE_SECURE = "false"
    $env:FRONTEND_ORIGIN = "http://127.0.0.1:8088"
    $env:COMPOSE_PROJECT_NAME = $ComposeProject

    $composeArgs = @("compose", "-p", $ComposeProject) + $ComposeFiles + @("up", "-d", "--build", "--wait", "--wait-timeout", "300")
    $dockerStarted = $true
    Run-Step "4/5 - Demarrer l'application et la base de test" $ProjectRoot "docker" $composeArgs

    Run-Step "Installer Playwright" (Join-Path $ProjectRoot "e2e") "npm.cmd" @("install", "--no-audit", "--no-fund")
    Run-Step "Installer Chromium" (Join-Path $ProjectRoot "e2e") "npx.cmd" @("playwright", "install", "chromium")

    $playwrightArgs = @("run", "test:securite")
    if ($Visible) {
        $playwrightArgs = @("run", "test:securite:visible")
    }
    if ($Debug) {
        $playwrightArgs = @("run", "test:securite", "--", "--debug")
    }

    Run-Step "5/5 - Simulations d'injection dans Chromium" (Join-Path $ProjectRoot "e2e") "npm.cmd" $playwrightArgs
}
catch {
    $failed = $true
    Write-Host ""
    Write-Host "TESTS DE SECURITE EN ECHEC : $($_.Exception.Message)" -ForegroundColor Red
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
            $cleanupExitCode = $LASTEXITCODE
            if ($cleanupExitCode -ne 0) {
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
Write-Host "TOUS LES TESTS DE SECURITE SONT REUSSIS." -ForegroundColor Green
