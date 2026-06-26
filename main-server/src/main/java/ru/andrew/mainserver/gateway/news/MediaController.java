package ru.andrew.mainserver.gateway.news;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Serves news media. Binaries are handled by the gateway directly (NOT via Kafka):
 * upload stores the file on a local volume; news posts only reference the returned URL.
 * GET is public (an &lt;img&gt; tag does not send the Authorization header); upload is ADMIN/BANKER.
 */
@Slf4j
@RestController
@RequestMapping("/news/media")
public class MediaController {

    private static final Set<String> ALLOWED = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");
    private static final Map<String, String> EXT = Map.of(
            "image/png", ".png", "image/jpeg", ".jpg", "image/gif", ".gif", "image/webp", ".webp");

    private final Path mediaDir;

    public MediaController(@Value("${app.media.dir:/app/media}") String dir) {
        this.mediaDir = Paths.get(dir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(mediaDir);
        log.info("News media directory: {}", mediaDir);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported type; allowed: PNG, JPEG, GIF, WEBP");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + EXT.get(contentType.toLowerCase());
        Path target = mediaDir.resolve(name).normalize();
        if (!target.getParent().equals(mediaDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid path");
        }
        file.transferTo(target);
        log.info("Stored news media {} ({} bytes)", name, file.getSize());
        return Map.of("url", "/api/news/media/" + name, "filename", name, "size", file.getSize());
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> get(@PathVariable String filename) {
        // Reject anything that isn't a plain filename (path-traversal guard).
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        Path file = mediaDir.resolve(filename).normalize();
        if (!file.getParent().equals(mediaDir) || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        MediaType type = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new FileSystemResource(file));
    }
}
