# M4：HTTP Tool 在线测试（向导式注册）

> 2026-06-23
> 对应里程碑：M4 — HTTP Tool 在线测试
> 前置条件：M3（API Tool 配置管理）已完成

---

## 1. 概述

将接口注册从"填写 → 保存"改为**三步向导**：配置 → 测试 → 确认。用户在最后一步确认前必须通过在线测试，确保登记的接口真实可用。

---

## 2. 用户流程

```
[注册接口] → Step 1 基础配置 → [下一步]
    → Step 2 在线测试 → [执行测试]
        → 成功 (2xx) → [下一步]
        → 失败       → 修改参数 → [重新执行]
    → Step 3 确认 → [创建]
```

### Step 1：基础配置
复用 M3 已有的弹窗内容：
- 名称、描述、HTTP 方法、URL 模板（`${var}` 变量自动解析）
- 参数映射表（来源 PATH/QUERY/HEADER/BODY、类型、必填、描述、位置）
- Header 模板、Body JSON Schema（支持导入 JSON Schema 解析）
- Schema 预览
- 操作按钮：~~"保存"~~ → **"下一步"**

### Step 2：在线测试
- 根据 Step 1 的 `parameterMappings` 动态渲染输入表单：

  | 参数来源 | 渲染控件 |
  |----------|----------|
  | PATH     | 单行输入框 |
  | QUERY    | 单行输入框 |
  | HEADER   | 单行输入框 |
  | BODY     | JSON 文本编辑器 |

- 控件根据参数 `type` 选择对应的 Element Plus 组件：
  - `string` → `el-input`（text）
  - `integer` / `number` → `el-input-number`
  - `boolean` → `el-switch`
  - `string` + `enum` → `el-select`
- 必填参数标记 `*`，非必填参数可选留空
- 点击 **"执行测试"** → 调用 `POST /api/http-tools/test`
- 展示结果面板：

  ```
  ┌─ 请求摘要 ──────────────────────────┐
  │ GET https://api.example.com/users/1  │
  │ Headers: {Accept: application/json}  │
  └──────────────────────────────────────┘
  ┌─ 响应 (200 OK, 342ms) ─────────────┐
  │ {"id":1,"name":"Alice","email":...} │
  └──────────────────────────────────────┘
  ```

- 测试通过（HTTP 2xx）→ "下一步"按钮启用
- 测试失败 → 显示具体错误信息（超时/网络不通/参数校验失败/白名单拦截），可重新填写再执行

### Step 3：确认
- 展示工具完整配置摘要
- 标记 **"已通过在线测试"** 状态
- **"创建"** 按钮 → 调用 `POST /api/http-tools` 持久化
- **"上一步"** 可回到 Step 1/2 修改

### 导航规则
- 所有 Step 之间可以**自由切换**（"上一步"/"下一步"），不会丢失已填写的内容
- Step 2 → Step 3 的唯一条件是：**测试通过至少一次**（当前表单中有通过的测试结果）
- 测试通过后修改参数 → 测试状态重置为"未验证"，需重新执行才能到 Step 3

---

## 3. 后端接口

### POST /api/http-tools/test

**用途：** 接收内存中的工具配置 + 参数值，执行一次 HTTP 调用，返回结果。**不走数据库。**

```
Request:
{
  httpMethod: "GET" | "POST" | "PUT" | "PATCH" | "DELETE",
  urlTemplate: "https://api.example.com/users/{id}",
  headers: "Authorization: Bearer xxx\nAccept: application/json",
  parameterMappings: [
    { name: "id", paramSource: "PATH", paramLocation: "{id}", type: "string", required: true },
    { name: "fields", paramSource: "QUERY", type: "string", required: false }
  ],
  parameterValues: {
    "id": "123",
    "fields": "name,email"
  },
  authConfig?: { authType: "NONE" | "FIXED_HEADER" | "API_KEY" | "BEARER_TOKEN", configJson: "..." }
}

Response (200 — 测试成功):
{
  success: true,
  statusCode: 200,
  durationMs: 342,
  requestSummary: {
    method: "GET",
    url: "https://api.example.com/users/123",
    headers: { "Accept": "application/json" }   // 敏感值脱敏
  },
  responseSummary: {
    statusCode: 200,
    headers: { "content-type": "application/json", ... },  // 仅保留非敏感 Header
    body: "{\"id\":1,\"name\":\"Alice\"}",                 // 截断，最大 10KB 字符
    bodyTruncated: false
  }
}

Response (400 — 参数校验失败 / 白名单拦截):
{
  success: false,
  error: "INVALID_PARAMETER | NETWORK_DENIED | TIMEOUT | CONNECTION_FAILED",
  errorMessage: "域名 api.example.com 不在网络白名单中"
}
```

### 原有接口变更

- `POST /api/http-tools` — 无变更，Step 3 确认时调用
- `GET /api/http-tools/{id}/mappings` — 无变更

---

## 4. 后端模块结构

### 新建 `executor` 模块

```
com.example.mcpgateway.executor
├── HttpToolExecutor.java       — 核心执行器
├── ExecutionResult.java        — 返回值 record
└── ExecutorException.java      — 执行异常
```

**HttpToolExecutor 职责：**

```java
public class HttpToolExecutor {
    public ExecutionResult execute(HttpToolDefinition tool, Map<String, Object> params);
}
```

内部流程：
1. 校验参数 → 替换 URL 模板变量、拼接 Query String、注入 Header/Body
2. 校验目标 URL 是否在白名单内（通过 `NetworkAllowlistService`）
3. 注入上游认证（通过 `AuthConfig` → 从请求参数中读取 AuthConfig 进行处理）
4. 发起 HTTP 请求（RestTemplate / WebClient）
   - 连接超时 5s
   - 读取超时 10s
   - 最大重定向次数 5
   - 最大响应体 1MB（超过截断并标记）
5. 返回脱敏结果（Authorization / API Key 等敏感 Header 不返回）

**注意：** executor 模块不持有数据库依赖，所有输入通过参数传入。

### apitool 模块变更

**HttpToolController.java** — 新增端点：
- `POST /api/http-tools/test` — 接收测试请求，调用 service

**HttpToolService.java** — 新增方法：
- `testTool(TestToolRequest req)` — 白名单校验 → 执行 → 返回结果

---

## 5. 前端组件结构

### HttpToolsView.vue 改造

```diff
- 单一弹窗（填写 → 保存）
+ 步骤条弹窗（3 步）
```

**新增状态：**
- `currentStep: 1 | 2 | 3`
- `testPassed: boolean` — 记录是否测试通过
- `testResult: object | null` — 最近一次测试结果
- `parameterValues: Record<string, any>` — 用户在 Step 2 填的参数值

**新增组件：**
- `HttpToolTestPanel.vue` — Step 2 的测试面板（参数输入 + 执行 + 结果）
  - 接收 `parameterMappings` 和 `parameterValues` 作为 prop
  - 发射 `@test-result` 事件
- `HttpToolConfirmPanel.vue` — Step 3 的确认摘要

---

## 6. 安全与约束

| 约束 | 说明 |
|------|------|
| SSRF 防护 | 调用前校验目标域名/IP 是否在网络白名单内 |
| 响应体截断 | 超过 1MB / 10,000 字符截断，标记 `bodyTruncated: true` |
| 敏感 Header 脱敏 | Authorization、X-API-Key、Cookie 等不在响应摘要中回传 |
| 超时 | 连接 5s + 读取 10s，防止后端被慢请求阻塞 |
| 重定向限制 | 最多跟随 5 次重定向 |
| 数据不落盘 | 测试结果纯内存返回，不入库 |

---

## 7. 不包含的范围（YAGNI）

- 测试历史记录 / 测试日志存储
- 批量测试 / 定时测试
- 测试结果导出
- 测试参数模板保存
- 自动化对比（diff）测试
- 文件上传测试

---

## 8. 实现顺序

1. 后端：创建 `executor` 模块 — `HttpToolExecutor`、`ExecutionResult`
2. 后端：`HttpToolService.testTool()` — 白名单校验 + 执行编排
3. 后端：`HttpToolController` 新增 `POST /api/http-tools/test`
4. 前端：改造 HttpToolsView — 步骤条、Step 1 改"下一步"
5. 前端：实现 `HttpToolTestPanel` — 参数输入表单 + 执行 + 结果
6. 前端：Step 3 确认面板
7. E2E 测试：覆盖三步向导全流程
