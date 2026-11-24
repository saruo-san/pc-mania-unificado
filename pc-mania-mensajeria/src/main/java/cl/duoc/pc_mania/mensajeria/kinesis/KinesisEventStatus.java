package cl.duoc.pc_mania.mensajeria.kinesis;

import java.time.Instant;

public record KinesisEventStatus(
        String eventId,
        String applicationName,
        String status,
        String sequenceNumber,
        Instant updatedAt) {
}
