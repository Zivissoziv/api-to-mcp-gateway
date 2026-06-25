package com.example.mcpgateway.aitest.controller;

import com.example.mcpgateway.aitest.application.service.AiModelConfigService;
import com.example.mcpgateway.aitest.domain.model.AiModelConfig;
import com.example.mcpgateway.identity.infrastructure.security.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-config")
public class AiModelConfigController {
    private final AiModelConfigService service;
    public AiModelConfigController(AiModelConfigService service) { this.service = service; }

    @GetMapping
    List<AiModelConfig> list() { return service.list(); }

    @GetMapping("/{id}")
    AiModelConfig get(@PathVariable long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AiModelConfig create(@Valid @RequestBody CreateRequest req, Authentication auth) {
        JwtTokenService.AuthenticatedUser user = (JwtTokenService.AuthenticatedUser) auth.getPrincipal();
        return service.create(req.name(), req.baseUrl(), req.apiKey(),
                req.model(), req.timeoutSeconds(), req.enabled(), user.userId());
    }

    @PutMapping("/{id}")
    AiModelConfig update(@PathVariable long id, @Valid @RequestBody UpdateRequest req) {
        return service.update(id, req.name(), req.baseUrl(), req.apiKey(),
                req.model(), req.timeoutSeconds(), req.enabled());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) { service.delete(id); }

    public record CreateRequest(
            @NotBlank String name, @NotBlank String baseUrl,
            @NotBlank String apiKey, @NotBlank String model,
            @NotNull Integer timeoutSeconds, boolean enabled) {}
    public record UpdateRequest(
            String name, String baseUrl, String apiKey, String model,
            Integer timeoutSeconds, Boolean enabled) {}
}
