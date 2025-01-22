package web.server.IDF_web_server;



import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class LeaderRequestController {

    @Value("${leader.url}")
    private String leaderUrl;

    private final RestTemplate restTemplate;

    public LeaderRequestController() {
        this.restTemplate = new RestTemplate();
    }

    @GetMapping("/search")
    public ResponseEntity<String> handleSearchRequest(@RequestParam String query) {
        try {
            // Forward the search query as a POST request to the leader
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<String> requestEntity = new HttpEntity<>(query);
            String response = restTemplate.postForObject(leaderUrl , requestEntity, String.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error communicating with leader: " + e.getMessage());
        }
    }

}
