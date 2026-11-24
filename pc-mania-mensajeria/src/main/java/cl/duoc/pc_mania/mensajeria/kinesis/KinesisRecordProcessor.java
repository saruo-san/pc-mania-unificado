package cl.duoc.pc_mania.mensajeria.kinesis;

import cl.duoc.pc_mania.mensajeria.config.KinesisProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.kinesis.lifecycle.events.InitializationInput;
import software.amazon.kinesis.lifecycle.events.LeaseLostInput;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.lifecycle.events.ShardEndedInput;
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput;
import software.amazon.kinesis.processor.ShardRecordProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KinesisRecordProcessor implements ShardRecordProcessor {

    private final Logger log = LoggerFactory.getLogger(KinesisRecordProcessor.class);
    private final String applicationName;
    private final String streamKey;
    private final ObjectMapper objectMapper;
    private final KinesisProducerService producerService;
    private final KinesisEventStatusService statusService;
    private final KinesisProperties.Retry retry;
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();
    private final Set<String> processedSequenceNumbers = ConcurrentHashMap.newKeySet();

    public KinesisRecordProcessor(String applicationName, String streamKey, ObjectMapper objectMapper,
                                  KinesisProducerService producerService,
                                  KinesisEventStatusService statusService,
                                  KinesisProperties.Retry retry) {
        this.applicationName = applicationName;
        this.streamKey = streamKey;
        this.objectMapper = objectMapper;
        this.producerService = producerService;
        this.statusService = statusService;
        this.retry = retry;
        if (retry.getMaxAttempts() < 1 || retry.getInitialDelayMs() < 0) {
            throw new IllegalArgumentException("La configuración de reintentos Kinesis no es válida");
        }
    }

    @Override
    public void initialize(InitializationInput input) {
        log.info("Kinesis consumer initialized application={} shard={} correlationSource=shard",
                applicationName, input.shardId());
    }

    @Override
    public void processRecords(ProcessRecordsInput input) {
        String lastProcessedSequenceNumber = null;
        for (var record : input.records()) {
            String sequenceNumber = record.sequenceNumber();
            if (processedSequenceNumbers.contains(sequenceNumber)) {
                log.info("Kinesis registro ya completado en este processor application={} sequenceNumber={}",
                        applicationName, sequenceNumber);
                lastProcessedSequenceNumber = sequenceNumber;
                continue;
            }
            String json = StandardCharsets.UTF_8.decode(record.data().duplicate()).toString();
            KinesisEvent event = null;
            boolean eventIsValid = false;
            try {
                event = objectMapper.readValue(json, KinesisEvent.class);
                validate(event);
                eventIsValid = true;

                if (processedEvents.contains(event.eventId())) {
                    statusService.markDuplicate(event.eventId(), applicationName, sequenceNumber);
                    log.info("Kinesis evento duplicado omitido application={} eventId={} correlationId={} sequenceNumber={}",
                            applicationName, event.eventId(), event.correlationId(), sequenceNumber);
                } else {
                    if ("true".equalsIgnoreCase(metadata(event, "forceFailure"))) {
                        throw new IllegalArgumentException("Fallo irrecuperable solicitado para demostración");
                    }
                    if ("true".equalsIgnoreCase(metadata(event, "transientFailure"))) {
                        processWithTransientRetries(event);
                    }
                    statusService.markProcessed(event.eventId(), applicationName, sequenceNumber);
                    processedEvents.add(event.eventId());
                    log.info("Kinesis evento procesado application={} eventId={} correlationId={} eventType={} sequenceNumber={} shardLagMs={}",
                            applicationName, event.eventId(), event.correlationId(), event.eventType(),
                            sequenceNumber, input.millisBehindLatest());
                }
            } catch (Exception ex) {
                try {
                    String eventId = sendToDlqWithRetries(event, eventIsValid, json,
                            record.partitionKey(), sequenceNumber, ex);
                    statusService.markFailed(eventId, applicationName, sequenceNumber);
                    processedEvents.add(eventId);
                    log.error("Evento Kinesis enviado al DLQ application={} eventId={} correlationId={} sequenceNumber={} cause={}",
                            applicationName, event == null ? eventId : event.eventId(),
                            event == null ? "unknown" : event.correlationId(),
                            sequenceNumber, ex.getMessage());
                } catch (Exception dlqEx) {
                    if (dlqEx instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    log.error("No se pudo guardar evento en DLQ; no se avanzará el checkpoint application={} eventId={} sequenceNumber={} cause={} dlqCause={}",
                            applicationName, event == null ? "unknown" : event.eventId(),
                            sequenceNumber, ex.getMessage(), dlqEx.getMessage());
                    throw new IllegalStateException(
                            "No se pudo guardar el evento de Kinesis en DLQ; el registro será reprocesado", dlqEx);
                }
            }
            processedSequenceNumbers.add(sequenceNumber);
            lastProcessedSequenceNumber = sequenceNumber;
        }

        if (lastProcessedSequenceNumber != null) {
            try {
                input.checkpointer().checkpoint(lastProcessedSequenceNumber);
            } catch (Exception ex) {
                log.warn("No se pudo guardar el checkpoint de Kinesis application={} sequenceNumber={} cause={}",
                        applicationName, lastProcessedSequenceNumber, ex.getMessage());
            }
        }
    }

    private void processWithTransientRetries(KinesisEvent event) throws InterruptedException {
        long delay = retry.getInitialDelayMs();
        for (int attempt = 1; attempt <= retry.getMaxAttempts(); attempt++) {
            try {
                log.warn("Simulación transitoria application={} eventId={} correlationId={} attempt={} delayMs={}",
                        applicationName, event.eventId(), event.correlationId(), attempt, delay);
                throw new IllegalStateException("Fallo transitorio solicitado para demostración");
            } catch (IllegalStateException ex) {
                if (attempt == retry.getMaxAttempts()) {
                    throw ex;
                }
                Thread.sleep(delay);
                delay = Math.min(delay * 2, 30_000);
            }
        }
    }

    private void validate(KinesisEvent event) {
        if (event == null
                || event.eventId() == null || event.eventId().isBlank()
                || event.correlationId() == null || event.correlationId().isBlank()
                || event.eventType() == null || event.eventType().isBlank()
                || event.partitionKey() == null || event.partitionKey().isBlank()
                || event.payload() == null || event.payload().isBlank()
                || event.occurredAt() == null || event.occurredAt().isBlank()) {
            throw new IllegalArgumentException("Evento Kinesis incompleto");
        }
        try {
            JsonNode payload = objectMapper.readTree(event.payload());
            if (payload == null || payload.isNull()) {
                throw new IllegalArgumentException("El payload Kinesis no contiene un valor JSON");
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("El payload Kinesis no contiene JSON válido", ex);
        }
        try {
            Instant.parse(event.occurredAt());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("El occurredAt Kinesis debe ser una fecha ISO-8601", ex);
        }
    }

    private String sendToDlqWithRetries(KinesisEvent event, boolean eventIsValid,
                                        String rawJson, String partitionKey,
                                        String sequenceNumber, Exception cause) throws InterruptedException {
        long delay = retry.getInitialDelayMs();
        for (int attempt = 1; attempt <= retry.getMaxAttempts(); attempt++) {
            try {
                if (eventIsValid) {
                    producerService.putDlq(event, cause.getMessage());
                    return event.eventId();
                }
                return producerService.putDlqRaw(rawJson, partitionKey, streamKey,
                        sequenceNumber, cause.getMessage());
            } catch (RuntimeException ex) {
                log.warn("Reintento de publicación DLQ application={} eventId={} sequenceNumber={} attempt={} delayMs={} cause={}",
                        applicationName, event == null ? "unknown" : event.eventId(),
                        sequenceNumber, attempt, delay, ex.getMessage());
                if (attempt == retry.getMaxAttempts()) {
                    throw ex;
                }
                Thread.sleep(delay);
                delay = Math.min(delay * 2, 30_000);
            }
        }
        throw new IllegalStateException("Se agotaron los reintentos de publicación DLQ");
    }

    private String metadata(KinesisEvent event, String key) {
        if (event.metadata() == null) {
            return null;
        }
        return event.metadata().get(key);
    }

    @Override
    public void leaseLost(LeaseLostInput input) {
        log.warn("Lease perdido application={}", applicationName);
    }

    @Override
    public void shardEnded(ShardEndedInput input) {
        try {
            input.checkpointer().checkpoint();
            log.info("Shard finalizado y checkpoint guardado application={}", applicationName);
        } catch (Exception ex) {
            log.error("No se pudo guardar checkpoint al finalizar shard application={} cause={}",
                    applicationName, ex.getMessage());
        }
    }

    @Override
    public void shutdownRequested(ShutdownRequestedInput input) {
        try {
            input.checkpointer().checkpoint();
            log.info("Checkpoint final guardado durante shutdown application={}", applicationName);
        } catch (Exception ex) {
            log.warn("No se pudo guardar checkpoint durante shutdown application={} cause={}",
                    applicationName, ex.getMessage());
        }
    }
}
