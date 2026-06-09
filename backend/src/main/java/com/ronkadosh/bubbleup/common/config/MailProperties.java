package com.ronkadosh.bubbleup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for transactional email sending via Amazon SES.
 *
 * <p>{@code from} must point at a <strong>verified SES identity</strong> in
 * the configured {@code awsRegion} — SES rejects any other sender address
 * with {@code MessageRejected: Email address is not verified}.
 *
 * <p>Leave {@code from} blank in environments without email configured
 * (CI, fresh local clones); the verification controller returns a clean
 * error in that case instead of throwing at startup.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        String from,
        String awsRegion,
        Duration verificationTtl,
        String frontendBaseUrl
) {
    public boolean isConfigured() {
        return from != null && !from.isBlank()
                && awsRegion != null && !awsRegion.isBlank();
    }
}
