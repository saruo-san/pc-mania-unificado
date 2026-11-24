package cl.duoc.pc_mania.mensajeria.kinesis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record KinesisEvent(
        @NotBlank String eventId,
        @NotBlank String correlationId,
        @NotBlank String eventType,
        @NotBlank String partitionKey,
        @NotBlank @Size(max = 1048576) String payload,
        @NotBlank String occurredAt,
        Map<String, String> metadata) {
}
