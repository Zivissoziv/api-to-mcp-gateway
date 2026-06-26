package com.example.mcpgateway.gateway.application.service;

import com.example.mcpgateway.gateway.domain.model.CallSummary;
import com.example.mcpgateway.gateway.domain.model.IpCallStats;
import com.example.mcpgateway.gateway.domain.model.ServerCallStats;
import com.example.mcpgateway.gateway.domain.model.ToolCallStats;
import com.example.mcpgateway.gateway.domain.repository.CallStatsRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallStatsService {

    private final CallStatsRepository repository;

    public CallStatsService(CallStatsRepository repository) {
        this.repository = repository;
    }

    public CallSummary getSummary() {
        return repository.getSummary();
    }

    public List<ServerCallStats> getStatsByServer() {
        return repository.getStatsByServer();
    }

    public List<ToolCallStats> getStatsByTool() {
        return repository.getStatsByTool();
    }

    public List<IpCallStats> getStatsByIp() {
        return repository.getStatsByIp();
    }

    public ServerDetail getServerDetail(String serverCode) {
        List<ToolCallStats> tools = repository.getStatsByServerCode(serverCode);
        List<IpCallStats> ips = repository.getIpStatsByServerCode(serverCode);
        return new ServerDetail(tools, ips);
    }

    public record ServerDetail(List<ToolCallStats> tools, List<IpCallStats> ips) {}
}
