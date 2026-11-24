package cl.duoc.pc_mania.mensajeria.kinesis;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kinesis/admin")
public class KinesisAdminController {

    private final KinesisAdminService adminService;
    private final KinesisEventStatusService eventStatusService;

    public KinesisAdminController(KinesisAdminService adminService,
                                  KinesisEventStatusService eventStatusService) {
        this.adminService = adminService;
        this.eventStatusService = eventStatusService;
    }

    @PostMapping("/streams")
    public List<KinesisStreamStatus> createTopology() {
        return adminService.createTopology();
    }

    @GetMapping("/streams")
    public List<String> list() {
        return adminService.listStreams();
    }

    @GetMapping("/streams/status")
    public List<KinesisStreamStatus> status() {
        return adminService.describeAll();
    }

    @GetMapping("/streams/{streamKey}")
    public KinesisStreamStatus describe(@PathVariable String streamKey) {
        return adminService.describe(streamKey);
    }

    @PutMapping("/streams/{streamKey}/shards")
    public KinesisStreamStatus updateShards(@PathVariable String streamKey,
                                           @RequestParam int shardCount) {
        return adminService.updateShards(streamKey, shardCount);
    }

    @PutMapping("/streams/{streamKey}/retention")
    public KinesisStreamStatus updateRetention(@PathVariable String streamKey,
                                               @RequestParam int retentionHours) {
        return adminService.updateRetention(streamKey, retentionHours);
    }

    @DeleteMapping("/streams/{streamKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String streamKey) {
        adminService.deleteStream(streamKey);
    }

    @GetMapping("/consumers")
    public List<KinesisConsumerStatus> consumers() {
        return adminService.consumers();
    }

    @PostMapping("/alarms/iterator-age")
    public List<String> createIteratorAgeAlarms() {
        return adminService.createIteratorAgeAlarms();
    }

    @GetMapping("/events/status")
    public List<KinesisEventStatus> eventStatus() {
        return eventStatusService.recent();
    }

    @GetMapping("/events/status/{eventId}")
    public KinesisEventStatus eventStatus(@PathVariable String eventId) {
        return eventStatusService.findByEventId(eventId);
    }
}
