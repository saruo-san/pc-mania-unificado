package cl.duoc.pc_mania.mensajeria.admin;

import cl.duoc.pc_mania.mensajeria.config.MessagingProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.DeleteTopicRequest;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicRequest;
import software.amazon.awssdk.services.sns.model.SetSubscriptionAttributesRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.Subscription;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessagingAdminService {

    private final SnsClient snsClient;
    private final SqsClient sqsClient;
    private final MessagingProperties properties;

    public MessagingAdminService(SnsClient snsClient, SqsClient sqsClient, MessagingProperties properties) {
        this.snsClient = snsClient;
        this.sqsClient = sqsClient;
        this.properties = properties;
    }

    public MessagingTopology createTopology() {
        String topicArn = snsClient.createTopic(CreateTopicRequest.builder()
                .name(properties.getTopic().getName())
                .build()).topicArn();

        List<MessagingResource> resources = new ArrayList<>();
        for (Map.Entry<String, MessagingProperties.Queue> entry : properties.getQueues().entrySet()) {
            MessagingProperties.Queue queue = entry.getValue();
            String dlqUrl = createQueue(queue.getDlqName(), queue.getDlqRetentionSeconds(), 30, null);
            String dlqArn = queueArn(dlqUrl);
            String redrivePolicy = "{\"deadLetterTargetArn\":\"" + dlqArn
                    + "\",\"maxReceiveCount\":\"" + queue.getMaxReceiveCount() + "\"}";
            String queueUrl = createQueue(queue.getName(), queue.getRetentionSeconds(),
                    queue.getVisibilityTimeoutSeconds(), redrivePolicy);
            String queueArn = queueArn(queueUrl);
            configureTopicAccessPolicy(queueUrl, queueArn, topicArn);

            String subscriptionArn = findSubscription(topicArn, queueArn);
            if (subscriptionArn == null) {
                subscriptionArn = snsClient.subscribe(SubscribeRequest.builder()
                        .topicArn(topicArn)
                        .protocol("sqs")
                        .endpoint(queueArn)
                        .returnSubscriptionArn(true)
                        .build()).subscriptionArn();
            }
            snsClient.setSubscriptionAttributes(SetSubscriptionAttributesRequest.builder()
                    .subscriptionArn(subscriptionArn)
                    .attributeName("RawMessageDelivery")
                    .attributeValue("true")
                    .build());
            snsClient.setSubscriptionAttributes(SetSubscriptionAttributesRequest.builder()
                    .subscriptionArn(subscriptionArn)
                    .attributeName("FilterPolicy")
                    .attributeValue(queue.getFilterPolicy())
                    .build());

            resources.add(new MessagingResource(
                    queue.getName(),
                    queueArn,
                    queueUrl,
                    queue.getDlqName(),
                    queue.getFilterPolicy(),
                    queueStatus(queueUrl)));
        }
        return new MessagingTopology(properties.getTopic().getName(), topicArn, resources);
    }

    public MessagingTopology inspectTopology() {
        String topicArn = snsClient.createTopic(CreateTopicRequest.builder()
                .name(properties.getTopic().getName())
                .build()).topicArn();
        List<MessagingResource> resources = new ArrayList<>();
        for (MessagingProperties.Queue queue : properties.getQueues().values()) {
            String queueUrl = sqsClient.getQueueUrl(builder -> builder.queueName(queue.getName())).queueUrl();
            resources.add(new MessagingResource(
                    queue.getName(),
                    queueArn(queueUrl),
                    queueUrl,
                    queue.getDlqName(),
                    queue.getFilterPolicy(),
                    queueStatus(queueUrl)));
        }
        return new MessagingTopology(properties.getTopic().getName(), topicArn, resources);
    }

    public void deleteTopic() {
        String topicArn = snsClient.createTopic(CreateTopicRequest.builder()
                .name(properties.getTopic().getName())
                .build()).topicArn();
        snsClient.deleteTopic(DeleteTopicRequest.builder().topicArn(topicArn).build());
    }

    public void deleteQueue(String queueKey) {
        MessagingProperties.Queue queue = queue(queueKey);
        deleteQueueByName(queue.getName());
        deleteQueueByName(queue.getDlqName());
    }

    public List<String> subscriptions() {
        String topicArn = snsClient.createTopic(CreateTopicRequest.builder()
                .name(properties.getTopic().getName())
                .build()).topicArn();
        return snsClient.listSubscriptionsByTopic(builder -> builder.topicArn(topicArn))
                .subscriptions().stream()
                .map(Subscription::subscriptionArn)
                .toList();
    }

    public void deleteSubscription(String subscriptionArn) {
        snsClient.unsubscribe(UnsubscribeRequest.builder()
                .subscriptionArn(subscriptionArn)
                .build());
    }

    public int purgeDlq(String queueKey) {
        MessagingProperties.Queue queue = queue(queueKey);
        String dlqUrl = sqsClient.getQueueUrl(builder -> builder.queueName(queue.getDlqName())).queueUrl();
        sqsClient.purgeQueue(builder -> builder.queueUrl(dlqUrl));
        return 0;
    }

    public int reprocessDlq(String queueKey) {
        MessagingProperties.Queue queue = queue(queueKey);
        String dlqUrl = sqsClient.getQueueUrl(builder -> builder.queueName(queue.getDlqName())).queueUrl();
        String queueUrl = sqsClient.getQueueUrl(builder -> builder.queueName(queue.getName())).queueUrl();
        int moved = 0;
        boolean moreMessages = true;
        while (moreMessages) {
            List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(dlqUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(1)
                    .build()).messages();
            moreMessages = !messages.isEmpty();
            for (Message message : messages) {
                sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .messageBody(message.body())
                        .build());
                sqsClient.deleteMessage(builder -> builder.queueUrl(dlqUrl).receiptHandle(message.receiptHandle()));
                moved++;
            }
        }
        return moved;
    }

    private String createQueue(String name, int retentionSeconds, int visibilityTimeoutSeconds, String redrivePolicy) {
        Map<QueueAttributeName, String> attributes = new HashMap<>();
        attributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(retentionSeconds));
        attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(visibilityTimeoutSeconds));
        if (redrivePolicy != null) {
            attributes.put(QueueAttributeName.REDRIVE_POLICY, redrivePolicy);
        }
        return sqsClient.createQueue(CreateQueueRequest.builder()
                .queueName(name)
                .attributes(attributes)
                .build()).queueUrl();
    }

    private String queueArn(String queueUrl) {
        GetQueueAttributesResponse attributes = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build());
        return attributes.attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    private QueueStatus queueStatus(String queueUrl) {
        Map<QueueAttributeName, String> values = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED,
                        QueueAttributeName.REDRIVE_POLICY)
                .build()).attributes();
        return new QueueStatus(
                number(values, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES),
                number(values, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE),
                number(values, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED),
                0,
                values.getOrDefault(QueueAttributeName.REDRIVE_POLICY, ""));
    }

    private long number(Map<QueueAttributeName, String> values, QueueAttributeName name) {
        return Long.parseLong(values.getOrDefault(name, "0"));
    }

    private void deleteQueueByName(String name) {
        String queueUrl = sqsClient.getQueueUrl(builder -> builder.queueName(name)).queueUrl();
        sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build());
    }

    private void configureTopicAccessPolicy(String queueUrl, String queueArn, String topicArn) {
        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"AllowSnsPublish\","
                + "\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"sns.amazonaws.com\"},"
                + "\"Action\":\"sqs:SendMessage\",\"Resource\":\"" + queueArn + "\","
                + "\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"" + topicArn + "\"}}}]}";
        sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, policy))
                .build());
    }

    private String findSubscription(String topicArn, String queueArn) {
        return snsClient.listSubscriptionsByTopic(ListSubscriptionsByTopicRequest.builder()
                        .topicArn(topicArn)
                        .build())
                .subscriptions().stream()
                .filter(subscription -> queueArn.equals(subscription.endpoint()))
                .map(Subscription::subscriptionArn)
                .findFirst()
                .orElse(null);
    }

    private MessagingProperties.Queue queue(String queueKey) {
        MessagingProperties.Queue queue = properties.getQueues().get(queueKey);
        if (queue == null) {
            throw new IllegalArgumentException("Cola no configurada: " + queueKey);
        }
        return queue;
    }
}
