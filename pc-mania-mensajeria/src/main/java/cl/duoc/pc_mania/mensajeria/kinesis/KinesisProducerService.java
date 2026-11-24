package cl.duoc.pc_mania.mensajeria.kinesis;

import cl.duoc.pc_mania.mensajeria.config.KinesisProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KinesisProducerService {

    private static final Logger log = LoggerFactory.getLogger(KinesisProducerService.class);

    private final KinesisClient kinesisClient;
    private final KinesisProperties properties;
    private final ObjectMapper objectMapper;

    public KinesisProducerService(KinesisClient kinesisClient, KinesisProperties properties,
                                  ObjectMapper objectMapper) {
        this.kinesisClient = kinesisClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PutRecordsResponse put(String streamKey, KinesisEvent event) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Kinesis está deshabilitado. Configure KINESIS_ENABLED=true");
        }

        String streamName = properties.stream(streamKey).getName();
        PutRecordsRequest request = PutRecordsRequest.builder()
                .streamName(streamName)
                .records(List.of(PutRecordsRequestEntry.builder()
                        .partitionKey(event.partitionKey())
                        .data(SdkBytes.fromUtf8String(toJson(event)))
                        .build()))
                .build();

        PutRecordsResponse response = kinesisClient.putRecords(request);
        if (response.failedRecordCount() != null && response.failedRecordCount() > 0) {
            List<PutRecordsResultEntry> failures = new ArrayList<>();
            for (PutRecordsResultEntry result : response.records()) {
                if (result.errorCode() != null) {
                    failures.add(result);
                }
            }
            log.error("Falló la publicación Kinesis eventId={} stream={} failedCount={} failures={}",
                    event.eventId(), streamName, response.failedRecordCount(), failures);
            throw new IllegalStateException("Kinesis aceptó registros parciales con fallos");
        }

        log.info("Evento publicado en Kinesis eventId={} correlationId={} stream={} shard={}",
                event.eventId(), event.correlationId(), streamName,
                response.records().isEmpty() ? "unknown" : response.records().get(0).shardId());
        return response;
    }

    public void putDlq(KinesisEvent event, String cause) {
        KinesisEvent dlqEvent = new KinesisEvent(
                event.eventId(),
                event.correlationId(),
                event.eventType(),
                event.partitionKey(),
                event.payload(),
                event.occurredAt(),
                metadataWithCause(event, cause));
        put("dlq", dlqEvent);
    }

    public String putDlqRaw(String rawJson, String originalPartitionKey,
                            String originalStream, String sequenceNumber, String cause) {
        JsonNode event = parseEvent(rawJson);
        String identity = originalStream + "|" + originalPartitionKey + "|" + sequenceNumber + "|" + rawJson;
        String fallbackId = "invalid-" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        String eventId = text(event, "eventId", fallbackId);
        String correlationId = text(event, "correlationId", eventId);
        String eventType = text(event, "eventType", "event.invalid");
        String partitionKey = text(event, "partitionKey",
                originalPartitionKey == null || originalPartitionKey.isBlank()
                        ? eventId
                        : originalPartitionKey);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("dlqCause", cause);
        metadata.put("originalStream", originalStream);
        metadata.put("invalidEvent", "true");
        if (event != null && event.hasNonNull("eventId") && !event.get("eventId").asText().isBlank()) {
            metadata.put("originalEventId", event.get("eventId").asText());
        }

        KinesisEvent dlqEvent = new KinesisEvent(
                eventId,
                correlationId,
                eventType,
                partitionKey,
                rawJson == null || rawJson.isBlank() ? "(empty record)" : rawJson,
                Instant.now().toString(),
                metadata);
        put("dlq", dlqEvent);
        return eventId;
    }

    private java.util.Map<String, String> metadataWithCause(KinesisEvent event, String cause) {
        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        if (event.metadata() != null) {
            metadata.putAll(event.metadata());
        }
        metadata.put("dlqCause", cause);
        metadata.put("originalStream", event.eventType().startsWith("product.") ? "product" : "order");
        return metadata;
    }

    private JsonNode parseEvent(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException ex) {
            log.warn("No se pudo interpretar el evento original para DLQ cause={}", ex.getOriginalMessage());
            return null;
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.hasNonNull(field) || !node.get(field).isTextual()) {
            return fallback;
        }
        String value = node.get(field).asText();
        return value.isBlank() ? fallback : value;
    }

    private String toJson(KinesisEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("No se pudo serializar el evento Kinesis " + event.eventId(), ex);
        }
    }
}
