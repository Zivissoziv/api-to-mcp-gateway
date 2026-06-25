package com.example.mcpgateway.apitool.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mcpgateway.apitool.domain.model.ParamSource;
import com.example.mcpgateway.apitool.domain.model.ParameterMapping;
import com.example.mcpgateway.apitool.domain.repository.ParameterMappingRepository;
import com.example.mcpgateway.apitool.infrastructure.persistence.mapper.ParameterMappingMapper;
import com.example.mcpgateway.apitool.infrastructure.persistence.row.ParameterMappingRow;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;

@Repository
public class MybatisParameterMappingRepository implements ParameterMappingRepository {
    private final ParameterMappingMapper mapper;
    public MybatisParameterMappingRepository(ParameterMappingMapper mapper) { this.mapper = mapper; }

    @Override public List<ParameterMapping> findByToolId(long toolId) {
        return mapper.selectList(new LambdaQueryWrapper<ParameterMappingRow>()
                .eq(ParameterMappingRow::getToolId, toolId)).stream().map(this::domain).toList();
    }
    @Override public void save(ParameterMapping mapping) {
        mapper.insert(row(mapping));
    }
    @Override public void saveAll(List<ParameterMapping> mappings) {
        for (ParameterMapping m : mappings) mapper.insert(row(m));
    }
    @Override public void deleteByToolId(long toolId) {
        mapper.delete(new LambdaQueryWrapper<ParameterMappingRow>()
                .eq(ParameterMappingRow::getToolId, toolId));
    }

    private ParameterMapping domain(ParameterMappingRow r) {
        return new ParameterMapping(r.id, r.toolId, r.name, ParamSource.valueOf(r.paramSource),
                r.paramLocation, r.schemaJson, r.required == 1, r.description,
                r.sortOrder, r.createdAt.toInstant(ZoneOffset.UTC));
    }
    private ParameterMappingRow row(ParameterMapping m) {
        ParameterMappingRow r = new ParameterMappingRow();
        r.id = m.id(); r.toolId = m.toolId(); r.name = m.name();
        r.paramSource = m.paramSource().name(); r.paramLocation = m.paramLocation();
        r.schemaJson = m.schemaJson(); r.required = m.required() ? 1 : 0;
        r.description = m.description(); r.sortOrder = m.sortOrder();
        return r;
    }
}
