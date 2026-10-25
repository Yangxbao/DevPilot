#!/usr/bin/env python3
"""DevPilot 端到端业务流程验证脚本。

前置条件：backend(8080)、ai-service(8200) 已启动，MySQL 可用。
覆盖链路：注册/登录 -> 创建项目 -> 上传演示仓库 -> 触发索引 -> 关键词/语义检索
        -> AI 问答(mock) -> Bug 分析 -> 创建缺陷 -> 状态流转 -> 仪表盘。

用法：python scripts/e2e_flow.py [--base-url http://127.0.0.1:8080]
"""
import argparse
import io
import sys
import time
import zipfile
from pathlib import Path

import httpx

BASE = Path(__file__).resolve().parents[1]
FIXTURE_ZIP = BASE / "fixtures" / "demo-repo.zip"

PASSED = 0
FAILED = 0


def step(name: str, ok: bool, detail: str = "") -> None:
    global PASSED, FAILED
    mark = "PASS" if ok else "FAIL"
    if ok:
        PASSED += 1
    else:
        FAILED += 1
    print(f"[{mark}] {name}" + (f"  -> {detail}" if detail else ""))


def require(ok: bool, message: str) -> None:
    if not ok:
        print(f"致命错误：{message}")
        sys.exit(2)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    client = httpx.Client(base_url=args.base_url, timeout=120)

    stamp = str(int(time.time()))

    # 1) 注册独立用户（避免演示数据互相污染）
    username = f"e2e_{stamp}"
    password = "E2ePassw0rd"
    resp = client.post("/api/v1/auth/register", json={
        "username": username, "password": password, "nickname": "E2E机器人"})
    step("注册新用户", resp.status_code == 200 and resp.json()["code"] == 0)

    # 2) 登录
    resp = client.post("/api/v1/auth/login", json={"username": username, "password": password})
    body = resp.json()
    step("登录获取 JWT", body.get("code") == 0, f"user={body.get('data', {}).get('user', {}).get('username')}")
    token = body["data"]["accessToken"]
    headers = {"Authorization": f"Bearer {token}"}

    # 3) 越权防护：未认证访问应 401
    resp = client.get("/api/v1/projects")
    step("未认证访问被拒绝(401)", resp.status_code == 401)

    # 4) 创建项目
    resp = client.post("/api/v1/projects", json={"name": f"e2e-project-{stamp}", "description": "E2E 自动验证项目"},
                       headers=headers)
    step("创建项目", resp.json().get("code") == 0)
    project_id = resp.json()["data"]["id"]

    # 5) 上传演示压缩包
    require(FIXTURE_ZIP.exists(), f"缺少演示压缩包 {FIXTURE_ZIP}，请先执行 fixtures/build_fixture.py")
    zip_bytes = FIXTURE_ZIP.read_bytes()
    resp = client.post(f"/api/v1/projects/{project_id}/repositories/upload",
                       files={"file": ("demo-task-app.zip", io.BytesIO(zip_bytes), "application/zip")},
                       data={"name": "demo-task-app"}, headers=headers)
    step("上传 zip 代码包", resp.json().get("code") == 0, f"repoId={resp.json().get('data', {}).get('id')}")
    repo_id = resp.json()["data"]["id"]

    # 6) 触发索引任务并轮询
    resp = client.post(f"/api/v1/repositories/{repo_id}/index-tasks", headers=headers)
    step("触发索引任务", resp.json().get("code") == 0)
    task_id = resp.json()["data"]["id"]
    status = "PENDING"
    for _ in range(60):
        time.sleep(1)
        task = client.get(f"/api/v1/index-tasks/{task_id}", headers=headers).json()["data"]
        status = task["status"]
        if status in ("SUCCESS", "FAILED"):
            break
    step("索引任务完成", status == "SUCCESS",
         f"files={task.get('totalFiles')} chunks={task.get('totalChunks')}")

    # 7) 关键词检索（英文标识符）
    resp = client.get("/api/v1/search", params={"repositoryId": repo_id, "q": "completeTask", "mode": "keyword"},
                      headers=headers)
    hits = resp.json()["data"]["results"]
    step("关键词检索 completeTask", bool(hits) and any("TaskService.java" in h["path"] for h in hits),
         f"hits={len(hits)} first={hits[0]['path'] if hits else '-'}")

    # 8) 中文关键词检索
    resp = client.get("/api/v1/search", params={"repositoryId": repo_id, "q": "任务", "mode": "keyword"},
                      headers=headers)
    step("中文关键词检索 任务", bool(resp.json()["data"]["results"]))

    # 9) 语义/混合检索（经 Python AI 服务）
    resp = client.get("/api/v1/search", params={"repositoryId": repo_id, "q": "完成任务时崩溃", "mode": "hybrid"},
                      headers=headers)
    body = resp.json()
    step("混合检索(语义通道)", body.get("code") == 0 and bool(body["data"]["results"]),
         f"vectorEnabled={body.get('data', {}).get('vectorEnabled')}")

    # 10) AI 问答（Mock 模式，结果必须带证据）
    resp = client.post(f"/api/v1/projects/{project_id}/ai/qa",
                       json={"repositoryId": repo_id, "question": "完成任务的业务逻辑在哪个文件？可能有什么问题？"},
                       headers=headers)
    body = resp.json()
    data = body.get("data") or {}
    step("AI 问答", body.get("code") == 0 and data.get("status") == "SUCCESS" and data.get("evidenceCount", 0) > 0,
         f"mode={data.get('mode')} evidence={data.get('evidenceCount')} confidence={data.get('confidence')}"
         + ("" if body.get("code") == 0 else f" err={body.get('message')}"))
    analysis_id = data.get("id")

    # 11) Bug 分析
    resp = client.post(f"/api/v1/projects/{project_id}/ai/bug-analysis",
                       json={"repositoryId": repo_id, "title": "完成任务时抛空指针",
                             "errorMessage": "java.lang.NullPointerException: Cannot invoke Task.setStatus()",
                             "stackTrace": ("java.lang.NullPointerException\n"
                                            " at com.demo.task.TaskService.completeTask(TaskService.java:24)\n"
                                            " at com.demo.task.TaskController.complete(TaskController.java:10)"),
                             "reproSteps": "1. 调用 complete 接口传入不存在的任务ID 999"},
                       headers=headers)
    body = resp.json()
    data = body.get("data") or {}
    step("Bug 根因分析", body.get("code") == 0 and data.get("status") == "SUCCESS",
         f"mode={data.get('mode')} report={'有' if data.get('report') else '无'}"
         + ("" if body.get("code") == 0 else f" err={body.get('message')}"))
    bug_analysis_id = data.get("id")

    # 12) 分析详情必须含执行轨迹
    resp = client.get(f"/api/v1/ai/analyses/{analysis_id}", headers=headers)
    steps = resp.json()["data"].get("steps") or []
    step("Agent 执行轨迹留痕", len(steps) >= 5, f"steps={[s['name'] for s in steps]}")

    # 13) 创建缺陷并关联分析
    resp = client.post(f"/api/v1/projects/{project_id}/defects",
                       json={"title": "completeTask 空指针", "description": "任务不存在时 NPE",
                             "severity": "MAJOR", "priority": "P1", "analysisId": bug_analysis_id},
                       headers=headers)
    step("创建缺陷(关联AI分析)", resp.json().get("code") == 0)
    defect_id = resp.json()["data"]["id"]

    # 14) 状态流转：OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED
    ok_flow = True
    for target in ("IN_PROGRESS", "RESOLVED", "CLOSED"):
        resp = client.post(f"/api/v1/defects/{defect_id}/status", json={"status": target}, headers=headers)
        ok_flow = ok_flow and resp.json().get("code") == 0
    step("缺陷状态流转 3 步", ok_flow)

    # 15) 非法流转被拒绝：CLOSED -> OPEN 应 409
    resp = client.post(f"/api/v1/defects/{defect_id}/status", json={"status": "OPEN"}, headers=headers)
    step("非法状态流转被拒绝(409)", resp.status_code == 409)

    # 16) 数据隔离：另一个用户无法访问本项目
    client.post("/api/v1/auth/register", json={"username": f"e2e_b_{stamp}", "password": password, "nickname": "B"})
    token_b = client.post("/api/v1/auth/login", json={"username": f"e2e_b_{stamp}", "password": password}).json()["data"]["accessToken"]
    resp = client.get(f"/api/v1/projects/{project_id}", headers={"Authorization": f"Bearer {token_b}"})
    step("越权访问他人项目被拒绝(403)", resp.status_code == 403)

    # 17) 仪表盘（真实统计）
    resp = client.get("/api/v1/stats/dashboard", headers=headers)
    stats = resp.json()["data"]
    step("仪表盘真实统计", stats.get("projectCount", 0) >= 1 and stats.get("defectTotal", 0) >= 1,
         f"projects={stats.get('projectCount')} defects={stats.get('defectTotal')} analyses={stats.get('analysisTotal')}")

    print(f"\n结果：PASS={PASSED} FAIL={FAILED}")
    return 0 if FAILED == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
