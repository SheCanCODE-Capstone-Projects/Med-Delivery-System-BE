package com.meddelivery.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${app.upload.dir:./uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        init();
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
     * Stores the given file and returns the relative URL path.
     * Filename is UUID + original extension.
     */
    public String storeFile(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        try {
            // Determine extension
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID() + extension;

            // Resolve target directory (optional subdirectory)
            Path targetDir = subDir != null && !subDir.isBlank()
                    ? root.resolve(subDir).normalize()
                    : root;
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // Copy file
            Path destination = targetDir.resolve(filename);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            // Return URL path relative to uploads root (e.g., "prescriptions/abc.pdf" or "insurance/xyz.jpg")
            String relativePath = root.relativize(destination).toString().replace('\\', '/');
            log.debug("Stored file: {}", relativePath);
            return relativePath;

        } catch (IOException e) {
            log.error("Failed to store file", e);
            throw new RuntimeException("Could not store file: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * Loads file as Resource for serving via HTTP.
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
     * Deletes a file.
     */
    public void deleteFile(String filename) {
        try {
            Path filePath = root.resolve(filename).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filename, e);
        }
    }
}
