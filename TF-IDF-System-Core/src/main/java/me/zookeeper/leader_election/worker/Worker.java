package me.zookeeper.leader_election.worker;

import Document_and_Data.Document;
import Document_and_Data.DocumentScoreInfo;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import jakarta.annotation.PostConstruct;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/worker")
public class Worker {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    @Value("${mydocument.path}")
    private String DOCUMENTS_PATH;

    @Value("${lucene.index.path}")
    private String INDEX_PATH;

    private Directory luceneDir;
    private IndexWriter indexWriter;

    @PostConstruct
    public void init() {
        log.info("Initializing Worker. DOCUMENTS_PATH={}  INDEX_PATH={}", DOCUMENTS_PATH, INDEX_PATH);
        try {
            Path docsPath = Paths.get(DOCUMENTS_PATH).normalize();
            if (!Files.exists(docsPath) || !Files.isDirectory(docsPath)) {
                log.error("Documents path invalid: {}", docsPath.toAbsolutePath());
                return;
            }

            Path idxPath = Paths.get(INDEX_PATH).normalize();
            Files.createDirectories(idxPath);
            luceneDir = FSDirectory.open(idxPath);

            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            indexWriter = new IndexWriter(luceneDir, config);

            // Index already existing files (skip index dir)
            log.info("Walking {} to index files…", docsPath);
            Files.walk(docsPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(idxPath))
                    .forEach(path -> {
                        try {
                            addDocToIndex(new Document(path.toString()));
                        } catch (Exception e) {
                            log.error("Failed to index {}: {}", path, e.getMessage());
                        }
                    });

            indexWriter.commit();
            log.info("Indexing complete. Total docs indexed (RAM): {}", indexWriter.numRamDocs());

        } catch (Exception e) {
            log.error("Error during Worker init:", e);
        }
    }

    /* -------------------- DOWNLOAD -------------------- */
    @GetMapping("/download")
    public ResponseEntity<Resource> workerDownload(@RequestParam String path) throws IOException {
        // ensure we decode (in case leader encoded or double-encoded)
        String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
        log.info("[download] requested path='{}' (decoded='{}')", path, decoded);

        Path base   = Paths.get(DOCUMENTS_PATH).normalize();
        Path target = base.resolve(decoded).normalize();

        boolean safe   = target.startsWith(base);
        boolean exists = Files.exists(target);
        log.debug("[download] base='{}', target='{}', safe={}, exists={}", base, target, safe, exists);

        if (!safe || !exists) {
            return ResponseEntity.notFound().build();
        }

        Resource res = new FileSystemResource(target.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + target.getFileName() + "\"")
                .contentLength(res.contentLength())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(res);
    }


    /* -------------------- UPLOAD -------------------- */
    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Empty file");
        }
        String filename = file.getOriginalFilename();
        log.info("[upload] Received '{}' size={} bytes", filename, file.getSize());
        try {
            Path dest = Paths.get(DOCUMENTS_PATH, filename).normalize();
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

            synchronized (indexWriter) {
                addDocToIndex(new Document(dest.toString()));
                indexWriter.commit();
            }
            log.info("[upload] Uploaded & indexed {}", dest);
            return ResponseEntity.ok("Uploaded");
        } catch (Exception e) {
            log.error("Upload failed", e);
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }
    @GetMapping("/index-size")
    public ResponseEntity<Long> getIndexSize() {
        try {
            Path indexPath = Paths.get(INDEX_PATH).normalize();
            if (!Files.exists(indexPath)) {
                return ResponseEntity.ok(0L);
            }

            long totalSize = Files.walk(indexPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            log.warn("Error reading size of {}", path);
                            return 0L;
                        }
                    }).sum();

            log.info("Index size for {} is {} bytes", indexPath, totalSize);
            return ResponseEntity.ok(totalSize);
        } catch (Exception e) {
            log.error("Failed to get index size", e);
            return ResponseEntity.status(500).build();
        }
    }

    /* -------------------- SEARCH -------------------- */
    @PostMapping("/process")
    public List<DocumentScoreInfo> processDocuments(@RequestBody String searchQuery) {
        log.info("Received query: \"{}\"", searchQuery);
        try {
            List<DocumentScoreInfo> results = searchIndex(searchQuery);
            log.info("Returning {} hits for query \"{}\"", results.size(), searchQuery);
            return results;
        } catch (Exception e) {
            log.error("Search failed for query \"{}\": {}", searchQuery, e.getMessage());
            return Collections.emptyList();
        }
    }

    /* -------------------- Helpers -------------------- */

    private void addDocToIndex(Document doc) throws IOException {
        Path base = Paths.get(DOCUMENTS_PATH).normalize();
        Path abs  = Paths.get(doc.getName()).normalize();

        // store RELATIVE path in the index
        String rel = abs.startsWith(base) ? base.relativize(abs).toString() : abs.getFileName().toString();
        log.debug("[index] Storing path='{}' (relative of {})", rel, base);

        String text;
        try {
            text = Files.readString(abs);
        } catch (MalformedInputException e) {
            try (InputStream in = Files.newInputStream(abs)) {
                AutoDetectParser parser = new AutoDetectParser();
                BodyContentHandler handler = new BodyContentHandler(-1);
                parser.parse(in, handler, new Metadata());
                text = handler.toString();
                log.debug("[index] Tika extracted {} chars from {}", text.length(), abs);
            } catch (TikaException | SAXException ex) {
                log.error("[index] Tika failed to parse {}: {}", abs, ex.getMessage());
                text = "";
            }
        }

        org.apache.lucene.document.Document ldoc = new org.apache.lucene.document.Document();
        ldoc.add(new StringField("path", rel, Field.Store.YES));
        ldoc.add(new TextField("contents", text, Field.Store.NO));

        indexWriter.updateDocument(new Term("path", rel), ldoc);
        log.debug("[index] Indexed {}", rel);
    }

    private List<DocumentScoreInfo> searchIndex(String queryString) throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(luceneDir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Analyzer analyzer = new StandardAnalyzer();
            QueryParser parser = new QueryParser("contents", analyzer);
            Query query = parser.parse(QueryParser.escape(queryString));
            log.debug("Parsed Lucene query: {}", query);

            TopDocs topDocs = searcher.search(query, Integer.MAX_VALUE);
            log.info("Lucene found {} total hits", topDocs.totalHits.value);

            List<DocumentScoreInfo> results = new ArrayList<>();
            for (ScoreDoc sd : topDocs.scoreDocs) {
                org.apache.lucene.document.Document hit = searcher.doc(sd.doc);
                String relPath = hit.get("path");  // relative
                results.add(new DocumentScoreInfo(new Document(relPath), sd.score));
            }
            return results;
        }
    }
}
