package com.example.mcpgateway.gateway.application.service;

import com.example.mcpgateway.gateway.domain.model.GatewayCall;
import com.example.mcpgateway.gateway.domain.repository.GatewayCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class GatewayCallRecordListener {

    private static final Logger log = LoggerFactory.getLogger(GatewayCallRecordListener.class);

    private final GatewayCallRepository repository;

    public GatewayCallRecordListener(GatewayCallRepository repository) {
        this.repository = repository;
    }

    @Async("gatewayCallRecorderExecutor")
    @EventListener
    public void onGatewayCallRecorded(GatewayCallRecordedEvent event) {
        try {
            repository.save(new GatewayCall(
                    null,
                    event.serverCode(),
                    event.toolName(),
                    event.clientIp(),
                    event.traceId(),
                    event.mcpMethod(),
                    event.success(),
                    event.statusCode(),
                    event.durationMs(),
                    truncate(event.errorSummary()),
                    event.recordedAt()));
        } catch (Exception e) {
            log.warn("Failed to persist gateway call record: server={} method={} traceId={}",
                    event.serverCode(), event.mcpMethod(), event.traceId(), e);
        }
    }

    private static String truncate(String value) {
        if (value != null && value.length() > 500) {
            return value.substring(0, 500);
        }
        return value;
    }
}
