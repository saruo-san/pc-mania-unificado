param(
    [string]$Region = "us-east-1",
    [string]$StackName = "pc-mania-api-gateway",
    [Parameter(Mandatory = $true)]
    [string]$BackendOrigin,
    [Parameter(Mandatory = $true)]
    [string]$CognitoUserPoolArns,
    [string]$AllowedOrigin = "*",
    [string]$ReadScope = "pcmania-api/read",
    [string]$WriteScope = "pcmania-api/write",
    [string]$StageName = "prod",
    [string]$DeploymentVersion = "1",
    [string]$Profile = ""
)

$ErrorActionPreference = "Stop"

if ($BackendOrigin -notmatch "^https://[^/]+$") {
    throw "BackendOrigin debe ser un origen HTTPS público sin ruta ni barra final."
}

$poolArns = $CognitoUserPoolArns -split "," | ForEach-Object { $_.Trim() }
if ($poolArns.Count -eq 0 -or ($poolArns | Where-Object { $_ -notmatch "^arn:[^:]+:cognito-idp:[^:]+:\d{12}:userpool/.+$" })) {
    throw "CognitoUserPoolArns debe contener uno o más ARN de User Pool, separados por comas."
}

$awsArguments = @(
    "cloudformation", "deploy",
    "--template-file", (Join-Path $PSScriptRoot "api-gateway-stack.yaml"),
    "--stack-name", $StackName,
    "--region", $Region,
    "--no-fail-on-empty-changeset",
    "--parameter-overrides",
    "BackendOrigin=$BackendOrigin",
    "CognitoUserPoolArns=$($poolArns -join ',')",
    "AllowedOrigin=$AllowedOrigin",
    "ReadScope=$ReadScope",
    "WriteScope=$WriteScope",
    "StageName=$StageName",
    "DeploymentVersion=$DeploymentVersion"
)

if ($Profile) {
    $awsArguments += @("--profile", $Profile)
}

& aws @awsArguments
if ($LASTEXITCODE -ne 0) {
    throw "Falló el despliegue de la API de PC Mania."
}

$getApiIdArguments = @(
    "cloudformation", "describe-stacks",
    "--stack-name", $StackName,
    "--region", $Region,
    "--query", "Stacks[0].Outputs[?OutputKey=='RestApiId'].OutputValue | [0]",
    "--output", "text"
)
if ($Profile) {
    $getApiIdArguments += @("--profile", $Profile)
}

$restApiId = & aws @getApiIdArguments
if ($LASTEXITCODE -ne 0 -or !$restApiId -or $restApiId -eq "None") {
    throw "No se pudo obtener el identificador de la API desplegada."
}

$createDeploymentArguments = @(
    "apigateway", "create-deployment",
    "--rest-api-id", $restApiId,
    "--stage-name", $StageName,
    "--description", "PC Mania API deployment $DeploymentVersion",
    "--region", $Region
)
if ($Profile) {
    $createDeploymentArguments += @("--profile", $Profile)
}

& aws @createDeploymentArguments
if ($LASTEXITCODE -ne 0) {
    throw "El stack se creó, pero no se pudo publicar el nuevo deployment del API."
}

$describeArguments = @(
    "cloudformation", "describe-stacks",
    "--stack-name", $StackName,
    "--region", $Region,
    "--query", "Stacks[0].Outputs",
    "--output", "table"
)
if ($Profile) {
    $describeArguments += @("--profile", $Profile)
}

& aws @describeArguments
if ($LASTEXITCODE -ne 0) {
    throw "La API fue desplegada, pero no se pudieron consultar sus outputs."
}
