package org.growforge.jalmarg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Stores report photos in Amazon S3 when S3_BUCKET is set, otherwise on local disk.
 * Photos are always served back through the backend (/api/photos/{key}),
 * so the bucket can stay private.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final S3Client s3;
    private final String bucket;
    private final Path localDir;

    public StorageService(S3Client s3,
                          @Value("${jalmarg.storage.s3-bucket}") String bucket,
                          @Value("${jalmarg.storage.local-dir}") String localDir) throws IOException {
        this.s3 = s3;
        this.bucket = bucket == null ? "" : bucket.trim();
        this.localDir = Paths.get(localDir).toAbsolutePath().normalize();
        Files.createDirectories(this.localDir);
        log.info("Photo storage: {}", usingS3() ? "S3 bucket " + this.bucket : "local dir " + this.localDir);
    }

    public boolean usingS3() {
        return !bucket.isEmpty();
    }

    /** Saves the photo and returns its key. */
    public String save(byte[] bytes, String contentType) throws IOException {
        String ext = switch (contentType == null ? "" : contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
        // keys look like reports/2026-10-09/uuid.jpg
        String key = "reports/" + LocalDate.now() + "/" + UUID.randomUUID() + ext;
        if (usingS3()) {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType == null ? "image/jpeg" : contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } else {
            Path target = localDir.resolve(key).normalize();
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        }
        return key;
    }

    /** Loads photo bytes, or null if missing. */
    public byte[] load(String key) {
        if (key == null || key.contains("..")) return null;
        try {
            if (usingS3()) {
                ResponseBytes<GetObjectResponse> obj = s3.getObjectAsBytes(
                        GetObjectRequest.builder().bucket(bucket).key(key).build());
                return obj.asByteArray();
            }
            Path p = localDir.resolve(key).normalize();
            if (!p.startsWith(localDir) || !Files.exists(p)) return null;
            return Files.readAllBytes(p);
        } catch (Exception e) {
            log.warn("Could not load photo {}: {}", key, e.getMessage());
            return null;
        }
    }

    public static String contentTypeFor(String key) {
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".webp")) return "image/webp";
        if (key.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
