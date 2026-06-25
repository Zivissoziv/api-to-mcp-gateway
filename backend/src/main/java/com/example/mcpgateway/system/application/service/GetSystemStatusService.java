package com.example.mcpgateway.system.application.service;

import com.example.mcpgateway.system.domain.SystemStatus;
import org.springframework.stereotype.Service;

@Service
public class GetSystemStatusService {

    public SystemStatus getStatus() {
        return SystemStatus.ready();
    }
}
