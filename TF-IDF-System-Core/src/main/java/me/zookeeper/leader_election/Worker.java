package me.zookeeper.leader_election;

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
import java.nio.charset.MalformedInputException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/worker")
public class Worker {
    private static final Logger logger = LoggerFactory.getLogger(Worker.class);

    @Value("${mydocument.path}")
    private String DOCUMENTS_PATH;

    @Value("${lucene.index.path}")
    private String INDEX_PATH;

    private Directory luceneDir;
    private IndexWriter indexWriter;

    @PostConstruct
    public void init() {
        logger.info("Initializing Worker. DOCUMENTS_PATH={}  INDEX_PATH={}", DOCUMENTS_PATH, INDEX_PATH);
        try {
            Path docsPath = Paths.get(DOCUMENTS_PATH);
            if (!Files.exists(docsPath) || !Files.isDirectory(docsPath)) {
                logger.error("Documents path invalid: {}", docsPath.toAbsolutePath());
                return;
            }

            // Prepare Lucene index directory (separate from documents)
            Path idxPath = Paths.get(INDEX_PATH);
            Files.createDirectories(idxPath);
            luceneDir = FSDirectory.open(idxPath);

            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            indexWriter = new IndexWriter(luceneDir, config);

            // Index all files under docsPath, skipping anything under idxPath
            logger.info("Walking {} to index files...", docsPath);
            Files.walk(docsPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(idxPath))
                    .forEach(path -> {
                        try {
                            String fileName = path.toAbsolutePath().toString();
                            addDocToIndex(new Document(fileName));
                        } catch (Exception e) {
                            logger.error("Failed to index {}: {}", path, e.getMessage());
                        }
                    });

            indexWriter.commit();
            logger.info("Indexing complete. Total docs indexed: {}", indexWriter.numRamDocs());

        } catch (Exception e) {
            logger.error("Error during Worker init:", e);
        }
    }
    @GetMapping("/download")
    public ResponseEntity<Resource> workerDownload(@RequestParam String path) throws IOException {

        Path base   = Paths.get(DOCUMENTS_PATH).normalize();
        Path target = base.resolve(path).normalize();

        if (!target.startsWith(base) || !Files.exists(target)) {
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


    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Empty file");
        }
        try {
            // 1. save to DOCUMENTS_PATH
            Path dest = Paths.get(DOCUMENTS_PATH, file.getOriginalFilename())
                    .normalize();
            Files.copy(file.getInputStream(), dest,
                    StandardCopyOption.REPLACE_EXISTING);

            // 2. add to Lucene index
            synchronized (indexWriter) {           // writer isn’t thread‑safe
                addDocToIndex(new Document(dest.toString()));
                indexWriter.commit();
            }
            logger.info("Uploaded & indexed {}", dest);
            return ResponseEntity.ok("Uploaded");
        } catch (Exception e) {
            logger.error("Upload failed", e);
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }



    private void addDocToIndex(Document doc) throws IOException {

        Path path = Paths.get(doc.getName());
        String text;

        try {
            /* 1. Try plain‑text fast path */
            text = Files.readString(path);              // works for UTF‑8 text
        } catch (MalformedInputException e) {
            /* 2. Fallback to Tika for binary / non‑UTF8 files */
            try (InputStream in = Files.newInputStream(path)) {
                AutoDetectParser parser = new AutoDetectParser();
                BodyContentHandler handler = new BodyContentHandler(-1); // -1 = unlimited
                parser.parse(in, handler, new Metadata());
                text = handler.toString();
                logger.debug("Tika extracted {} bytes from {}", text.length(), path);
            } catch (TikaException | SAXException ex) {
                logger.error("Tika failed to parse {}: {}", path, ex.getMessage());
                text = "";                               // index filename only
            }
        }

        /* 3. Index filename + extracted text */
        org.apache.lucene.document.Document ldoc = new org.apache.lucene.document.Document();
        ldoc.add(new StringField("path", doc.getName(), Field.Store.YES));
        ldoc.add(new TextField("contents", text, Field.Store.NO));
        indexWriter.updateDocument(new Term("path", doc.getName()), ldoc);
        logger.debug("Indexed {}", doc.getName());
    }

    @PostMapping("/process")
    public List<DocumentScoreInfo> processDocuments(@RequestBody String searchQuery) {
        logger.info("Received query: \"{}\"", searchQuery);
        try {
            List<DocumentScoreInfo> results = searchIndex(searchQuery);
            logger.info("Returning {} hits for query \"{}\"", results.size(), searchQuery);
            return results;
        } catch (Exception e) {
            logger.error("Search failed for query \"{}\": {}", searchQuery, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<DocumentScoreInfo> searchIndex(String queryString) throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(luceneDir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Analyzer analyzer = new StandardAnalyzer();
            QueryParser parser = new QueryParser("contents", analyzer);
            Query query = parser.parse(QueryParser.escape(queryString));
            logger.debug("Parsed Lucene query: {}", query);

            TopDocs topDocs = searcher.search(query, Integer.MAX_VALUE);
            logger.info("Lucene found {} total hits", topDocs.totalHits.value);

            List<DocumentScoreInfo> results = new ArrayList<>();
            for (ScoreDoc sd : topDocs.scoreDocs) {
                org.apache.lucene.document.Document hit = searcher.doc(sd.doc);
                String path = hit.get("path");
                results.add(new DocumentScoreInfo(new Document(path), sd.score));
            }
            return results;
        }
    }
}