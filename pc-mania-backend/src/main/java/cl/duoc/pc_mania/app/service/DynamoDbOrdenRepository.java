package cl.duoc.pc_mania.app.service;

import cl.duoc.pc_mania.app.model.Orden;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

@Repository
@ConditionalOnProperty(name = "pc-mania.orders.persistence", havingValue = "dynamodb")
public class DynamoDbOrdenRepository implements OrdenRepository {

    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    private final String tableName;

    public DynamoDbOrdenRepository(
            DynamoDbClient dynamoDbClient,
            ObjectMapper objectMapper,
            @Value("${pc-mania.orders.table}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
    }

    @Override
    public void guardar(Orden orden) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(orden);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar la orden " + orden.id(), exception);
        }

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(Map.of(
                        "orderId", AttributeValue.builder().s(orden.id()).build(),
                        "createdAt", AttributeValue.builder().s(orden.creadaEn().toString()).build(),
                        "status", AttributeValue.builder().s(orden.estado()).build(),
                        "payload", AttributeValue.builder().s(payload).build()))
                .conditionExpression("attribute_not_exists(orderId)")
                .build());
    }

    @Override
    public void eliminar(String orderId) {
        dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("orderId", AttributeValue.builder().s(orderId).build()))
                .build());
    }
}
