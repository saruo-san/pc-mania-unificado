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
import java.time.Instant;

@Component
public class MessagingEventClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean kinesisEnabled;
    private final RestClient kinesisRestClient;

    public MessagingEventClient(
            ObjectMapper objectMapper,
            @Value("${pc-mania.messaging.url:http://localhost:8081}") String messagingUrl,
            @Value("${pc-mania.messaging.events-enabled:false}") boolean enabled,
            @Value("${pc-mania.kinesis.url:http://localhost:8081}") String kinesisUrl,
            @Value("${pc-mania.kinesis.events-enabled:false}") boolean kinesisEnabled) {
        this.restClient = RestClient.builder()
                .baseUrl(messagingUrl)
                .build();
        this.kinesisRestClient = RestClient.builder()
                .baseUrl(kinesisUrl)
                .build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.kinesisEnabled = kinesisEnabled;
    }

    public void publishProductEvent(String eventType, Producto producto, String authorizationHeader) {
        if (!enabled && !kinesisEnabled) {
            return;
        }
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalStateException("No se puede publicar el evento sin un token Bearer");
        }

        if (enabled) {
            publishSnsEvent(eventType, producto, authorizationHeader);
        }
        if (kinesisEnabled) {
            publishKinesisEvent(eventType, producto, authorizationHeader);
        }
    }

    private void publishSnsEvent(String eventType, Producto producto, String authorizationHeader) {
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

    private void publishKinesisEvent(String eventType, Producto producto, String authorizationHeader) {
        String eventId = UUID.randomUUID().toString();
        KinesisEventRequest event = new KinesisEventRequest(
                    eventId,
                    eventId,
                    eventType,
                    "product-" + producto.getId(),
                    serializar(producto),
                    Instant.now().toString(),
                    Map.of("source", "pc-mania-backend",
                            "productId", String.valueOf(producto.getId())));

        kinesisRestClient.post()
                    .uri("/api/kinesis/events")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new KinesisPublishRequest("product", event))
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
