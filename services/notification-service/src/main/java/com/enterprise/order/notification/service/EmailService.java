package com.enterprise.order.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Simulated email gateway (Phase 9). Logs the send instead of calling a real
 * provider; the persisted Notification row is the audit trail. Swap the body of
 * {@link #send} for a real provider (SES/SMTP) in production without touching
 * callers.
 */
@Service
@Slf4j
public class EmailService {

    public boolean send(String to, String subject, String body) {
        log.info("EMAIL SENT (simulated) to={} subject=\"{}\" body=\"{}\"", to, subject, body);
        return true;
    }
}
