package com.example.mcpgateway.gateway.infrastructure.persistence.row;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mcp_server_auth")
public class McpServerAuthRow {
    @TableId(type = IdType.AUTO) public Long id;
    public Long serverId;
    public String mcpKeyHash;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getServerId() { return serverId; }
}
