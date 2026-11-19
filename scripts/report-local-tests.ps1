param(
    [Parameter(Mandatory = $true)]
    [string]$BackendDir,
    [Parameter(Mandatory = $true)]
    [string]$ArtifactsDir,
    [Parameter(Mandatory = $true)]
    [datetime]$StartedAt,
    [string]$ApiStatus = "NON EXECUTE",
    [string]$CleanupStatus = "NON EXECUTE",
    [string]$RunStatus = "ECHEC"
)

$ErrorActionPreference = "Stop"

# One row per real test: Java reports come from Surefire/Failsafe XML; React
# cases come from the npm/Node test output; API steps come from check-api.ps1.
# Do not invent test totals or treat a missing report as a success.
$rows = New-Object System.Collections.Generic.List[object]

function Add-Result {
    param([string]$Group, [string]$TestName, [string]$Status, [string]$Details = "")
    $rows.Add([pscustomobject]@{
        Groupe = $Group
        Test = $TestName
        Resultat = $Status
        Detail = $Details
    })
}

function Escape-Cell {
    param([string]$Value)
    if ($null -eq $Value) { return "" }
    return ($Value -replace '\|', '/' -replace '[\r\n]+', ' ').Trim()
}

function Is-CurrentRunFile {
    param([string]$Path)
    return (Test-Path $Path) -and ((Get-Item $Path).LastWriteTime -ge $StartedAt.AddSeconds(-5))
}

$reportDirs = @(
    @{ Path = (Join-Path $BackendDir "target\surefire-reports"); Group = "Java - unitaires" },
    @{ Path = (Join-Path $BackendDir "target\failsafe-reports"); Group = "Java - integration PostgreSQL" }
)

foreach ($dir in $reportDirs) {
    if (-not (Test-Path $dir.Path)) { continue }
    $files = @(Get-ChildItem -Path $dir.Path -Filter "TEST-*.xml" -File |
        Where-Object { $_.LastWriteTime -ge $StartedAt.AddSeconds(-5) })

    foreach ($file in $files) {
        try {
            [xml]$xml = Get-Content -Path $file.FullName -Raw -Encoding UTF8
            foreach ($testCase in $xml.SelectNodes("//*[local-name()='testcase']")) {
                $name = $testCase.GetAttribute("classname") + "." + $testCase.GetAttribute("name")
                $failure = $testCase.SelectSingleNode("./*[local-name()='failure' or local-name()='error']")
                $skipped = $testCase.SelectSingleNode("./*[local-name()='skipped']")

                if ($null -ne $failure) {
                    $errorMessage = [string]$failure.GetAttribute("message")
                    if ([string]::IsNullOrWhiteSpace($errorMessage)) {
                        $errorMessage = [string]$failure.InnerText
                    }
                    Add-Result $dir.Group $name "ECHEC" $errorMessage
                } elseif ($null -ne $skipped) {
                    Add-Result $dir.Group $name "IGNORE" ([string]$skipped.GetAttribute("message"))
                } else {
                    Add-Result $dir.Group $name "OK"
                }
            }
        } catch {
            Add-Result $dir.Group $file.Name "RAPPORT ILLISIBLE" $_.Exception.Message
        }
    }
}

$frontendLog = Join-Path $ArtifactsDir "frontend-quality.log"
if (Is-CurrentRunFile $frontendLog) {
    $insideNodeTests = $false
    foreach ($line in Get-Content -Path $frontendLog) {
        $clean = ($line -replace '\x1b\[[0-9;]*m', '').Trim()
        if ($clean -match '^> node --test(\s|$)') {
            $insideNodeTests = $true
            continue
        }
        if ($insideNodeTests -and $clean -match '^> vite build') {
            $insideNodeTests = $false
        }
        if (-not $insideNodeTests) { continue }

        # Node emits TAP when piped to a log; the human-friendly reporter uses
        # a checkmark or a cross. Support both, including skipped cases.
        if ($clean -match '^not ok\s+\d+\s+-\s+(.+)$') {
            Add-Result "React - Node test" $matches[1] "ECHEC"
        } elseif ($clean -match '^ok\s+\d+\s+-\s+(.+)$') {
            $testName = $matches[1]
            if ($testName -match '\s+#\s+SKIP') {
                Add-Result "React - Node test" $testName "IGNORE"
            } else {
                Add-Result "React - Node test" $testName "OK"
            }
        } elseif ($clean -match ('^' + [regex]::Escape([string][char]0x2714) + '\s+(.+?)\s+\(')) {
            Add-Result "React - Node test" $matches[1] "OK"
        } elseif ($clean -match ('^' + [regex]::Escape([string][char]0x2716) + '\s+(.+?)\s+\(')) {
            Add-Result "React - Node test" $matches[1] "ECHEC"
        }
    }
}

$apiLog = Join-Path $ArtifactsDir "api-tests.log"
$apiSteps = @()
if (Is-CurrentRunFile $apiLog) {
    foreach ($line in Get-Content -Path $apiLog) {
        $clean = ($line -replace '\x1b\[[0-9;]*m', '').Trim()
        if ($clean -match '^\[(\d+)/(\d+)\]\s+(.+)$') {
            $apiSteps += [pscustomobject]@{
                Number = [int]$matches[1]
                Total = [int]$matches[2]
                Description = $matches[3]
            }
        }
    }
}
# If PowerShell did not redirect Write-Host to the log, read the exact step
# labels from check-api.ps1. Mark them OK only if the script returned success.
if ($apiSteps.Count -eq 0 -and $ApiStatus -eq "OK") {
    $apiSource = Join-Path $PSScriptRoot "check-api.ps1"
    if (Test-Path $apiSource) {
        foreach ($line in Get-Content -Path $apiSource) {
            if ($line -match 'Write-Host "\[(\d+)/(\d+)\]\s+([^"]+)"') {
                $apiSteps += [pscustomobject]@{
                    Number = [int]$matches[1]
                    Total = [int]$matches[2]
                    Description = $matches[3]
                }
            }
        }
    }
}
if ($apiSteps.Count -gt 0) {
    $maxStep = ($apiSteps | Measure-Object Number -Maximum).Maximum
    foreach ($step in $apiSteps) {
        $state = "OK"
        if ($ApiStatus -ne "OK" -and $step.Number -eq $maxStep) {
            $state = "A VERIFIER"
        }
        Add-Result "Parcours API local" $step.Description $state
    }
} elseif ($ApiStatus -eq "OK") {
    Add-Result "Parcours API local" "check-api.ps1" "A VERIFIER" "Sortie des etapes indisponible."
}

$coverageLog = Join-Path $ArtifactsDir "coverage.log"
$coverageLines = @()
if (Is-CurrentRunFile $coverageLog) {
    $coverageLines = @(Get-Content -Path $coverageLog |
        Where-Object { $_ -match 'Couverture (lignes|branches|zones critiques)' -or $_ -match 'ETAPE COUVERTURE' })
}

$md = New-Object System.Collections.Generic.List[string]
$md.Add("# GML-SHIELD - rapport des tests locaux")
$md.Add("")
$md.Add("Execution : " + $StartedAt.ToString("yyyy-MM-dd HH:mm:ss"))
$md.Add("")
$md.Add("**Tests : $RunStatus** | **Nettoyage Docker : $CleanupStatus** | **Parcours API : $ApiStatus**")
$md.Add("")
$md.Add("## Bilan des tests")
$md.Add("")
$md.Add("| Groupe | Reussis | Echecs | Ignores | Total |")
$md.Add("| --- | ---: | ---: | ---: | ---: |")

$groups = @("Java - unitaires", "Java - integration PostgreSQL", "React - Node test", "Parcours API local")
foreach ($group in $groups) {
    $selected = @($rows | Where-Object { $_.Groupe -eq $group })
    $ok = @($selected | Where-Object { $_.Resultat -eq "OK" }).Count
    $ko = @($selected | Where-Object { $_.Resultat -eq "ECHEC" -or $_.Resultat -eq "RAPPORT ILLISIBLE" }).Count
    $skipped = @($selected | Where-Object { $_.Resultat -eq "IGNORE" }).Count
    $md.Add("| $group | $ok | $ko | $skipped | $($selected.Count) |")
}

$md.Add("")
$md.Add("## Couverture JaCoCo")
$md.Add("")
if ($coverageLines.Count -eq 0) {
    $md.Add("Non disponible dans cette execution.")
} else {
    foreach ($line in $coverageLines) {
        $md.Add("- " + (Escape-Cell $line))
    }
}

foreach ($group in $groups) {
    $md.Add("")
    $md.Add("## $group")
    $md.Add("")
    $selected = @($rows | Where-Object { $_.Groupe -eq $group })
    if ($selected.Count -eq 0) {
        $md.Add("Aucun resultat disponible pour cette execution.")
        continue
    }
    $md.Add("| Test | Resultat | Detail en cas d'erreur |")
    $md.Add("| --- | --- | --- |")
    foreach ($entry in $selected) {
        $detail = Escape-Cell $entry.Detail
        if ($detail.Length -gt 400) { $detail = $detail.Substring(0, 400) + "..." }
        $md.Add("| $(Escape-Cell $entry.Test) | $($entry.Resultat) | $detail |")
    }
}

$md.Add("")
$md.Add("## Diagnostic Docker")
$md.Add("")
$md.Add("Nettoyage : **$CleanupStatus**. La fin des tests ne garantit pas, a elle seule, que le nettoyage a reussi.")
$md.Add("")
$md.Add("Fichiers : docker-compose.log, docker-compose-ps.txt, docker-cleanup.log, remaining-containers.txt et remaining-volumes.txt (selon l'etape atteinte).")

$reportPath = Join-Path $ArtifactsDir "resultats-tests.md"
$csvPath = Join-Path $ArtifactsDir "resultats-tests.csv"
$md | Set-Content -Path $reportPath -Encoding UTF8
if ($rows.Count -gt 0) {
    $rows | Export-Csv -Path $csvPath -Delimiter ';' -NoTypeInformation -Encoding UTF8
} else {
    '"Groupe";"Test";"Resultat";"Detail"' | Set-Content -Path $csvPath -Encoding UTF8
}

Write-Host ""
Write-Host "================ BILAN DES TESTS ================" -ForegroundColor Cyan
foreach ($group in $groups) {
    $selected = @($rows | Where-Object { $_.Groupe -eq $group })
    $ok = @($selected | Where-Object { $_.Resultat -eq "OK" }).Count
    $ko = @($selected | Where-Object { $_.Resultat -eq "ECHEC" -or $_.Resultat -eq "RAPPORT ILLISIBLE" }).Count
    $skip = @($selected | Where-Object { $_.Resultat -eq "IGNORE" }).Count
    Write-Host ("{0} : {1} OK, {2} echec(s), {3} ignore(s)" -f $group, $ok, $ko, $skip)
}
Write-Host "Validation tests : $RunStatus"
Write-Host "Nettoyage Docker : $CleanupStatus"
Write-Host "Rapport detaille : $reportPath" -ForegroundColor Green
Write-Host "Fichier CSV      : $csvPath"
Write-Host "=================================================" -ForegroundColor Cyan
