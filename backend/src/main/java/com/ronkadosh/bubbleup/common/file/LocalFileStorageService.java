package com.ronkadosh.bubbleup.common.file;

import com.ronkadosh.bubbleup.common.config.FileStorageProperties;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
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
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                    "Cannot create local file storage directory: " + e.getMessage());
        }
    }

    @Override
    public StoredFile upload(FileUploadRequest request) {
        String fileId = UUID.randomUUID().toString();
        try {
            Files.write(storageRoot.resolve(fileId), request.bytes());
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                    "Failed to store file: " + e.getMessage());
        }
        return new StoredFile(fileId, request.fileName(), request.contentType(),
                request.bytes().length, Instant.now());
    }

    @Override
    public byte[] download(String fileId) {
        try {
            return Files.readAllBytes(storageRoot.resolve(fileId));
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                    "Failed to read file " + fileId + ": " + e.getMessage());
        }
    }

    @Override
    public void delete(String fileId) {
        try {
            Files.deleteIfExists(storageRoot.resolve(fileId));
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                    "Failed to delete file " + fileId + ": " + e.getMessage());
        }
    }
}
