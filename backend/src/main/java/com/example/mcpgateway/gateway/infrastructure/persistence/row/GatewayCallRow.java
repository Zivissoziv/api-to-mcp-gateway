package com.example.mcpgateway.gateway.infrastructure.persistence.row;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("gateway_calls")
public class GatewayCallRow {
    @TableId(type = IdType.AUTO) public Long id;
    public String serverCode;
    public String toolName;
    public String clientIp;
    public String traceId;
    public String mcpMethod;
    public Boolean success;
    public Integer statusCode;
    public Integer durationMs;
    public String errorSummary;
    public String createdAt;
}
