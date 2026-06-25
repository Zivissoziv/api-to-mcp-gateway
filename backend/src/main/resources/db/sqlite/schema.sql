CREATE TABLE IF NOT EXISTS schema_marker (
    id INTEGER NOT NULL PRIMARY KEY,
    application_name VARCHAR(64) NOT NULL,
    created_at VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO schema_marker (id, application_name)
VALUES (1, 'mcp-gateway');

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at VARCHAR(32) NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0,
    created_at VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS upstream_auth_configs (
    id              INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    auth_type       VARCHAR(16) NOT NULL,
    config          VARCHAR(2000) NOT NULL,
    created_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS http_tools (
    id              INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512) NOT NULL DEFAULT '',
    http_method     VARCHAR(8) NOT NULL,
    url_template    VARCHAR(2000) NOT NULL,
    headers         VARCHAR(2000),
    header_template VARCHAR(4000),
    body_template   VARCHAR(4000),
    auth_config_id  INTEGER,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by      VARCHAR(36) NOT NULL,
    created_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auth_config_id) REFERENCES upstream_auth_configs(id),
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS tool_parameter_mappings (
    id              INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    tool_id         INTEGER   NOT NULL,
    name            VARCHAR(64) NOT NULL,
    param_source    VARCHAR(8) NOT NULL,
    param_location  VARCHAR(128) NOT NULL,
    schema_json     VARCHAR(2000) NOT NULL,
    required        INTEGER   NOT NULL DEFAULT 0,
    description     VARCHAR(512) NOT NULL DEFAULT '',
    sort_order      INTEGER   NOT NULL DEFAULT 0,
    created_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tool_id) REFERENCES http_tools(id)
);

CREATE TABLE IF NOT EXISTS mcp_servers (
    id              INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    code            VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512) NOT NULL DEFAULT '',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by      VARCHAR(36) NOT NULL,
    created_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS mcp_server_tools (
    id              INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    server_id       INTEGER   NOT NULL,
    tool_id         INTEGER   NOT NULL,
    sort_order      INTEGER   NOT NULL DEFAULT 0,
    created_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (server_id) REFERENCES mcp_servers(id),
    FOREIGN KEY (tool_id) REFERENCES http_tools(id),
    UNIQUE(server_id, tool_id)
);

CREATE TABLE IF NOT EXISTS network_allowlist (
    id              INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    pattern         VARCHAR(256) NOT NULL,
    pattern_type    VARCHAR(8) NOT NULL,
    description     VARCHAR(512) NOT NULL DEFAULT '',
    enabled         INTEGER   NOT NULL DEFAULT 1,
    created_by      VARCHAR(36) NOT NULL,
    created_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- M6: MCP 调用记录
CREATE TABLE IF NOT EXISTS gateway_calls (
    id              INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    server_code     VARCHAR(64) NOT NULL,
    tool_name       VARCHAR(128),
    client_ip       VARCHAR(64),
    trace_id        VARCHAR(64) NOT NULL,
    mcp_method      VARCHAR(32) NOT NULL,
    success         INTEGER   NOT NULL DEFAULT 0,
    status_code     INTEGER   NOT NULL DEFAULT 0,
    duration_ms     INTEGER   NOT NULL DEFAULT 0,
    error_summary   VARCHAR(512),
    created_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- M6: Server 共享 MCP Key
CREATE TABLE IF NOT EXISTS mcp_server_auth (
    id              INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    server_id       INTEGER   NOT NULL UNIQUE,
    mcp_key_hash    VARCHAR(100) NOT NULL,
    created_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (server_id) REFERENCES mcp_servers(id)
);

-- M7: AI 模型配置
CREATE TABLE IF NOT EXISTS ai_model_configs (
    id              INTEGER   NOT NULL PRIMARY KEY AUTOINCREMENT,
    name            VARCHAR(64) NOT NULL,
    base_url        VARCHAR(512) NOT NULL,
    api_key_enc     VARCHAR(1000) NOT NULL,
    model           VARCHAR(128) NOT NULL,
    timeout_seconds INTEGER   NOT NULL DEFAULT 60,
    enabled         INTEGER   NOT NULL DEFAULT 0,
    created_by      VARCHAR(36) NOT NULL,
    created_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      VARCHAR(32) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);
