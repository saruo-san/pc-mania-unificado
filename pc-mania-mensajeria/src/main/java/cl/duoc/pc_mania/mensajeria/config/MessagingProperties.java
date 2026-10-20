package cl.duoc.pc_mania.mensajeria.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "pc-mania.messaging")
public class MessagingProperties {

    private String region;
    private Topic topic = new Topic();
    private Map<String, Queue> queues = new LinkedHashMap<>();

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public Map<String, Queue> getQueues() {
        return queues;
    }

    public void setQueues(Map<String, Queue> queues) {
        this.queues = queues;
    }

    public static class Topic {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Queue {
        private String name;
        private String dlqName;
        private int retentionSeconds;
        private int dlqRetentionSeconds;
        private int visibilityTimeoutSeconds;
        private int maxReceiveCount;
        private String filterPolicy;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDlqName() {
            return dlqName;
        }

        public void setDlqName(String dlqName) {
            this.dlqName = dlqName;
        }

        public int getRetentionSeconds() {
            return retentionSeconds;
        }

        public void setRetentionSeconds(int retentionSeconds) {
            this.retentionSeconds = retentionSeconds;
        }

        public int getDlqRetentionSeconds() {
            return dlqRetentionSeconds;
        }

        public void setDlqRetentionSeconds(int dlqRetentionSeconds) {
            this.dlqRetentionSeconds = dlqRetentionSeconds;
        }

        public int getVisibilityTimeoutSeconds() {
            return visibilityTimeoutSeconds;
        }

        public void setVisibilityTimeoutSeconds(int visibilityTimeoutSeconds) {
            this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
        }

        public int getMaxReceiveCount() {
            return maxReceiveCount;
        }

        public void setMaxReceiveCount(int maxReceiveCount) {
            this.maxReceiveCount = maxReceiveCount;
        }

        public String getFilterPolicy() {
            return filterPolicy;
        }

        public void setFilterPolicy(String filterPolicy) {
            this.filterPolicy = filterPolicy;
        }
    }
}
