package cl.duoc.pc_mania.mensajeria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsClientConfig {

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
}
