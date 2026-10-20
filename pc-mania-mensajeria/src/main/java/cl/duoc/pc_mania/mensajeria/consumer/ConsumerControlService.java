package cl.duoc.pc_mania.mensajeria.consumer;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConsumerControlService {

    private final Map<String, Boolean> states = new ConcurrentHashMap<>();

    public boolean isRunning(String consumerKey) {
        return states.getOrDefault(consumerKey, true);
    }

    public void pause(String consumerKey) {
        states.put(consumerKey, false);
    }

    public void resume(String consumerKey) {
        states.put(consumerKey, true);
    }

    public Map<String, Boolean> states() {
        return Map.copyOf(states);
    }
}
