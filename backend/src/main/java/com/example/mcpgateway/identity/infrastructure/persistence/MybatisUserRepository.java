package com.example.mcpgateway.identity.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mcpgateway.identity.domain.model.User;
import com.example.mcpgateway.identity.domain.model.UserRole;
import com.example.mcpgateway.identity.domain.model.UserStatus;
import com.example.mcpgateway.identity.domain.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisUserRepository implements UserRepository {
    private final UserMapper mapper;
    public MybatisUserRepository(UserMapper mapper) { this.mapper = mapper; }

    @Override public Optional<User> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::domain);
    }
    @Override public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<UserRow>()
                .eq(UserRow::getUsername, username))).map(this::domain);
    }
    @Override public List<User> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<UserRow>().orderByAsc(UserRow::getUsername))
                .stream().map(this::domain).toList();
    }
    @Override public User save(User user) {
        UserRow row = row(user);
        mapper.insert(row);
        return domain(row);
    }
    @Override public void updateStatus(String id, String status) {
        mapper.update(new LambdaUpdateWrapper<UserRow>().eq(UserRow::getId, id)
                .set(UserRow::getStatus, status).set(UserRow::getUpdatedAt, LocalDateTime.now()));
    }
    @Override public long count() { return mapper.selectCount(null); }

    private User domain(UserRow row) {
        return new User(row.id, row.username, row.passwordHash, UserRole.valueOf(row.role),
                UserStatus.valueOf(row.status), row.createdAt.toInstant(ZoneOffset.UTC),
                row.updatedAt.toInstant(ZoneOffset.UTC));
    }
    private UserRow row(User user) {
        UserRow row = new UserRow();
        row.id = user.id(); row.username = user.username(); row.passwordHash = user.passwordHash();
        row.role = user.role().name(); row.status = user.status().name();
        row.createdAt = LocalDateTime.ofInstant(user.createdAt(), ZoneOffset.UTC);
        row.updatedAt = LocalDateTime.ofInstant(user.updatedAt(), ZoneOffset.UTC);
        return row;
    }
}
