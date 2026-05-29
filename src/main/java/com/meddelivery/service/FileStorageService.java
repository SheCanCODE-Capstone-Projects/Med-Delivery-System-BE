package com.meddelivery.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final Path root;

    @Nullable
    private final Cloudinary cloudinary;

    public FileStorageService(
            @Value("${app.upload.dir:./uploads}") String uploadDir,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {

        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        init();

        if (!cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank()) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
            ));
            log.info("Cloudinary configured — uploads will go to cloud storage");
        } else {
            this.cloudinary = null;
            log.info("Cloudinary not configured — uploads will use local filesystem");
        }
    }

    private void init() {
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                log.info("Created upload directory: {}", root);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + root, e);
        }
    }

    /**
     * Stores the file and returns the URL to access it.
     * If Cloudinary is configured: returns full Cloudinary HTTPS URL.
     * Otherwise: returns relative path like "prescriptions/uuid.jpg" (prefix with /api/files/ for serving).
     */
    public String storeFile(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        if (cloudinary != null) {
            return uploadToCloudinary(file, subDir);
        }
        return uploadToLocal(file, subDir);
    }

    @SuppressWarnings("unchecked")
    private String uploadToCloudinary(MultipartFile file, String subDir) {
        try {
            String folder = (subDir != null && !subDir.isBlank()) ? subDir : "uploads";
            String publicId = folder + "/" + UUID.randomUUID();

            Map<String, Object> options = ObjectUtils.asMap(
                "public_id", publicId,
                "resource_type", "auto"
            );

            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), options);
            String url = (String) result.get("secure_url");
            log.debug("Cloudinary upload: {}", url);
            return url;
        } catch (IOException e) {
            log.error("Cloudinary upload failed", e);
            throw new RuntimeException("Could not upload file to Cloudinary: " + file.getOriginalFilename(), e);
        }
    }

    private String uploadToLocal(MultipartFile file, String subDir) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID() + extension;

            Path targetDir = subDir != null && !subDir.isBlank()
                    ? root.resolve(subDir).normalize()
                    : root;
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            Path destination = targetDir.resolve(filename);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            String relativePath = root.relativize(destination).toString().replace('\\', '/');
            log.debug("Stored file locally: {}", relativePath);
            return relativePath;
        } catch (IOException e) {
            log.error("Failed to store file locally", e);
            throw new RuntimeException("Could not store file: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * Loads a locally-stored file as a Resource.
     * Only call this for files stored via local filesystem (path is relative, not a URL).
     */
    public Resource loadFileAsResource(String filename) {
        try {
            Path filePath = root.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found: " + filename, e);
        }
    }

    /**
     * Deletes a locally-stored file. No-op for Cloudinary URLs.
     */
    public void deleteFile(String filename) {
        if (filename == null || filename.startsWith("http")) {
            return; // Cloudinary — deletion via API not needed for thesis demo
        }
        try {
            Path filePath = root.resolve(filename).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filename, e);
        }
    }
}
