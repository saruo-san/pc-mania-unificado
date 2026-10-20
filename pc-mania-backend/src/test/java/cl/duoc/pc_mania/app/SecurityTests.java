package cl.duoc.pc_mania.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
    void catalogoSinTokenRespondeNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientePuedeConsultarConScopeRead() throws Exception {
        mockMvc.perform(get("/api/productos")
                        .with(jwt().authorities(() -> "SCOPE_pcmania-api/read")))
                .andExpect(status().isOk());
    }

    @Test
    void clienteNoPuedeCrearConScopeRead() throws Exception {
        mockMvc.perform(post("/api/productos")
                        .with(jwt().authorities(() -> "SCOPE_pcmania-api/read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Producto de prueba",
                                  "marca": "Marca de prueba",
                                  "categoria": "Categoria de prueba",
                                  "precio": 1000,
                                  "stock": 1
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPuedeCrearConScopeWrite() throws Exception {
        mockMvc.perform(post("/api/productos")
                        .with(jwt().authorities(() -> "SCOPE_pcmania-api/write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Producto de seguridad",
                                  "marca": "Marca de seguridad",
                                  "categoria": "Categoria de seguridad",
                                  "precio": 1000,
                                  "stock": 1
                                }
                                """))
                .andExpect(status().isCreated());
    }
}