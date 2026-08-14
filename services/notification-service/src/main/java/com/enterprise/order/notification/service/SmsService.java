package com.enterprise.order.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Simulated SMS gateway (Phase 9). Logs the send instead of calling a real
 * provider; the persisted Notification row is the audit trail.
 */
@Service
@Slf4j
public class SmsService {

    public boolean send(String to, String body) {
        log.info("SMS SENT (simulated) to={} body=\"{}\"", to, body);
        return true;
    }
}
