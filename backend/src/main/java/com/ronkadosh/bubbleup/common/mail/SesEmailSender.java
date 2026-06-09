package com.ronkadosh.bubbleup.common.mail;

import com.ronkadosh.bubbleup.common.config.MailProperties;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * Thin wrapper around the AWS SES Java SDK. Only owns the "send a plain
 * transactional email" path — we don't use SES for marketing or templates.
 *
 * <p>The SES client is created at startup ONLY when {@code app.mail.from}
 * is configured. Without it the component still wires (so the rest of the
 * app boots in CI) but every send throws {@code MAIL_NOT_CONFIGURED}.
 *
 * <p>Credentials come from the default AWS credentials provider chain — in
 * prod that's the EC2 instance role (see infra/iam.tf:ses_send_email); in
 * dev it falls back to AWS_ACCESS_KEY_ID/_SECRET_ACCESS_KEY env vars.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SesEmailSender implements EmailSender {

    private final MailProperties mailProperties;
    private SesClient sesClient;

    @PostConstruct
    void init() {
        if (!mailProperties.isConfigured()) {
            log.info("MailProperties not configured (MAIL_FROM_ADDRESS blank) — email sending is disabled.");
            return;
        }
        this.sesClient = SesClient.builder()
                .region(Region.of(mailProperties.awsRegion()))
                .build();
        log.info("SES client ready: region={} from={}",
                mailProperties.awsRegion(), mailProperties.from());
    }

    @PreDestroy
    void close() {
        if (sesClient != null) sesClient.close();
    }

    @Override
    public void send(String to, String subject, String body) {
        if (sesClient == null) {
            throw new AppException(ErrorCode.MAIL_NOT_CONFIGURED,
                    "Email sending isn't configured in this environment.");
        }
        SendEmailRequest request = SendEmailRequest.builder()
                .source(mailProperties.from())
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .text(Content.builder().data(body).charset("UTF-8").build())
                                .build())
                        .build())
                .build();
        try {
            sesClient.sendEmail(request);
            log.debug("SES email sent: to={} subject={}", to, subject);
        } catch (SesException e) {
            log.error("SES send failed (status={} requestId={}): {}",
                    e.statusCode(), e.requestId(), e.awsErrorDetails().errorMessage());
            throw new AppException(ErrorCode.MAIL_SEND_FAILED,
                    "Couldn't send the verification email. Please try again in a minute.");
        }
    }
}
