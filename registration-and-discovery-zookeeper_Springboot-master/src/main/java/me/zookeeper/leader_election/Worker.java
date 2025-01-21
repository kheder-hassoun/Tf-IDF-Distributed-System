package me.zookeeper.leader_election;

import Document_and_Data.Document;
import Document_and_Data.DocumentTermsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/worker")
public class Worker {

    private static final Logger logger = LoggerFactory.getLogger(Worker.class);
    private static final String DOCUMENTS_PATH = "D:\\4 and 5\\five\\Ds\\project\\ds_project_part1\\My-TF-IDF\\src\\main\\resources\\documents"; // Update as needed

    @PostMapping("/process")
    public List<DocumentTermsInfo> processDocuments(@RequestBody String searchQuery) {
        logger.info("Processing documents for query: {}", searchQuery);
        return calculateDocumentScores(getDocumentsFromResources(), searchQuery);
    }
    private List<Document> getDocumentsFromResources() {
        try {
            return Files.walk(Paths.get(DOCUMENTS_PATH))
                    .filter(Files::isRegularFile)
                    .map(path -> new Document(path.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error reading documents: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    private List<DocumentTermsInfo> calculateDocumentScores(List<Document> documents, String searchQuery) {
        List<DocumentTermsInfo> documentTermsInfos = new ArrayList<>();
        String[] queryWords = searchQuery.split("\\s+");

        for (Document document : documents) {
            String filePath = DOCUMENTS_PATH + "\\" + document.getName();
            File file = new File(filePath);

            if (!file.exists()) {
                logger.warn("File not found: {}", filePath);
                continue;
            }

            try (InputStream inputStream = new FileInputStream(file)) {
                byte[] data = inputStream.readAllBytes();
                String fileContent = new String(data);
                DocumentTermsInfo termsInfo = calculateScore(fileContent, queryWords, document.getName());
                documentTermsInfos.add(termsInfo);
            } catch (Exception e) {
                logger.error("Error processing document {}: {}", document.getName(), e.getMessage());
            }
        }
        return documentTermsInfos;
    }

    private DocumentTermsInfo calculateScore(String fileContent, String[] queryWords, String docName) {
        DocumentTermsInfo documentTermsInfo = new DocumentTermsInfo();
        Document document = new Document(docName);
        documentTermsInfo.setDocument(document);

        HashMap<String, Double> termsInfo = new HashMap<>();
        for (String word : queryWords) {
            long count = fileContent.split("\\b" + word + "\\b").length - 1;
            termsInfo.put(word, (double) count);
        }

        documentTermsInfo.setTermFrequency(termsInfo);
        return documentTermsInfo;
    }
}
