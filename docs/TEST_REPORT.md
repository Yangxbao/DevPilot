# DevPilot 测试与验收报告

> 生成时间：2026-10-25 · 环境：Windows 11（10.0.26200 x64）· JDK Temurin 21.0.12.1 · Maven 3.9.4 · Python 3.11.9 · Node 24.21（npm 11.19）· MySQL 8.0.34（独立实例，端口 3307）
> 原则：只记录实际执行的命令与真实结果；未执行或未通过的明确标注。

## 1. 测试结果总览

| 套件 | 命令 | 结果 | 说明 |
|---|---|---|---|
| 后端单元+集成 | `mvn test`（backend/） | **20 通过 / 0 失败 / 0 跳过** | 真实 MySQL（devpilot_test@3307）+ MockMvc 全链路；AI 客户端以 Mockito 打桩 |
| AI 服务 | `python -m pytest tests/ -q`（ai-service/） | **31 通过 / 0 失败** | 纯本地：FTS5/向量/融合/注入/校验/工作流/API |
| 前端构建 | `npx vue-tsc --noEmit && npx vite build` | **通过** | 严格模式类型检查 + 生产构建 |
| 前端单测 | `npx vitest run` | **3 通过 / 0 失败** | auth store（mock 网络层） |
| 端到端业务流 | `python scripts/e2e_flow.py` | **18 通过 / 0 失败** | 打真实运行栈（8080+8200），见第 3 节逐项 |
| 浏览器人工验收 | ZCode 内置浏览器 | **7 页通过** | 登录/仪表盘/项目详情/检索/问答/Bug 分析/缺陷详情，截图存 `docs/screenshots/` |

## 2. 后端测试明细（mvn test）

| 测试类 | 用例数 | 覆盖点 |
|---|---|---|
| AuthPermissionIntegrationTest | 7 | 注册→登录→me 链路；错误口令拒绝；伪造 Token 401；普通用户访问管理员接口 403；**越权读他人项目 403**；管理员可跨域访问；**登出后 Token 立即失效** |
| ChunkerTest | 6 | 语言识别；分块行号连续性；分块上限；NUL 字节二进制嗅探；zip 条目路径穿越/依赖目录拒绝；敏感文件名识别 |
| DefectFlowIntegrationTest | 5 | 合法流转 3 步；非法流转（OPEN→CLOSED）409；**越权流转他人缺陷 403**；处理记录回读 |
| RepositoryIndexSearchIntegrationTest | 2 | 上传 zip→索引 SUCCESS→关键词检索命中；`.env` 标记 sensitive、node_modules 整目录过滤；**重复触发索引 409** |

执行方式：`JAVA_HOME=<JDK21> mvn test`；数据库为独立实例 `127.0.0.1:3307/devpilot_test`，Flyway 自动建表。测试中发现并修复的缺陷：
1. `Chunker.chunk` 对以换行结尾的文本行号虚高 1（尾行空条目），已修复并补测试；
2. 分页泛型、`QueryWrapper` 类型、Redis ping 返回类型等编译期问题（详见提交历史）。

## 3. E2E 业务流程逐项（scripts/e2e_flow.py，最终轮）

```
[PASS] 注册新用户 / 登录获取 JWT / 未认证访问被拒绝(401)
[PASS] 创建项目 / 上传 zip 代码包
[PASS] 触发索引任务 / 索引任务完成        files=7 chunks=6
[PASS] 关键词检索 completeTask            hits=5 first=.../TaskService.java
[PASS] 中文关键词检索 任务
[PASS] 混合检索(语义通道)                 vectorEnabled=True
[PASS] AI 问答                            mode=mock evidence=6 confidence=0.5
[PASS] Bug 根因分析                       mode=mock report=有
[PASS] Agent 执行轨迹留痕                 steps=[validate_input, plan_retrieval,
        retrieve, assemble_evidence, generate, validate_output]
[PASS] 创建缺陷(关联AI分析) / 缺陷状态流转 3 步
[PASS] 非法状态流转被拒绝(409)
[PASS] 越权访问他人项目被拒绝(403)
[PASS] 仪表盘真实统计
结果：PASS=18 FAIL=0
```

## 4. 浏览器人工验收

真实浏览器（1280×720）逐页操作验证，过程中发现并修复 1 个缺陷：检索按钮把 `MouseEvent` 误传为 `page` 参数导致后端 500（前端 `@click="doSearch"` 缺括号；同时在后端为参数类型不匹配补了 400 映射）。修复后全部页面正常，截图：

| # | 页面 | 验证要点 |
|---|---|---|
| 01 | 登录 | 表单渲染、演示账号提示 |
| 02 | 仪表盘 | 真实统计数字、状态分布、空态样式 |
| 03 | 项目详情 | 仓库导入状态/索引时间/操作入口 |
| 04 | AI 问答 | Mock 徽标、证据路径+行号+相关度、Agent 轨迹 |
| 05 | Bug 分析 | 结构化报告（摘要/原因/影响/验证/修复方向）+ 置信度 |
| 06 | 缺陷详情 | 状态流转按钮、处理记录、关联分析入口 |
| 07 | 代码检索 | 命中数、向量通道标识、路径/行号/score、查看代码 |

## 5. 未验证 / 未覆盖事项（诚实清单）

| 项 | 状态 | 原因 |
|---|---|---|
| Docker Compose 一键启动 | ❌ 未在本机执行 | 作者环境未安装 Docker；compose 配置已通过语法编写与人工核对，CI 中有 `docker compose config -q` 校验 |
| 真实大模型 Provider（openai） | ❌ 未接入真实 Key | 无可用凭据；mock 路径全部验证，OpenAI 路径经单测以打桩方式覆盖 |
| Redis 真实连接 | ❌ 未在本机运行 | 本机无 Redis；走的是进程内降级实现（有日志标注），Redis 实现类逻辑相同、接口级测试覆盖 |
| 混合检索的语义质量 | ⚠️ 仅功能性验证 | 默认 embedding 为特征哈希降级实现，语义效果有限（UI 有明确标识），真实 embedding API 未验证 |
| GitHub Actions | ❌ 未实际触发 | 仓库尚未推送；workflow 语法按官方 schema 编写 |
| 移动端宽度 | ⚠️ 基础响应式已实现 | 已按栅格与折叠侧栏实现，未做逐机型视觉回归 |
| 性能指标 | ⚠️ 未压测 | 演示规模（千级分块）下操作流畅；未做基准测试，不做任何性能声明 |

## 6. 安全自查

- 密钥扫描：对全部追踪文件执行模式扫描（`sk-`、`ghp_`、`AKIA`、私钥块），仅命中 `ai-service/tests/test_guard.py` 中的**故意构造的假密钥**（已改为拼接生成避免误报）；
- Git 历史：`git log --all --diff-filter=A -- "**/.env" "**/*.pem" "**/*.key"` 无记录；
- `.env` 已入 `.gitignore`（仅演示工程的虚构 `.env` 经白名单入库，内容为占位符并在 README 中说明）；
- 管理员默认密码 `admin123456` 仅用于本地演示，启动时日志显著告警，`.env.example` 要求生产覆盖；
- 用户导入代码全程只读处理：zip 不落盘解压、路径穿越拒绝、流式限额读取、依赖目录/敏感文件过滤、Git 仅 https + SSRF 防护、`GIT_TERMINAL_PROMPT=0`。

## 7. 复现指南

```bash
# 1) MySQL：任意 8.0 实例建两个库 devpilot / devpilot_test
# 2) 后端测试
cd backend && TEST_DB_USERNAME=root TEST_DB_PASSWORD=<你的密码> mvn test
# 3) AI 服务测试
cd ai-service && pip install -r requirements.txt && python -m pytest tests/ -q
# 4) 前端
cd frontend && npm ci && npm run build && npm test
# 5) 三端启动后跑 E2E
python scripts/e2e_flow.py
```
