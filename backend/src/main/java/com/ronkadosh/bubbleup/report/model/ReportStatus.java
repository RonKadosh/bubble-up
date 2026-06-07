package com.ronkadosh.bubbleup.report.model;

/**
 * Lifecycle of a report in the admin inbox. New reports land PENDING; an admin
 * either RESOLVED (acted on it) or DISMISSED (no action needed). Only PENDING
 * reports drive the inbox badge count.
 */
public enum ReportStatus {
    PENDING,
    RESOLVED,
    DISMISSED
}
