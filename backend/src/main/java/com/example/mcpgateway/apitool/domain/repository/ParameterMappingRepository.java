package com.example.mcpgateway.apitool.domain.repository;

import com.example.mcpgateway.apitool.domain.model.ParameterMapping;

import java.util.List;

public interface ParameterMappingRepository {
    List<ParameterMapping> findByToolId(long toolId);
    void save(ParameterMapping mapping);
    void saveAll(List<ParameterMapping> mappings);
    void deleteByToolId(long toolId);
}
