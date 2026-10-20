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

    public DocumentsConsumer(SqsClient sqsClient, ObjectMapper objectMapper, MessagingProperties properties) {
        super(sqsClient, objectMapper, event -> process(event));
        queueUrl = sqsClient.getQueueUrl(builder -> builder.queueName(
                properties.getQueues().get("documents").getName())).queueUrl();
    }

    @Scheduled(fixedDelayString = "${pc-mania.messaging.poll-delay-ms:1000}")
    void consume() {
        poll(queueUrl, "documents");
    }

    private static void process(MessagingEvent event) {
        System.out.printf("Documento procesado: eventId=%s type=%s%n", event.eventId(), event.eventType());
    }
}
