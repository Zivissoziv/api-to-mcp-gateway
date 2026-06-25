package com.example.mcpgateway.apitool.domain.repository;

import com.example.mcpgateway.apitool.domain.model.HttpTool;

import java.util.List;
import java.util.Optional;

public interface HttpToolRepository {
    Optional<HttpTool> findById(long id);
    List<HttpTool> findAll();
    HttpTool save(HttpTool tool);
    void update(HttpTool tool);
    void deleteById(long id);
    boolean existsByName(String name);
}
