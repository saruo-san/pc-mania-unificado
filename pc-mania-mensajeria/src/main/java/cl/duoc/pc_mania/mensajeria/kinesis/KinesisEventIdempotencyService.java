package cl.duoc.pc_mania.mensajeria.kinesis;

import cl.duoc.pc_mania.mensajeria.config.KinesisProperties;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class KinesisEventIdempotencyService {

    private final DynamoDbClient dynamoDbClient;
    private final KinesisProperties properties;

    public KinesisEventIdempotencyService(DynamoDbClient dynamoDbClient, KinesisProperties properties) {
        this.dynamoDbClient = dynamoDbClient;
        this.properties = properties;
    }

    public boolean isProcessed(String applicationName, String eventId) {
        String dedupeKey = dedupeKey(applicationName, eventId);
        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(properties.getIdempotencyTable())
                .key(Map.of("dedupeKey", AttributeValue.builder().s(dedupeKey).build()))
                .consistentRead(true)
                .build()).item();

        if (item == null || item.isEmpty()) {
            return false;
        }

        AttributeValue expiresAt = item.get("expiresAt");
        return expiresAt == null || Long.parseLong(expiresAt.n()) > Instant.now().getEpochSecond();
    }

    public boolean markProcessed(String applicationName, String eventId) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = Instant.now()
                .plusSeconds(properties.getIdempotencyRetentionHours() * 3600L)
                .getEpochSecond();
        String dedupeKey = dedupeKey(applicationName, eventId);

        try {
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(properties.getIdempotencyTable())
                    .item(Map.of(
                            "dedupeKey", AttributeValue.builder().s(dedupeKey).build(),
                            "applicationName", AttributeValue.builder().s(applicationName).build(),
                            "eventId", AttributeValue.builder().s(eventId).build(),
                            "expiresAt", AttributeValue.builder().n(Long.toString(expiresAt)).build()))
                    .conditionExpression("attribute_not_exists(#dedupeKey) OR #expiresAt <= :now")
                    .expressionAttributeNames(Map.of(
                            "#dedupeKey", "dedupeKey",
                            "#expiresAt", "expiresAt"))
                    .expressionAttributeValues(Map.of(
                            ":now", AttributeValue.builder().n(Long.toString(now)).build()))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }

    private String dedupeKey(String applicationName, String eventId) {
        try {
            byte[] input = (applicationName + "\u0000" + eventId).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return applicationName + "#" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible para construir la clave de idempotencia",
                    exception);
        }
    }
}
