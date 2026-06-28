package com.example.mcpgateway.gateway.infrastructure.persistence.repository;

import com.example.mcpgateway.gateway.domain.model.CallSummary;
import com.example.mcpgateway.gateway.domain.model.IpCallStats;
import com.example.mcpgateway.gateway.domain.model.ServerCallStats;
import com.example.mcpgateway.gateway.domain.model.ToolCallStats;
import com.example.mcpgateway.gateway.domain.repository.CallStatsRepository;
import com.example.mcpgateway.gateway.infrastructure.persistence.mapper.GatewayCallMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisCallStatsRepository implements CallStatsRepository {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GatewayCallMapper mapper;

    public MybatisCallStatsRepository(GatewayCallMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CallSummary getSummary() {
        Map<String, Object> row = mapper.selectSummary();
        return new CallSummary(
                longValue(row, "totalCalls"),
                longValue(row, "uniqueServers"),
                longValue(row, "uniqueTools"),
                longValue(row, "uniqueIps"));
    }

    @Override
    public List<ServerCallStats> getStatsByServer() {
        return mapper.selectStatsByServer().stream()
                .map(row -> new ServerCallStats(
                        str(row, "serverCode"),
                        longValue(row, "callCount"),
                        longValue(row, "successCount"),
                        longValue(row, "uniqueIps"),
                        doubleValue(row, "avgDurationMs"),
                        instantValue(row, "lastCallAt")))
                .toList();
    }

    @Override
    public List<ToolCallStats> getStatsByTool() {
        return mapper.selectStatsByTool().stream()
                .map(row -> new ToolCallStats(
                        str(row, "serverCode"),
                        str(row, "toolName"),
                        longValue(row, "callCount"),
                        longValue(row, "successCount"),
                        longValue(row, "uniqueIps"),
                        doubleValue(row, "avgDurationMs"),
                        instantValue(row, "lastCallAt")))
                .toList();
    }

    @Override
    public List<IpCallStats> getStatsByIp() {
        return mapper.selectStatsByIp().stream()
                .map(row -> new IpCallStats(
                        str(row, "clientIp"),
                        longValue(row, "callCount"),
                        instantValue(row, "lastCallAt")))
                .toList();
    }

    @Override
    public List<ToolCallStats> getStatsByServerCode(String serverCode) {
        return mapper.selectStatsByServerCode(serverCode).stream()
                .map(row -> new ToolCallStats(
                        serverCode,
                        str(row, "toolName"),
                        longValue(row, "callCount"),
                        longValue(row, "successCount"),
                        longValue(row, "uniqueIps"),
                        doubleValue(row, "avgDurationMs"),
                        instantValue(row, "lastCallAt")))
                .toList();
    }

    @Override
    public List<IpCallStats> getIpStatsByServerCode(String serverCode) {
        return mapper.selectIpStatsByServerCode(serverCode).stream()
                .map(row -> new IpCallStats(
                        str(row, "clientIp"),
                        longValue(row, "callCount"),
                        instantValue(row, "lastCallAt")))
                .toList();
    }

    // -- conversion helpers --

    private static long longValue(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static double doubleValue(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v != null ? v.toString() : "";
    }

    private static Instant instantValue(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Instant instant) {
            return instant;
        }
        if (v instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.ofHours(8));
        }
        if (v instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (v instanceof String s && !s.isBlank()) {
            return parseStringInstant(s);
        }
        return null;
    }

    private static Instant parseStringInstant(String value) {
        try {
            return LocalDateTime.parse(value, ISO_FORMATTER).toInstant(ZoneOffset.ofHours(8));
        } catch (Exception ignored) {
            // Try the next supported database/JDBC timestamp shape.
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.ofHours(8));
        } catch (Exception ignored) {
            // Try the next supported database/JDBC timestamp shape.
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}
