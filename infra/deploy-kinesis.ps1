param(
    [string]$Region = "us-east-1",
    [string]$StackName = "pc-mania-kinesis"
)

$ErrorActionPreference = "Stop"

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

$templatePath = Join-Path $PSScriptRoot "kinesis-stack.yaml"
Write-Host "Aplicando stack '$StackName' en la región '$Region'."
$deployResult = Invoke-AwsCli -Arguments @(
    "cloudformation", "deploy",
    "--template-file", $templatePath,
    "--stack-name", $StackName,
    "--region", $Region,
    "--no-fail-on-empty-changeset",
    "--no-cli-pager"
)
if ($deployResult.ExitCode -ne 0) {
    $detail = ($deployResult.Output | Out-String).Trim()
    throw "Falló el despliegue del stack '$StackName'. Detalle de AWS CLI: $detail"
}
if ($deployResult.Output.Count -gt 0) {
    Write-Output $deployResult.Output
}

$describeResult = Invoke-AwsCli -Arguments @(
    "cloudformation", "describe-stacks",
    "--stack-name", $StackName,
    "--region", $Region,
    "--query", "Stacks[0].Outputs",
    "--no-cli-pager"
)
if ($describeResult.ExitCode -ne 0) {
    $detail = ($describeResult.Output | Out-String).Trim()
    throw "El stack se aplicó, pero no se pudieron consultar sus outputs. Detalle de AWS CLI: $detail"
}
Write-Output $describeResult.Output
