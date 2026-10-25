# DevPilot API 契约

> 前端与 Java 后端、Java 后端与 Python AI 服务之间的**唯一接口依据**。任何一侧修改接口必须先改本文档。

## 0. 通用约定

- 基础路径：`/api/v1`；认证方式：`Authorization: Bearer <JWT>`（除 auth 接口与公开端点）。
- 统一响应结构：
  ```json
  { "code": 0, "message": "ok", "data": { } }
  ```
  `code=0` 成功；非 0 失败（HTTP 状态码与 code 同时表达）。主要错误码：

  | code | 含义 |
  |---|---|
  | 0 | 成功 |
  | 40001 | 参数校验失败（data 中含字段错误明细） |
  | 40100 | 未认证 / Token 无效或过期 |
  | 40300 | 无权限（含越权访问他人数据） |
  | 40400 | 资源不存在 |
  | 40900 | 状态冲突（重名/重复任务/非法状态流转） |
  | 42900 | 登录尝试过于频繁 |
  | 50000 | 系统内部错误 |
  | 50200 | 上游 AI 服务不可用或失败 |

- 分页请求：`?page=1&size=10`（1 起始）；分页响应：`{ "list": [...], "total": 123, "page": 1, "size": 10 }`。
- 时间格式：`yyyy-MM-dd HH:mm:ss`（ISO 风格 `yyyy-MM-dd'T'HH:mm:ss` 亦可，序列化统一为前者）。

## 1. 对前端 API（节选，字段以 springdoc Swagger 为准）

### 1.1 认证
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 公开 | `{username,nickname,password}`；密码≥8位含字母数字 |
| POST | `/api/v1/auth/login` | 公开 | 返回 `{accessToken, expiresIn, user{id,username,nickname,role}}`；连续失败 5 次锁定 10 分钟 |
| POST | `/api/v1/auth/logout` | 登录 | Token 进黑名单 |
| GET | `/api/v1/users/me` | 登录 | 当前用户信息 |

### 1.2 用户管理（仅 ADMIN）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/admin/users?page&size&keyword` | 用户分页列表 |
| POST | `/api/v1/admin/users` | 创建用户（可指定角色） |
| PATCH | `/api/v1/admin/users/{id}/status` | 启用/禁用 |

### 1.3 项目与仓库
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/projects?page&size&keyword` | 我的项目（ADMIN 看全部） |
| POST | `/api/v1/projects` | `{name,description}` |
| GET | `/api/v1/projects/{id}` | 详情（含统计） |
| DELETE | `/api/v1/projects/{id}` | 软删除 |
| POST | `/api/v1/projects/{id}/repositories/upload` | multipart `file`(.zip)+`name`；≤100MB |
| POST | `/api/v1/projects/{id}/repositories/git` | `{name,gitUrl,gitRef}`；仅 https 公开仓库 |
| GET | `/api/v1/projects/{id}/repositories` | 仓库列表（含导入状态与错误原因） |
| GET | `/api/v1/repositories/{id}/files?page&size&path` | 文件清单（含 skip_reason） |
| GET | `/api/v1/code-files/{id}` | 文件内容预览 |
| DELETE | `/api/v1/repositories/{id}` | 删除仓库并清理索引 |

### 1.4 索引与检索
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/repositories/{id}/index-tasks` | 触发(重新)索引；重复触发返回 409 |
| GET | `/api/v1/repositories/{id}/index-tasks` | 任务历史 |
| GET | `/api/v1/index-tasks/{id}` | 任务详情（进度轮询） |
| GET | `/api/v1/search?projectId&q&mode&page&size` | mode=keyword(默认)/semantic/hybrid；返回 `{results:[{fileId,repositoryId,path,language,startLine,endLine,snippet,score,channel}],vectorEnabled,total}` |

### 1.5 AI 分析
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/projects/{id}/ai/qa` | `{repositoryId,question}` → `{analysisId,...}`（同步，≤90s） |
| POST | `/api/v1/projects/{id}/ai/bug-analysis` | `{repositoryId,title,errorMessage,stackTrace,reproSteps}` |
| GET | `/api/v1/projects/{id}/ai/analyses?type&page&size` | 分析记录列表 |
| GET | `/api/v1/ai/analyses/{id}` | 记录详情（含 steps 轨迹与 evidence） |
| POST | `/api/v1/ai/analyses/{id}/link-defect` | `{defectId}` 关联到缺陷（校验同项目） |

响应关键字段：`mode`(real/mock)、`confidence`(0~1)、`evidence[]`（path/startLine/endLine/snippet）、`steps[]`（Agent 轨迹）、`warnings[]`（注入嫌疑/引用修正/降级说明）。

### 1.6 缺陷
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/projects/{id}/defects?page&size&status&severity&keyword` | 分页筛选 |
| POST | `/api/v1/projects/{id}/defects` | `{title,description,severity,priority,assigneeId}` |
| GET | `/api/v1/defects/{id}` | 详情（含评论与关联分析） |
| PATCH | `/api/v1/defects/{id}` | 更新标题/描述/指派/优先级 |
| POST | `/api/v1/defects/{id}/status` | `{status}`；非法流转 409，附 `allowedNextStatuses` |
| POST | `/api/v1/defects/{id}/comments` | `{content}` 处理记录 |

状态机：`OPEN → IN_PROGRESS → RESOLVED → CLOSED`；`RESOLVED → OPEN`（退回）；`IN_PROGRESS → OPEN`；CLOSED 为终态。允许操作人：报告人/负责人/项目管理员(项目 owner 或 ADMIN)。

### 1.7 仪表盘
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/stats/dashboard` | `{projectCount,repositoryCount,repositoryStatus{...},indexTaskStats,defectStatus{...},analysisCount,analysisByType}` —— ADMIN 全局 / USER 本人数据 |

### 1.8 运维端点（公开）
`GET /actuator/health`（Java）、`GET /health`（Python，返回 `{status, llmProvider, embeddingProvider, vectorReady}`）、Swagger：`/swagger-ui.html`。

## 2. Java ↔ Python 内部 API（Header: `X-Internal-Token`）

### 2.1 索引推送
`POST /internal/reindex`
```json
{ "repositoryId": 1, "chunks": [ { "id": "1-3", "fileId": 10, "path": "src/main/java/Demo.java", "language": "java", "startLine": 1, "endLine": 60, "content": "..." } ] }
```
→ `{ "repositoryId": 1, "indexed": 500, "vectorEnabled": true }`（embedding 失败时 `vectorEnabled=false` 且仅建立 FTS5——**明确降级**）
`DELETE /internal/index/{repositoryId}` 清理该仓库索引。

### 2.2 检索
`POST /internal/search` `{ "repositoryId":1, "query":"...", "topK":8, "mode":"hybrid" }`
→ `{ "vectorEnabled":true, "results":[{ "chunkId":"1-3","path":"...","language":"java","startLine":10,"endLine":25,"snippet":"...","score":0.83,"channel":"vector|keyword|both" }] }`

### 2.3 Agent
`POST /agent/qa` `{ "repositoryId":1, "question":"...", "topK":8 }`
→ `{ "mode":"mock", "provider":"mock", "model":"mock-v1", "answer":"...", "confidence":0.55, "evidence":[...], "steps":[{ "name":"retrieve","status":"SUCCESS","durationMs":120,"detail":"4 hits" }], "warnings":[], "durationMs":1500 }`

`POST /agent/bug-analysis` `{ "repositoryId":1, "title":"...","errorMessage":"...","stackTrace":"...","reproSteps":"..." }`
→ 同上，`report` 字段为 `{summary, possible_causes[], impact, verification_steps[], fix_directions, confidence}`（结构化输出经 Pydantic 校验 + 证据引用核查后返回）。

`GET /health` → `{ "status":"UP", "llmProvider":"mock", "embeddingProvider":"hashing", "vectorReady":true }`

### 2.4 调用方约束
Java 侧超时：检索类 15s、Agent 类 150s；Python 侧 LLM 单次调用超时 60s、重试 2 次；两侧行为均以本文档为准，超限按对应错误码返回。
