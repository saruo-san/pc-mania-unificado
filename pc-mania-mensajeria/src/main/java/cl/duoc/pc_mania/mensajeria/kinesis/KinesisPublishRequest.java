package cl.duoc.pc_mania.mensajeria.kinesis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record KinesisPublishRequest(
        @NotBlank String stream,
        @Valid KinesisEvent event) {
}
