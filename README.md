# MCP Gateway

面向企业内部 HTTP API 的 MCP Tool 发布平台。通过配置即可将 HTTP/REST API 转换、测试并发布为标准 MCP Tool，供 AI 应用和 MCP Client 调用。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | JDK 17, Spring Boot 3.5.x, Spring Security |
| 数据访问 | MyBatis-Plus 3.5.x |
| 数据库迁移 | Flyway (MySQL) / SQL 初始化脚本 (SQLite) |
| MCP & AI | Spring AI 1.1.x |
| 前端 | Vue 3, TypeScript, Element Plus |
| 数据库 | MySQL (运行环境) / SQLite (本地开发) |

## 项目结构

```
mcp-gateway/
├─ backend/           # Spring Boot 后端（DDD 四层架构）
├─ frontend/          # Vue 3 管理控制台
├─ compose.yml        # MySQL 容器编排
└─ docs/              # 产品文档与实施计划
```

## 功能

| 功能 | 状态 |
|---|---|
| 认证与 RBAC（JWT 登录/用户管理） | ✅ |
| HTTP API Tool 注册（向导式：配置→测试→确认） | ✅ |
| MCP Server 编组与发布（共享 MCP Key 认证） | ✅ |
| MCP 协议端点（Streamable HTTP, tools/list/call） | ✅ |
| 在线测试（参数输入/脱敏结果展示） | ✅ |
| AI 聊天测试（OpenAI 兼容模型 + 函数调用） | ✅ |
| 网络白名单（SSRF 防护） | ✅ |
| 发布快照 | ⬜ |
| AI 模型 API Key 加密存储 | ✅ |
| 调用记录（含客户端 IP 追溯） | ✅ |

## 本地启动

```powershell
# 后端 (SQLite)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 前端
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`，默认账号 `admin` / `Admin@123456`。

## API 入口

| 入口 | 地址 |
|---|---|
| 后端 API | `http://localhost:8080` |
| API 文档 | `http://localhost:8080/swagger-ui.html` |
| 前端 | `http://localhost:5173` |
| MCP 端点 | `POST /mcp/{serverCode}` |

> 详细实施计划见 [docs/superpowers/plans/2026-06-21-api-to-mcp-mvp-plan.md](docs/superpowers/plans/2026-06-21-api-to-mcp-mvp-plan.md)
