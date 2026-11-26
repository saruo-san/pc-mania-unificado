package cl.duoc.pc_mania.app.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Orden(
        String id,
        String estado,
        Instant creadaEn,
        List<LineaOrden> lineas,
        BigDecimal total) {

    public Orden {
        lineas = List.copyOf(lineas);
    }

    public record LineaOrden(
            Long productoId,
            String nombreProducto,
            int cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {
    }
}
