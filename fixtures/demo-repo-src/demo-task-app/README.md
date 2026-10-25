# demo-task-app

一个用于 DevPilot 功能演示与自动化测试的迷你 Java 项目。

## 重要说明

本目录中故意包含了会被 DevPilot 导入过滤器拦截的内容，用于验证安全过滤逻辑：

| 文件/目录 | 预期过滤行为 |
|---|---|
| `.env` | 记录入库并标记 sensitive（敏感文件） |
| `target/`、`vendor/` | 整目录跳过（构建/依赖目录黑名单） |
| `data/dump.bin` | 记录入库并标记 binary（NUL 字节二进制探测） |

除上述内容外，`src/` 与 `docs/` 下的 Java 与 Markdown 文件应全部正常入库并可检索。
`TaskService.completeTask` 中埋了一个空指针缺陷，专供 Bug 根因分析功能演示。
