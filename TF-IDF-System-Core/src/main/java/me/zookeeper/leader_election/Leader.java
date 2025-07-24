package me.zookeeper.leader_election;
import Document_and_Data.DocumentScoreInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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


    @Value("${mydocument.path:/app/documents}")
    private String docRoot;
    /** Streams a file to the caller (the entry‑point pod). */

    @GetMapping("/download")
    public ResponseEntity<Resource> leaderDownload(@RequestParam String path) throws IOException {

        Path baseDir = Paths.get(docRoot).normalize();
        Path requested = Paths.get(path).normalize();

        // if client sent an absolute path, convert to relative
        String relative =
                requested.startsWith(baseDir)
                        ? baseDir.relativize(requested).toString()   // "New Microsoft Word Document.docx"
                        : path;                                      // already relative

        /* 1. try local disk --------------------------------------------------- */
        Path localFile = baseDir.resolve(relative).normalize();
        if (Files.exists(localFile)) {
            Resource res = new FileSystemResource(localFile.toFile());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + localFile.getFileName() + "\"")
                    .contentLength(res.contentLength())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(res);
        }

        /* 2. ask every worker ------------------------------------------------- */
        RestTemplate rt = new RestTemplate();
        for (String w : serviceRegistry.getAllServiceAddresses()) {
            String url = w + "/worker/download?path=" +
                    UriUtils.encode(relative, StandardCharsets.UTF_8);
            try {
                ResponseEntity<Resource> r =
                        rt.exchange(url, HttpMethod.GET, null, Resource.class);
                if (r.getStatusCode().is2xxSuccessful() && r.getBody() != null) {
                    logger.info("Downloaded '{}' from {}", relative, w);
                    return r;
                }
            } catch (Exception ex) {
                logger.debug("Worker {} had no file {}", w, relative);
            }
        }
        return ResponseEntity.notFound().build();
    }



    @PostMapping("/upload")
    public ResponseEntity<String> uploadToWorkers(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Empty file");

        // read once into byte[] so we can send to many workers
        byte[] bytes = file.getBytes();
        String filename = file.getOriginalFilename();

        RestTemplate rt = new RestTemplate();
        List<String> workers = serviceRegistry.getAllServiceAddresses();
        if (workers == null || workers.isEmpty()) {
            return ResponseEntity.status(503).body("No workers available");
        }

        // build multipart body once
        MultiValueMap<String,Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        });
        HttpEntity<MultiValueMap<String,Object>> req =
                new HttpEntity<>(body, createMultipartHeaders());

        int ok = 0;
        for (String w : workers) {
            String url = w + "/worker/upload";
            try {
                ResponseEntity<String> r =
                        rt.postForEntity(url, req, String.class);
                if (r.getStatusCode().is2xxSuccessful()) ok++;
                logger.info("Upload to {} -> {}", w, r.getStatusCode());
            } catch (Exception ex) {
                logger.warn("Upload to {} failed: {}", w, ex.getMessage());
            }
        }
        return ResponseEntity.ok("Uploaded to " + ok + "/" + workers.size() + " workers");
    }

    private static HttpHeaders createMultipartHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return h;
    }




}




