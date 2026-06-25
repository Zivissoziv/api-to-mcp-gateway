package com.example.mcpgateway.system.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class E2ETestController {

    @GetMapping("/hello")
    Map<String, Object> hello() {
        return Map.of("message", "Hello from MCP Gateway!", "status", "ok");
    }

    @GetMapping("/users/{id}")
    Map<String, Object> getUser(@PathVariable String id) {
        return Map.of("id", id, "name", "User_" + id, "email", id + "@example.com");
    }

    @GetMapping("/search")
    Map<String, Object> search(@RequestParam(defaultValue = "") String q,
                                @RequestParam(defaultValue = "1") String page) {
        return Map.of("query", q, "page", page,
                "results", List.of(
                        Map.of("id", 1, "title", "Result 1"),
                        Map.of("id", 2, "title", "Result 2")));
    }

    @PostMapping("/echo")
    Map<String, Object> echo(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) body = Map.of();
        return Map.of("echo", body);
    }
}
