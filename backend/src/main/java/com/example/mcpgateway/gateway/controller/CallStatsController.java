package com.example.mcpgateway.gateway.controller;

import com.example.mcpgateway.gateway.application.service.CallStatsService;
import com.example.mcpgateway.gateway.domain.model.CallSummary;
import com.example.mcpgateway.gateway.domain.model.IpCallStats;
import com.example.mcpgateway.gateway.domain.model.ServerCallStats;
import com.example.mcpgateway.gateway.domain.model.ToolCallStats;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class CallStatsController {

    private final CallStatsService statsService;

    public CallStatsController(CallStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/summary")
    CallSummary getSummary() {
        return statsService.getSummary();
    }

    @GetMapping("/by-server")
    List<ServerCallStats> getStatsByServer() {
        return statsService.getStatsByServer();
    }

    @GetMapping("/by-tool")
    List<ToolCallStats> getStatsByTool() {
        return statsService.getStatsByTool();
    }

    @GetMapping("/by-ip")
    List<IpCallStats> getStatsByIp() {
        return statsService.getStatsByIp();
    }

    @GetMapping("/servers/{serverCode}")
    CallStatsService.ServerDetail getServerDetail(@PathVariable String serverCode) {
        return statsService.getServerDetail(serverCode);
    }
}
