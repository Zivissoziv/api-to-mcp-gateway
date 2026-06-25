package com.example.mcpgateway.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("refresh_tokens")
public class RefreshTokenRow {
    @TableId public Long id;
    public String userId;
    public String tokenHash;
    public LocalDateTime expiresAt;
    public Boolean revoked;
    public LocalDateTime createdAt;
    public String getTokenHash() { return tokenHash; }
    public String getUserId() { return userId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public Boolean getRevoked() { return revoked; }
}
