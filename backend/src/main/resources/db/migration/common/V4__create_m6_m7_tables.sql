-- M6: MCP 调用记录 + Server 共享 Key / M7: AI 模型配置

CREATE TABLE IF NOT EXISTS gateway_calls (
    id              BIGINT    NOT NULL PRIMARY KEY,
    server_code     VARCHAR(64) NOT NULL,
    tool_name       VARCHAR(128),
    client_ip       VARCHAR(64),
    trace_id        VARCHAR(64) NOT NULL,
    mcp_method      VARCHAR(32) NOT NULL,
    success         INTEGER   NOT NULL DEFAULT 0,
    status_code     INTEGER   NOT NULL DEFAULT 0,
    duration_ms     INTEGER   NOT NULL DEFAULT 0,
    error_summary   VARCHAR(512),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mcp_server_auth (
    id              BIGINT    NOT NULL PRIMARY KEY,
    server_id       BIGINT    NOT NULL UNIQUE,
    mcp_key_hash    VARCHAR(100) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (server_id) REFERENCES mcp_servers(id)
);

CREATE TABLE IF NOT EXISTS ai_model_configs (
    id              BIGINT    NOT NULL PRIMARY KEY,
    name            VARCHAR(64) NOT NULL,
    base_url        VARCHAR(512) NOT NULL,
    api_key_enc     VARCHAR(1000) NOT NULL,
    model           VARCHAR(128) NOT NULL,
    timeout_seconds INTEGER   NOT NULL DEFAULT 60,
    enabled         INTEGER   NOT NULL DEFAULT 0,
    created_by      VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);
