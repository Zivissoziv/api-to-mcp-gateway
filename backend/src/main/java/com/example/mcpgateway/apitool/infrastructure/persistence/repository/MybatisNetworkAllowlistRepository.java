package com.example.mcpgateway.apitool.infrastructure.persistence.repository;

import com.example.mcpgateway.apitool.domain.model.NetworkAllowlist;
import com.example.mcpgateway.apitool.domain.model.PatternType;
import com.example.mcpgateway.apitool.domain.repository.NetworkAllowlistRepository;
import com.example.mcpgateway.apitool.infrastructure.persistence.mapper.NetworkAllowlistMapper;
import com.example.mcpgateway.apitool.infrastructure.persistence.row.NetworkAllowlistRow;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisNetworkAllowlistRepository implements NetworkAllowlistRepository {
    private final NetworkAllowlistMapper mapper;
    public MybatisNetworkAllowlistRepository(NetworkAllowlistMapper mapper) { this.mapper = mapper; }

    @Override public List<NetworkAllowlist> findAll() {
        return mapper.selectList(null).stream().map(this::domain).toList();
    }
    @Override public Optional<NetworkAllowlist> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::domain);
    }
    @Override public NetworkAllowlist save(NetworkAllowlist entry) {
        NetworkAllowlistRow row = row(entry);
        mapper.insert(row);
        NetworkAllowlistRow saved = mapper.selectById(row.id);
        return domain(saved);
    }
    @Override public void deleteById(long id) { mapper.deleteById(id); }

    private NetworkAllowlist domain(NetworkAllowlistRow r) {
        return new NetworkAllowlist(r.id, r.pattern, PatternType.valueOf(r.patternType),
                r.description, r.enabled, r.createdBy, r.createdAt.toInstant(ZoneOffset.UTC));
    }
    private NetworkAllowlistRow row(NetworkAllowlist e) {
        NetworkAllowlistRow r = new NetworkAllowlistRow();
        r.pattern = e.pattern(); r.patternType = e.patternType().name();
        r.description = e.description(); r.enabled = e.enabled(); r.createdBy = e.createdBy();
        return r;
    }
}
