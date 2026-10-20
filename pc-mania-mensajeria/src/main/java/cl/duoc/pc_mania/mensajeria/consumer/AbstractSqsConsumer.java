package cl.duoc.pc_mania.mensajeria.consumer;

import cl.duoc.pc_mania.mensajeria.event.MessagingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractSqsConsumer {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final DomainMessageHandler handler;
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();

    protected AbstractSqsConsumer(SqsClient sqsClient, ObjectMapper objectMapper, DomainMessageHandler handler) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    protected void poll(String queueUrl, String domain) {
        for (Message message : sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                .build()).messages()) {
            int attempt = Integer.parseInt(message.attributesAsStrings()
                    .getOrDefault(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT.toString(), "1"));
            try {
                MessagingEvent event = objectMapper.readValue(message.body(), MessagingEvent.class);
                if (processedEvents.add(event.eventId())) {
                    handler.handle(event);
                } else {
                    log.info("Mensaje duplicado omitido: domain={} eventId={} attempt={}", domain, event.eventId(), attempt);
                }
                sqsClient.deleteMessage(builder -> builder.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            } catch (Exception ex) {
                log.error("Mensaje no entregado: domain={} messageId={} attempt={} cause={}",
                        domain, message.messageId(), attempt, ex.getMessage(), ex);
            }
        }
    }
}
