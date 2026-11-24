package cl.duoc.pc_mania.mensajeria.kinesis;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KinesisEventStatusService {

    private final Map<String, KinesisEventStatus> statuses = new ConcurrentHashMap<>();

    public void markProcessed(String eventId, String applicationName, String sequenceNumber) {
        statuses.put(statusKey(eventId, applicationName),
                new KinesisEventStatus(eventId, applicationName, "PROCESSED",
                        sequenceNumber, Instant.now()));
    }

    public void markDuplicate(String eventId, String applicationName, String sequenceNumber) {
        statuses.put(statusKey(eventId, applicationName),
                new KinesisEventStatus(eventId, applicationName, "DUPLICATE",
                        sequenceNumber, Instant.now()));
    }

    public void markFailed(String eventId, String applicationName, String sequenceNumber) {
        statuses.put(statusKey(eventId, applicationName),
                new KinesisEventStatus(eventId, applicationName, "DLQ",
                        sequenceNumber, Instant.now()));
    }

    public KinesisEventStatus findByEventId(String eventId) {
        return statuses.entrySet().stream()
                .filter(entry -> entry.getValue().eventId().equals(eventId))
                .map(Map.Entry::getValue)
                .max(Comparator.comparing(KinesisEventStatus::updatedAt))
                .orElse(null);
    }

    public List<KinesisEventStatus> recent() {
        List<KinesisEventStatus> result = new ArrayList<>(statuses.values());
        result.sort(Comparator.comparing(KinesisEventStatus::updatedAt).reversed());
        return result.stream().limit(20).toList();
    }

    private String statusKey(String eventId, String applicationName) {
        return eventId + ":" + applicationName;
    }
}
