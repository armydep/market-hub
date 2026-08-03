package com.am.market_hub.support;

import java.util.concurrent.atomic.AtomicReference;

import com.am.market_hub.auth.email.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test-only {@link EmailSender} that records the last sent (email, token)
 * pair in memory instead of logging, so an IT can retrieve the real raw
 * token and complete a full request → confirm round-trip without
 * screen-scraping logs. Mirrors {@link StubProviderConfig}'s exact pattern.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingEmailSenderConfig {

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }

    public static class RecordingEmailSender implements EmailSender {

        private final AtomicReference<String> lastToken = new AtomicReference<>();
        private final AtomicReference<String> lastEmail = new AtomicReference<>();

        @Override
        public void sendPasswordResetEmail(String toEmail, String rawToken) {
            lastEmail.set(toEmail);
            lastToken.set(rawToken);
        }

        public String lastToken() {
            return lastToken.get();
        }

        public String lastEmail() {
            return lastEmail.get();
        }
    }
}
