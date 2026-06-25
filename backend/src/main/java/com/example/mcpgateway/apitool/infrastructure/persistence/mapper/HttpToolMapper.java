package com.example.mcpgateway.apitool.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mcpgateway.apitool.infrastructure.persistence.row.HttpToolRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HttpToolMapper extends BaseMapper<HttpToolRow> {
}
