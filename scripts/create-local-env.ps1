$ErrorActionPreference = "Stop"

$RootDir = Resolve-Path "$PSScriptRoot\.."
$EnvFile = Join-Path $RootDir ".env"

if (Test-Path $EnvFile) {
    Write-Error ".env existe déjà. Supprimez-le explicitement si vous voulez régénérer les secrets."
    exit 1
}

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()

try {
    $jwtBytes = New-Object byte[] 48
    $aesBytes = New-Object byte[] 32
    $dbBytes = New-Object byte[] 24
    $adminBytes = New-Object byte[] 18

    $rng.GetBytes($jwtBytes)
    $rng.GetBytes($aesBytes)
    $rng.GetBytes($dbBytes)
    $rng.GetBytes($adminBytes)

    $JwtSecret = [Convert]::ToBase64String($jwtBytes)
    $EncryptionKey = [Convert]::ToBase64String($aesBytes)
    $DbPassword = -join ($dbBytes | ForEach-Object { $_.ToString("x2") })
    $AdminPassword = [Convert]::ToBase64String($adminBytes)

    $content = @"
# Généré localement. Ce fichier est ignoré par Git.
JWT_SECRET=$JwtSecret
ENCRYPTION_KEY=$EncryptionKey
DB_PASSWORD=$DbPassword
DEMO_SEED_ENABLED=true
ADMIN_EMAIL=admin@gml.local
ADMIN_PASSWORD=$AdminPassword
COOKIE_SECURE=false
FRONTEND_ORIGIN=http://localhost:8088
"@

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($EnvFile, $content, $utf8NoBom)
    Write-Host "Fichier .env local généré : $EnvFile"
    Write-Host "Compte administrateur : admin@gml.local (mot de passe : ADMIN_PASSWORD dans .env)"
}
finally {
    $rng.Dispose()
}
