package cl.duoc.pc_mania.app.messaging;

import cl.duoc.pc_mania.app.model.Producto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class MessagingEventClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public MessagingEventClient(
            ObjectMapper objectMapper,
            @Value("${pc-mania.messaging.url:http://localhost:8081}") String messagingUrl,
            @Value("${pc-mania.messaging.events-enabled:false}") boolean enabled) {
        this.restClient = RestClient.builder()
                .baseUrl(messagingUrl)
                .build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void publishProductEvent(String eventType, Producto producto, String authorizationHeader) {
        if (!enabled) {
            return;
        }
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalStateException("No se puede publicar el evento sin un token Bearer");
        }

        MessagingEventRequest event = new MessagingEventRequest(
                UUID.randomUUID().toString(),
                eventType,
                serializar(producto),
                Map.of("source", "pc-mania-backend", "productId", String.valueOf(producto.getId())));

        restClient.post()
                .uri("/api/messaging/events")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }

    private String serializar(Producto producto) {
        try {
            return objectMapper.writeValueAsString(producto);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar el producto para mensajería", exception);
        }
    }
}
