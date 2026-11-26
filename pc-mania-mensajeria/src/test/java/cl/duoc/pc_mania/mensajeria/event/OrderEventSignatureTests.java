package cl.duoc.pc_mania.mensajeria.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderEventSignatureTests {

    @Test
    void verificaLaFirmaHmacGeneradaPorElBackend() {
        assertTrue(OrderEventSignature.matches(
                "key",
                "The quick brown fox jumps over the lazy dog",
                "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"));
        assertFalse(OrderEventSignature.matches("key", "altered payload", "invalid"));
    }
}
