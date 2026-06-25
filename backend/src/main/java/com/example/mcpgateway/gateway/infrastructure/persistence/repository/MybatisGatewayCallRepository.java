package com.example.mcpgateway.gateway.infrastructure.persistence.repository;

import com.example.mcpgateway.gateway.domain.model.GatewayCall;
import com.example.mcpgateway.gateway.domain.repository.GatewayCallRepository;
import com.example.mcpgateway.gateway.infrastructure.persistence.mapper.GatewayCallMapper;
import com.example.mcpgateway.gateway.infrastructure.persistence.row.GatewayCallRow;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;

@Repository
public class MybatisGatewayCallRepository implements GatewayCallRepository {
    private final GatewayCallMapper mapper;
    public MybatisGatewayCallRepository(GatewayCallMapper mapper) { this.mapper = mapper; }

    @Override public void save(GatewayCall call) {
        mapper.insert(row(call));
    }

    private GatewayCallRow row(GatewayCall c) {
        GatewayCallRow r = new GatewayCallRow();
        r.serverCode = c.serverCode();
        r.toolName = c.toolName();
        r.clientIp = c.clientIp();
        r.traceId = c.traceId();
        r.mcpMethod = c.mcpMethod();
        r.success = c.success();
        r.statusCode = c.statusCode();
        r.durationMs = (int) c.durationMs();
        r.errorSummary = c.errorSummary();
        return r;
    }
}
