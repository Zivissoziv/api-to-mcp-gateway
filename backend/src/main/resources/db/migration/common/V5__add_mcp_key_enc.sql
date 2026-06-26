-- Add encrypted raw MCP key column to mcp_server_auth

ALTER TABLE mcp_server_auth ADD COLUMN mcp_key_enc VARCHAR(1000);
