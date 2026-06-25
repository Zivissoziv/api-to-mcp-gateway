package com.example.mcpgateway.apitool.infrastructure.persistence.row;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mcp_server_tools")
public class McpServerToolRow {
    @TableId(type = IdType.AUTO) public Long id;
    public Long serverId;
    public Long toolId;
    public Integer sortOrder;
    public LocalDateTime createdAt;

    public Long getServerId() { return serverId; }
    public Long getToolId() { return toolId; }
}
