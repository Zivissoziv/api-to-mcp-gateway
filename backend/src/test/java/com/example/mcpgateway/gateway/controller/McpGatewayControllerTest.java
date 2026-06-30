package com.example.mcpgateway.gateway.controller;

import com.example.mcpgateway.gateway.application.service.McpProtocolService;
import com.example.mcpgateway.gateway.application.service.McpStreamableHttpSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpGatewayControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void initializeCreatesStreamableHttpSession() {
        McpProtocolService protocol = mock(McpProtocolService.class);
        McpStreamableHttpSessionRegistry registry = new McpStreamableHttpSessionRegistry();
        McpGatewayController controller = new McpGatewayController(protocol, registry);

        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", 1);
        response.putObject("result").put("protocolVersion", "2025-06-18");
        when(protocol.handle(eq("orders"), any(), any(), any(), any(HttpServletRequest.class)))
                .thenReturn(new McpProtocolService.ProtocolResult(response, true, "2025-06-18"));

        ResponseEntity<?> entity = controller.post("orders",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}",
                "application/json, text/event-stream",
                MediaType.APPLICATION_JSON_VALUE,
                null,
                null,
                null,
                mock(HttpServletRequest.class));

        assertThat(entity.getStatusCode().value()).isEqualTo(200);
        assertThat(entity.getHeaders().getFirst(McpGatewayController.HEADER_SESSION_ID)).isNotBlank();
        assertThat(entity.getHeaders().getFirst(McpGatewayController.HEADER_PROTOCOL_VERSION)).isEqualTo("2025-06-18");
    }

    @Test
    void nonInitializeRequestRequiresSession() {
        McpProtocolService protocol = mock(McpProtocolService.class);
        McpGatewayController controller = new McpGatewayController(protocol, new McpStreamableHttpSessionRegistry());

        ResponseEntity<?> entity = controller.post("orders",
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
                "application/json, text/event-stream",
                MediaType.APPLICATION_JSON_VALUE,
                null,
                "2025-06-18",
                "Bearer key",
                mock(HttpServletRequest.class));

        assertThat(entity.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(protocol);
    }
}
