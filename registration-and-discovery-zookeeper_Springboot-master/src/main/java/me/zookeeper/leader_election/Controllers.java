package me.zookeeper.leader_election;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class Controllers {

    private final LeaderElection leaderElection;
    private final ServiceRegistry serviceRegistry;
    @Autowired
    public Controllers(LeaderElection leaderElection, ServiceRegistry serviceRegistry) {
        this.leaderElection = leaderElection;
        this.serviceRegistry = serviceRegistry;
    }

    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        String status = leaderElection.isLeader() ? "I am the leader" : "I am a follower";
        return ResponseEntity.ok(status);
    }
    @GetMapping("/services")
    public ResponseEntity<List<String>> getServices() {
        List<String> services = serviceRegistry.getAllServiceAddresses();
        if (services == null || services.isEmpty()) {
            return ResponseEntity.ok(List.of("No services registered."));
        }
        return ResponseEntity.ok(services);
    }

}
