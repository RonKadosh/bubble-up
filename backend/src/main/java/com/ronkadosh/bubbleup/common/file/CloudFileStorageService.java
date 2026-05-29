package com.ronkadosh.bubbleup.common.file;

import org.springframework.stereotype.Service;

/**
 * Stub cloud file storage. Not yet implemented.
 * Activate by setting FILE_STORAGE_TYPE=cloud and injecting this instead of LocalFileStorageService.
 */
@Service("cloudFileStorageService")
public class CloudFileStorageService implements FileStorageService {

    @Override
    public StoredFile upload(FileUploadRequest request) {
        throw new UnsupportedOperationException("Cloud file storage not yet implemented");
    }

    @Override
    public byte[] download(String fileId) {
        throw new UnsupportedOperationException("Cloud file storage not yet implemented");
    }

    @Override
    public void delete(String fileId) {
        throw new UnsupportedOperationException("Cloud file storage not yet implemented");
    }
}
