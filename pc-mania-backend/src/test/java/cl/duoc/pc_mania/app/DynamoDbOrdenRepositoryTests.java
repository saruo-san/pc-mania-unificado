package cl.duoc.pc_mania.app;

import cl.duoc.pc_mania.app.model.Orden;
import cl.duoc.pc_mania.app.service.DynamoDbOrdenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class DynamoDbOrdenRepositoryTests {

    @Test
    void persisteLaOrdenConClaveUnicaYPayloadJson() throws Exception {
        DynamoDbClient client = mock(DynamoDbClient.class);
        DynamoDbOrdenRepository repository = new DynamoDbOrdenRepository(
                client, new ObjectMapper().findAndRegisterModules(), "pc-mania-orders");
        Orden order = new Orden(
                "order-123",
                "CREATED",
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of(new Orden.LineaOrden(
                        1L, "GPU demo", 1, new BigDecimal("1000"), new BigDecimal("1000"))),
                new BigDecimal("1000"));

        repository.guardar(order);

        ArgumentCaptor<PutItemRequest> requestCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(client).putItem(requestCaptor.capture());
        PutItemRequest request = requestCaptor.getValue();
        assertEquals("pc-mania-orders", request.tableName());
        assertEquals("attribute_not_exists(orderId)", request.conditionExpression());
        assertEquals("order-123", request.item().get("orderId").s());
        assertEquals("CREATED", request.item().get("status").s());
        assertEquals("order-123", new ObjectMapper()
                .readTree(request.item().get("payload").s())
                .get("id").asText());
    }
}
