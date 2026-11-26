package cl.duoc.pc_mania.app.messaging;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

final class OrderEventSignature {

    private OrderEventSignature() {
    }

    static String sign(String secret, String payload) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("ORDER_EVENT_SHARED_SECRET no está configurado");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo firmar el evento de orden", exception);
        }
    }
}
