package cl.duoc.pc_mania.mensajeria.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "pc-mania.kinesis")
public class KinesisProperties {

    private String region = "us-east-1";
    private boolean enabled;
    private String initialPosition = "TRIM_HORIZON";
    private String leaseTablePrefix = "pc-mania-kinesis";
    private String idempotencyTable = "pc-mania-kinesis-idempotency";
    private int idempotencyRetentionHours = 24;
    private Retry retry = new Retry();
    private Map<String, StreamConfig> streams = new LinkedHashMap<>();
    private Map<String, ConsumerApplication> consumerApplications = new LinkedHashMap<>();

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getInitialPosition() { return initialPosition; }
    public void setInitialPosition(String initialPosition) { this.initialPosition = initialPosition; }
    public String getLeaseTablePrefix() { return leaseTablePrefix; }
    public void setLeaseTablePrefix(String leaseTablePrefix) { this.leaseTablePrefix = leaseTablePrefix; }
    public String getIdempotencyTable() { return idempotencyTable; }
    public void setIdempotencyTable(String idempotencyTable) { this.idempotencyTable = idempotencyTable; }
    public int getIdempotencyRetentionHours() { return idempotencyRetentionHours; }
    public void setIdempotencyRetentionHours(int idempotencyRetentionHours) {
        this.idempotencyRetentionHours = idempotencyRetentionHours;
    }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }
    public Map<String, StreamConfig> getStreams() { return streams; }
    public void setStreams(Map<String, StreamConfig> streams) { this.streams = streams; }
    public Map<String, ConsumerApplication> getConsumerApplications() { return consumerApplications; }
    public void setConsumerApplications(Map<String, ConsumerApplication> consumerApplications) {
        this.consumerApplications = consumerApplications;
    }

    public StreamConfig stream(String key) {
        StreamConfig stream = streams.get(key);
        if (stream == null) {
            throw new IllegalArgumentException("No existe la configuración del stream: " + key);
        }
        return stream;
    }

    public static class StreamConfig {
        private String name;
        private int shardCount = 3;
        private int retentionHours = 24;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getShardCount() { return shardCount; }
        public void setShardCount(int shardCount) { this.shardCount = shardCount; }
        public int getRetentionHours() { return retentionHours; }
        public void setRetentionHours(int retentionHours) { this.retentionHours = retentionHours; }
    }

    public static class Retry {
        private int maxAttempts = 5;
        private int initialDelayMs = 500;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public int getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(int initialDelayMs) { this.initialDelayMs = initialDelayMs; }
    }

    public static class ConsumerApplication {
        private String stream;

        public String getStream() { return stream; }
        public void setStream(String stream) { this.stream = stream; }
    }
}
