package cl.duoc.pc_mania.mensajeria.event;

import cl.duoc.pc_mania.mensajeria.config.MessagingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

@Service
public class EventProducerService {

    private final SnsClient snsClient;
    private final MessagingProperties properties;
    private final ObjectMapper objectMapper;

    public EventProducerService(SnsClient snsClient, MessagingProperties properties, ObjectMapper objectMapper) {
        this.snsClient = snsClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String publish(MessagingEvent event) {
        try {
            String topicArn = snsClient.createTopic(builder -> builder.name(properties.getTopic().getName())).topicArn();
            String messageId = snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(objectMapper.writeValueAsString(event))
                    .messageAttributes(Map.of(
                            "eventType", MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(event.eventType())
                                    .build()))
                    .build()).messageId();
            return messageId;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("No se pudo serializar el evento " + event.eventId(), ex);
        }
    }
}
