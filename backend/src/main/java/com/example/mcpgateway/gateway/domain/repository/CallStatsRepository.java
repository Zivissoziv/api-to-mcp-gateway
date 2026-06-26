package com.example.mcpgateway.gateway.domain.repository;

import com.example.mcpgateway.gateway.domain.model.CallSummary;
import com.example.mcpgateway.gateway.domain.model.IpCallStats;
import com.example.mcpgateway.gateway.domain.model.ServerCallStats;
import com.example.mcpgateway.gateway.domain.model.ToolCallStats;
import java.util.List;

public interface CallStatsRepository {

    CallSummary getSummary();

    List<ServerCallStats> getStatsByServer();

    List<ToolCallStats> getStatsByTool();

    List<IpCallStats> getStatsByIp();

    List<ToolCallStats> getStatsByServerCode(String serverCode);

    List<IpCallStats> getIpStatsByServerCode(String serverCode);
}
