package cl.duoc.pc_mania.mensajeria.kinesis;

import cl.duoc.pc_mania.mensajeria.config.KinesisProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.InitialPositionInStream;
import software.amazon.kinesis.common.InitialPositionInStreamExtended;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KinesisConsumerService {

    private static final Logger log = LoggerFactory.getLogger(KinesisConsumerService.class);

    private final KinesisProperties properties;
    private final KinesisAsyncClient kinesisAsyncClient;
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final CloudWatchAsyncClient cloudWatchAsyncClient;
    private final ObjectMapper objectMapper;
    private final KinesisProducerService producerService;
    private final KinesisEventStatusService statusService;
    private final KinesisEventIdempotencyService idempotencyService;
    private final List<Scheduler> schedulers = new ArrayList<>();
    private final List<Thread> schedulerThreads = new ArrayList<>();
    private final Map<String, Thread> schedulerThreadsByApplication = new ConcurrentHashMap<>();

    public KinesisConsumerService(KinesisProperties properties,
                                  KinesisAsyncClient kinesisAsyncClient,
                                  DynamoDbAsyncClient dynamoDbAsyncClient,
                                  CloudWatchAsyncClient cloudWatchAsyncClient,
                                  ObjectMapper objectMapper,
                                  KinesisProducerService producerService,
                                  KinesisEventStatusService statusService,
                                  KinesisEventIdempotencyService idempotencyService) {
        this.properties = properties;
        this.kinesisAsyncClient = kinesisAsyncClient;
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
        this.cloudWatchAsyncClient = cloudWatchAsyncClient;
        this.objectMapper = objectMapper;
        this.producerService = producerService;
        this.statusService = statusService;
        this.idempotencyService = idempotencyService;
    }

    @PostConstruct
    public void startConsumers() {
        if (!properties.isEnabled()) {
            log.info("Consumidores KCL deshabilitados. Configure KINESIS_ENABLED=true");
            return;
        }

        for (var entry : properties.getConsumerApplications().entrySet()) {
            String applicationName = entry.getKey();
            KinesisProperties.ConsumerApplication application = entry.getValue();
            KinesisProperties.StreamConfig stream = properties.stream(application.getStream());
            start(applicationName, application.getStream(), stream.getName());
        }
    }

    private void start(String applicationName, String streamKey, String streamName) {
        String workerIdentifier = workerIdentifier(applicationName);
        String leaseTable = properties.getLeaseTablePrefix() + "-" + applicationName;
        ShardRecordProcessorFactory processorFactory = () ->
                new KinesisRecordProcessor(applicationName, streamKey, objectMapper,
                        producerService, statusService, idempotencyService, properties.getRetry());

        ConfigsBuilder configs = new ConfigsBuilder(
                streamName,
                applicationName,
                kinesisAsyncClient,
                dynamoDbAsyncClient,
                cloudWatchAsyncClient,
                workerIdentifier,
                processorFactory);
        configs.tableName(leaseTable);
        InitialPositionInStream position = "LATEST".equalsIgnoreCase(properties.getInitialPosition())
                ? InitialPositionInStream.LATEST
                : InitialPositionInStream.TRIM_HORIZON;
        configs.retrievalConfig().initialPositionInStreamExtended(
                InitialPositionInStreamExtended.newInitialPosition(position));
        configs.coordinatorConfig().skipShardSyncAtWorkerInitializationIfLeasesExist(false);
        configs.metricsConfig().metricsBufferTimeMillis(10_000);
        configs.leaseManagementConfig().failoverTimeMillis(10_000);

        Scheduler scheduler = new Scheduler(
                configs.checkpointConfig(),
                configs.coordinatorConfig(),
                configs.leaseManagementConfig(),
                configs.lifecycleConfig(),
                configs.metricsConfig(),
                configs.processorConfig(),
                configs.retrievalConfig());

        schedulers.add(scheduler);
        Thread thread = new Thread(scheduler, "kcl-" + applicationName);
        thread.setDaemon(true);
        schedulerThreadsByApplication.put(applicationName, thread);
        thread.start();
        schedulerThreads.add(thread);
        log.info("Consumidor KCL iniciado application={} stream={} leaseTable={} worker={}",
                applicationName, streamName, leaseTable, workerIdentifier);
    }

    public boolean isRunning(String applicationName) {
        Thread thread = schedulerThreadsByApplication.get(applicationName);
        return thread != null && thread.isAlive();
    }

    private String workerIdentifier(String applicationName) {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            host = "local";
        }
        return applicationName + "-" + host + "-" + UUID.randomUUID();
    }

    @PreDestroy
    public void stopConsumers() {
        for (Scheduler scheduler : schedulers) {
            try {
                scheduler.shutdown();
            } catch (Exception ex) {
                log.warn("Error al detener consumidor KCL cause={}", ex.getMessage());
            }
        }
        for (Thread thread : schedulerThreads) {
            thread.interrupt();
        }
    }
}
