[CmdletBinding()]
param(
    [switch]$EnableSnsSqs,
    [switch]$EnableKinesis,
    [switch]$EnableCloudOrders,
    [switch]$DeployKinesis,
    [switch]$CheckOnly,
    [string]$ApiBaseUrl = "http://localhost:8080",
    [ValidateRange(30, 600)]
    [int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and
                (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                 ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $values[$name] = $value
        }
    }

    return $values
}

function Import-DotEnv {
    param([hashtable]$Values)

    $credentialVariables = @(
        "AWS_ACCESS_KEY_ID",
        "AWS_SECRET_ACCESS_KEY",
        "AWS_SESSION_TOKEN"
    )

    foreach ($name in $Values.Keys) {
        if ($name -in $credentialVariables) {
            Write-Warning "AWS credential variables in .env are not loaded. Use an AWS profile or the current PowerShell environment."
            continue
        }

        $value = [string]$Values[$name]
        $currentValue = [Environment]::GetEnvironmentVariable($name, "Process")
        if (![string]::IsNullOrWhiteSpace($value) -and
            [string]::IsNullOrWhiteSpace($currentValue)) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

function Test-ConfiguredValue {
    param(
        [string]$Name,
        [hashtable[]]$DotEnvFiles
    )

    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if (![string]::IsNullOrWhiteSpace($value)) {
        return $true
    }

    foreach ($fileValues in $DotEnvFiles) {
        if ($fileValues.ContainsKey($Name) -and
            ![string]::IsNullOrWhiteSpace([string]$fileValues[$Name])) {
            return $true
        }
    }

    return $false
}

function Assert-ConfiguredValues {
    param(
        [string]$Component,
        [string[]]$Names,
        [hashtable[]]$DotEnvFiles
    )

    $missing = @()
    foreach ($name in $Names) {
        if (!(Test-ConfiguredValue -Name $name -DotEnvFiles $DotEnvFiles)) {
            $missing += $name
        }
    }

    if ($missing.Count -gt 0) {
        throw "$Component tiene configuración incompleta. Revisa sus .env locales; faltan: $($missing -join ', '). No se muestran valores."
    }
}

function Set-DefaultEnvironmentValue {
    param(
        [string]$Name,
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace(
        [Environment]::GetEnvironmentVariable($Name, "Process"))) {
        [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
    }
}

function Test-Enabled {
    param([string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    return $value -match '^(?i:true|1|yes)$'
}

function Test-PortOpen {
    param([int]$Port)

    return [bool](Test-NetConnection -ComputerName "127.0.0.1" -Port $Port `
        -InformationLevel Quiet -WarningAction SilentlyContinue)
}

function Assert-CommandExists {
    param([string]$Name)

    if (!(Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "No se encontró '$Name' en PATH. Instala el requisito indicado en GUIA_INICIO_SERVICIOS.md."
    }
}

function Invoke-AwsCli {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & aws @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return [pscustomobject]@{
        Output = @($output)
        ExitCode = $exitCode
    }
}

function Invoke-AwsPreflight {
    $awsCommand = Get-Command "aws" -ErrorAction SilentlyContinue
    if (!$awsCommand) {
        throw "Las integraciones AWS están habilitadas, pero AWS CLI no está en PATH."
    }

    $region = [Environment]::GetEnvironmentVariable("AWS_REGION", "Process")
    if ([string]::IsNullOrWhiteSpace($region)) {
        $region = [Environment]::GetEnvironmentVariable("AWS_DEFAULT_REGION", "Process")
    }
    if ([string]::IsNullOrWhiteSpace($region)) {
        $region = "us-east-1"
    }

    $identityResult = Invoke-AwsCli -Arguments @(
        "sts", "get-caller-identity",
        "--region", $region,
        "--query", "Account",
        "--output", "text",
        "--no-cli-pager"
    )
    if ($identityResult.ExitCode -ne 0) {
        $detail = ($identityResult.Output | Out-String).Trim()
        if ([string]::IsNullOrWhiteSpace($detail)) {
            $detail = "AWS CLI terminó con código $($identityResult.ExitCode)."
        }
        throw "No se pudieron validar las credenciales AWS. Revisa que sean temporales vigentes y que la región '$region' sea la correcta. Detalle de AWS CLI: $detail"
    }

    if ($DeployKinesis) {
        Write-Host "Desplegando el stack de Kinesis antes de iniciar los servicios locales."
        Write-Warning "Este despliegue crea recursos AWS que pueden generar costos."
        & (Join-Path $repositoryRoot "infra\deploy-kinesis.ps1") -Region $region
    }

    if (Test-Enabled "KINESIS_ENABLED") {
        $streamNames = @(
            [Environment]::GetEnvironmentVariable("KINESIS_PRODUCT_STREAM", "Process"),
            [Environment]::GetEnvironmentVariable("KINESIS_ORDER_STREAM", "Process"),
            [Environment]::GetEnvironmentVariable("KINESIS_DLQ_STREAM", "Process")
        )
        $defaults = @(
            "pc-mania-product-events",
            "pc-mania-order-events",
            "pc-mania-kinesis-dlq"
        )

        for ($index = 0; $index -lt $streamNames.Count; $index++) {
            if ([string]::IsNullOrWhiteSpace($streamNames[$index])) {
                $streamNames[$index] = $defaults[$index]
            }

            $statusResult = Invoke-AwsCli -Arguments @(
                "kinesis", "describe-stream-summary",
                "--stream-name", $streamNames[$index],
                "--region", $region,
                "--query", "StreamDescriptionSummary.StreamStatus",
                "--output", "text",
                "--no-cli-pager"
            )
            if ($statusResult.ExitCode -ne 0) {
                $detail = ($statusResult.Output | Out-String).Trim()
                if ([string]::IsNullOrWhiteSpace($detail)) {
                    $detail = "AWS CLI terminó con código $($statusResult.ExitCode)."
                }
                throw "No se pudo consultar el stream '$($streamNames[$index])' en la región '$region'. Revisa que exista y que las credenciales tengan permiso para describirlo. Detalle de AWS CLI: $detail"
            }

            $status = ($statusResult.Output | Out-String).Trim()
            if ($status -ne "ACTIVE") {
                throw "El stream '$($streamNames[$index])' tiene estado '$status', no ACTIVE. Este launcher no crea recursos AWS."
            }
        }
    }

    if ([Environment]::GetEnvironmentVariable("ORDERS_PERSISTENCE", "Process") -eq "dynamodb") {
        $ordersTable = [Environment]::GetEnvironmentVariable("ORDERS_TABLE", "Process")
        if ([string]::IsNullOrWhiteSpace($ordersTable)) {
            $ordersTable = "pc-mania-orders"
        }
        $tableResult = Invoke-AwsCli -Arguments @(
            "dynamodb", "describe-table",
            "--table-name", $ordersTable,
            "--region", $region,
            "--query", "Table.TableStatus",
            "--output", "text",
            "--no-cli-pager"
        )
        if ($tableResult.ExitCode -ne 0 -or (($tableResult.Output | Out-String).Trim() -ne "ACTIVE")) {
            $detail = ($tableResult.Output | Out-String).Trim()
            throw "La tabla de órdenes '$ordersTable' no está accesible/ACTIVE. Despliega el stack Kinesis antes de activar la persistencia cloud. Detalle de AWS CLI: $detail"
        }
    }

    Write-Host "Credenciales AWS y recursos requeridos verificados (sin desplegar ni modificar recursos)."
}

function Assert-PortsAvailable {
    param([switch]$WarnOnly)

    $ports = @(
        @{ Number = 8081; Name = "mensajería" },
        @{ Number = 8080; Name = "backend" },
        @{ Number = 5174; Name = "admin" },
        @{ Number = 5173; Name = "tienda" }
    )

    foreach ($entry in $ports) {
        if (Test-PortOpen -Port $entry.Number) {
            $message = "El puerto $($entry.Number) ($($entry.Name)) ya está en uso. Para evitar iniciar una segunda instancia, cierra el servicio existente o libera ese puerto."
            if ($WarnOnly) {
                Write-Warning $message
            }
            else {
                throw $message
            }
        }
    }
}

function Start-ServiceWindow {
    param(
        [string]$Title,
        [string]$WorkingDirectory,
        [string]$Command,
        [int]$ServerPort = 0
    )

    $serverPortSetup = ""
    if ($ServerPort -gt 0) {
        $serverPortSetup = "`$env:SERVER_PORT = '$ServerPort'; "
    }

    $childScript = "$serverPortSetup`$Host.UI.RawUI.WindowTitle = '$Title'; Write-Host '$Title'; $Command"
    $encodedCommand = [Convert]::ToBase64String(
        [Text.Encoding]::Unicode.GetBytes($childScript))
    $powershellPath = (Get-Command "powershell.exe" -ErrorAction Stop).Source

    $null = Start-Process -FilePath $powershellPath `
        -WorkingDirectory $WorkingDirectory `
        -ArgumentList @("-NoExit", "-EncodedCommand", $encodedCommand) `
        -PassThru
}

function Wait-ForPort {
    param(
        [int]$Port,
        [string]$ServiceName
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-PortOpen -Port $Port) {
            Write-Host "$ServiceName está escuchando en el puerto $Port."
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "No se confirmó que $ServiceName iniciara en el puerto $Port dentro de $StartupTimeoutSeconds segundos. Revisa su ventana; el launcher no cerró el proceso."
}

$backendDirectory = Join-Path $repositoryRoot "pc-mania-backend"
$messagingDirectory = Join-Path $repositoryRoot "pc-mania-mensajeria"
$adminDirectory = Join-Path $repositoryRoot "pc-mania-admin"
$storeDirectory = Join-Path $repositoryRoot "pc-mania-tienda"

$backendEnv = Read-DotEnv (Join-Path $backendDirectory ".env")
$messagingEnv = Read-DotEnv (Join-Path $messagingDirectory ".env")
$adminEnv = Read-DotEnv (Join-Path $adminDirectory ".env")
$storeEnv = Read-DotEnv (Join-Path $storeDirectory ".env")

Import-DotEnv -Values $backendEnv
Import-DotEnv -Values $messagingEnv

Assert-ConfiguredValues -Component "Backend" -Names @(
    "COGNITO_CLIENT_ISSUER",
    "COGNITO_CLIENT_ID",
    "COGNITO_ADMIN_ISSUER",
    "COGNITO_ADMIN_CLIENT_ID"
) -DotEnvFiles @($backendEnv)

Assert-ConfiguredValues -Component "Panel admin" -Names @(
    "VITE_COGNITO_USER_POOL_ID",
    "VITE_COGNITO_CLIENT_ID",
    "VITE_COGNITO_DOMAIN"
) -DotEnvFiles @($adminEnv)

Assert-ConfiguredValues -Component "Tienda" -Names @(
    "VITE_COGNITO_USER_POOL_ID",
    "VITE_COGNITO_CLIENT_ID",
    "VITE_COGNITO_DOMAIN"
) -DotEnvFiles @($storeEnv)

foreach ($command in @("java", "javac", "node", "npm")) {
    Assert-CommandExists -Name $command
}

foreach ($directory in @($backendDirectory, $messagingDirectory)) {
    if (!(Test-Path -LiteralPath (Join-Path $directory "mvnw.cmd"))) {
        throw "No se encontró mvnw.cmd en '$directory'."
    }
}

foreach ($directory in @($adminDirectory, $storeDirectory)) {
    if (!(Test-Path -LiteralPath (Join-Path $directory "node_modules"))) {
        throw "Faltan dependencias en '$directory'. Ejecuta npm ci dentro de esa carpeta antes de iniciar la demo."
    }
}

Set-DefaultEnvironmentValue -Name "AWS_REGION" -Value "us-east-1"
Set-DefaultEnvironmentValue -Name "AWS_DEFAULT_REGION" -Value $env:AWS_REGION
Set-DefaultEnvironmentValue -Name "MESSAGING_SERVICE_URL" -Value "http://localhost:8081"
Set-DefaultEnvironmentValue -Name "KINESIS_SERVICE_URL" -Value "http://localhost:8081"
Set-DefaultEnvironmentValue -Name "CORS_ALLOWED_ORIGINS" -Value "http://localhost:5173,http://localhost:5174"
Set-DefaultEnvironmentValue -Name "MESSAGING_EVENTS_ENABLED" -Value "false"
Set-DefaultEnvironmentValue -Name "MESSAGING_CONSUMERS_ENABLED" -Value "false"
Set-DefaultEnvironmentValue -Name "KINESIS_EVENTS_ENABLED" -Value "false"
Set-DefaultEnvironmentValue -Name "KINESIS_ENABLED" -Value "false"
Set-DefaultEnvironmentValue -Name "ORDERS_PERSISTENCE" -Value "memory"

if ($ApiBaseUrl -notmatch '^https?://') {
    throw "ApiBaseUrl debe ser una URL HTTP o HTTPS absoluta."
}
if ($env:ORDERS_PERSISTENCE -notin @("memory", "dynamodb")) {
    throw "ORDERS_PERSISTENCE debe ser 'memory' o 'dynamodb'."
}

if ($EnableSnsSqs) {
    $env:MESSAGING_EVENTS_ENABLED = "true"
    $env:MESSAGING_CONSUMERS_ENABLED = "true"
}

if ($EnableKinesis) {
    $env:KINESIS_EVENTS_ENABLED = "true"
    $env:KINESIS_ENABLED = "true"
}

if ($EnableCloudOrders) {
    $env:ORDERS_PERSISTENCE = "dynamodb"
}

if ((Test-Enabled "KINESIS_EVENTS_ENABLED") -and !(Test-Enabled "KINESIS_ENABLED")) {
    throw "KINESIS_EVENTS_ENABLED está activo en backend, pero KINESIS_ENABLED está apagado en mensajería."
}

if ($DeployKinesis -and !(Test-Enabled "KINESIS_ENABLED")) {
    throw "Usa -DeployKinesis junto con -EnableKinesis para desplegar la infraestructura que utilizará la demo."
}

$awsRequired = (Test-Enabled "MESSAGING_EVENTS_ENABLED") -or
    (Test-Enabled "MESSAGING_CONSUMERS_ENABLED") -or
    (Test-Enabled "KINESIS_EVENTS_ENABLED") -or
    (Test-Enabled "KINESIS_ENABLED") -or
    ($env:ORDERS_PERSISTENCE -eq "dynamodb")
if ($awsRequired) {
    Invoke-AwsPreflight
}
else {
    Write-Host "Integraciones AWS apagadas por configuración. Se iniciarán los servicios locales sin publicar/consumir eventos cloud."
}

foreach ($directory in @($backendDirectory, $messagingDirectory, $adminDirectory, $storeDirectory)) {
    if (!(Test-Path -LiteralPath $directory -PathType Container)) {
        throw "No se encontró el módulo requerido '$directory'."
    }
}

Assert-PortsAvailable -WarnOnly:$CheckOnly

if ($CheckOnly) {
    Write-Host "Preflight correcto. No se inició ningún servicio."
    return
}

if ([string]::IsNullOrWhiteSpace($env:ORDER_EVENT_SHARED_SECRET)) {
    $secretBytes = New-Object byte[] 32
    $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($secretBytes)
    }
    finally {
        $randomGenerator.Dispose()
    }
    $env:ORDER_EVENT_SHARED_SECRET = [Convert]::ToBase64String($secretBytes)
    Write-Host "Se generó una clave temporal para autenticar eventos de órdenes entre servicios."
}

Write-Host "Iniciando mensajería; revisa su ventana para ver los logs."
Start-ServiceWindow -Title "PC Mania - Mensajería" `
    -WorkingDirectory $messagingDirectory `
    -ServerPort 8081 `
    -Command "& .\mvnw.cmd spring-boot:run"
Wait-ForPort -Port 8081 -ServiceName "Mensajería"
$null = Invoke-WebRequest -Uri "http://localhost:8081/health" `
    -UseBasicParsing -TimeoutSec 10

Write-Host "Iniciando backend; revisa su ventana para ver los logs."
Start-ServiceWindow -Title "PC Mania - Backend" `
    -WorkingDirectory $backendDirectory `
    -ServerPort 8080 `
    -Command "& .\mvnw.cmd spring-boot:run"
Wait-ForPort -Port 8080 -ServiceName "Backend"
$null = Invoke-WebRequest -Uri "http://localhost:8080/health" `
    -UseBasicParsing -TimeoutSec 10

$env:VITE_API_URL = $ApiBaseUrl.TrimEnd("/")
$env:VITE_MESSAGING_API_URL = "http://localhost:8081"
$env:VITE_REDIRECT_SIGN_IN = "http://localhost:5174/"
$env:VITE_REDIRECT_SIGN_OUT = "http://localhost:5174/"
Write-Host "Iniciando panel admin."
Start-ServiceWindow -Title "PC Mania - Admin" `
    -WorkingDirectory $adminDirectory `
    -Command "npm run dev -- --host 127.0.0.1 --strictPort"
Wait-ForPort -Port 5174 -ServiceName "Panel admin"

$env:VITE_REDIRECT_SIGN_IN = "http://localhost:5173/"
$env:VITE_REDIRECT_SIGN_OUT = "http://localhost:5173/"
Write-Host "Iniciando tienda."
Start-ServiceWindow -Title "PC Mania - Tienda" `
    -WorkingDirectory $storeDirectory `
    -Command "npm run dev -- --host 127.0.0.1 --strictPort"
Wait-ForPort -Port 5173 -ServiceName "Tienda"

Write-Host ""
Write-Host "Servicios iniciados:"
Write-Host "  Admin:  http://localhost:5174"
Write-Host "  Tienda: http://localhost:5173"
Write-Host "  Backend: http://localhost:8080/health"
Write-Host "  Mensajería: http://localhost:8081/health"
Write-Host "Las cuatro ventanas quedan abiertas. Para detener cada servicio, usa Ctrl+C en su ventana."
Write-Host "Este launcher no despliega ni elimina recursos AWS."
