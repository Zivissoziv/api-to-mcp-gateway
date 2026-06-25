package com.example.mcpgateway.executor;

public record ExecutionResult(
        boolean success,
        int statusCode,
        long durationMs,
        RequestSummary requestSummary,
        ResponseSummary responseSummary,
        String error,
        String errorMessage
) {
    public record RequestSummary(String method, String url, java.util.Map<String, String> headers) {}
    public record ResponseSummary(int statusCode, java.util.Map<String, String> headers, String body, boolean bodyTruncated) {}
}
