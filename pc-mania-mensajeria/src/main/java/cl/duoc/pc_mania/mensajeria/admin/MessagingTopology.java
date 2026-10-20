package cl.duoc.pc_mania.mensajeria.admin;

import java.util.List;

public record MessagingTopology(String topicName, String topicArn, List<MessagingResource> queues) {
}
