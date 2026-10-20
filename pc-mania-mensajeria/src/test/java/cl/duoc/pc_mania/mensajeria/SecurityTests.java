package cl.duoc.pc_mania.mensajeria;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

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
    }
}
