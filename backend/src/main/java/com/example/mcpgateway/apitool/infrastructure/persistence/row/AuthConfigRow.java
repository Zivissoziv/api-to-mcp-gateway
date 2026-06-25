package com.example.mcpgateway.apitool.infrastructure.persistence.row;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("upstream_auth_configs")
public class AuthConfigRow {
    @TableId(type = IdType.AUTO) public Long id;
    public String authType;
    public String config;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public Long getId() { return id; }
}
