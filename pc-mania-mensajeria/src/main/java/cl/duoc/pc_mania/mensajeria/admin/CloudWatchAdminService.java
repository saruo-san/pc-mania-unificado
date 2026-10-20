package cl.duoc.pc_mania.mensajeria.admin;

import cl.duoc.pc_mania.mensajeria.config.MessagingProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.ComparisonOperator;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmRequest;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;

import java.util.List;
import java.util.Map;

@Service
public class CloudWatchAdminService {

    private static final String NAMESPACE = "AWS/SQS";
    private static final String METRIC = "ApproximateNumberOfMessagesVisible";
    private static final String ALARM_PREFIX = "pc-mania-dlq-messages-";

    private final CloudWatchClient cloudWatchClient;
    private final MessagingProperties properties;

    public CloudWatchAdminService(CloudWatchClient cloudWatchClient, MessagingProperties properties) {
        this.cloudWatchClient = cloudWatchClient;
        this.properties = properties;
    }

    public List<String> configureDlqAlarms() {
        return properties.getQueues().values().stream()
                .map(MessagingProperties.Queue::getDlqName)
                .map(this::configureDlqAlarm)
                .toList();
    }

    private String configureDlqAlarm(String dlqName) {
        String alarmName = ALARM_PREFIX + dlqName;
        cloudWatchClient.putMetricAlarm(PutMetricAlarmRequest.builder()
                .alarmName(alarmName)
                .alarmDescription("Alerta cuando existen mensajes pendientes en la DLQ " + dlqName)
                .namespace(NAMESPACE)
                .metricName(METRIC)
                .dimensions(Dimension.builder().name("QueueName").value(dlqName).build())
                .statistic(Statistic.MAXIMUM)
                .period(60)
                .evaluationPeriods(1)
                .threshold(1.0)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .treatMissingData("notBreaching")
                .actionsEnabled(false)
                .build());
        return alarmName;
    }

    public Map<String, String> policy() {
        return Map.of(
                "mainQueueRetention", "4 días",
                "dlqRetention", "14 días",
                "dlqAlarmThreshold", "1 mensaje",
                "cleanup", "Purgar únicamente después de revisar o reprocesar la DLQ");
    }
}
