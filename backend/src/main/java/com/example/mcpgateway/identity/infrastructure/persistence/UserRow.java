package com.example.mcpgateway.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("users")
public class UserRow {
    @TableId public String id;
    public String username;
    public String passwordHash;
    public String role;
    public String status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getStatus() { return status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
