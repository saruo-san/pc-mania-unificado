package cl.duoc.pc_mania.mensajeria.event;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

public final class OrderEventSignature {

    private OrderEventSignature() {
    }

    public static boolean matches(String secret, String payload, String signature) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
            return java.security.MessageDigest.isEqual(
                    expected, signature.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo verificar la firma del evento de orden", exception);
        }
    }
}
