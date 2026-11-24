package cl.duoc.pc_mania.mensajeria.kinesis;

public record KinesisStreamStatus(
        String key,
        String name,
        String streamArn,
        String status,
        int openShardCount,
        int configuredShardCount,
        int retentionHours,
        Double iteratorAgeMilliseconds) {
}
