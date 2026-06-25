package com.example.mcpgateway.apitool.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mcpgateway.apitool.domain.model.McpServerTool;
import com.example.mcpgateway.apitool.domain.repository.McpServerToolRepository;
import com.example.mcpgateway.apitool.infrastructure.persistence.mapper.McpServerToolMapper;
import com.example.mcpgateway.apitool.infrastructure.persistence.row.McpServerToolRow;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;

@Repository
public class MybatisMcpServerToolRepository implements McpServerToolRepository {
    private final McpServerToolMapper mapper;
    public MybatisMcpServerToolRepository(McpServerToolMapper mapper) { this.mapper = mapper; }

    @Override public List<McpServerTool> findByServerId(long serverId) {
        return mapper.selectList(new LambdaQueryWrapper<McpServerToolRow>()
                .eq(McpServerToolRow::getServerId, serverId)).stream().map(this::domain).toList();
    }
    @Override public List<McpServerTool> findByToolId(long toolId) {
        return mapper.selectList(new LambdaQueryWrapper<McpServerToolRow>()
                .eq(McpServerToolRow::getToolId, toolId)).stream().map(this::domain).toList();
    }
    @Override public void save(McpServerTool binding) {
        McpServerToolRow row = new McpServerToolRow();
        row.serverId = binding.serverId(); row.toolId = binding.toolId();
        row.sortOrder = binding.sortOrder();
        mapper.insert(row);
    }
    @Override public void deleteByServerIdAndToolId(long serverId, long toolId) {
        mapper.delete(new LambdaQueryWrapper<McpServerToolRow>()
                .eq(McpServerToolRow::getServerId, serverId)
                .eq(McpServerToolRow::getToolId, toolId));
    }
    @Override public void deleteByServerId(long serverId) {
        mapper.delete(new LambdaQueryWrapper<McpServerToolRow>()
                .eq(McpServerToolRow::getServerId, serverId));
    }
    @Override public boolean existsByServerIdAndToolId(long serverId, long toolId) {
        return mapper.selectCount(new LambdaQueryWrapper<McpServerToolRow>()
                .eq(McpServerToolRow::getServerId, serverId)
                .eq(McpServerToolRow::getToolId, toolId)) > 0;
    }

    private McpServerTool domain(McpServerToolRow r) {
        return new McpServerTool(r.id, r.serverId, r.toolId, r.sortOrder,
                r.createdAt.toInstant(ZoneOffset.UTC));
    }
}
