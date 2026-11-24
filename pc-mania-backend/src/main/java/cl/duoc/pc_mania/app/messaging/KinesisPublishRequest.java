package cl.duoc.pc_mania.app.messaging;

public record KinesisPublishRequest(String stream, KinesisEventRequest event) {
}
