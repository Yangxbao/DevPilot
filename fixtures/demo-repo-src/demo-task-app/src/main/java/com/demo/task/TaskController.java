package com.demo.task;

/**
 * 任务控制器：HTTP 层入口（演示用，非真实 Spring 组件）。
 */
public class TaskController {

    private final TaskService taskService = new TaskService();

    public String complete(String taskId, String operatorId) {
        Task task = taskService.completeTask(Long.valueOf(taskId), Long.valueOf(operatorId));
        return "task " + task.getId() + " -> " + task.getStatus();
    }
}
