package com.ronkadosh.bubbleup.report.internal.dto;

/** The decoded attachment bytes + their content type, for the admin stream endpoint. */
public record ReportImage(byte[] bytes, String contentType) {}
