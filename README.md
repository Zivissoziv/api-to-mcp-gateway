# MCP Gateway

面向企业内部 HTTP API 的 MCP Tool 发布平台。企业无需改造原有 HTTP/REST API，即可通过配置将其转换、测试并发布为标准 MCP Tool，供 AI 应用和 MCP Client 使用。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | JDK 17, Spring Boot 3.5.x, Spring Security |
| 数据访问 | MyBatis-Plus 3.5.x |
| 数据库迁移 | Flyway（MySQL）/ SQL 初始化脚本（SQLite） |
| API 文档 | SpringDoc OpenAPI |
| MCP & AI | Spring AI 1.1.x |
| 前端 | Vue 3, TypeScript, Element Plus |
| 架构验证 | ArchUnit |
| 数据库 | MySQL（运行环境）/ SQLite（本地开发） |

## 项目结构

```text
mcp-gateway/
├─ backend/                          # Spring Boot 后端
│  ├─ src/main/java/com/example/mcpgateway/
│  │  ├─ McpGatewayApplication.java  # 入口
│  │  ├─ common/                     # 公共组件：ApiError、TraceIdFilter、GlobalExceptionHandler、配置
│  │  ├─ identity/                   # ✅ 身份认证：登录、JWT、用户管理、RBAC
│  │  ├─ system/                     # ✅ 系统监控：健康检查、状态
│  │  ├─ credential/                 # ⬜ 客户端凭证（待实现）
│  │  ├─ apitool/                    # ⬜ HTTP Tool 草稿（待实现）
│  │  ├─ mcpserver/                  # ⬜ Server 编组与发布（待实现）
│  │  ├─ executor/                   # ⬜ HTTP 执行器（待实现）
│  │  ├─ network/                    # ⬜ 网络白名单（待实现）
│  │  ├─ gateway/                    # ⬜ MCP 网关（待实现）
│  │  ├─ mcptest/                    # ⬜ MCP 协议测试（待实现）
│  │  ├─ aitest/                     # ⬜ AI 测试（待实现）
│  │  ├─ ratelimit/                  # 🔒 限流（第二阶段）
│  │  ├─ monitoring/                 # 🔒 监控（第二阶段）
│  │  └─ audit/                      # 🔒 审计（第二阶段）
│  ├─ src/main/resources/
│  │  ├─ application.yml             # 主配置（MySQL）
│  │  ├─ application-local.yml       # 本地配置（SQLite）
│  │  └─ db/                         # 数据库迁移脚本
│  └─ src/test/
├─ frontend/                         # Vue 3 管理控制台
│  └─ src/
│     ├─ api/                        # HTTP 请求层
│     ├─ router/                     # 路由与导航守卫
│     ├─ stores/                     # Pinia 状态管理
│     ├─ views/                      # 页面视图
│     └─ styles.css                  # 全局样式
├─ compose.yml                       # MySQL 容器编排
└─ docs/
   ├─ superpowers/                   # 产品文档
   │  ├─ specs/                      # 需求设计
   │  └─ plans/                      # 实施计划
   └─ development-standards.md       # 开发规范
```

## 当前完成状态（P0 基线）

| 里程碑 | 状态 | 关键交付物 |
|---|---|---|
| 工程基线 | ✅ 完成 | Spring Boot + Vue 骨架、双 Profile 数据库、统一异常处理 |
| 认证与 RBAC | ✅ 完成 | JWT 登录/刷新/退出、BCrypt、用户 CRUD、Security 权限控制 |
| HTTP Tool 管理 | ⬜ 未开始 | Tool 草稿 CRUD、参数映射、上游认证 |
| 在线测试 | ⬜ 未开始 | 草稿配置调用企业接口、脱敏结果展示 |
| 发布快照 | ⬜ 未开始 | 不可变版本、原子事务切换 |
| MCP 调用 | ⬜ 未开始 | Streamable HTTP 端点、凭证管理 |
| AI 测试 | ⬜ 未开始 | 大模型单 Tool 自然语言调用 |
| 闭环验收 | ⬜ 未开始 | 端到端自动化测试、安全测试 |

> 详细实施计划见 [docs/superpowers/plans/2026-06-21-api-to-mcp-mvp-plan.md](docs/superpowers/plans/2026-06-21-api-to-mcp-mvp-plan.md)

## 架构约束 — DDD 四层架构

每个业务模块按 DDD 经典四层组织，依赖方向**外向内**：

```text
controller/                 Interface 层：@RestController，仅做参数校验 + 一行调 Service
    ↓
application/service/        Application 层：用例编排、@Transactional、调 Repository 接口
    ↓
domain/                     Domain 层：纯 model/ + repository/ 接口（零框架注解）
    ↕ (implements)
infrastructure/             Infrastructure 层：MyBatis 实体、Mapper、Repository 实现、JWT/BCrypt
```

### 4 条核心规则

1. **Domain 层零框架注解**：`domain/model/` 和 `domain/repository/` 不允许 `import org.springframework.*` 或 `com.baomidou.*`
2. **跨模块通信走 Service，不走 Mapper**：模块 A 要访问模块 B 的数据，注入 B 的 Service 而非 Mapper
3. **Controller 只做两件事**：参数校验 + 一行调 Service
4. **Repository 接口属于 Domain 层**，实现在 Infrastructure 层

`ArchitectureTest` 会自动验证规则 1。详细规范见 [docs/development-standards.md](docs/development-standards.md)。

## 本地快速启动

### 后端快速测试（SQLite）

```powershell
cd backend
mvn test
```

### 本地 SQLite 运行

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### MySQL 运行

```powershell
docker compose up -d mysql
cd backend
mvn spring-boot:run
```

### 前端

```powershell
cd frontend
npm install
npm run dev          # 开发服务器
npm run test         # 前端测试
npm run build        # 生产构建
```

## 访问入口

| 入口 | 地址 |
|---|---|
| 后端 API | `http://localhost:8080` |
| 健康检查 | `http://localhost:8080/actuator/health` |
| 系统状态 | `http://localhost:8080/api/system/status` |
| API 文档 | `http://localhost:8080/swagger-ui.html` |
| 前端控制台 | `http://localhost:5173`（Vite 开发服务器）|

### 默认管理员账号

- 用户名：`admin`
- 密码：`Admin@123456`

可通过环境变量 `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` 自定义。

## 数据库说明

| 环境 | 数据库 | 迁移方式 | 配置 Profile |
|---|---|---|---|
| 运行环境 | MySQL | Flyway（`V1__`、`V2__`...） | `default` |
| 本地开发 | SQLite | `spring.sql.init` 执行 `schema.sql` | `local` |
| 测试 | SQLite（内存） | `spring.sql.init` 执行 `schema.sql` | `test` |

Docker 暂不可用时，可先执行 SQLite 快速测试。MySQL 与 Flyway 实机验证应在进入业务开发前或具备 Docker/MySQL 环境后补跑。

## API 概览

已实现端点：

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/api/auth/login` | 登录获取 JWT | 公开 |
| POST | `/api/auth/refresh` | 刷新 Access Token | 公开 |
| POST | `/api/auth/logout` | 退出登录（撤销 Refresh Token） | 已认证 |
| POST | `/api/auth/change-password` | 修改密码 | 已认证 |
| GET | `/api/users` | 用户列表 | SYSTEM_ADMIN |
| POST | `/api/users` | 创建用户 | SYSTEM_ADMIN |
| GET | `/api/users/{id}` | 用户详情 | SYSTEM_ADMIN |
| PUT | `/api/users/{id}` | 编辑用户 | SYSTEM_ADMIN |
| PATCH | `/api/users/{id}/status` | 启用/停用用户 | SYSTEM_ADMIN |
| GET | `/api/system/status` | 系统运行状态 | 公开 |

MCP 网关端点（待实现）：`/mcp/{serverCode}`

## 贡献指南

1. 新建特性分支，命名 `{type}/{description}`，如 `feat/http-tool-crud`
2. 按 [docs/development-standards.md](docs/development-standards.md) 的 DDD 四层规范编码
3. 每个里程碑完成后运行 `mvn test` 确保架构规则和集成测试通过
4. 前端开发前先完成后端接口和测试，再实现对应页面
5. 提交前确认敏感信息（密码、Token、API Key）未进入代码库

## 安全要求（开发时注意）

- 上游认证加密存储，查询 API 不回显明文
- 日志、异常、审计记录不得包含密码、Token、API Key
- 网络白名单、SSRF 防护是核心安全防线
- 普通管理员不得越权访问系统管理员专属接口
- JWT Secret 必须通过环境变量 `JWT_SECRET` 注入，不得硬编码
