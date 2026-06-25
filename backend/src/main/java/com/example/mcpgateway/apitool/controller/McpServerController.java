package com.example.mcpgateway.apitool.controller;

import com.example.mcpgateway.apitool.application.service.McpServerService;
import com.example.mcpgateway.apitool.domain.model.McpServer;
import com.example.mcpgateway.apitool.domain.model.McpServerTool;
import com.example.mcpgateway.identity.infrastructure.security.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servers")
public class McpServerController {
    private final McpServerService service;
    public McpServerController(McpServerService service) { this.service = service; }

    @GetMapping
    List<McpServer> list() { return service.list(); }

    @GetMapping("/{id}")
    McpServer get(@PathVariable long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    McpServer create(@Valid @RequestBody UpsertRequest req, Authentication auth) {
        JwtTokenService.AuthenticatedUser user = (JwtTokenService.AuthenticatedUser) auth.getPrincipal();
        return service.create(req.code(), req.name(), req.description(), user.userId());
    }

    @PutMapping("/{id}")
    McpServer update(@PathVariable long id, @Valid @RequestBody UpsertRequest req) {
        return service.update(id, req.code(), req.name(), req.description());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) { service.delete(id); }

    @GetMapping("/{id}/tools")
    List<McpServerTool> getTools(@PathVariable long id) { return service.getTools(id); }

    @PostMapping("/{id}/tools")
    @ResponseStatus(HttpStatus.CREATED)
    McpServerTool bindTool(@PathVariable long id, @Valid @RequestBody BindRequest req) {
        return service.bindTool(id, req.toolId());
    }

    @DeleteMapping("/{id}/tools/{toolId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unbindTool(@PathVariable long id, @PathVariable long toolId) {
        service.unbindTool(id, toolId);
    }

    public record UpsertRequest(@NotBlank String code, @NotBlank String name, String description) {}
    public record BindRequest(@NotNull Long toolId) {}
}
