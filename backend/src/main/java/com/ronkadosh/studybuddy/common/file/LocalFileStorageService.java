package com.ronkadosh.studybuddy.common.file;

import com.ronkadosh.studybuddy.common.config.FileStorageProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

@Service
@Primary
public class LocalFileStorageService implements FileStorageService {

    private final Path storageRoot;

    public LocalFileStorageService(FileStorageProperties props) {
        this.storageRoot = Paths.get(props.localPath());
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create local file storage directory", e);
        }
    }

    @Override
    public StoredFile upload(FileUploadRequest request) {
        String fileId = UUID.randomUUID().toString();
        try {
            Files.write(storageRoot.resolve(fileId), request.bytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
        return new StoredFile(fileId, request.fileName(), request.contentType(),
                request.bytes().length, Instant.now());
    }

    @Override
    public byte[] download(String fileId) {
        try {
            return Files.readAllBytes(storageRoot.resolve(fileId));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + fileId, e);
        }
    }

    @Override
    public void delete(String fileId) {
        try {
            Files.deleteIfExists(storageRoot.resolve(fileId));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + fileId, e);
        }
    }
}
