package cl.duoc.pc_mania.mensajeria.kinesis;

import cl.duoc.pc_mania.mensajeria.event.OrderEventSignature;
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
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;

@RestController
@RequestMapping("/api/kinesis/events")
public class KinesisProducerController {

    private final KinesisProducerService producerService;
    private final ObjectMapper objectMapper;
    private final String orderEventSecret;

    public KinesisProducerController(
            KinesisProducerService producerService,
            ObjectMapper objectMapper,
            @Value("${pc-mania.security.order-event-secret:}") String orderEventSecret) {
        this.producerService = producerService;
        this.objectMapper = objectMapper;
        this.orderEventSecret = orderEventSecret;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public KinesisPublishResponse publish(@Valid @RequestBody KinesisPublishRequest request) {
        PutRecordsResponse response = producerService.put(request.stream(), request.event());
        String sequenceNumber = response.records().isEmpty()
                ? null
                : response.records().get(0).sequenceNumber();
        return new KinesisPublishResponse(request.event().eventId(), sequenceNumber);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public KinesisPublishResponse publishOrder(
            @Valid @RequestBody KinesisEvent event,
            @RequestHeader("X-Order-Event-Signature") String signature) {
        verificarEventoOrden(event, signature);
        PutRecordsResponse response = producerService.put("order", event);
        String sequenceNumber = response.records().isEmpty()
                ? null
                : response.records().get(0).sequenceNumber();
        return new KinesisPublishResponse(event.eventId(), sequenceNumber);
    }

    private void verificarEventoOrden(KinesisEvent event, String signature) {
        if (orderEventSecret == null || orderEventSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "ORDER_EVENT_SHARED_SECRET no está configurado");
        }
        if (!OrderEventSignature.matches(orderEventSecret, event.payload(), signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma de evento de orden inválida");
        }
        try {
            String payloadOrderId = objectMapper.readTree(event.payload()).path("id").asText();
            if (!"order.created".equals(event.eventType())
                    || !event.eventId().equals(payloadOrderId)
                    || !event.correlationId().equals(event.eventId())
                    || !event.partitionKey().equals("order-" + event.eventId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento de orden inconsistente");
            }
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload de orden inválido", exception);
        }
    }
}
