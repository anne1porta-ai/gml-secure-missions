param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$DispatcherEmail = "dispatcher1@gml.local",
    [string]$DispatcherPassword = "dispatch123",
    [string]$DriverEmail = "driver1@gml.local",
    [string]$DriverPassword = "driver123"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$target = [Uri]$BaseUrl

# Ce test cree des donnees et ne doit etre execute que sur une instance locale.
if ($target.Scheme -ne "http" -or ($target.Host -ne "localhost" -and $target.Host -ne "127.0.0.1")) {
    throw "Le test API accepte uniquement http://localhost ou http://127.0.0.1."
}
$dispatcherSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$driverSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Get-CsrfHeaders {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Url
    )

    Invoke-RestMethod -Uri "$Url/api/auth/csrf" -Method Get -WebSession $Session | Out-Null

    $cookies = $Session.Cookies.GetCookies([Uri]$Url)
    $csrfCookie = $cookies | Where-Object { $_.Name -eq "XSRF-TOKEN" } | Select-Object -First 1

    if (-not $csrfCookie) {
        throw "Cookie XSRF-TOKEN introuvable apres GET /api/auth/csrf"
    }

    return @{ "X-XSRF-TOKEN" = $csrfCookie.Value }
}

Write-Host "[1/12] Health" -ForegroundColor Cyan
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get
if ($health.status -ne "UP") {
    throw "Healthcheck backend KO"
}

Write-Host "[2/12] Login Dispatcher" -ForegroundColor Cyan
$dispatcherHeaders = Get-CsrfHeaders -Session $dispatcherSession -Url $BaseUrl
$dispatcherBody = @{ email = $DispatcherEmail; password = $DispatcherPassword } | ConvertTo-Json
$dispatcher = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
    -WebSession $dispatcherSession -Headers $dispatcherHeaders `
    -ContentType "application/json" -Body $dispatcherBody
if ($dispatcher.role -ne "DISPATCHER") {
    throw "Le compte Dispatcher n'a pas le role attendu"
}

Write-Host "[3/12] Creation mission" -ForegroundColor Cyan
$dispatcherHeaders = Get-CsrfHeaders -Session $dispatcherSession -Url $BaseUrl
$missionBody = @{
    title = "E2E validation"
    destination = "Lyon"
    secretCode = "E2E-SECRET"
    driverEmail = $DriverEmail
} | ConvertTo-Json
$mission = Invoke-RestMethod -Uri "$BaseUrl/api/missions" -Method Post `
    -WebSession $dispatcherSession -Headers $dispatcherHeaders `
    -ContentType "application/json" -Body $missionBody
if (-not $mission.id) {
    throw "La mission E2E n'a pas ete creee"
}

Write-Host "[4/12] Login Driver" -ForegroundColor Cyan
$driverHeaders = Get-CsrfHeaders -Session $driverSession -Url $BaseUrl
$driverBody = @{ email = $DriverEmail; password = $DriverPassword } | ConvertTo-Json
$driver = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
    -WebSession $driverSession -Headers $driverHeaders `
    -ContentType "application/json" -Body $driverBody
if ($driver.role -ne "DRIVER") {
    throw "Le compte Driver n'a pas le role attendu"
}

function Assert-Conflict {
    param(
        [scriptblock]$Action,
        [string]$Message
    )

    $rejected = $false
    try {
        & $Action | Out-Null
    }
    catch {
        $response = $_.Exception.Response
        if ($null -eq $response) {
            throw
        }
        if ([int]$response.StatusCode -eq 409) {
            $rejected = $true
        }
        else {
            throw
        }
    }
    if (-not $rejected) {
        throw $Message
    }
}

Write-Host "[5/12] Mission visible sans code secret" -ForegroundColor Cyan
$driverMissions = Invoke-RestMethod -Uri "$BaseUrl/api/missions" -Method Get -WebSession $driverSession
$driverMission = $driverMissions | Where-Object { $_.id -eq $mission.id } | Select-Object -First 1
$dispatcherMissions = Invoke-RestMethod -Uri "$BaseUrl/api/missions" -Method Get -WebSession $dispatcherSession
$dispatcherMission = $dispatcherMissions | Where-Object { $_.id -eq $mission.id } | Select-Object -First 1
if (-not $driverMission -or -not $dispatcherMission) {
    throw "La mission est absente d'une liste autorisee."
}
if ($null -ne $driverMission.PSObject.Properties["secretCode"] -or $null -ne $dispatcherMission.PSObject.Properties["secretCode"]) {
    throw "Le code secret ne doit jamais figurer dans les listes."
}

Write-Host "[6/12] Code secret indisponible avant le depart" -ForegroundColor Cyan
$secretUrl = "$BaseUrl/api/missions/$($mission.id)/secret-code"
Assert-Conflict -Action {
    Invoke-RestMethod -Uri $secretUrl -Method Get -WebSession $driverSession
} -Message "Le code est disponible avant le depart."

Write-Host "[7/12] Passage en cours" -ForegroundColor Cyan
$driverHeaders = Get-CsrfHeaders -Session $driverSession -Url $BaseUrl
$statusBody = @{ status = "IN_PROGRESS" } | ConvertTo-Json
$started = Invoke-RestMethod -Uri "$BaseUrl/api/missions/$($mission.id)/status" -Method Patch -WebSession $driverSession -Headers $driverHeaders -ContentType "application/json" -Body $statusBody
if ($started.status -ne "IN_PROGRESS") {
    throw "Le demarrage de la mission a echoue."
}

Write-Host "[8/12] Code secret pendant la mission" -ForegroundColor Cyan
$revealed = Invoke-RestMethod -Uri $secretUrl -Method Get -WebSession $driverSession
if ($revealed.secretCode -ne "E2E-SECRET") {
    throw "Le code secret est incorrect."
}

Write-Host "[9/12] Cloture refusee sans preuve" -ForegroundColor Cyan
$driverHeaders = Get-CsrfHeaders -Session $driverSession -Url $BaseUrl
$completedBody = @{ status = "COMPLETED" } | ConvertTo-Json
Assert-Conflict -Action {
    Invoke-RestMethod -Uri "$BaseUrl/api/missions/$($mission.id)/status" -Method Patch -WebSession $driverSession -Headers $driverHeaders -ContentType "application/json" -Body $completedBody
} -Message "La cloture sans preuve a ete acceptee."

Write-Host "[10/12] Depot d'une preuve PDF" -ForegroundColor Cyan
Add-Type -AssemblyName System.Net.Http
$driverHeaders = Get-CsrfHeaders -Session $driverSession -Url $BaseUrl
$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.CookieContainer = $driverSession.Cookies
$httpClient = [System.Net.Http.HttpClient]::new($handler)
$multipart = [System.Net.Http.MultipartFormDataContent]::new()

try {
    $httpClient.DefaultRequestHeaders.Add("X-XSRF-TOKEN", $driverHeaders["X-XSRF-TOKEN"])
    $pdfText = "%PDF-1.4" + [Environment]::NewLine + "1 0 obj" + [Environment]::NewLine + "<<>>" + [Environment]::NewLine + "endobj" + [Environment]::NewLine + "%%EOF"
    $pdfBytes = [System.Text.Encoding]::ASCII.GetBytes($pdfText)
    $pdfPart = [System.Net.Http.ByteArrayContent]::new($pdfBytes)
    $pdfPart.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/pdf")
    $multipart.Add($pdfPart, "file", "preuve-test.pdf")
    $uploadResponse = $httpClient.PostAsync("$BaseUrl/api/missions/$($mission.id)/documents", $multipart).GetAwaiter().GetResult()
    $uploadBody = $uploadResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $uploadResponse.IsSuccessStatusCode) {
        throw "Depot refuse (HTTP $([int]$uploadResponse.StatusCode)) : $uploadBody"
    }
    $document = $uploadBody | ConvertFrom-Json
    if (-not $document.id -or $document.mimeType -ne "application/pdf") {
        throw "La preuve PDF n'est pas correctement enregistree."
    }
}
finally {
    $multipart.Dispose()
    $httpClient.Dispose()
}

Write-Host "[11/12] Cloture autorisee apres depot" -ForegroundColor Cyan
$driverHeaders = Get-CsrfHeaders -Session $driverSession -Url $BaseUrl
$completed = Invoke-RestMethod -Uri "$BaseUrl/api/missions/$($mission.id)/status" -Method Patch -WebSession $driverSession -Headers $driverHeaders -ContentType "application/json" -Body $completedBody
if ($completed.status -ne "COMPLETED") {
    throw "La cloture a echoue."
}
Assert-Conflict -Action {
    Invoke-RestMethod -Uri $secretUrl -Method Get -WebSession $driverSession
} -Message "Le code est toujours accessible apres la cloture."

Write-Host "[12/12] Documents et sessions" -ForegroundColor Cyan
$documents = Invoke-RestMethod -Uri "$BaseUrl/api/missions/$($mission.id)/documents" -Method Get -WebSession $driverSession
$storedDocument = $documents | Where-Object { $_.id -eq $document.id } | Select-Object -First 1
if (-not $storedDocument) {
    throw "La preuve n'est plus visible apres la cloture."
}
$dispatcherMe = Invoke-RestMethod -Uri "$BaseUrl/api/auth/me" -Method Get -WebSession $dispatcherSession
$driverMe = Invoke-RestMethod -Uri "$BaseUrl/api/auth/me" -Method Get -WebSession $driverSession
if ($dispatcherMe.role -ne "DISPATCHER" -or $driverMe.role -ne "DRIVER") {
    throw "Les sessions ne sont plus valides."
}

Write-Host "VERIFICATION API LOCALE OK" -ForegroundColor Green
