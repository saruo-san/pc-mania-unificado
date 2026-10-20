package cl.duoc.pc_mania.mensajeria.consumer;

import cl.duoc.pc_mania.mensajeria.config.MessagingProperties;
import cl.duoc.pc_mania.mensajeria.event.MessagingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

@Component
@ConditionalOnProperty(name = "pc-mania.messaging.consumers-enabled", havingValue = "true")
public class DocumentsConsumer extends AbstractSqsConsumer {

    private final String queueUrl;
    private final ConsumerControlService controlService;

    public DocumentsConsumer(SqsClient sqsClient, ObjectMapper objectMapper, MessagingProperties properties,
                             ConsumerControlService controlService) {
        super(sqsClient, objectMapper, event -> process(event));
        queueUrl = sqsClient.getQueueUrl(builder -> builder.queueName(
                properties.getQueues().get("documents").getName())).queueUrl();
        this.controlService = controlService;
    }

    @Scheduled(fixedDelayString = "${pc-mania.messaging.poll-delay-ms:1000}")
    void consume() {
        if (!controlService.isRunning("documents")) return;
        poll(queueUrl, "documents");
    }

    private static void process(MessagingEvent event) {
        System.out.printf("Documento procesado: eventId=%s type=%s%n", event.eventId(), event.eventType());
    }
}
