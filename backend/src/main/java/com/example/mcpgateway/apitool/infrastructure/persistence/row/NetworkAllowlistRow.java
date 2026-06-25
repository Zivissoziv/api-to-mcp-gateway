package com.example.mcpgateway.apitool.infrastructure.persistence.row;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("network_allowlist")
public class NetworkAllowlistRow {
    @TableId(type = IdType.AUTO) public Long id;
    public String pattern;
    public String patternType;
    public String description;
    public Boolean enabled;
    public String createdBy;
    public LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getPattern() { return pattern; }
}
