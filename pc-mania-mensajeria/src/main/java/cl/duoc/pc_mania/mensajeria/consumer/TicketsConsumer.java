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
public class TicketsConsumer extends AbstractSqsConsumer {

    private final String queueUrl;
    private final ConsumerControlService controlService;

    public TicketsConsumer(SqsClient sqsClient, ObjectMapper objectMapper, MessagingProperties properties,
                           ConsumerControlService controlService) {
        super(sqsClient, objectMapper, event -> process(event));
        queueUrl = sqsClient.getQueueUrl(builder -> builder.queueName(
                properties.getQueues().get("tickets").getName())).queueUrl();
        this.controlService = controlService;
    }

    @Scheduled(fixedDelayString = "${pc-mania.messaging.poll-delay-ms:1000}")
    void consume() {
        if (!controlService.isRunning("tickets")) return;
        poll(queueUrl, "tickets");
    }

    private static void process(MessagingEvent event) {
        System.out.printf("Ticket procesado: eventId=%s type=%s%n", event.eventId(), event.eventType());
    }
}
