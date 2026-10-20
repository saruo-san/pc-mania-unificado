package cl.duoc.pc_mania.mensajeria.admin;

public record QueueStatus(
        long visibleMessages,
        long inFlightMessages,
        long delayedMessages,
        long oldestMessageAgeSeconds,
        String redrivePolicy) {
}
