package com.example.mcpgateway.aitest.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mcpgateway.aitest.infrastructure.persistence.row.AiModelConfigRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfigRow> {
}
