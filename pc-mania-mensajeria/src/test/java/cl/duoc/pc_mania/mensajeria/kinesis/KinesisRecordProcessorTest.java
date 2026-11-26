package cl.duoc.pc_mania.mensajeria.kinesis;

import cl.duoc.pc_mania.mensajeria.config.KinesisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.retrieval.KinesisClientRecord;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KinesisRecordProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KinesisProducerService producerService;
    private KinesisEventStatusService statusService;
    private KinesisEventIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        producerService = mock(KinesisProducerService.class);
        statusService = new KinesisEventStatusService();
        idempotencyService = mock(KinesisEventIdempotencyService.class);
        when(idempotencyService.isProcessed(anyString(), anyString())).thenReturn(false);
        when(idempotencyService.markProcessed(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void sendsMalformedRecordToDlqAndCheckpointsOnlyAfterDlqSucceeds() throws Exception {
        when(producerService.putDlqRaw(eq("not-json"), eq("product-1"), eq("product"),
                eq("1"), anyString())).thenReturn("invalid-record-1");
        RecordProcessorCheckpointer checkpointer = mock(RecordProcessorCheckpointer.class);
        KinesisRecordProcessor processor = processor(1);

        processor.processRecords(input("not-json", "1", checkpointer));

        verify(producerService).putDlqRaw(eq("not-json"), eq("product-1"), eq("product"),
                eq("1"), contains("Unrecognized token"));
        verify(checkpointer).checkpoint("1");
        assertEquals("DLQ", statusService.findByEventId("invalid-record-1").status());
    }

    @Test
    void doesNotCheckpointWhenDlqPublicationFailsAfterConfiguredRetries() {
        doThrow(new IllegalStateException("DLQ unavailable"))
                .when(producerService).putDlqRaw(eq("not-json"), eq("product-1"), eq("product"),
                        eq("2"), anyString());
        RecordProcessorCheckpointer checkpointer = mock(RecordProcessorCheckpointer.class);
        KinesisRecordProcessor processor = processor(3);

        assertThrows(IllegalStateException.class,
                () -> processor.processRecords(input("not-json", "2", checkpointer)));

        verify(producerService, times(3)).putDlqRaw(eq("not-json"), eq("product-1"), eq("product"),
                eq("2"), anyString());
        verifyNoInteractions(checkpointer);
    }

    @Test
    void doesNotPublishTheSameDlqRecordAgainWhenCheckpointMustBeRetried() throws Exception {
        when(producerService.putDlqRaw(eq("not-json"), eq("product-1"), eq("product"),
                eq("5"), anyString())).thenReturn("invalid-record-5");
        RecordProcessorCheckpointer checkpointer = mock(RecordProcessorCheckpointer.class);
        doAnswer(invocation -> {
            throw new IllegalStateException("Checkpoint unavailable");
        }).when(checkpointer).checkpoint("5");
        KinesisRecordProcessor processor = processor(1);

        processor.processRecords(input("not-json", "5", checkpointer));
        processor.processRecords(input("not-json", "5", checkpointer));

        verify(producerService, times(1)).putDlqRaw(eq("not-json"), eq("product-1"), eq("product"),
                eq("5"), anyString());
        verify(checkpointer, times(2)).checkpoint("5");
    }

    @Test
    void marksARepeatedEventAsDuplicateAfterProcessorRestart() throws Exception {
        KinesisEvent event = new KinesisEvent(
                "event-1",
                "correlation-1",
                "product.created",
                "product-1",
                "{\"sku\":1}",
                "2026-01-01T00:00:00Z",
                Map.of());
        String json = objectMapper.writeValueAsString(event);
        RecordProcessorCheckpointer firstCheckpointer = mock(RecordProcessorCheckpointer.class);
        RecordProcessorCheckpointer secondCheckpointer = mock(RecordProcessorCheckpointer.class);
        KinesisRecordProcessor firstProcessor = processor(1);
        KinesisRecordProcessor secondProcessor = processor(1);
        when(idempotencyService.isProcessed("product-realtime", "event-1")).thenReturn(false, true);

        firstProcessor.processRecords(input(json, "3", firstCheckpointer));
        secondProcessor.processRecords(input(json, "4", secondCheckpointer));

        assertEquals("DUPLICATE", statusService.findByEventId("event-1").status());
        verify(producerService, never()).putDlq(any(KinesisEvent.class), anyString());
        verify(idempotencyService).markProcessed("product-realtime", "event-1");
        verify(firstCheckpointer).checkpoint("3");
        verify(secondCheckpointer).checkpoint("4");
    }

    private KinesisRecordProcessor processor(int attempts) {
        KinesisProperties.Retry retry = new KinesisProperties.Retry();
        retry.setMaxAttempts(attempts);
        retry.setInitialDelayMs(0);
        return new KinesisRecordProcessor(
                "product-realtime", "product", objectMapper, producerService, statusService,
                idempotencyService, retry);
    }

    private ProcessRecordsInput input(String json, String sequenceNumber,
                                      RecordProcessorCheckpointer checkpointer) {
        KinesisClientRecord record = KinesisClientRecord.builder()
                .sequenceNumber(sequenceNumber)
                .partitionKey("product-1")
                .data(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)))
                .build();
        return ProcessRecordsInput.builder()
                .records(List.of(record))
                .checkpointer(checkpointer)
                .millisBehindLatest(0L)
                .build();
    }
}
