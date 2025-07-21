package me.zookeeper.leader_election;
import Document_and_Data.DocumentScoreInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/leader")
public class Leader {
    private static final Logger logger = LoggerFactory.getLogger(Leader.class);

    @Autowired
    private ServiceRegistry serviceRegistry;

    @PostMapping("/start")
    public TreeMap<String, Double> start(@RequestBody String searchQuery) {
        logger.info("Leader received search query: \"{}\"", searchQuery);
        RestTemplate restTemplate = new RestTemplate();

        List<String> workerAddresses = serviceRegistry.getAllServiceAddresses();
        if (workerAddresses == null || workerAddresses.isEmpty()) {
            logger.warn("No workers available");
            return new TreeMap<>();
        }

        List<DocumentScoreInfo> allResults = new ArrayList<>();
        for (String worker : workerAddresses) {
            logger.info("Dispatching query to worker {}", worker);
            List<DocumentScoreInfo> resp = restTemplate.exchange(
                    worker + "/worker/process",
                    HttpMethod.POST,
                    new HttpEntity<>(searchQuery),
                    new ParameterizedTypeReference<List<DocumentScoreInfo>>() {}
            ).getBody();

            if (resp == null) {
                logger.warn("Worker {} returned null", worker);
                continue;
            }
            logger.info("Worker {} returned {} hits", worker, resp.size());
            allResults.addAll(resp);
        }

        logger.info("Merging {} total hits from all workers", allResults.size());
        Map<String, Double> merged = new HashMap<>();
        for (DocumentScoreInfo info : allResults) {
            String name = info.getDocument().getName();
            merged.merge(name, info.getScore(), Double::sum);
        }

        logger.info("Total distinct documents: {}", merged.size());
        TreeMap<String, Double> sorted = merged.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        TreeMap::new
                ));

        logger.info("Returning final response with {} entries", sorted.size());
        return sorted;
    }
}