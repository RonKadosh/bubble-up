package com.ronkadosh.bubbleup.report.model;

/**
 * The reason bucket a user picks when filing a report. Entity-agnostic by
 * design — the Report Center is a concise free-form inbox, not a per-entity
 * flag, so the category is the only structured signal beyond the free text.
 */
public enum ReportCategory {
    ABUSE,
    HARASSMENT,
    SPAM,
    SAFETY,
    TECHNICAL_ISSUE,
    OTHER
}
