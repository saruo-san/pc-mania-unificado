package cl.duoc.pc_mania.mensajeria.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messaging/admin/cloudwatch")
public class CloudWatchAdminController {

    private final CloudWatchAdminService cloudWatchAdminService;

    public CloudWatchAdminController(CloudWatchAdminService cloudWatchAdminService) {
        this.cloudWatchAdminService = cloudWatchAdminService;
    }

    @PostMapping("/dlq-alarms")
    @ResponseStatus(HttpStatus.CREATED)
    public List<String> configureDlqAlarms() {
        return cloudWatchAdminService.configureDlqAlarms();
    }

    @GetMapping("/retention-policy")
    public Map<String, String> retentionPolicy() {
        return cloudWatchAdminService.policy();
    }
}
