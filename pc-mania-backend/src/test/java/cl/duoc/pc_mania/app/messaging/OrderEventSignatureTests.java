package cl.duoc.pc_mania.app.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderEventSignatureTests {

    @Test
    void generaLaFirmaHmacEsperada() {
        assertEquals(
                "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                OrderEventSignature.sign("key", "The quick brown fox jumps over the lazy dog"));
    }

    @Test
    void rechazaUnaClaveSinConfigurar() {
        assertThrows(IllegalStateException.class,
                () -> OrderEventSignature.sign("", "payload"));
    }
}
