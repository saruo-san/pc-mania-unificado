param(
    [string]$Region = "us-east-1",
    [string]$StackName = "pc-mania-api-gateway",
    [string]$Profile = ""
)

$ErrorActionPreference = "Stop"

$awsArguments = @(
    "cloudformation", "delete-stack",
    "--stack-name", $StackName,
    "--region", $Region
)
if ($Profile) {
    $awsArguments += @("--profile", $Profile)
}

& aws @awsArguments
if ($LASTEXITCODE -ne 0) {
    throw "No se pudo solicitar la eliminación del stack $StackName."
}

$waitArguments = @(
    "cloudformation", "wait", "stack-delete-complete",
    "--stack-name", $StackName,
    "--region", $Region
)
if ($Profile) {
    $waitArguments += @("--profile", $Profile)
}

& aws @waitArguments
if ($LASTEXITCODE -ne 0) {
    throw "La espera de eliminación del stack $StackName terminó con error."
}

Write-Host "Stack eliminado: $StackName"
