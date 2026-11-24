package cl.duoc.pc_mania.mensajeria.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

@Configuration
public class AwsClientConfig {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    SnsClient snsClient(MessagingProperties properties) {
        return SnsClient.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }

    @Bean
    SqsClient sqsClient(MessagingProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }

    @Bean
    KinesisClient kinesisClient(KinesisProperties properties) {
        return KinesisClient.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }

    @Bean
    DynamoDbClient dynamoDbClient(KinesisProperties properties) {
        return DynamoDbClient.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }

    @Bean
    CloudWatchClient kinesisCloudWatchClient(KinesisProperties properties) {
        return CloudWatchClient.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }

    @Bean
    KinesisAsyncClient kinesisAsyncClient(KinesisProperties properties) {
        return KinesisAsyncClient.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }

    @Bean
    DynamoDbAsyncClient dynamoDbAsyncClient(KinesisProperties properties) {
        return DynamoDbAsyncClient.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }

    @Bean
    CloudWatchAsyncClient cloudWatchAsyncClient(KinesisProperties properties) {
        return CloudWatchAsyncClient.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }
}
