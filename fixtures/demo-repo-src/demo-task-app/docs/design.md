# demo-task-app 设计说明

## 模块结构

- `TaskController`：HTTP 入口，解析参数并调用服务层。
- `TaskService`：核心业务逻辑（创建任务、完成任务）。
- `TaskRepository`：内存仓储，任务不存在时 `findById` 返回 `null`。

## 已知问题

`TaskService.completeTask` 在任务不存在时会抛出 `NullPointerException`，
因为 `TaskRepository.findById` 的契约是"找不到返回 null"。

## 检索验证建议

- 中文关键词：`任务` / `登录` / `设计说明`
- 英文标识符：`completeTask` / `findById` / `TaskRepository`
