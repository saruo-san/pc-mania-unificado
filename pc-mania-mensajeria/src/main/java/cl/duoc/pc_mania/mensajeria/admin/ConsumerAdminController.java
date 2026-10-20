package cl.duoc.pc_mania.mensajeria.admin;

import cl.duoc.pc_mania.mensajeria.consumer.ConsumerControlService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/messaging/admin/consumers")
public class ConsumerAdminController {

    private final ConsumerControlService controlService;

    public ConsumerAdminController(ConsumerControlService controlService) {
        this.controlService = controlService;
    }

    @GetMapping
    public Map<String, Boolean> states() {
        return controlService.states();
    }

    @PostMapping("/{consumerKey}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pause(@PathVariable String consumerKey) {
        controlService.pause(validate(consumerKey));
    }

    @PostMapping("/{consumerKey}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resume(@PathVariable String consumerKey) {
        controlService.resume(validate(consumerKey));
    }

    private String validate(String consumerKey) {
        if (!SetOfConsumers.ALL.contains(consumerKey)) {
            throw new IllegalArgumentException("Consumidor no configurado: " + consumerKey);
        }
        return consumerKey;
    }

    private static final class SetOfConsumers {
        private static final java.util.Set<String> ALL = java.util.Set.of(
                "notifications", "tickets", "documents");
    }
}
