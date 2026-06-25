package com.example.mcpgateway.apitool.infrastructure.persistence.row;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tool_parameter_mappings")
public class ParameterMappingRow {
    @TableId(type = IdType.AUTO) public Long id;
    public Long toolId;
    public String name;
    public String paramSource;
    public String paramLocation;
    public String schemaJson;
    public Integer required;
    public String description;
    public Integer sortOrder;
    public LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getToolId() { return toolId; }
}
