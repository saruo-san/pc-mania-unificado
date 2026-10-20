package cl.duoc.pc_mania.mensajeria.event;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/messaging/events")
public class EventProducerController {

    private final EventProducerService producerService;

    public EventProducerController(EventProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> publish(@Valid @RequestBody MessagingEvent event) {
        return Map.of("messageId", producerService.publish(event), "eventId", event.eventId());
    }
}
