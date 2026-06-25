# 后端开发规范

## 1. DDD 四层架构

每个业务模块按以下 4 层组织：

```
┌──────────────────────────────────────────┐
│  controller/         Interface 层         │
│  @RestController, 参数校验, DTO record    │
├──────────────────────────────────────────┤
│  application/service/  Application 层     │
│  @Service, @Transactional, 用例编排       │
├──────────────────────────────────────────┤
│  domain/               Domain 层          │
│  model/    纯 record/enum，零框架注解     │
│  repository/  Repository 接口             │
├──────────────────────────────────────────┤
│  infrastructure/      Infrastructure 层   │
│  persistence/  Mapper, Row, Repository 实现│
│  security/     JWT/BCrypt 实现            │
└──────────────────────────────────────────┘
```

### 依赖方向

```
controller  →  application/service  →  domain/repository
                                          ↓ (implements)
                                    infrastructure/
```

- Controller **只调** Application Service，**不调** Domain 或 Infrastructure
- Application Service **调** Domain 的 Repository 接口
- Infrastructure **实现** Domain 的 Repository 接口

---

## 2. 包命名规范

```
com.example.mcpgateway.{
    common,         // 公共组件（api 格式、全局异常、配置）
    identity,       // 身份认证模块
    system,         // 系统监控模块
    新业务模块       // 每个模块同级展开
}
```

每个模块内部固定包：

| 包 | 职责 | 允许的注解 |
|---|---|---|
| `controller/` | HTTP 接口 | `@RestController`, `@RequestMapping` |
| `application/service/` | 业务逻辑 | `@Service`, `@Transactional` |
| `domain/model/` | 纯领域模型 | **无** `org.springframework`, `com.baomidou` |
| `domain/repository/` | Repository 接口 | **无** `org.springframework`, `com.baomidou` |
| `infrastructure/persistence/` | 数据访问 | `@TableName`, `@TableId`, `@Mapper`, `@Repository` |
| `infrastructure/security/` | JWT/BCrypt 等安全实现 | `@Component` |
- **例外**：`identity/security/` — JWT Filter 独立为包（Spring Security 过滤器，不属于四层中的任何一层，按功能归类）

---

## 3. 各层代码规则

### 3.1 Controller 层

```java
@RestController
@RequestMapping("/api/xxx")
public class XxxController {
    private final XxxService service;
    // 构造器注入

    @GetMapping
    Result list() {
        return service.list();           // 一行调用，不做业务处理
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Result create(@Valid @RequestBody CreateRequest request) {
        return service.create(request);  // 参数校验由 @Valid 完成
    }
}
```

规则：
- 方法体**只做**：参数校验（`@Valid`）+ 一行调 Service
- Controller **不处理**：数据转换、权限判断、事务控制
- DTO record 定义在 Controller 内（如 `LoginRequest`, `CreateRequest`）

### 3.2 Application Service 层

```java
@Service
public class XxxService {
    private final XxxRepository repo;
    // 构造器注入

    @Transactional
    public Result create(CreateRequest request) {
        // 1. 业务校验
        // 2. 调 Repository 接口
        // 3. 返回结果
    }
}
```

规则：
- 依赖 Repository **接口**（domain 层），非 Mapper 或 Row
- 方法上标注 `@Transactional`
- 抛自定义异常（`RuntimeException` 子类），由 `GlobalExceptionHandler` 统一捕获

### 3.3 Domain 层

```java
// domain/model/ — 纯 record，零框架注解
public record User(Long id, String username, UserRole role, UserStatus status) {
    public boolean isActive() { return status == UserStatus.ACTIVE; }
}

// domain/repository/ — 纯接口，零框架注解
public interface UserRepository {
    Optional<User> findById(long id);
    User save(User user);
}
```

规则：
- **禁止** import `org.springframework.*` 或 `com.baomidou.*`
- 业务行为方法写在 `model` 上（如 `isActive()`）
- Repository 接口定义在 domain 层，实现在 infrastructure 层

### 3.4 Infrastructure 层

```java
// infrastructure/persistence/ — MyBatis 映射
@TableName("users")
public class UserRow { @TableId public Long id; ... }

@Mapper
public interface UserMapper extends BaseMapper<UserRow> {}

@Repository
public class MybatisUserRepository implements UserRepository {
    // User → UserRow 转换逻辑写在这里
}
```

规则：
- Infrastructure **负责**：框架注解、DB 映射、DTO 转换
- `UserRow` 和 `UserMapper` 不对外暴露，外界只通过 `UserRepository` 接口访问

---

## 4. 模块间通信

| 场景 | 方式 | 示例 |
|---|---|---|
| 同进程跨模块 | 注入目标模块的 Service | `@Autowired IdentityService` |
| 未来拆微服务 | Feign 接口（预留 `client/` 包） | `identity/client/UserClient.java` |

**禁止**：模块 A 直接注入模块 B 的 Mapper 或 Row 类。
**禁止**：模块间循环依赖。

---

## 5. 异常处理

- 业务异常：业务模块内定义为 `RuntimeException` 子类，在 `GlobalExceptionHandler` 中统一处理
- `GlobalExceptionHandler` 在 `common/api/` 下，负责所有模块的异常映射

---

## 6. 测试规范

```
src/test/java/com/example/mcpgateway/
├── identity/
│   └── service/AuthenticationServiceTest.java   // Mock Repository 的单元测试
├── common/...
```

- **单元测试** mock Repository 接口，不启动 Spring
- **集成测试** 用 `@SpringBootTest` + `MockMvc`，走真实 DB（SQLite test profile）

---

## 7. ArchitectureTest 验证

项目通过 ArchUnit 自动验证：

| 规则 | 检测内容 |
|---|---|
| `domainMustNotDependOnFrameworkOrInfrastructure` | domain 包不能引用 Spring、MyBatis、infrastructure |
| `applicationMustNotDependOnInboundAdapters` | application 包不能反向依赖 controller |

新增模块时只需确保包名在 `..domain..` / `..application..` 规则下即可自动验证。
