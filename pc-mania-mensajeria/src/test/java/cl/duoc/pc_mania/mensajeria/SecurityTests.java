package cl.duoc.pc_mania.mensajeria;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "pc-mania.security.order-event-secret=test-order-secret")
@AutoConfigureMockMvc
class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void secretoDeOrdenSeConfiguraEnLaRutaQueLeeLaMensajeria() throws IOException {
        Object secret = new YamlPropertySourceLoader()
                .load("application", new FileSystemResource("src/main/resources/application.yml"))
                .get(0)
                .getProperty("pc-mania.security.order-event-secret");

        assertEquals("${ORDER_EVENT_SHARED_SECRET:}", secret);
    }

    @Test
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void administracionSinTokenRespondeNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/messaging/admin/consumers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administracionRequiereScopeAdministrativo() throws Exception {
        mockMvc.perform(get("/api/messaging/admin/consumers")
                        .with(jwt().authorities(() -> "SCOPE_pcmania-api/read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void eventoRequiereScopeDeEscritura() throws Exception {
        mockMvc.perform(post("/api/messaging/events")
                        .with(jwt().authorities(() -> "SCOPE_pcmania-api/read")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/messaging/events")
                        .with(jwt().authorities(() -> "SCOPE_pcmania-api/order")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordenRequiereFirmaInternaValida() throws Exception {
        mockMvc.perform(post("/api/messaging/events/orders")
                        .with(jwt().authorities(() -> "SCOPE_pcmania-api/order"))
                        .header("X-Order-Event-Signature", "invalid")
                        .contentType("application/json")
                        .content("""
                                {"eventId":"order-1","eventType":"order.created",
                                 "payload":"{\\"id\\":\\"order-1\\"}","metadata":{}}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kinesisSinTokenRespondeNoAutorizado() throws Exception {
        mockMvc.perform(post("/api/kinesis/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordenKinesisRequiereFirmaInternaValida() throws Exception {
        mockMvc.perform(post("/api/kinesis/events/orders")
                        .with(jwt().authorities(() -> "SCOPE_pcmania-api/order"))
                        .header("X-Order-Event-Signature", "invalid")
                        .contentType("application/json")
                        .content("""
                                {"eventId":"order-1","correlationId":"order-1",
                                 "eventType":"order.created","partitionKey":"order-order-1",
                                 "payload":"{\\"id\\":\\"order-1\\"}",
                                 "occurredAt":"2026-01-01T00:00:00Z","metadata":{}}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administracionKinesisRequiereScopeAdministrativo() throws Exception {
        mockMvc.perform(get("/api/kinesis/admin/streams/status")
                        .with(jwt().authorities(() -> "SCOPE_pcmania-api/read")))
                .andExpect(status().isForbidden());
    }
}
