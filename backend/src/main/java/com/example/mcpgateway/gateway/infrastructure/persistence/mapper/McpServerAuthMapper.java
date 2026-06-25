package com.example.mcpgateway.gateway.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mcpgateway.gateway.infrastructure.persistence.row.McpServerAuthRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface McpServerAuthMapper extends BaseMapper<McpServerAuthRow> {
}
