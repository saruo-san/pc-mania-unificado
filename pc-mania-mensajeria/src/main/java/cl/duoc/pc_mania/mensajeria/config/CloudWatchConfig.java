package cl.duoc.pc_mania.mensajeria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

@Configuration
public class CloudWatchConfig {

    @Bean
    CloudWatchClient cloudWatchClient(MessagingProperties properties) {
        return CloudWatchClient.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }
}
