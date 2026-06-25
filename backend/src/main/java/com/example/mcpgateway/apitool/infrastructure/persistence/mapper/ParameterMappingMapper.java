package com.example.mcpgateway.apitool.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mcpgateway.apitool.infrastructure.persistence.row.ParameterMappingRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ParameterMappingMapper extends BaseMapper<ParameterMappingRow> {
}
