package cl.duoc.pc_mania.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
@ConditionalOnProperty(name = "pc-mania.orders.persistence", havingValue = "dynamodb")
public class DynamoDbConfig {

    @Bean
    DynamoDbClient ordersDynamoDbClient(@Value("${pc-mania.orders.region}") String region) {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .build();
    }
}
