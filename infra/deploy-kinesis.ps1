param(
    [string]$Region = "us-east-1",
    [string]$StackName = "pc-mania-kinesis"
)

$ErrorActionPreference = "Stop"

aws cloudformation deploy `
    --template-file "$PSScriptRoot/kinesis-stack.yaml" `
    --stack-name $StackName `
    --region $Region

aws cloudformation describe-stacks `
    --stack-name $StackName `
    --region $Region `
    --query "Stacks[0].Outputs"
