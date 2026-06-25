# API-to-MCP MVP 实施计划

## 1. 交付目标

第一阶段只交付一条可运行的核心链路：

> 管理员登录 → 注册企业 HTTP API Tool → 定义 Schema 和参数映射 → 在线测试 → 自由选择 Tool 组成 MCP Server → 发布 → MCP Client 通过 Streamable HTTP 调用 Tool。

已有 MCP Server 的注册和代理不在本阶段实现。

## 2. 实施原则

- 使用 JDK 17、Spring Boot 3.5.x、MyBatis-Plus 3.5.x、Flyway、Spring AI 1.1.x、SpringDoc、Vue 3、TypeScript、Element Plus，MySQL 和 SQLite。
- 后端采用模块化单体和 DDD 四层架构，前端采用单页管理应用。
- 优先完成纵向业务闭环，再补充次要管理页面。
- 只抽象当前确定存在的变化点，不预建微服务和通用编排引擎。
- 每个任务完成后运行对应测试并形成独立 Git 提交。

## 3. 项目目录

```text
mcp-gateway/
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/example/mcpgateway/
│     │  ├─ McpGatewayApplication.java
│     │  ├─ common/           # ApiError, TraceIdFilter, GlobalExceptionHandler, 配置
│     │  ├─ identity/         # 认证、JWT、用户管理、RBAC
│     │  ├─ system/           # 健康检查、系统状态
│     │  ├─ credential/       # 客户端凭证与 Server 授权 (待实现)
│     │  ├─ api-tool/         # HTTP Tool 草稿与参数映射 (待实现)
│     │  ├─ mcp-server/       # Server 草稿、编组、发布快照 (待实现)
│     │  ├─ http-executor/    # HTTP 请求构建与执行 (待实现)
│     │  ├─ network-policy/   # 网络白名单与 SSRF 防护 (待实现)
│     │  ├─ gateway/          # MCP 网关与协议适配 (待实现)
│     │  ├─ mcp-test/         # MCP 协议测试 (待实现)
│     │  ├─ ai-test/          # AI Tool 测试 (待实现)
│     │  ├─ ratelimit/        # 进程内限流 (第二阶段)
│     │  ├─ monitoring/       # 调用记录与监控 (第二阶段)
│     │  └─ audit/            # 审计日志 (第二阶段)
│     ├─ main/resources/
│     │  ├─ application.yml
│     │  ├─ application-local.yml
│     │  └─ db/
│     │     ├─ migration/common/
│     │     └─ sqlite/schema.sql
│     └─ test/
│        └─ java/com/example/mcpgateway/
│           ├─ ArchitectureTest.java
│           ├─ identity/
│           └─ ...
├─ frontend/
│  ├─ package.json
│  └─ src/
│     ├─ api/
│     ├─ router/
│     ├─ stores/
│     ├─ layouts/
│     └─ views/
├─ compose.yml
└─ docs/
   └─ superpowers/
      ├─ plans/
      └─ specs/
```

## 4. 里程碑

### M1：工程基线与数据库（已完成）

目标：后端、前端和数据库可以本地启动，并具备可重复的数据迁移。

已完成任务：

1. ✅ Spring Boot 工程，包含 Web、Validation、Security、MyBatis-Plus、MySQL、SQLite、Flyway、Spring AI、SpringDoc 和测试依赖。
2. ✅ Vue 3 + TypeScript + Element Plus 工程，含路由、API 层、Pinia Store 骨架。
3. ✅ 本地 SQLite profile（`application-local.yml`），替代原 compose.yml 方案的 MySQL-only 方式，降低本地启动门槛。
4. ✅ 统一响应（`ApiError`）、错误码、异常处理（`GlobalExceptionHandler`）和请求追踪 ID（`TraceIdFilter`）。
5. ✅ MySQL Flyway 迁移 + SQLite schema.sql 同步。
6. ✅ ArchUnit 架构验证规则（domain 无框架依赖、application 不依赖 controller）。
7. ✅ MyBatis-Plus 基础配置、分页插件。
8. ✅ SpringDoc OpenAPI 文档配置（`/api-docs`、`/swagger-ui.html`）。
9. ✅ 日志格式增加 `traceId` MDC 输出。
10. ✅ 系统状态端点（`/api/system/status`）已实现。

验收标志：

- ✅ 后端健康检查返回成功。
- ✅ 空数据库启动后自动完成迁移（MySQL 用 Flyway，SQLite 用 spring.sql.init）。
- ✅ MyBatis-Plus 可通过集成测试正确读写迁移后的表。
- ✅ 前端能够构建并访问登录页骨架。
- ✅ ArchUnit 架构规则通过。

### M2：认证、用户与最小 RBAC（已完成）

目标：系统管理员和普通管理员可以安全登录，并按固定角色访问管理功能。

已完成任务：

1. ✅ BCrypt 密码存储（`BcryptPasswordService`）。
2. ✅ Access Token（15 分钟）、Refresh Token（7 天）、退出和密码修改。
3. ✅ `SYSTEM_ADMIN`、`OPERATOR` 固定角色，`users` 表 `role` 字段直接存储。
4. ✅ 用户基础管理 API（创建、查询、编辑、启用/停用）。
5. ✅ 默认系统管理员初始化（`BootstrapAdminInitializer`）。
6. ✅ 前端登录页、路由守卫、用户管理页面骨架。
7. ✅ Spring Security 配置（`/api/users/**` 仅限 `SYSTEM_ADMIN`）。
8. ✅ JWT 认证过滤器（`JwtAuthenticationFilter`）。
9. ✅ 统一异常处理覆盖：无效凭证、无效刷新令牌、用户名已存在、用户未找到。

重点测试：

- ✅ 登录成功、失败、刷新和退出。
- ⬜ 停用用户后令牌立即不可用（注入型测试待补充）。
- ✅ 普通管理员无法访问用户管理 API（Security 配置保障）。

### M3：API Tool 草稿配置（当前阶段）

目标：管理员可以手工定义独立 HTTP API Tool，并自由编组到 MCP Server。

数据库迁移：

- `mcp_servers`
- `mcp_server_tools`
- `http_tools`
- `tool_parameter_mappings`
- `upstream_auth_configs`
- `network_allowlist`

核心模型：

- `VirtualServerDraft`
- `HttpToolDraft`
- `InputSchema`
- `ParameterMapping`
- `UpstreamAuth`
- `NetworkPolicy`

任务：

1. 实现独立 HTTP Tool 草稿的增删改查。
2. 实现 MCP Server 草稿的增删改查。
3. 实现 Tool 与 MCP Server 的多对多编组，一个 Tool 可加入多个 Server。
4. 支持 GET、POST、PUT、PATCH、DELETE。
5. 支持 Path、Query、Header 和 JSON Body 映射。
6. 支持无认证、固定 Header、API Key 和 Bearer Token。
7. 加密保存认证敏感值，查询时仅返回"已配置"状态。
8. 使用规范化 JSON Schema 作为后端唯一存储格式。
9. 提供可视化字段模型与 JSON Schema 的转换 API。
10. 实现平台级域名、IP、CIDR 白名单管理。
11. 完成 API 工具、Tool 编组和 MCP Server 的核心管理页面。

接口边界：

```java
interface UpstreamAuthProvider {
    void apply(UpstreamAuth auth, MutableHttpRequest request);
}

interface RequestMapper {
    MutableHttpRequest map(HttpToolDefinition tool, JsonNode arguments);
}

interface NetworkPolicy {
    void validate(URI target);
}
```

重点测试：

- JSON Schema 保存与校验。
- 四类参数映射。
- 四类认证配置。
- 敏感值不回显、不进入日志。
- 删除 Tool 时校验其 Server 引用关系。
- 同一个 Tool 可被多个 Server 正确复用。

### M4：HTTP Tool 在线测试

目标：管理员可以使用草稿配置真实调用企业 API，并看到脱敏结果。

任务：

1. 实现 `HttpToolExecutor`。
2. 按 JSON Schema 校验测试参数。
3. 通过 `RequestMapper` 构建最终请求。
4. 注入上游认证。
5. 调用前校验 URL、DNS 结果和网络白名单。
6. 限制连接超时、读取超时、重定向次数和响应体大小。
7. 根据成功状态码范围判断调用结果。
8. 返回脱敏请求摘要、响应、耗时和错误信息。
9. 前端提供参数输入和测试结果面板。

接口边界：

```java
interface ToolExecutor {
    ToolExecutionResult execute(ToolDefinition tool, JsonNode arguments);
}

interface ResponseMapper {
    ToolExecutionResult map(UpstreamResponse response);
}
```

重点测试：

- Path、Query、Header 和 Body 组合请求。
- JSON 和文本响应。
- 超时、非成功状态码、超大响应和非法 JSON。
- 重定向或 DNS 变化后仍执行网络策略校验。
- 测试结果中的认证信息被脱敏。

### M5：发布快照

目标：草稿可以原子发布，线上调用不受后续草稿编辑影响。

数据库迁移：

- `mcp_server_versions`
- `mcp_server_version_tools`
- `http_tool_versions`

任务：

1. 实现发布前完整校验。
2. 创建不可变的 Server 和 Tool 版本快照。
3. 在单个数据库事务内切换当前发布版本。
4. 发布失败时保留原版本。
5. 修改已发布配置时只修改草稿。
6. 前端增加配置检查、发布确认和当前版本展示。

接口边界：

```java
interface ReleaseService {
    ReleaseResult publish(long virtualServerId);
    PublishedServer loadCurrent(String serverCode);
}
```

重点测试：

- 发布快照不可变。
- 草稿修改不影响当前版本。
- 模拟发布失败后仍加载旧版本。
- 同一虚拟 Server 内 Tool 名称不可重复。

### M6：MCP 发布与调用

目标：MCP Client 可以发现并调用已发布的 HTTP Tool。

任务：

1. 建立网关地址 `/mcp/{serverCode}`。
2. 完成客户端访问密钥的创建、摘要存储、吊销和 Server 授权。
3. 使用 Spring AI MCP Server 能力实现动态 Tool 发布，并补充项目所需的协议适配层。
4. 提供 Streamable HTTP 端点。
5. `tools/list` 只读取当前发布版本中的启用 Tool。
6. `tools/call` 复用 `ToolExecutor` 执行 HTTP 请求。
7. 实现标准化 MCP 错误转换。
8. 记录调用时间、客户端 Key ID、Tool、结果、耗时、追踪 ID 和错误摘要。
9. 提供 MCP 连接信息和调用示例页面。
10. 使用 Spring AI MCP Client 实现发布后协议测试，覆盖 `initialize`、`tools/list` 和 `tools/call`。

数据库迁移：

- `client_credentials`
- `credential_servers`
- `gateway_calls`

接口边界：

```java
interface McpServerProvider {
    PublishedServer load(String serverCode);
}

interface CredentialVerifier {
    VerifiedClient verify(String presentedKey, long virtualServerId);
}
```

重点测试：

- `initialize`、`tools/list`、`tools/call` 协议交互。
- Streamable HTTP 连接与调用。
- 未发布、停用、无授权、过期和吊销凭证。
- Tool 参数校验错误和上游 HTTP 错误。
- MCP 调用与在线测试使用同一个执行器。
- Spring AI MCP Client 可以发现并调用刚发布的 Tool。

### M7：单 Tool AI 测试

目标：用户可以用自然语言验证当前 Tool 是否能被模型正确理解和调用。

数据库迁移：

- `ai_model_configs`

任务：

1. 实现平台级 OpenAI 兼容模型配置。
2. 支持 Base URL、API Key、Model 和请求超时。
3. API Key 加密保存，查询时不回显。
4. 使用 Spring AI ChatClient 创建测试会话。
5. 每次测试只向模型注册当前选中的一个 Tool。
6. 将模型产生的 Tool 参数交给现有 `ToolExecutor`。
7. 展示模型回答、Tool 参数、脱敏执行结果、耗时和错误。
8. 默认不保存完整提示词、模型回答和企业 API 响应正文。
9. 当前端或模型服务不可用时返回可诊断但不泄密的错误。

接口边界：

```java
interface AiToolTester {
    AiTestResult test(PublishedTool tool, String userPrompt);
}
```

重点测试：

- OpenAI 兼容 Base URL 和模型配置生效。
- 模型只能看到当前 Tool。
- 模型无法调用其他 Tool 或跨 Server 调用。
- Tool 参数仍经过 JSON Schema 和网络策略校验。
- 模型 API Key 和企业 API 凭证不进入日志。

### M8：核心闭环验收

目标：形成第一版可演示、可回归的产品。

端到端场景：

1. 系统管理员配置允许访问的企业 API 地址范围。
2. 普通管理员登录并注册多个 HTTP Tool。
3. 配置上游认证并在线测试成功。
4. 创建 MCP Server，并自由选择 Tool 加入。
5. 将同一个 Tool 加入另一个 MCP Server，验证可复用。
6. 发布 MCP Server。
7. 创建并授权客户端访问密钥。
8. MCP Client 发现并调用 Tool。
9. 使用 Spring AI MCP Client 完成发布后协议测试。
10. 输入自然语言，让 OpenAI 兼容模型调用当前 Tool。
11. 控制台查看调用记录和失败摘要。

完成条件：

- 核心场景的自动化端到端测试通过。
- 角色越权和客户端凭证越权测试通过。
- 敏感信息扫描没有发现密码、Token 或 API Key 泄漏。
- 项目 README 可以指导新开发者在本地启动并完成一次调用。

## 5. 第二阶段：核心治理

第一阶段通过后再实现：

- 客户端级和虚拟 Server 级进程内限流
- 登录失败限流
- 健康状态、请求量、成功率、平均延迟和 P95
- 完整登录及配置变更审计
- 发布历史、版本对比和一键回滚
- 客户端密钥轮换过渡期
- 完整前端仪表盘
- 更系统的 SSRF、DNS 重绑定和重定向安全测试

## 6. 第三阶段：已有 MCP Server 代理

以 `McpServerProvider` 的独立实现接入已有 Streamable HTTP Server，复用凭证、限流、监控和审计。

该模块不得复用或修改 `HttpToolDefinition`、`ToolExecutor` 和发布快照模型。代理型 Server 与虚拟 Server 只在统一网关入口和治理能力处汇合。

## 7. 暂不实现

- OpenAPI 导入
- 自定义角色和工具级授权
- JSONPath、响应模板和脚本
- 多 API 编排
- 表单和文件上传
- Redis、消息队列和微服务
- Prometheus、链路追踪和告警
- stdio MCP Server

## 8. 推荐执行顺序

严格按 M1 → M8 顺序推进。当前已完成 M1 和 M2（P0 基线），从 M3 开始继续。

每个里程碑都先完成后端测试和接口，再完成对应前端页面，最后运行该里程碑的集成测试。

不建议按"先写完全部后端、再写前端"的方式推进，因为在线测试和发布流程需要持续验证端到端交互。

## 附录 A：当前状态总览

| 里程碑 | 状态 | 说明 |
|--------|------|------|
| M1 工程基线 | ✅ 已完成 | 后端骨架、前端骨架、双 profile 数据库、统一异常、追踪 ID、ArchUnit |
| M2 认证与 RBAC | ✅ 已完成 | JWT 登录/刷新/退出、用户 CRUD、BCrypt、固定角色、Security 配置 |
| M3 API Tool 草稿 | ⬜ 待实现 | 无代码改动 |
| M4 在线测试 | ⬜ 待实现 | 无代码改动 |
| M5 发布快照 | ⬜ 待实现 | 无代码改动 |
| M6 MCP 发布与调用 | ⬜ 待实现 | 无代码改动 |
| M7 AI 测试 | ⬜ 待实现 | 无代码改动 |
| M8 闭环验收 | ⬜ 待实现 | 无代码改动 |

## 附录 B：后端模块物化清单

| 模块 | 包路径 | 状态 |
|------|--------|------|
| identity | `com.example.mcpgateway.identity` | ✅ 已落地 |
| system | `com.example.mcpgateway.system` | ✅ 已落地 |
| common | `com.example.mcpgateway.common` | ✅ 已落地 |
| credential | `com.example.mcpgateway.credential` | ⬜ 待创建 |
| api-tool | `com.example.mcpgateway.apitool` | ⬜ 待创建 |
| mcp-server | `com.example.mcpgateway.mcpserver` | ⬜ 待创建 |
| http-executor | `com.example.mcpgateway.executor` | ⬜ 待创建 |
| network-policy | `com.example.mcpgateway.network` | ⬜ 待创建 |
| gateway | `com.example.mcpgateway.gateway` | ⬜ 待创建 |
| mcp-test | `com.example.mcpgateway.mcptest` | ⬜ 待创建 |
| ai-test | `com.example.mcpgateway.aitest` | ⬜ 待创建 |
| ratelimit | `com.example.mcpgateway.ratelimit` | ⬜ 第二阶段 |
| monitoring | `com.example.mcpgateway.monitoring` | ⬜ 第二阶段 |
| audit | `com.example.mcpgateway.audit` | ⬜ 第二阶段 |

## 附录 C：关键配置与端口

| 项目 | 值 |
|------|-----|
| 后端端口 | 8080（`SERVER_PORT` 可配置）|
| 数据库（生产） | MySQL `mcp_gateway` |
| 数据库（本地） | SQLite `./data/mcp-gateway-local.db` |
| JWT Secret | `JWT_SECRET` 环境变量 |
| Access Token 有效期 | 15 分钟 |
| Refresh Token 有效期 | 7 天 |
| Bootstrap 管理员 | `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` |
| API 文档 | `http://localhost:8080/swagger-ui.html` |
| 健康检查 | `http://localhost:8080/actuator/health` |
