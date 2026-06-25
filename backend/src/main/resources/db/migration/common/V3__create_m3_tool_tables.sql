-- M3: API Tool 草稿、MCP Server、参数映射、认证、网络白名单

CREATE TABLE IF NOT EXISTS upstream_auth_configs (
    id              BIGINT    NOT NULL PRIMARY KEY,
    auth_type       VARCHAR(16) NOT NULL,
    config          VARCHAR(2000) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS http_tools (
    id              BIGINT    NOT NULL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512) NOT NULL DEFAULT '',
    http_method     VARCHAR(8) NOT NULL,
    url_template    VARCHAR(2000) NOT NULL,
    headers         VARCHAR(2000),
    auth_config_id  BIGINT,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by      VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auth_config_id) REFERENCES upstream_auth_configs(id),
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS tool_parameter_mappings (
    id              BIGINT    NOT NULL PRIMARY KEY,
    tool_id         BIGINT    NOT NULL,
    name            VARCHAR(64) NOT NULL,
    param_source    VARCHAR(8) NOT NULL,
    param_location  VARCHAR(128) NOT NULL,
    schema_json     VARCHAR(2000) NOT NULL,
    required        INTEGER   NOT NULL DEFAULT 0,
    description     VARCHAR(512) NOT NULL DEFAULT '',
    sort_order      INTEGER   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tool_id) REFERENCES http_tools(id)
);

CREATE TABLE IF NOT EXISTS mcp_servers (
    id              BIGINT    NOT NULL PRIMARY KEY,
    code            VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512) NOT NULL DEFAULT '',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by      VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS mcp_server_tools (
    id              BIGINT    NOT NULL PRIMARY KEY,
    server_id       BIGINT    NOT NULL,
    tool_id         BIGINT    NOT NULL,
    sort_order      INTEGER   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (server_id) REFERENCES mcp_servers(id),
    FOREIGN KEY (tool_id) REFERENCES http_tools(id),
    UNIQUE(server_id, tool_id)
);

CREATE TABLE IF NOT EXISTS network_allowlist (
    id              BIGINT    NOT NULL PRIMARY KEY,
    pattern         VARCHAR(256) NOT NULL,
    pattern_type    VARCHAR(8) NOT NULL,
    description     VARCHAR(512) NOT NULL DEFAULT '',
    enabled         INTEGER   NOT NULL DEFAULT 1,
    created_by      VARCHAR(36) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);
