package com.example.mcpgateway.apitool.controller;

import com.example.mcpgateway.apitool.application.service.NetworkAllowlistService;
import com.example.mcpgateway.apitool.domain.model.NetworkAllowlist;
import com.example.mcpgateway.apitool.domain.model.PatternType;
import com.example.mcpgateway.identity.infrastructure.security.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/network-allowlist")
public class NetworkAllowlistController {
    private final NetworkAllowlistService service;
    public NetworkAllowlistController(NetworkAllowlistService service) { this.service = service; }

    @GetMapping
    List<NetworkAllowlist> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    NetworkAllowlist add(@Valid @RequestBody AddRequest req, Authentication auth) {
        JwtTokenService.AuthenticatedUser user = (JwtTokenService.AuthenticatedUser) auth.getPrincipal();
        return service.add(req.pattern(), req.patternType(), req.description(), user.userId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) { service.delete(id); }

    public record AddRequest(
            @NotBlank String pattern, @NotNull PatternType patternType, String description) {}
}
