package cl.duoc.pc_mania.mensajeria.kinesis;

import cl.duoc.pc_mania.mensajeria.config.KinesisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KinesisEventIdempotencyServiceTests {

    private DynamoDbClient dynamoDbClient;
    private KinesisEventIdempotencyService service;

    @BeforeEach
    void setUp() {
        dynamoDbClient = mock(DynamoDbClient.class);
        KinesisProperties properties = new KinesisProperties();
        properties.setIdempotencyTable("pc-mania-kinesis-idempotency");
        properties.setIdempotencyRetentionHours(24);
        service = new KinesisEventIdempotencyService(dynamoDbClient, properties);
    }

    @Test
    void recentEventIsDetectedUsingConsistentRead() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of("expiresAt", AttributeValue.builder()
                        .n(Long.toString(Instant.now().plusSeconds(60).getEpochSecond()))
                        .build()))
                .build());

        assertTrue(service.isProcessed("order-processing", "event-1"));

        ArgumentCaptor<GetItemRequest> requestCaptor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(requestCaptor.capture());
        assertEquals("pc-mania-kinesis-idempotency", requestCaptor.getValue().tableName());
        assertTrue(requestCaptor.getValue().consistentRead());
    }

    @Test
    void expiredEventCanBeProcessedAgain() {
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(Map.of("expiresAt", AttributeValue.builder()
                        .n(Long.toString(Instant.now().minusSeconds(1).getEpochSecond()))
                        .build()))
                .build());

        assertFalse(service.isProcessed("order-processing", "event-1"));
    }

    @Test
    void persistsProcessedEventWithTwentyFourHourTtlAndConditionalWrite() {
        long before = Instant.now().plusSeconds(24 * 60 * 60).getEpochSecond();

        assertTrue(service.markProcessed("order-processing", "event-1"));

        ArgumentCaptor<PutItemRequest> requestCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(requestCaptor.capture());
        PutItemRequest request = requestCaptor.getValue();
        long actualExpiry = Long.parseLong(request.item().get("expiresAt").n());
        assertEquals("pc-mania-kinesis-idempotency", request.tableName());
        assertEquals("attribute_not_exists(#dedupeKey) OR #expiresAt <= :now", request.conditionExpression());
        assertTrue(actualExpiry >= before && actualExpiry <= before + 2);
    }

    @Test
    void conditionalConflictMeansAnotherProcessingAttemptAlreadyRecordedTheEvent() {
        doThrow(ConditionalCheckFailedException.builder().message("duplicate").build())
                .when(dynamoDbClient).putItem(any(PutItemRequest.class));

        assertFalse(service.markProcessed("order-processing", "event-1"));
    }
}
