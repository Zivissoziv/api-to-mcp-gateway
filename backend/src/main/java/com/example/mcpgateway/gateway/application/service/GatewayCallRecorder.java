package com.example.mcpgateway.gateway.application.service;

import com.example.mcpgateway.gateway.domain.model.GatewayCall;
import com.example.mcpgateway.gateway.domain.repository.GatewayCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class GatewayCallRecorder {
    private static final Logger log = LoggerFactory.getLogger(GatewayCallRecorder.class);
    private final GatewayCallRepository repository;
    public GatewayCallRecorder(GatewayCallRepository repository) { this.repository = repository; }

    public void record(String serverCode, String toolName, String clientIp,
                        String traceId, String mcpMethod, boolean success,
                        int statusCode, int durationMs, String errorSummary) {
        log.debug("record: server={} tool={} method={} success={}", serverCode, toolName, mcpMethod, success);
        String summary = errorSummary;
        if (summary != null && summary.length() > 500) {
            summary = summary.substring(0, 500);
        }
        repository.save(new GatewayCall(
                null, serverCode, toolName, clientIp, traceId,
                mcpMethod, success, statusCode, durationMs, summary, Instant.now()));
    }
}
