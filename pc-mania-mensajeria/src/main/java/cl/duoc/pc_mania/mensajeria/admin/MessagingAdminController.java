package cl.duoc.pc_mania.mensajeria.admin;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messaging/admin")
public class MessagingAdminController {

    private final MessagingAdminService adminService;

    public MessagingAdminController(MessagingAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/topology")
    @ResponseStatus(HttpStatus.CREATED)
    public MessagingTopology createTopology() {
        return adminService.createTopology();
    }

    @GetMapping("/topology")
    public MessagingTopology inspectTopology() {
        return adminService.inspectTopology();
    }

    @PostMapping("/queues/{queueKey}/dlq/reprocess")
    public String reprocessDlq(@PathVariable @NotBlank String queueKey) {
        return "{\"movedMessages\":" + adminService.reprocessDlq(queueKey) + "}";
    }

    @DeleteMapping("/queues/{queueKey}/dlq/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgeDlq(@PathVariable @NotBlank String queueKey) {
        adminService.purgeDlq(queueKey);
    }
}
