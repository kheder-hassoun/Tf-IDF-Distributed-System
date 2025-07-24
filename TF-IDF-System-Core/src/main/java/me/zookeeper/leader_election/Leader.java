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
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/leader")
public class Leader {

    private static final Logger log = LoggerFactory.getLogger(Leader.class);

    @Autowired
    private ServiceRegistry serviceRegistry;

    @Value("${mydocument.path:/app/documents}")
    private String docRoot;

    /* -------------------- SEARCH -------------------- */
    @PostMapping("/start")
    public TreeMap<String, Double> start(@RequestBody String searchQuery) {
        log.info("Leader received search query: \"{}\"", searchQuery);
        RestTemplate rt = new RestTemplate();

        List<String> workers = serviceRegistry.getAllServiceAddresses();
        if (workers == null || workers.isEmpty()) {
            log.warn("No workers available");
            return new TreeMap<>();
        }

        List<DocumentScoreInfo> allResults = new ArrayList<>();
        for (String w : workers) {
            log.info("Dispatching query to worker {}", w);
            try {
                List<DocumentScoreInfo> resp = rt.exchange(
                        w + "/worker/process",
                        HttpMethod.POST,
                        new HttpEntity<>(searchQuery),
                        new ParameterizedTypeReference<List<DocumentScoreInfo>>() {}
                ).getBody();

                if (resp == null) {
                    log.warn("Worker {} returned null", w);
                    continue;
                }
                log.info("Worker {} returned {} hits", w, resp.size());
                allResults.addAll(resp);
            } catch (Exception ex) {
                log.warn("Worker {} search call failed: {}", w, ex.getMessage());
            }
        }

        log.info("Merging {} total hits from all workers", allResults.size());
        Map<String, Double> merged = new HashMap<>();
        for (DocumentScoreInfo info : allResults) {
            String name = info.getDocument().getName();   // should be RELATIVE now
            merged.merge(name, info.getScore(), Double::sum);
        }

        log.info("Total distinct documents: {}", merged.size());
        TreeMap<String, Double> sorted = merged.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        TreeMap::new
                ));

        log.info("Returning final response with {} entries", sorted.size());
        return sorted;
    }

    /* -------------------- DOWNLOAD -------------------- */
    @GetMapping("/download")
    public ResponseEntity<Resource> leaderDownload(@RequestParam String path) throws IOException {
        log.info("Download requested for path='{}'", path);

        Path baseDir   = Paths.get(docRoot).normalize();
        Path requested = Paths.get(path).normalize();

        // Normalise to relative
        String relative;
        if (requested.isAbsolute() && requested.startsWith(baseDir)) {
            relative = baseDir.relativize(requested).toString();
        } else {
            relative = path;
        }
        log.debug("Normalised download path to relative='{}'", relative);

        // 1) try local disk
        Path localFile = baseDir.resolve(relative).normalize();
        if (Files.exists(localFile)) {
            log.info("Serving file '{}' from leader local disk", localFile);
            Resource res = new FileSystemResource(localFile.toFile());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + localFile.getFileName() + "\"")
                    .contentLength(res.contentLength())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(res);
        } else {
            log.debug("Leader does not have '{}', probing workers…", relative);
        }

        // 2) ask workers
        RestTemplate rt = new RestTemplate();
        for (String w : serviceRegistry.getAllServiceAddresses()) {
            // build url with queryParam (RestTemplate will handle encoding)
            String url = UriComponentsBuilder.fromHttpUrl(w)
                    .path("/worker/download")
                    .queryParam("path", relative)
                    .toUriString();

            try {
                ResponseEntity<Resource> r = rt.exchange(url, HttpMethod.GET, null, Resource.class);
                if (r.getStatusCode().is2xxSuccessful() && r.getBody() != null) {
                    log.info("File '{}' obtained from worker {}", relative, w);
                    return r;
                } else {
                    log.debug("Worker {} responded {} (bodyPresent={}) for '{}'",
                            w, r.getStatusCode(), r.getBody() != null, relative);
                }
            } catch (Exception ex) {
                log.debug("Worker {} threw {} for '{}'", w, ex.getMessage(), relative);
            }
        }

        log.warn("File '{}' not found on leader or any worker", relative);
        return ResponseEntity.notFound().build();
    }

    /* -------------------- UPLOAD -------------------- */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadToWorkers(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Empty file");

        String filename = file.getOriginalFilename();
        log.info("Leader received upload for '{}', size={} bytes", filename, file.getSize());

        byte[] bytes = file.getBytes(); // read once

        RestTemplate rt = new RestTemplate();
        List<String> workers = serviceRegistry.getAllServiceAddresses();
        if (workers == null || workers.isEmpty()) {
            return ResponseEntity.status(503).body("No workers available");
        }

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
                ResponseEntity<String> r = rt.postForEntity(url, req, String.class);
                log.info("Upload to {} -> {}", w, r.getStatusCode());
                if (r.getStatusCode().is2xxSuccessful()) ok++;
            } catch (Exception ex) {
                log.warn("Upload to {} failed: {}", w, ex.getMessage());
            }
        }
        String msg = "Uploaded to " + ok + "/" + workers.size() + " workers";
        log.info(msg);
        return ResponseEntity.ok(msg);
    }

    private static HttpHeaders createMultipartHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return h;
    }
}
