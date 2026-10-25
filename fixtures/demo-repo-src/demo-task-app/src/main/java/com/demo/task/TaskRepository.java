package com.demo.task;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版任务仓储。
 */
public class TaskRepository {

    private final Map<Long, Task> store = new ConcurrentHashMap<>();

    private final AtomicLong idGenerator = new AtomicLong(1);

    public Task save(Task task) {
        if (task.getId() == null) {
            task.setId(idGenerator.getAndIncrement());
        }
        store.put(task.getId(), task);
        return task;
    }

    /**
     * 注意：任务不存在时返回 null，而不是抛出异常。
     * 调用方（TaskService.completeTask）必须自行判空。
     */
    public Task findById(Long taskId) {
        return store.get(taskId);
    }
}
