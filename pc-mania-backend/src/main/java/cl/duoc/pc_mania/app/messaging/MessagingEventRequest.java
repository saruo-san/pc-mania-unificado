package cl.duoc.pc_mania.app.messaging;

import java.util.Map;

public record MessagingEventRequest(
        String eventId,
        String eventType,
        String payload,
        Map<String, String> metadata) {
}
