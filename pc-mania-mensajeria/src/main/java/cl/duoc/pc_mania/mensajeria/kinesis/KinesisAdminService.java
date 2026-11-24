package cl.duoc.pc_mania.mensajeria.kinesis;

import cl.duoc.pc_mania.mensajeria.config.KinesisProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmRequest;
import software.amazon.awssdk.services.cloudwatch.model.ComparisonOperator;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DeleteStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamSummaryRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamSummaryResponse;
import software.amazon.awssdk.services.kinesis.model.IncreaseStreamRetentionPeriodRequest;
import software.amazon.awssdk.services.kinesis.model.DecreaseStreamRetentionPeriodRequest;
import software.amazon.awssdk.services.kinesis.model.ListStreamsRequest;
import software.amazon.awssdk.services.kinesis.model.StreamModeDetails;
import software.amazon.awssdk.services.kinesis.model.StreamMode;
import software.amazon.awssdk.services.kinesis.model.UpdateShardCountRequest;
import software.amazon.awssdk.services.kinesis.model.ScalingType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KinesisAdminService {

    private final KinesisClient kinesisClient;
    private final CloudWatchClient cloudWatchClient;
    private final DynamoDbClient dynamoDbClient;
    private final KinesisProperties properties;
    private final KinesisConsumerService consumerService;

    public KinesisAdminService(KinesisClient kinesisClient, CloudWatchClient cloudWatchClient,
                               DynamoDbClient dynamoDbClient, KinesisProperties properties,
                               KinesisConsumerService consumerService) {
        this.kinesisClient = kinesisClient;
        this.cloudWatchClient = cloudWatchClient;
        this.dynamoDbClient = dynamoDbClient;
        this.properties = properties;
        this.consumerService = consumerService;
    }

    public List<KinesisStreamStatus> createTopology() {
        List<KinesisStreamStatus> result = new ArrayList<>();
        for (var entry : properties.getStreams().entrySet()) {
            KinesisProperties.StreamConfig stream = entry.getValue();
            try {
                kinesisClient.describeStreamSummary(builder ->
                        builder.streamName(stream.getName()));
            } catch (software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException ex) {
                kinesisClient.createStream(CreateStreamRequest.builder()
                        .streamName(stream.getName())
                        .shardCount(stream.getShardCount())
                        .streamModeDetails(StreamModeDetails.builder()
                                .streamMode(StreamMode.PROVISIONED)
                                .build())
                        .build());
            }

            kinesisClient.increaseStreamRetentionPeriod(IncreaseStreamRetentionPeriodRequest.builder()
                    .streamName(stream.getName())
                    .retentionPeriodHours(stream.getRetentionHours())
                    .build());
            result.add(describe(entry.getKey()));
        }
        return result;
    }

    public List<String> listStreams() {
        return kinesisClient.listStreams(ListStreamsRequest.builder().build()).streamNames();
    }

    public KinesisStreamStatus describe(String streamKey) {
        KinesisProperties.StreamConfig stream = properties.stream(streamKey);
        DescribeStreamSummaryResponse response =
                kinesisClient.describeStreamSummary(DescribeStreamSummaryRequest.builder()
                        .streamName(stream.getName())
                        .build());
        var summary = response.streamDescriptionSummary();
        return new KinesisStreamStatus(
                streamKey,
                stream.getName(),
                summary.streamARN(),
                summary.streamStatusAsString(),
                summary.openShardCount(),
                stream.getShardCount(),
                summary.retentionPeriodHours(),
                iteratorAge(stream.getName()));
    }

    public List<KinesisStreamStatus> describeAll() {
        List<KinesisStreamStatus> result = new ArrayList<>();
        for (String key : properties.getStreams().keySet()) {
            try {
                result.add(describe(key));
            } catch (software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException ex) {
                KinesisProperties.StreamConfig stream = properties.stream(key);
                result.add(new KinesisStreamStatus(key, stream.getName(), null, "NOT_FOUND",
                        0, stream.getShardCount(), stream.getRetentionHours(), null));
            }
        }
        return result;
    }

    public KinesisStreamStatus updateShards(String streamKey, int shardCount) {
        if (shardCount < 1) {
            throw new IllegalArgumentException("El shardCount debe ser mayor que cero");
        }
        KinesisProperties.StreamConfig stream = properties.stream(streamKey);
        kinesisClient.updateShardCount(UpdateShardCountRequest.builder()
                .streamName(stream.getName())
                .targetShardCount(shardCount)
                .scalingType(ScalingType.UNIFORM_SCALING)
                .build());
        stream.setShardCount(shardCount);
        return describe(streamKey);
    }

    public KinesisStreamStatus updateRetention(String streamKey, int retentionHours) {
        if (retentionHours < 24 || retentionHours > 8760) {
            throw new IllegalArgumentException("La retención debe estar entre 24 y 8760 horas");
        }
        KinesisProperties.StreamConfig stream = properties.stream(streamKey);
        if (retentionHours > stream.getRetentionHours()) {
            kinesisClient.increaseStreamRetentionPeriod(IncreaseStreamRetentionPeriodRequest.builder()
                    .streamName(stream.getName())
                    .retentionPeriodHours(retentionHours)
                    .build());
        } else if (retentionHours < stream.getRetentionHours()) {
            kinesisClient.decreaseStreamRetentionPeriod(DecreaseStreamRetentionPeriodRequest.builder()
                    .streamName(stream.getName())
                    .retentionPeriodHours(retentionHours)
                    .build());
        }
        stream.setRetentionHours(retentionHours);
        return describe(streamKey);
    }

    public void deleteStream(String streamKey) {
        KinesisProperties.StreamConfig stream = properties.stream(streamKey);
        kinesisClient.deleteStream(DeleteStreamRequest.builder()
                .streamName(stream.getName())
                .build());
    }

    public List<KinesisConsumerStatus> consumers() {
        List<KinesisConsumerStatus> result = new ArrayList<>();
        for (var entry : properties.getConsumerApplications().entrySet()) {
            String appName = entry.getKey();
            KinesisProperties.ConsumerApplication application = entry.getValue();
            KinesisProperties.StreamConfig stream = properties.stream(application.getStream());
            String leaseTable = properties.getLeaseTablePrefix() + "-" + appName;
            boolean leaseTableExists = leaseTableExists(leaseTable);
            List<KinesisLeaseStatus> leases = leaseTableExists ? leases(leaseTable) : List.of();
            int assignedShardCount = (int) leases.stream()
                    .filter(lease -> lease.leaseOwner() != null && !lease.leaseOwner().isBlank())
                    .count();
            boolean running = consumerService.isRunning(appName);
            result.add(new KinesisConsumerStatus(
                    appName,
                    application.getStream(),
                    stream.getName(),
                    leaseTable,
                    properties.isEnabled(),
                    running,
                    leases.size(),
                    assignedShardCount,
                    leases,
                    !properties.isEnabled()
                            ? "Consumidor deshabilitado por configuración"
                            : !running
                                    ? "El hilo KCL no está activo"
                                    : !leaseTableExists
                                            ? "Consumidor activo; tabla de leases aún no creada"
                                            : "Leases y checkpoints leídos desde DynamoDB"));
        }
        return result;
    }

    public List<String> createIteratorAgeAlarms() {
        List<String> alarmNames = new ArrayList<>();
        for (var entry : properties.getStreams().entrySet()) {
            if ("dlq".equals(entry.getKey())) {
                continue;
            }
            KinesisProperties.StreamConfig stream = entry.getValue();
            String alarmName = "pc-mania-" + entry.getKey() + "-iterator-age";
            cloudWatchClient.putMetricAlarm(PutMetricAlarmRequest.builder()
                    .alarmName(alarmName)
                    .alarmDescription("Retraso de consumo en " + stream.getName())
                    .namespace("AWS/Kinesis")
                    .metricName("GetRecords.IteratorAgeMilliseconds")
                    .dimensions(Dimension.builder()
                            .name("StreamName")
                            .value(stream.getName())
                            .build())
                    .statistic(software.amazon.awssdk.services.cloudwatch.model.Statistic.MAXIMUM)
                    .period(60)
                    .evaluationPeriods(5)
                    .datapointsToAlarm(3)
                    .threshold(60_000.0)
                    .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                    .treatMissingData("notBreaching")
                    .build());
            alarmNames.add(alarmName);
        }
        return alarmNames;
    }

    private boolean leaseTableExists(String tableName) {
        try {
            dynamoDbClient.describeTable(builder -> builder.tableName(tableName));
            return true;
        } catch (ResourceNotFoundException ex) {
            return false;
        }
    }

    private List<KinesisLeaseStatus> leases(String tableName) {
        List<KinesisLeaseStatus> result = new ArrayList<>();
        Map<String, AttributeValue> startKey = Map.of();
        do {
            ScanRequest.Builder request = ScanRequest.builder()
                    .tableName(tableName)
                    .projectionExpression("#leaseKey, #leaseOwner, #checkpoint, #subSequence")
                    .expressionAttributeNames(Map.of(
                            "#leaseKey", "leaseKey",
                            "#leaseOwner", "leaseOwner",
                            "#checkpoint", "checkpoint",
                            "#subSequence", "checkpointSubSequenceNumber"));
            if (!startKey.isEmpty()) {
                request.exclusiveStartKey(startKey);
            }
            var response = dynamoDbClient.scan(request.build());
            for (Map<String, AttributeValue> item : response.items()) {
                result.add(new KinesisLeaseStatus(
                        stringValue(item, "leaseKey"),
                        stringValue(item, "leaseOwner"),
                        stringValue(item, "checkpoint"),
                        numberValue(item, "checkpointSubSequenceNumber")));
            }
            startKey = response.lastEvaluatedKey();
        } while (startKey != null && !startKey.isEmpty());
        return result;
    }

    private String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value == null ? null : value.s();
    }

    private String numberValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value == null ? null : value.n();
    }

    private Double iteratorAge(String streamName) {
        Instant end = Instant.now();
        Instant start = end.minus(10, ChronoUnit.MINUTES);
        var response = cloudWatchClient.getMetricStatistics(GetMetricStatisticsRequest.builder()
                .namespace("AWS/Kinesis")
                .metricName("GetRecords.IteratorAgeMilliseconds")
                .dimensions(Dimension.builder().name("StreamName").value(streamName).build())
                .startTime(start)
                .endTime(end)
                .period(60)
                .statistics(Statistic.MAXIMUM)
                .build());
        if (response.datapoints().isEmpty()) {
            return null;
        }
        return response.datapoints().stream()
                .map(Datapoint::maximum)
                .filter(value -> value != null)
                .max(Double::compareTo)
                .orElse(null);
    }
}
