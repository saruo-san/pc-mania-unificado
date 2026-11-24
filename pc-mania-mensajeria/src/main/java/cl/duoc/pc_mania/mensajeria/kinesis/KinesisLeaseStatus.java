package cl.duoc.pc_mania.mensajeria.kinesis;

public record KinesisLeaseStatus(
        String leaseKey,
        String leaseOwner,
        String checkpoint,
        String checkpointSubSequenceNumber) {
}
