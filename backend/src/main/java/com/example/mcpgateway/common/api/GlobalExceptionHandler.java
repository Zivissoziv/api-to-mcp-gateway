package com.example.mcpgateway.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.mcpgateway.identity.application.service.AuthenticationService;
import com.example.mcpgateway.identity.application.service.UserManagementService;
import com.example.mcpgateway.apitool.application.service.HttpToolService;
import com.example.mcpgateway.apitool.application.service.McpServerService;
import com.example.mcpgateway.apitool.application.service.SchemaConversionService;
import com.example.mcpgateway.aitest.application.service.AiModelConfigService;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request error", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error", request);
    }

    @ExceptionHandler(AuthenticationService.InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials(HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password", request);
    }

    @ExceptionHandler(AuthenticationService.InvalidRefreshTokenException.class)
    ResponseEntity<ApiError> invalidRefreshToken(HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid", request);
    }

    @ExceptionHandler(UserManagementService.UsernameAlreadyExistsException.class)
    ResponseEntity<ApiError> duplicateUsername(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Username already exists", request);
    }

    @ExceptionHandler(UserManagementService.UserNotFoundException.class)
    ResponseEntity<ApiError> userNotFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found", request);
    }

    @ExceptionHandler(HttpToolService.ToolNotFoundException.class)
    ResponseEntity<ApiError> toolNotFound(HttpToolService.ToolNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", e.getMessage(), request);
    }

    @ExceptionHandler(HttpToolService.DuplicateToolNameException.class)
    ResponseEntity<ApiError> duplicateToolName(HttpToolService.DuplicateToolNameException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_TOOL_NAME", e.getMessage(), request);
    }

    @ExceptionHandler(McpServerService.ServerNotFoundException.class)
    ResponseEntity<ApiError> serverNotFound(McpServerService.ServerNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "SERVER_NOT_FOUND", e.getMessage(), request);
    }

    @ExceptionHandler(McpServerService.DuplicateServerCodeException.class)
    ResponseEntity<ApiError> duplicateServerCode(McpServerService.DuplicateServerCodeException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_SERVER_CODE", e.getMessage(), request);
    }

    @ExceptionHandler(McpServerService.ToolAlreadyBoundException.class)
    ResponseEntity<ApiError> toolAlreadyBound(McpServerService.ToolAlreadyBoundException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "TOOL_ALREADY_BOUND", e.getMessage(), request);
    }

    @ExceptionHandler(SchemaConversionService.InvalidSchemaException.class)
    ResponseEntity<ApiError> invalidSchema(SchemaConversionService.InvalidSchemaException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_SCHEMA", e.getMessage(), request);
    }

    @ExceptionHandler(McpServerService.AlreadyPublishedException.class)
    ResponseEntity<ApiError> alreadyPublished(McpServerService.AlreadyPublishedException e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ALREADY_PUBLISHED", e.getMessage(), request);
    }

    @ExceptionHandler(McpServerService.NotPublishedException.class)
    ResponseEntity<ApiError> notPublished(McpServerService.NotPublishedException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "NOT_PUBLISHED", e.getMessage(), request);
    }

    @ExceptionHandler(McpServerService.PublishValidationException.class)
    ResponseEntity<ApiError> publishValidation(McpServerService.PublishValidationException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "PUBLISH_VALIDATION", e.getMessage(), request);
    }

    @ExceptionHandler(AiModelConfigService.ConfigNotFoundException.class)
    ResponseEntity<ApiError> configNotFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CONFIG_NOT_FOUND", "AI model config not found", request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, traceId, Instant.now()));
    }
}
