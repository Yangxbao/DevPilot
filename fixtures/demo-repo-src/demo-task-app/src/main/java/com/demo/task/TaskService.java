package com.demo.task;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务服务：演示项目中的核心业务类。
 * 已知缺陷：completeTask 未做判空，任务不存在时会抛 NullPointerException。
 */
public class TaskService {

    private final TaskRepository taskRepository = new TaskRepository();

    private final Map<Long, String> auditLog = new HashMap<>();

    public Task createTask(String title, Long ownerId) {
        Task task = new Task();
        task.setTitle(title);
        task.setOwnerId(ownerId);
        task.setStatus("OPEN");
        return taskRepository.save(task);
    }

    public Task completeTask(Long taskId, Long operatorId) {
        Task task = taskRepository.findById(taskId);
        // BUG: findById 可能返回 null，这里直接解引用会抛 NullPointerException
        task.setStatus("DONE");
        auditLog.put(taskId, "completed by " + operatorId);
        return taskRepository.save(task);
    }

    public Task getTask(Long taskId) {
        return taskRepository.findById(taskId);
    }
}
