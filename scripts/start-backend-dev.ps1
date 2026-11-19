# Lance le backend localement avec PostgreSQL comme base du MVP.
# Les secrets sont chargés depuis .env ou depuis les variables d'environnement.
# La clé AES ne doit pas être régénérée à chaque démarrage, sinon les secrets déjà
# chiffrés en base deviennent illisibles.

$RootDir = Resolve-Path "$PSScriptRoot\.."
$EnvFile = Join-Path $RootDir ".env"

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line -split "=", 2
            $name = $parts[0].Trim()
            $value = $parts[1]
            if (-not [Environment]::GetEnvironmentVariable($name, "Process")) {
                [Environment]::SetEnvironmentVariable($name, $value, "Process")
            }
        }
    }
}

$env:DB_URL = if ($env:DB_URL) { $env:DB_URL } else { "jdbc:postgresql://localhost:5432/gmlshield" }
$env:DB_USER = if ($env:DB_USER) { $env:DB_USER } else { "gml" }
$env:FRONTEND_ORIGIN = "http://localhost:5173"
$env:DEMO_SEED_ENABLED = "true"
$env:COOKIE_SECURE = "false"

$required = @("DB_PASSWORD", "JWT_SECRET", "ENCRYPTION_KEY")
$missing = $required | Where-Object { -not [Environment]::GetEnvironmentVariable($_, "Process") }

if ($missing.Count -gt 0) {
    Write-Error "Variables manquantes: $($missing -join ', '). Exécutez .\scripts\create-local-env.ps1 ou définissez-les avant le lancement."
    exit 1
}

Set-Location "$RootDir\backend"
mvn spring-boot:run
