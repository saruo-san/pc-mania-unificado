package cl.duoc.pc_mania.app.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CrearOrdenRequest(
        @NotEmpty @Size(max = 50) List<@Valid Linea> lineas) {

    public record Linea(
            @NotNull @Positive Long productoId,
            @NotNull @Positive @Max(100) Integer cantidad) {
    }
}
