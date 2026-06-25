package com.example.mcpgateway.system.controller;

import com.example.mcpgateway.system.application.service.GetSystemStatusService;
import com.example.mcpgateway.system.domain.SystemStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final GetSystemStatusService getSystemStatusService;

    public SystemStatusController(GetSystemStatusService getSystemStatusService) {
        this.getSystemStatusService = getSystemStatusService;
    }

    @GetMapping("/status")
    SystemStatus status() {
        return getSystemStatusService.getStatus();
    }
}
