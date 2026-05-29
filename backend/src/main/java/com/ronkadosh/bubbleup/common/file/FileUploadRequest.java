package com.ronkadosh.bubbleup.common.file;

public record FileUploadRequest(
        String fileName,
        String contentType,
        byte[] bytes,
        FileAccessPolicy accessPolicy
) {}
