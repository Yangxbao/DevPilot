#!/usr/bin/env python3
"""DevPilot 演示数据种子脚本（幂等：重复执行会创建新的演示项目）。

前置：backend(8080) 与 ai-service(8200) 已启动。
效果：以 admin 身份创建「演示项目」，上传 fixtures/demo-repo.zip，完成索引，
     生成一次 AI 问答、一次 Bug 分析和 3 个不同状态的缺陷任务。
用法：python scripts/demo_seed.py
"""
import io
import sys
import time
from pathlib import Path

import httpx

BASE = Path(__file__).resolve().parents[1]
FIXTURE_ZIP = BASE / "fixtures" / "demo-repo.zip"
API = "http://127.0.0.1:8080/api/v1"

ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = "admin123456"  # 与本地启动引导一致，仅演示环境


def main() -> int:
    client = httpx.Client(base_url=API, timeout=120)
    resp = client.post("/auth/login", json={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD})
    resp.raise_for_status()
    token = resp.json()["data"]["accessToken"]
    headers = {"Authorization": f"Bearer {token}"}
    print("✔ admin 登录成功")

    stamp = time.strftime("%m%d-%H%M")
    project = client.post("/projects", json={"name": f"demo-task-app（演示 {stamp}）",
                                             "description": "用于 README 截图与功能演示的示例项目"},
                          headers=headers).json()["data"]
    print(f"✔ 创建项目 #{project['id']}")

    repo = client.post(f"/projects/{project['id']}/repositories/upload",
                       files={"file": ("demo-task-app.zip", io.BytesIO(FIXTURE_ZIP.read_bytes()), "application/zip")},
                       data={"name": "demo-task-app"}, headers=headers).json()["data"]
    print(f"✔ 上传仓库 #{repo['id']}")

    task = client.post(f"/repositories/{repo['id']}/index-tasks", headers=headers).json()["data"]
    for _ in range(60):
        time.sleep(1)
        t = client.get(f"/index-tasks/{task['id']}", headers=headers).json()["data"]
        if t["status"] in ("SUCCESS", "FAILED"):
            break
    print(f"✔ 索引任务 {t['taskNo']} -> {t['status']} (files={t.get('totalFiles')} chunks={t.get('totalChunks')})")
    if t["status"] != "SUCCESS":
        print("索引失败：", t.get("errorMessage"))
        return 1

    qa = client.post(f"/projects/{project['id']}/ai/qa",
                     json={"repositoryId": repo["id"],
                           "question": "完成任务的业务逻辑在哪个文件？这个实现有什么潜在问题？"},
                     headers=headers).json()["data"]
    print(f"✔ AI 问答 #{qa['id']} mode={qa.get('mode')} evidence={qa.get('evidenceCount')}")

    bug = client.post(f"/projects/{project['id']}/ai/bug-analysis",
                      json={"repositoryId": repo["id"], "title": "完成任务时抛空指针",
                            "errorMessage": "java.lang.NullPointerException: Cannot invoke Task.setStatus()",
                            "stackTrace": "java.lang.NullPointerException\n at com.demo.task.TaskService.completeTask(TaskService.java:24)\n at com.demo.task.TaskController.complete(TaskController.java:10)",
                            "reproSteps": "1. 调用 complete 接口传入不存在的任务ID 999"},
                      headers=headers).json()["data"]
    print(f"✔ Bug 分析 #{bug['id']} mode={bug.get('mode')}")

    d1 = client.post(f"/projects/{project['id']}/defects",
                     json={"title": "completeTask 空指针（未判空）", "description": "任务不存在时 NPE，需补判空与异常处理",
                           "severity": "MAJOR", "priority": "P1", "analysisId": bug["id"]},
                     headers=headers).json()["data"]
    client.post(f"/defects/{d1['id']}/status", json={"status": "IN_PROGRESS"}, headers=headers)
    client.post(f"/defects/{d1['id']}/comments", json={"content": "已定位 TaskService.java:24，修复方案：Optional 判空 + 业务异常"}, headers=headers)
    d2 = client.post(f"/projects/{project['id']}/defects",
                     json={"title": "任务检索接口缺少分页参数", "severity": "MINOR", "priority": "P3"},
                     headers=headers).json()["data"]
    d3 = client.post(f"/projects/{project['id']}/defects",
                     json={"title": "审计日志缺少操作人IP记录", "severity": "MEDIUM", "priority": "P2"},
                     headers=headers).json()["data"]
    for target in ("IN_PROGRESS", "RESOLVED"):
        client.post(f"/defects/{d3['id']}/status", json={"status": target}, headers=headers)
    print(f"✔ 缺陷任务 #{d1['id']}(处理中) #{d2['id']}(待处理) #{d3['id']}(已解决)")

    print(f"\n演示项目 ID: {project['id']} —— 可在前端打开该项目的详情页截图")
    return 0


if __name__ == "__main__":
    sys.exit(main())
