package com.example.mcpgateway.gateway.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class GatewayCallRecorder {
    private static final Logger log = LoggerFactory.getLogger(GatewayCallRecorder.class);
    private final ApplicationEventPublisher eventPublisher;

    public GatewayCallRecorder(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void record(String serverCode, String toolName, String clientIp,
                        String traceId, String mcpMethod, boolean success,
                        int statusCode, int durationMs, String errorSummary) {
        log.debug("record: server={} tool={} method={} success={}", serverCode, toolName, mcpMethod, success);
        eventPublisher.publishEvent(new GatewayCallRecordedEvent(
                serverCode, toolName, clientIp, traceId,
                mcpMethod, success, statusCode, durationMs, errorSummary, Instant.now()));
    }
}
