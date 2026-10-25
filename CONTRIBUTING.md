# 贡献指南

感谢关注 DevPilot！这是一个由仓库所有者在 AI 辅助下完成的学习型项目，欢迎任何形式的贡献。

## 开发环境

1. 依赖：JDK 21、Maven 3.9+、Python 3.11+、Node 22+、MySQL 8（或 Docker）。
2. 参考 [README](../README.md) 的「本地开发」章节启动三端。

## 提交规范

- 提交信息格式：`<type>: <描述>`，type 取 `feat / fix / docs / test / refactor / chore`。
- 一个 PR 聚焦一件事；涉及接口变更时必须同步修改 `docs/API_CONTRACT.md`。

## 测试要求

- 后端改动必须通过 `mvn test`（需要本地 MySQL，测试库 `devpilot_test`）。
- AI 服务改动必须通过 `python -m pytest tests/`。
- 前端改动必须通过 `npm run build` 与 `npm test`。
- 新功能请附带覆盖核心路径的测试；不允许为通过测试而关闭鉴权或校验逻辑。

## 安全底线

- 严禁提交任何真实密钥、密码、令牌；配置一律走环境变量（见 `.env.example`）。
- 不得引入"执行用户导入代码"类的能力。
- 发现安全漏洞请勿公开 Issue，先通过邮件联系仓库所有者。

## 已知边界

MVP 的功能边界与非目标见 [docs/PRD.md](docs/PRD.md)，请勿在 Issue 中重复要求 Non-Goals 中的能力（如自动改代码、多租户）。
