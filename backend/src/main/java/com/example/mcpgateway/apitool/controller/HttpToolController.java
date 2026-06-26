package com.example.mcpgateway.apitool.controller;

import com.example.mcpgateway.apitool.application.service.HttpToolService;
import com.example.mcpgateway.apitool.application.service.HttpToolService.ParameterMappingRequest;
import com.example.mcpgateway.apitool.application.service.HttpToolService.TestToolRequest;
import com.example.mcpgateway.apitool.domain.model.HttpMethod;
import com.example.mcpgateway.apitool.domain.model.HttpTool;
import com.example.mcpgateway.apitool.domain.model.ParamSource;
import com.example.mcpgateway.apitool.domain.model.ParameterMapping;
import com.example.mcpgateway.executor.ExecutionResult;
import com.example.mcpgateway.identity.infrastructure.security.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/http-tools")
public class HttpToolController {
    private final HttpToolService service;
    public HttpToolController(HttpToolService service) { this.service = service; }

    @GetMapping
    List<HttpTool> list() { return service.list(); }

    @GetMapping("/{id}")
    HttpTool get(@PathVariable long id) { return service.get(id); }

    @GetMapping("/{id}/mappings")
    List<ParameterMapping> getMappings(@PathVariable long id) { return service.getMappings(id); }

    @GetMapping("/{id}/schema")
    String getSchema(@PathVariable long id) { return service.getInputSchema(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    HttpTool create(@Valid @RequestBody UpsertRequest req, Authentication auth) {
        JwtTokenService.AuthenticatedUser user = (JwtTokenService.AuthenticatedUser) auth.getPrincipal();
        return service.create(req.name(), req.description(), req.httpMethod(),
                req.urlTemplate(), req.headers(), req.headerTemplate(), req.bodyTemplate(),
                user.userId(), toService(req.parameterMappings()));
    }

    @PutMapping("/{id}")
    HttpTool update(@PathVariable long id, @Valid @RequestBody UpsertRequest req) {
        return service.update(id, req.name(), req.description(), req.httpMethod(),
                req.urlTemplate(), req.headers(), req.headerTemplate(), req.bodyTemplate(),
                toService(req.parameterMappings()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) { service.delete(id); }

    @PostMapping("/test")
    ExecutionResult test(@Valid @RequestBody TestToolRequest req) {
        return service.testTool(req);
    }

    private static List<ParameterMappingRequest> toService(List<ParamRequest> reqs) {
        if (reqs == null) return List.of();
        return reqs.stream().map(r -> new ParameterMappingRequest(
                r.name(), r.paramSource(), r.paramLocation(),
                r.schemaJson(), r.required(), r.description())).toList();
    }

    public record ParamRequest(
            @NotBlank String name, @NotNull ParamSource paramSource, @NotBlank String paramLocation,
            @NotBlank String schemaJson, boolean required, String description) {}

    public record UpsertRequest(
            @NotBlank String name, String description,
            @NotNull HttpMethod httpMethod, @NotBlank String urlTemplate,
            String headers, String headerTemplate, String bodyTemplate,
            List<ParamRequest> parameterMappings) {}
}
