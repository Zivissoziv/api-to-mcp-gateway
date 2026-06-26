package com.example.mcpgateway.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class HttpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpToolExecutor.class);
    private static final int MAX_BODY_LENGTH = 10_000;
    private static final int MAX_REDIRECTS = 5;

    private final RestTemplate restTemplate;

    public HttpToolExecutor() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(factory));
        this.restTemplate.setRequestFactory(factory);
    }

    public ExecutionResult execute(HttpToolDefinition definition, Map<String, Object> paramValues) {
        long start = System.nanoTime();
        try {
            // 1. Validate params
            for (var pm : definition.parameterMappings()) {
                if (pm.required() && (!paramValues.containsKey(pm.name()) || paramValues.get(pm.name()) == null)) {
                    return error("INVALID_PARAMETER", "Missing required parameter: " + pm.name(), System.nanoTime() - start);
                }
            }

            // 2. Build URL
            String url = definition.urlTemplate();
            Map<String, String> queryParams = new HashMap<>();
            Map<String, String> headerParams = new HashMap<>();

            for (var pm : definition.parameterMappings()) {
                Object val = paramValues.get(pm.name());
                if (val == null) continue;
                String strVal = val.toString();
                switch (pm.paramSource()) {
                    case "PATH" -> url = url.replace(pm.paramLocation(), strVal);
                    case "QUERY" -> queryParams.put(pm.name(), strVal);
                    case "HEADER" -> headerParams.put(pm.name(), strVal);
                    // BODY is handled by the caller
                }
            }

            // 3. Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (definition.headers() != null && !definition.headers().isBlank()) {
                for (String line : definition.headers().split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        headers.set(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                    }
                }
            }
            headerParams.forEach(headers::set);

            // 4. Build URI with query params
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url);
            queryParams.forEach(uriBuilder::queryParam);
            URI finalUri = uriBuilder.build(true).toUri();

            // 5. Build request body from bodyTemplate
            String requestBody = null;
            if (definition.bodyTemplate() != null && !definition.bodyTemplate().isBlank()) {
                requestBody = definition.bodyTemplate();
                // Replace ${paramName} placeholders with actual values from BODY-sourced params
                for (var pm : definition.parameterMappings()) {
                    if ("BODY".equals(pm.paramSource())) {
                        Object val = paramValues.get(pm.name());
                        if (val != null) {
                            String placeholder = "${" + pm.name() + "}";
                            requestBody = requestBody.replace(placeholder, val.toString());
                        }
                    }
                }
                // Auto-set Content-Type if not explicitly set
                if (headers.getContentType() == null) {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                }
            }

            // 6. Execute
            HttpMethod method = HttpMethod.valueOf(definition.httpMethod().toUpperCase());
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(finalUri, method, entity, String.class);

            long durationNs = System.nanoTime() - start;
            int statusCode = response.getStatusCode().value();
            String body = response.getBody() != null ? response.getBody() : "";

            // Truncate body
            boolean truncated = false;
            if (body.length() > MAX_BODY_LENGTH) {
                body = body.substring(0, MAX_BODY_LENGTH);
                truncated = true;
            }

            // Sanitize response headers
            Map<String, String> respHeaders = new HashMap<>();
            for (var entry : response.getHeaders().entrySet()) {
                String key = entry.getKey();
                if (!isSensitive(key)) {
                    respHeaders.put(key, String.join(", ", entry.getValue()));
                }
            }

            // Sanitize request headers
            Map<String, String> reqHeaders = new HashMap<>();
            for (var entry : headers.entrySet()) {
                String key = entry.getKey();
                if (!isSensitive(key)) {
                    reqHeaders.put(key, String.join(", ", entry.getValue()));
                }
            }

            boolean success = statusCode >= 200 && statusCode < 300;

            return new ExecutionResult(
                    success, statusCode, durationNs / 1_000_000,
                    new ExecutionResult.RequestSummary(definition.httpMethod(), finalUri.toString(), reqHeaders),
                    new ExecutionResult.ResponseSummary(statusCode, respHeaders, body, truncated),
                    success ? null : "HTTP_" + statusCode,
                    success ? null : "Request returned status " + statusCode
            );

        } catch (Exception e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("HTTP execution error: {}", e.getMessage());
            return new ExecutionResult(false, 0, durationMs, null, null, "EXECUTION_ERROR", e.getMessage());
        }
    }

    private boolean isSensitive(String header) {
        String lower = header.toLowerCase();
        return lower.contains("authorization") || lower.contains("x-api-key")
                || lower.contains("cookie") || lower.contains("set-cookie")
                || lower.contains("token") || lower.contains("secret");
    }

    private ExecutionResult error(String code, String message, long durationNs) {
        return new ExecutionResult(false, 0, durationNs / 1_000_000, null, null, code, message);
    }

    public static class ExecutorException extends RuntimeException {
        public final String code;
        public ExecutorException(String code, String message) { super(message); this.code = code; }
    }
}
