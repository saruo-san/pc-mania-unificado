package cl.duoc.pc_mania.mensajeria.kinesis;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;

@RestController
@RequestMapping("/api/kinesis/events")
public class KinesisProducerController {

    private final KinesisProducerService producerService;

    public KinesisProducerController(KinesisProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public KinesisPublishResponse publish(@Valid @RequestBody KinesisPublishRequest request) {
        PutRecordsResponse response = producerService.put(request.stream(), request.event());
        String sequenceNumber = response.records().isEmpty()
                ? null
                : response.records().get(0).sequenceNumber();
        return new KinesisPublishResponse(request.event().eventId(), sequenceNumber);
    }
}
