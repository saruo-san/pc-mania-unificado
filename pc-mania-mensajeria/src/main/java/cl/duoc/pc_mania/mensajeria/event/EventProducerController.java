package cl.duoc.pc_mania.mensajeria.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/messaging/events")
public class EventProducerController {

    private final EventProducerService producerService;
    private final ObjectMapper objectMapper;
    private final String orderEventSecret;

    public EventProducerController(
            EventProducerService producerService,
            ObjectMapper objectMapper,
            @Value("${pc-mania.security.order-event-secret:}") String orderEventSecret) {
        this.producerService = producerService;
        this.objectMapper = objectMapper;
        this.orderEventSecret = orderEventSecret;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> publish(@Valid @RequestBody MessagingEvent event) {
        return Map.of("messageId", producerService.publish(event), "eventId", event.eventId());
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> publishOrder(
            @Valid @RequestBody MessagingEvent event,
            @RequestHeader("X-Order-Event-Signature") String signature) {
        verificarEventoOrden(event.eventId(), event.eventType(), event.payload(), signature);
        return Map.of("messageId", producerService.publish(event), "eventId", event.eventId());
    }

    private void verificarEventoOrden(String eventId, String eventType, String payload, String signature) {
        if (orderEventSecret == null || orderEventSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "ORDER_EVENT_SHARED_SECRET no está configurado");
        }
        if (!OrderEventSignature.matches(orderEventSecret, payload, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma de evento de orden inválida");
        }
        try {
            String payloadOrderId = objectMapper.readTree(payload).path("id").asText();
            if (!"order.created".equals(eventType) || !eventId.equals(payloadOrderId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento de orden inconsistente");
            }
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload de orden inválido", exception);
        }
    }
}
