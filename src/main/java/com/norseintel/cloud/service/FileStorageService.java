package com.norseintel.cloud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.norseintel.cloud.exception.ForensicException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {
    
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${norseintel.storage.temp-dir}")
    private String tempDir;

    public File storeFile(MultipartFile file) {
        try {
            createDirectoryIfNotExists(tempDir);
            
            String originalFilename = file.getOriginalFilename();
            String extension = getExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID() + extension;
            Path targetPath = Paths.get(tempDir, uniqueFilename);
            
            Files.copy(file.getInputStream(), targetPath);
            log.info("Stored file: {} as {}", originalFilename, targetPath);
            
            return targetPath.toFile();
        } catch (IOException e) {
            throw new ForensicException("Failed to store file", e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    public void deleteFile(File file) {
        try {
            if (file != null && file.exists()) {
                Files.delete(file.toPath());
                log.info("Deleted file: {}", file.getAbsolutePath());
            }
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", file.getAbsolutePath(), e);
        }
    }
    
    private void createDirectoryIfNotExists(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("Created directory: {}", directoryPath);
        }
    }
    
    private String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return (lastDotIndex > 0) ? filename.substring(lastDotIndex) : "";
    }
}