package cl.duoc.pc_mania.mensajeria.event;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record MessagingEvent(
        @NotBlank String eventId,
        @NotBlank String eventType,
        @NotBlank String payload,
        Map<String, String> metadata) {
}
