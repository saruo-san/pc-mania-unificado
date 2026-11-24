param(
    [string]$Region = "us-east-1",
    [string]$StackName = "pc-mania-kinesis"
)

$ErrorActionPreference = "Stop"

$streamNames = @(
    "pc-mania-product-events",
    "pc-mania-order-events",
    "pc-mania-kinesis-dlq"
)

foreach ($streamName in $streamNames) {
    try {
        $streamArn = aws kinesis describe-stream-summary `
            --stream-name $streamName `
            --region $Region `
            --query "StreamDescriptionSummary.StreamARN" `
            --output text

        $consumerArns = aws kinesis list-stream-consumers `
            --stream-arn $streamArn `
            --region $Region `
            --query "Consumers[].ConsumerARN" `
            --output text

        foreach ($consumerArn in ($consumerArns -split "\s+" | Where-Object { $_ })) {
            aws kinesis deregister-stream-consumer `
                --stream-arn $streamArn `
                --consumer-arn $consumerArn `
                --region $Region `
                --output json | Out-Null
            Write-Host "Consumer desregistrado: $consumerArn"
        }
    } catch {
        Write-Host "Stream no encontrado o ya eliminado: $streamName"
    }
}

aws cloudformation delete-stack `
    --stack-name $StackName `
    --region $Region

aws cloudformation wait stack-delete-complete `
    --stack-name $StackName `
    --region $Region

Write-Host "Stack eliminado: $StackName"
