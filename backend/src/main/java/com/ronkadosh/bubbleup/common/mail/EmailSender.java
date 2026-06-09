package com.ronkadosh.bubbleup.common.mail;

/**
 * Send one transactional plain-text email. The interface exists so that
 * tests (and the future templated-email path) can swap implementations
 * without touching call sites in {@code auth/application}.
 */
public interface EmailSender {
    /**
     * @param to        recipient address (single)
     * @param subject   email subject line
     * @param body      plain-text body; UTF-8 is enforced by the SES implementation
     * @throws com.ronkadosh.bubbleup.common.error.AppException with
     *         {@code MAIL_NOT_CONFIGURED} or {@code MAIL_SEND_FAILED}.
     */
    void send(String to, String subject, String body);
}
