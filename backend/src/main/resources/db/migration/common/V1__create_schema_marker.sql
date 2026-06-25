CREATE TABLE schema_marker (
    id BIGINT NOT NULL PRIMARY KEY,
    application_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_marker (id, application_name)
VALUES (1, 'mcp-gateway');

