param(
    [switch]$KeepContainers
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path
$BackendDir = Join-Path $ProjectRoot "backend"
$FrontendDir = Join-Path $ProjectRoot "frontend"
$ArtifactsDir = Join-Path $ProjectRoot "artifacts\run-all-tests"
$ComposeProject = "gmlshield-run-all-tests"
$ComposeFiles = @("-f", "compose.yaml", "-f", "compose.test.yaml")

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor DarkGray
    Write-Host $Message -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor DarkGray
}

function Assert-Command {
    param([string]$Name, [string]$Help)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Commande '$Name' introuvable. $Help"
    }
}

function Invoke-External {
    param(
        [string]$Label,
        [string]$Command,
        [string[]]$Arguments,
        [string]$WorkingDirectory = $ProjectRoot,
        [string]$LogPath = ""
    )

    Write-Step $Label
    Push-Location $WorkingDirectory
    try {
        if ($LogPath) {
            # Capture stdout/stderr without treating native warning text as a test failure
            # in Windows PowerShell 5.1. The real exit code still decides the result.
            $ErrorActionPreference = "Continue"
            & $Command @Arguments 2>&1 | Tee-Object -FilePath $LogPath
        } else {
            & $Command @Arguments
        }
        $commandExitCode = $LASTEXITCODE
        if ($commandExitCode -ne 0) {
            throw "$Label a echoue (code $commandExitCode)."
        }
    }
    finally {
        Pop-Location
    }
}

function Invoke-DockerQuiet {
    param([string[]]$Arguments, [string]$LogPath)
    # Docker writes normal progress (Stopping/Removing) to stderr.
    # Record that output and check Docker's exit status, not its stream.
    $ErrorActionPreference = "Continue"
    & docker @Arguments *> $LogPath
    return [int]$LASTEXITCODE
}

function New-RandomBase64Key32 {
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

function Wait-HttpOk {
    param(
        [string]$Url,
        [int]$Attempts = 60,
        [int]$DelaySeconds = 2
    )

    for ($i = 1; $i -le $Attempts; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                return
            }
        }
        catch {
            # Le service peut etre encore en cours de demarrage.
        }

        Write-Host "Attente de $Url ($i/$Attempts)..." -ForegroundColor DarkGray
        Start-Sleep -Seconds $DelaySeconds
    }

    throw "Le service n'est pas devenu disponible : $Url"
}

$startTime = Get-Date
$success = $false
$exitCode = 0
$composeTouched = $false
$envWasOverridden = $false
$previousEnv = @{}
$apiStatus = "NON EXECUTE"
$cleanupStatus = "NON EXECUTE"
$frontendLog = Join-Path $ArtifactsDir "frontend-quality.log"
$apiLog = Join-Path $ArtifactsDir "api-tests.log"
$coverageLog = Join-Path $ArtifactsDir "coverage.log"

New-Item -ItemType Directory -Path $ArtifactsDir -Force | Out-Null

try {
    Write-Host "GML-SHIELD - validation locale complete" -ForegroundColor Green
    Write-Host "Projet : $ProjectRoot"

    Write-Step "[0/9] Verification des prerequis"
    Assert-Command "docker" "Installez/demarrez Docker Desktop."
    Assert-Command "mvn" "Installez Maven 3.9+ et Java 17."
    Assert-Command "npm.cmd" "Installez Node.js 24+ avec npm."

    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker n'est pas disponible. Demarrez Docker Desktop puis relancez le script."
    }

    & mvn -version
    if ($LASTEXITCODE -ne 0) { throw "Maven n'est pas operationnel." }
    & npm.cmd --version
    if ($LASTEXITCODE -ne 0) { throw "npm n'est pas operationnel." }

    Invoke-External `
        -Label "[1/9] Backend - formatage Java (Spotless)" `
        -Command "mvn" `
        -Arguments @("-B", "-ntp", "spotless:check") `
        -WorkingDirectory $BackendDir

    Invoke-External `
        -Label "[2/9] Backend - tests unitaires + integration Failsafe/PostgreSQL Testcontainers + JaCoCo" `
        -Command "mvn" `
        -Arguments @("-B", "-ntp", "verify") `
        -WorkingDirectory $BackendDir

    Write-Step "[3/9] Backend - seuils de couverture JaCoCo"
    & (Join-Path $ScriptDir "check-jacoco-coverage.ps1") -ReportPath (Join-Path $BackendDir "target\site\jacoco\jacoco.xml") 6>&1 | Tee-Object -FilePath $coverageLog
    if ($LASTEXITCODE -ne 0) {
        throw "Les seuils de couverture JaCoCo ne sont pas conformes."
    }

    Invoke-External `
        -Label "[4/9] Backend - analyse statique SpotBugs" `
        -Command "mvn" `
        -Arguments @("-B", "-ntp", "spotbugs:check") `
        -WorkingDirectory $BackendDir

    Invoke-External `
        -Label "[5/9] Frontend - installation reproductible des dependances" `
        -Command "npm.cmd" `
        -Arguments @("ci") `
        -WorkingDirectory $FrontendDir

    Invoke-External `
        -Label "[6/9] Frontend - lint + format + tests + build" `
        -Command "npm.cmd" `
        -Arguments @("run", "quality") `
        -WorkingDirectory $FrontendDir `
        -LogPath $frontendLog

    # Secrets ephemeres uniquement pour le docker-compose de validation.
    # Ils ne sont ni ecrits dans .env ni versionnes. Les anciennes valeurs
    # de l'environnement courant sont restaurees en fin de script.
    $envNames = @(
        "DB_PASSWORD",
        "JWT_SECRET",
        "ENCRYPTION_KEY",
        "DEMO_SEED_ENABLED",
        "COOKIE_SECURE",
        "FRONTEND_ORIGIN"
    )
    foreach ($name in $envNames) {
        $previousEnv[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    }
    $envWasOverridden = $true

    $env:DB_PASSWORD = "gmlshield-test-db-password"
    $env:JWT_SECRET = New-RandomBase64Key32
    $env:ENCRYPTION_KEY = New-RandomBase64Key32
    $env:DEMO_SEED_ENABLED = "true"
    $env:COOKIE_SECURE = "false"
    $env:FRONTEND_ORIGIN = "http://localhost:8088"

    # Nettoyage d'une execution precedente du SEUL projet Docker de test.
    Push-Location $ProjectRoot
    try {
        $beforeExitCode = Invoke-DockerQuiet -Arguments (@("compose", "-p", $ComposeProject) + $ComposeFiles + @("down", "-v", "--remove-orphans")) -LogPath (Join-Path $ArtifactsDir "docker-before.log")
        if ($beforeExitCode -ne 0) {
            throw "Impossible de preparer Docker de test (code $beforeExitCode). Voir docker-before.log."
        }
    }
    finally {
        Pop-Location
    }

    $composeTouched = $true

    Invoke-External `
        -Label "[7/9] Docker - build des images backend/frontend" `
        -Command "docker" `
        -Arguments (@("compose", "-p", $ComposeProject) + $ComposeFiles + @("build")) `
        -WorkingDirectory $ProjectRoot

    Invoke-External `
        -Label "[8/9] Docker - demarrage PostgreSQL + backend + frontend" `
        -Command "docker" `
        -Arguments (@("compose", "-p", $ComposeProject) + $ComposeFiles + @("up", "-d")) `
        -WorkingDirectory $ProjectRoot
    Write-Step "Attente des services"
    Wait-HttpOk -Url "http://localhost:8080/actuator/health" -Attempts 60 -DelaySeconds 2
    Wait-HttpOk -Url "http://localhost:8088/" -Attempts 30 -DelaySeconds 2

    Write-Step "[9/9] E2E API - code secret, preuve et cloture"
    & (Join-Path $ScriptDir "check-api.ps1") -BaseUrl "http://localhost:8080" 6>&1 | Tee-Object -FilePath $apiLog
    if (-not (Select-String -Path $apiLog -Pattern "VERIFICATION API LOCALE OK" -Quiet)) {
        throw "Le parcours API local n'a pas confirme sa reussite."
    }
    $apiStatus = "OK"

    $success = $true
}
catch {
    $exitCode = 1
    Write-Host ""
    Write-Host "VALIDATION KO" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
}
finally {
    if ($composeTouched) {
        Push-Location $ProjectRoot
        try {
            $logsExit = Invoke-DockerQuiet -Arguments (@("compose", "-p", $ComposeProject) + $ComposeFiles + @("logs", "--no-color")) -LogPath (Join-Path $ArtifactsDir "docker-compose.log")
            $statusExit = Invoke-DockerQuiet -Arguments (@("compose", "-p", $ComposeProject) + $ComposeFiles + @("ps", "--all")) -LogPath (Join-Path $ArtifactsDir "docker-compose-ps.txt")
            if ($logsExit -ne 0 -or $statusExit -ne 0) {
                Write-Warning "Diagnostic Docker incomplet : consulter docker-compose.log et docker-compose-ps.txt."
            }

            if ($KeepContainers) {
                $cleanupStatus = "CONSERVES (option)"
            } else {
                $downExit = Invoke-DockerQuiet -Arguments (@("compose", "-p", $ComposeProject) + $ComposeFiles + @("down", "-v", "--remove-orphans")) -LogPath (Join-Path $ArtifactsDir "docker-cleanup.log")

                # Verify both containers and named volumes, not only docker ps.
                $containersPath = Join-Path $ArtifactsDir "remaining-containers.txt"
                $volumesPath = Join-Path $ArtifactsDir "remaining-volumes.txt"
                $containersExit = Invoke-DockerQuiet -Arguments @("ps", "-a", "--filter", "label=com.docker.compose.project=$ComposeProject", "--format", "{{.Names}}") -LogPath $containersPath
                $volumesExit = Invoke-DockerQuiet -Arguments @("volume", "ls", "--filter", "label=com.docker.compose.project=$ComposeProject", "--format", "{{.Name}}") -LogPath $volumesPath

                $remainingContainers = [string](Get-Content -Path $containersPath -Raw -ErrorAction SilentlyContinue)
                $remainingVolumes = [string](Get-Content -Path $volumesPath -Raw -ErrorAction SilentlyContinue)

                if ($downExit -eq 0 -and $containersExit -eq 0 -and $volumesExit -eq 0 -and
                    [string]::IsNullOrWhiteSpace($remainingContainers) -and
                    [string]::IsNullOrWhiteSpace($remainingVolumes)) {
                    $cleanupStatus = "OK"
                } else {
                    $cleanupStatus = "ANOMALIE"
                    $exitCode = 1
                    Write-Warning "Nettoyage Docker non confirme : consulter docker-cleanup.log et remaining-*.txt."
                }
            }
        }
        catch {
            $cleanupStatus = "ANOMALIE"
            $exitCode = 1
            Write-Warning "Erreur de nettoyage Docker : $($_.Exception.Message)"
        }
        finally {
            Pop-Location
        }
    }

    if ($envWasOverridden) {
        foreach ($name in $previousEnv.Keys) {
            [Environment]::SetEnvironmentVariable($name, $previousEnv[$name], "Process")
        }
    }
}

# Generate the detail even if tests fail. Test and cleanup results stay separate.
try {
    $testStatus = if ($success) { "OK" } else { "ECHEC" }
    & (Join-Path $ScriptDir "report-local-tests.ps1") -BackendDir $BackendDir -ArtifactsDir $ArtifactsDir -StartedAt $startTime -ApiStatus $apiStatus -CleanupStatus $cleanupStatus -RunStatus $testStatus
} catch {
    Write-Warning "Rapport des tests impossible : $($_.Exception.Message)"
    $exitCode = 1
}

if ($exitCode -ne 0) {
    exit $exitCode
}

if ($success) {
    $duration = (Get-Date) - $startTime
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "VALIDATION DES TESTS : OK" -ForegroundColor Green
    Write-Host ("Duree : {0:mm\:ss}" -f $duration) -ForegroundColor Green
    Write-Host "Rapport JaCoCo : backend\target\site\jacoco\index.html" -ForegroundColor DarkGray
    Write-Host "Rapport detaille : artifacts\run-all-tests\resultats-tests.md" -ForegroundColor DarkGray
    Write-Host "Nettoyage Docker : $cleanupStatus" -ForegroundColor DarkGray
    Write-Host "Logs Docker      : artifacts\run-all-tests\docker-compose.log" -ForegroundColor DarkGray
    if ($KeepContainers) {
        Write-Host "Conteneurs conserves : http://localhost:8088" -ForegroundColor Yellow
        Write-Host "Pour arreter : docker compose -p $ComposeProject -f compose.yaml -f compose.test.yaml down -v" -ForegroundColor Yellow
    }
    Write-Host "============================================================" -ForegroundColor Green
}
