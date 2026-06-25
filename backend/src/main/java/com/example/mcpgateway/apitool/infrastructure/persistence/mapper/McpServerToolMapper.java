package com.example.mcpgateway.apitool.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mcpgateway.apitool.infrastructure.persistence.row.McpServerToolRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface McpServerToolMapper extends BaseMapper<McpServerToolRow> {
}
