package com.example.mcpgateway.apitool.infrastructure.persistence.row;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mcp_servers")
public class McpServerRow {
    @TableId(type = IdType.AUTO) public Long id;
    public String code;
    public String name;
    public String description;
    public String status;
    public String createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
