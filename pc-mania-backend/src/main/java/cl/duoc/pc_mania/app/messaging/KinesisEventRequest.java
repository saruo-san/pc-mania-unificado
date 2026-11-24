package cl.duoc.pc_mania.app.messaging;

import java.util.Map;

public record KinesisEventRequest(
        String eventId,
        String correlationId,
        String eventType,
        String partitionKey,
        String payload,
        String occurredAt,
        Map<String, String> metadata) {
}
