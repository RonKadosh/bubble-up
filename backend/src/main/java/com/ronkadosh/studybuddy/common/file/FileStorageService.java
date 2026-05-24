package com.ronkadosh.studybuddy.common.file;

public interface FileStorageService {
    StoredFile upload(FileUploadRequest request);
    byte[] download(String fileId);
    void delete(String fileId);
}
