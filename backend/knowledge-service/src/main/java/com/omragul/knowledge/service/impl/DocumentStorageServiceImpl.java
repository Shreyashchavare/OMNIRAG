package com.omragul.knowledge.service.impl;

import com.omragul.knowledge.entity.Document;
import com.omragul.knowledge.exception.ResourceNotFoundException;
import com.omragul.knowledge.service.DocumentStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Service
public class DocumentStorageServiceImpl implements DocumentStorageService {

    private final Path storageRootDir;

    public DocumentStorageServiceImpl(
            @Value("${knowledge.storage.root-dir:${KNOWLEDGE_STORAGE_ROOT:./storage}}") String rootDir
    ) {
        Path root = Paths.get(rootDir).toAbsolutePath().normalize();
        Path realRoot;
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
            realRoot = root.toRealPath();
        } catch (IOException e) {
            realRoot = root;
        }
        this.storageRootDir = realRoot;
    }

    @Override
    public boolean exists(Document document) {
        if (document == null || document.getStoragePath() == null || document.getStoragePath().isBlank()) {
            return false;
        }
        try {
            Path path = resolveAndValidatePath(document.getStoragePath());
            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public long getFileSize(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        Path path = resolveAndValidatePath(document.getStoragePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Document storage file not found for document: " + document.getId());
        }
        try {
            return Files.size(path);
        } catch (IOException e) {
            log.error("Failed to determine file size for document {}: {}", document.getId(), e.getMessage());
            throw new ResourceNotFoundException("Document storage file not found for document: " + document.getId());
        }
    }

    @Override
    public InputStream openStream(Document document) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        Path path = resolveAndValidatePath(document.getStoragePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Document storage file not found for document: " + document.getId());
        }
        return Files.newInputStream(path, StandardOpenOption.READ);
    }

    Path resolveAndValidatePath(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Document storage path is required");
        }

        // 1. Defend against path traversal, null bytes, and malicious escaping
        if (storagePath.contains("\0") || storagePath.contains("..")) {
            log.warn("Security warning: Invalid character sequence detected in storage path");
            throw new SecurityException("Illegal path traversal attempt in storage path");
        }

        Path candidate = Paths.get(storagePath);
        Path resolved;
        if (candidate.isAbsolute()) {
            resolved = candidate.normalize();
        } else {
            resolved = storageRootDir.resolve(candidate).normalize();
        }

        // 2. Confinement check against normalized storage root
        if (!resolved.startsWith(storageRootDir)) {
            log.warn("Security warning: Attempt to access path outside storage root detected");
            throw new SecurityException("Access to path outside storage root is prohibited");
        }

        // 3. Resolve symbolic links if the target exists to verify real canonical target
        if (Files.exists(resolved)) {
            try {
                Path realPath = resolved.toRealPath();
                if (!realPath.startsWith(storageRootDir)) {
                    log.warn("Security warning: Symbolic link escaping storage root detected");
                    throw new SecurityException("Access to path outside storage root is prohibited");
                }
                return realPath;
            } catch (IOException e) {
                log.warn("Security warning: Unable to verify real path for storage file");
                throw new SecurityException("Access to path outside storage root is prohibited");
            }
        }

        return resolved;
    }
}
