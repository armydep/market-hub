package com.am.market_hub.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link EmailSender}: logs instead of sending, so the app boots and
 * the test suite runs with no mail configuration — mirroring how a missing
 * {@code CMC_API_KEY} degrades rather than crashes.
 *
 * <p>The raw token is deliberately <b>never</b> logged: PRD §3.8 names
 * password-reset tokens specifically as something that must not appear in
 * logs, with no "until a real provider exists" carve-out. That makes this
 * default genuinely non-functional for completing an actual reset — the
 * same way a missing {@code CMC_API_KEY} makes the poller serve an empty
 * universe rather than real data. A working reset flow requires a real
 * {@link EmailSender} to be configured (PRD OQ-007); tests use
 * {@code RecordingEmailSenderConfig} instead, which captures the token
 * in memory rather than logging it.
 */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        log.info("Password reset requested for {}; no email provider is configured, "
                + "so this reset cannot be completed until one is", toEmail);
    }
}
