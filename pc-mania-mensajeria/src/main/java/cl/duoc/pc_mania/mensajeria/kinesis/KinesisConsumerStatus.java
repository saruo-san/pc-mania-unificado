package cl.duoc.pc_mania.mensajeria.kinesis;

public record KinesisConsumerStatus(
        String applicationName,
        String streamKey,
        String streamName,
        String leaseTable,
        boolean enabled,
        boolean running,
        int leaseCount,
        int assignedShardCount,
        java.util.List<KinesisLeaseStatus> leases,
        String message) {
}
