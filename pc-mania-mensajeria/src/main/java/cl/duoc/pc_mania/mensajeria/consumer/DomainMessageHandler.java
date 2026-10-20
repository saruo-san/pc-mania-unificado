package cl.duoc.pc_mania.mensajeria.consumer;

import cl.duoc.pc_mania.mensajeria.event.MessagingEvent;

public interface DomainMessageHandler {

    void handle(MessagingEvent event);
}
