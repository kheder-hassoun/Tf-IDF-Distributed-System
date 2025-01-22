
package me.zookeeper.leader_election;
import Document_and_Data.Document;
import Document_and_Data.DocumentTermsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;


import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/leader")
public class Leader {

    private static final List<DocumentTermsInfo> documentpTermsInfo = Collections.synchronizedList(new ArrayList<>());

    private static final Logger logger = LoggerFactory.getLogger(Leader.class);

    //new
    @Autowired
    private ServiceRegistry serviceRegistry;

    @PostMapping("/start")
    public TreeMap<String, Double> start(@RequestBody String searchQuery) {
        RestTemplate restTemplate = new RestTemplate();

        //new
        List<String> workerAddresses = serviceRegistry.getAllServiceAddresses();
        if (workerAddresses == null || workerAddresses.isEmpty()) {
            logger.warn("No workers available in the cluster.");
            return new TreeMap<>();
        }
        //new
        List<DocumentTermsInfo> allWorkerResponses = new ArrayList<>();
        for (String workerAddress : workerAddresses){
            List<DocumentTermsInfo> workerResponse = restTemplate.exchange(
                    workerAddress+"/worker/process",
                    HttpMethod.POST,
                    new HttpEntity<>(searchQuery),
                    new ParameterizedTypeReference<List<DocumentTermsInfo>>() {}
            ).getBody();
            System.out.println("Worker response is: ");
            for (DocumentTermsInfo info : workerResponse) {
                System.out.println(info);
            }
            if (workerResponse == null || workerResponse.isEmpty()) {
                logger.warn("No results returned from the worker.");
                return new TreeMap<>();
            }
            allWorkerResponses.addAll(workerResponse);
        }



        // Calculate IDF and document scores
        Map<String, Double> idfs = calculateIDF(allWorkerResponses, allWorkerResponses.size(), searchQuery);
        // Print the results
        System.out.println("IDF Values:");
        idfs.forEach((term, idf) -> System.out.println("Term: " + term + ", IDF: " + idf));

        Map<Document, Double> documentScores = calculateDocumentsScore(idfs, allWorkerResponses);
        System.out.println("Document Scores:");
        documentScores.forEach((document, score) -> System.out.println("Document: " + document.getName() + ", Score: " + score));

        // Sort and return the results
        return sortDocumentsScoresByName(documentScores);
    }



    private Map<String, Double> calculateIDF(List<DocumentTermsInfo> documentTermsInfo, double totalDocuments, String searchQuery) {
        Map<String, Double> wordDocumentCount = new HashMap<>();

        // Count documents containing each term
        for (DocumentTermsInfo termsInfo : documentTermsInfo) {
            termsInfo.getTermFrequency().keySet().stream()
                    .map(String::toLowerCase)
                    .map(String::trim)
                    .distinct()
                    .forEach(term -> wordDocumentCount.merge(term, 1.0, Double::sum));
        }

        Map<String, Double> idfs = new HashMap<>();
        for (String term : searchQuery.split("\\s+")) {
            term = term.toLowerCase().trim();
            double documentCount = wordDocumentCount.getOrDefault(term, 0.0);
            idfs.put(term, Math.log10((double) totalDocuments / (documentCount + 1)));

//            idfs.put(term, documentCount > 0 ? Math.log10(totalDocuments / documentCount) : 0.0);
        }
        return idfs;
    }


    private Map<Document, Double> calculateDocumentsScore(Map<String, Double> idfs, List<DocumentTermsInfo> documentTermsInfo) {
        Map<Document, Double> documentScores = new HashMap<>();
        for (DocumentTermsInfo termsInfo : documentTermsInfo) {
            double score = termsInfo.getTermFrequency().entrySet().stream()
                    .mapToDouble(entry -> idfs.getOrDefault(entry.getKey(), 0.0) * entry.getValue())
                    .sum();
            documentScores.put(termsInfo.getDocument(), score);
        }
        return documentScores;
    }

    private TreeMap<String, Double> sortDocumentsScoresByName(Map<Document, Double> documentScores) {
        return documentScores.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getName(), Map.Entry::getValue, (e1, e2) -> e1, TreeMap::new));
    }
}
