package com.example.mcpgateway.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mcpgateway.identity.domain.repository.RefreshTokenRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class MybatisRefreshTokenRepository implements RefreshTokenRepository {
    private final RefreshTokenMapper mapper;
    public MybatisRefreshTokenRepository(RefreshTokenMapper mapper) { this.mapper = mapper; }
    @Override public void save(long id, String userId, String tokenHash, Instant expiresAt) {
        RefreshTokenRow row = new RefreshTokenRow();
        row.id=id; row.userId=userId; row.tokenHash=tokenHash;
        row.expiresAt=LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
        row.revoked=false; row.createdAt=LocalDateTime.now(ZoneOffset.UTC);
        mapper.insert(row);
    }
    @Override public Optional<String> findActiveUserId(String tokenHash, Instant now) {
        RefreshTokenRow row=mapper.selectOne(new LambdaQueryWrapper<RefreshTokenRow>()
                .eq(RefreshTokenRow::getTokenHash,tokenHash).eq(RefreshTokenRow::getRevoked,false)
                .gt(RefreshTokenRow::getExpiresAt,LocalDateTime.ofInstant(now,ZoneOffset.UTC)));
        return Optional.ofNullable(row).map(RefreshTokenRow::getUserId);
    }
    @Override public void revoke(String tokenHash) {
        mapper.update(new LambdaUpdateWrapper<RefreshTokenRow>().eq(RefreshTokenRow::getTokenHash,tokenHash)
                .set(RefreshTokenRow::getRevoked,true));
    }
    @Override public void revokeAllForUser(String userId) {
        mapper.update(new LambdaUpdateWrapper<RefreshTokenRow>().eq(RefreshTokenRow::getUserId,userId)
                .set(RefreshTokenRow::getRevoked,true));
    }
}
