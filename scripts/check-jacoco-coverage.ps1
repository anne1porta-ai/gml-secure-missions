# Script local : affiche les pourcentages de couverture dans le bilan de
# run-all-tests.ps1. Les seuils bloquants sont dans backend/pom.xml
# (jacoco:check) ; garder les memes valeurs ici.
param(
    [string]$ReportPath = "backend/target/site/jacoco/jacoco.xml"
)

$ErrorActionPreference = "Stop"

$LineThreshold = 80.0
$BranchThreshold = 75.0
$CriticalLineThreshold = 90.0

# Zones critiques définies par le guide : authentification, ownership,
# chiffrement et upload. Les classes internes sont incluses automatiquement.
$CriticalClasses = @(
    "com/gml/shield/controller/AuthController",
    "com/gml/shield/security/AuthCookieService",
    "com/gml/shield/security/DatabaseUserDetailsService",
    "com/gml/shield/security/JwtAuthenticationFilter",
    "com/gml/shield/security/JwtService",
    "com/gml/shield/security/LoginAttemptService",
    "com/gml/shield/security/LoginRateLimitFilter",
    "com/gml/shield/security/RefreshTokenService",
    "com/gml/shield/security/RestAccessDeniedHandler",
    "com/gml/shield/security/RestAuthenticationEntryPoint",
    "com/gml/shield/service/MissionService",
    "com/gml/shield/service/DocumentService",
    "com/gml/shield/service/SecretEncryptionService",
    "com/gml/shield/service/FileStorageService"
)

function Get-Percentage {
    param(
        [int]$Covered,
        [int]$Missed
    )

    $total = $Covered + $Missed

    if ($total -eq 0) {
        return 100.0
    }

    return (100.0 * $Covered / $total)
}

function Get-DirectCounter {
    param(
        [System.Xml.XmlNode]$Element,
        [string]$Type
    )

    return $Element.SelectSingleNode(
        "./*[local-name()='counter' and @type='$Type']"
    )
}

function Get-CounterValue {
    param(
        [System.Xml.XmlNode]$Counter,
        [string]$AttributeName
    )

    if ($null -eq $Counter) {
        return 0
    }

    $attribute = $Counter.Attributes[$AttributeName]

    if ($null -eq $attribute) {
        return 0
    }

    return [int]$attribute.Value
}

# ---------------------------------------------------------------------------
# Chargement du rapport JaCoCo
# ---------------------------------------------------------------------------

$resolvedReportPath = Resolve-Path $ReportPath -ErrorAction Stop

[xml]$report = Get-Content -Raw -Encoding UTF8 $resolvedReportPath

# ---------------------------------------------------------------------------
# Couverture globale
#
# On additionne les compteurs de chaque package.
# Cela évite de dépendre de la présence de compteurs globaux à la racine
# du document XML.
# ---------------------------------------------------------------------------

$packageNodes = @(
    $report.SelectNodes(
        "//*[local-name()='package']"
    )
)

if ($packageNodes.Count -eq 0) {
    throw "Aucun package trouvé dans le rapport JaCoCo '$ReportPath'."
}

$globalLineCovered = 0
$globalLineMissed = 0
$globalBranchCovered = 0
$globalBranchMissed = 0

$lineCounterFound = $false
$branchCounterFound = $false

foreach ($package in $packageNodes) {

    $lineCounter = Get-DirectCounter `
        -Element $package `
        -Type "LINE"

    if ($null -ne $lineCounter) {
        $lineCounterFound = $true

        $globalLineCovered += Get-CounterValue `
            -Counter $lineCounter `
            -AttributeName "covered"

        $globalLineMissed += Get-CounterValue `
            -Counter $lineCounter `
            -AttributeName "missed"
    }

    $branchCounter = Get-DirectCounter `
        -Element $package `
        -Type "BRANCH"

    if ($null -ne $branchCounter) {
        $branchCounterFound = $true

        $globalBranchCovered += Get-CounterValue `
            -Counter $branchCounter `
            -AttributeName "covered"

        $globalBranchMissed += Get-CounterValue `
            -Counter $branchCounter `
            -AttributeName "missed"
    }
}

if (-not $lineCounterFound) {
    throw "Aucun compteur LINE trouvé dans les packages du rapport JaCoCo."
}

if (-not $branchCounterFound) {
    throw "Aucun compteur BRANCH trouvé dans les packages du rapport JaCoCo."
}

$lineCoverage = Get-Percentage `
    $globalLineCovered `
    $globalLineMissed

$branchCoverage = Get-Percentage `
    $globalBranchCovered `
    $globalBranchMissed

# ---------------------------------------------------------------------------
# Couverture des classes critiques
# ---------------------------------------------------------------------------

$criticalCovered = 0
$criticalMissed = 0

$foundCritical = [System.Collections.Generic.HashSet[string]]::new()

$classNodes = @(
    $report.SelectNodes(
        "//*[local-name()='class']"
    )
)

foreach ($class in $classNodes) {

    $classNameAttribute = $class.Attributes["name"]

    if ($null -eq $classNameAttribute) {
        continue
    }

    $className = [string]$classNameAttribute.Value
    $baseClassName = $className.Split('$')[0]

    if ($CriticalClasses -notcontains $baseClassName) {
        continue
    }

    [void]$foundCritical.Add($baseClassName)

    $lineCounter = Get-DirectCounter `
        -Element $class `
        -Type "LINE"

    if ($null -eq $lineCounter) {
        continue
    }

    $criticalCovered += Get-CounterValue `
        -Counter $lineCounter `
        -AttributeName "covered"

    $criticalMissed += Get-CounterValue `
        -Counter $lineCounter `
        -AttributeName "missed"
}

$missingClasses = @(
    $CriticalClasses |
        Where-Object {
            -not $foundCritical.Contains($_)
        }
)

if ($missingClasses.Count -gt 0) {
    Write-Error (
        "Classes critiques absentes du rapport JaCoCo : " +
        ($missingClasses -join ", ")
    )
    exit 2
}

$criticalCoverage = Get-Percentage `
    $criticalCovered `
    $criticalMissed

# ---------------------------------------------------------------------------
# Affichage de l'etape de controle de couverture
# ---------------------------------------------------------------------------

Write-Host (
    "Couverture lignes          : {0:N2}% (seuil {1:N0}%)" -f `
        $lineCoverage,
        $LineThreshold
)

Write-Host (
    "Couverture branches        : {0:N2}% (seuil {1:N0}%)" -f `
        $branchCoverage,
        $BranchThreshold
)

Write-Host (
    "Couverture zones critiques : {0:N2}% lignes (seuil {1:N0}%)" -f `
        $criticalCoverage,
        $CriticalLineThreshold
)

$failures = @()

if ($lineCoverage -lt $LineThreshold) {
    $failures += "couverture lignes"
}

if ($branchCoverage -lt $BranchThreshold) {
    $failures += "couverture branches"
}

if ($criticalCoverage -lt $CriticalLineThreshold) {
    $failures += "couverture zones critiques"
}

if ($failures.Count -gt 0) {
    Write-Error (
        "ETAPE COUVERTURE : ECHEC -> " +
        ($failures -join ", ")
    )
    exit 1
}

Write-Host "ETAPE COUVERTURE : OK" -ForegroundColor Green