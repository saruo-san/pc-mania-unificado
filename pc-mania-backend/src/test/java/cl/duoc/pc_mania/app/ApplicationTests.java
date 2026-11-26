package cl.duoc.pc_mania.app;

import cl.duoc.pc_mania.app.model.Orden;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class ApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void objectMapperSerializaInstantEnLaOrden() {
		Orden orden = new Orden(
				"order-123",
				"CREATED",
				Instant.parse("2026-01-01T00:00:00Z"),
				List.of(),
				BigDecimal.ZERO);

		assertDoesNotThrow(() -> objectMapper.writeValueAsString(orden));
	}

}
