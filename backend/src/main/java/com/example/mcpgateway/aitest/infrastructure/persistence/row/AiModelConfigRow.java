package com.example.mcpgateway.aitest.infrastructure.persistence.row;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_model_configs")
public class AiModelConfigRow {
    @TableId(type = IdType.AUTO) public Long id;
    public String name;
    public String baseUrl;
    public String apiKeyEnc;
    public String model;
    public Integer timeoutSeconds;
    public Integer enabled; // 0/1
    public String createdBy;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getName() { return name; }
    public Integer getEnabled() { return enabled; }
}
