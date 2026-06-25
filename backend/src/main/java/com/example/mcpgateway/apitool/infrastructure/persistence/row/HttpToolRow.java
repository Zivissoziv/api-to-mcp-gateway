package com.example.mcpgateway.apitool.infrastructure.persistence.row;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("http_tools")
public class HttpToolRow {
    @TableId(type = IdType.AUTO) public Long id;
    public String name;
    public String description;
    public String httpMethod;
    public String urlTemplate;
    public String headers;
    public Long authConfigId;
    public String status;
    public String createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
}
