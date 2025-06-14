package me.zookeeper.leader_election;

import Document_and_Data.Document;
import Document_and_Data.DocumentTermsInfo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.nio.file.Files;
import java.nio.file.Path;
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

    @Value("${mydocument.path}")
    private String DOCUMENTS_PATH;

    // Add diagnostic logging
    @PostConstruct
    public void init() {
        logger.info("DOCUMENTS_PATH: {}", DOCUMENTS_PATH);
        try {
            Path path = Paths.get(DOCUMENTS_PATH);
            logger.info("Path exists: {}", Files.exists(path));
            logger.info("Is directory: {}", Files.isDirectory(path));

            if (Files.exists(path) && Files.isDirectory(path)) {
                List<Path> files = Files.list(path).collect(Collectors.toList());
                logger.info("Files in directory: {}", files);
            }
        } catch (Exception e) {
            logger.error("Init error: ", e);
        }
    }

    @PostMapping("/process")
    public List<DocumentTermsInfo> processDocuments(@RequestBody String searchQuery) {
        logger.info("Processing documents for query: {}", searchQuery);
        return calculateDocumentScores(getDocumentsFromResources(), searchQuery);
    }

    private List<Document> getDocumentsFromResources() {
        try {
            Path startPath = Paths.get(DOCUMENTS_PATH);
            logger.info("Scanning directory: {}", startPath.toAbsolutePath());

            return Files.walk(startPath)
                    .filter(Files::isRegularFile)
                    .peek(path -> logger.info("Found file: {}", path))
                    .map(path -> new Document(path.toAbsolutePath().toString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error reading documents: ", e);  // Full stack trace
            return Collections.emptyList();
        }
    }

    private List<DocumentTermsInfo> calculateDocumentScores(List<Document> documents, String searchQuery) {
        List<DocumentTermsInfo> documentTermsInfos = new ArrayList<>();
        String[] queryWords = searchQuery.split("\\s+");

        for (Document document : documents) {
            String filePath = document.getName();
            try {
                Path path = Paths.get(filePath);
                if (!Files.exists(path)) {
                    logger.warn("File not found: {}", filePath);
                    continue;
                }

                String fileContent = Files.readString(path);
                DocumentTermsInfo termsInfo = calculateScore(fileContent, queryWords, document);
                documentTermsInfos.add(termsInfo);
                logger.info("Processed document: {}", document.getName());
            } catch (Exception e) {
                logger.error("Error processing document {}: {}", filePath, e.getMessage());
            }
        }
        logger.info("Processed {} documents", documentTermsInfos.size());
        return documentTermsInfos;
    }

    private DocumentTermsInfo calculateScore(String fileContent, String[] queryWords, Document document) {
        DocumentTermsInfo documentTermsInfo = new DocumentTermsInfo();
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