<template>
  <el-tag :type="type" size="small" effect="plain">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ value: string; kind: 'import' | 'task' | 'defect' | 'step' }>()

const MAPS: Record<string, Record<string, { label: string; type: string }>> = {
  import: {
    PENDING: { label: '待导入', type: 'info' },
    PROCESSING: { label: '处理中', type: 'warning' },
    SUCCESS: { label: '成功', type: 'success' },
    FAILED: { label: '失败', type: 'danger' },
  },
  task: {
    PENDING: { label: '排队中', type: 'info' },
    RUNNING: { label: '执行中', type: 'warning' },
    SUCCESS: { label: '成功', type: 'success' },
    FAILED: { label: '失败', type: 'danger' },
  },
  defect: {
    OPEN: { label: '待处理', type: 'info' },
    IN_PROGRESS: { label: '处理中', type: 'warning' },
    RESOLVED: { label: '已解决', type: 'success' },
    CLOSED: { label: '已关闭', type: 'info' },
  },
  step: {
    RUNNING: { label: '执行中', type: 'warning' },
    SUCCESS: { label: '成功', type: 'success' },
    FAILED: { label: '失败', type: 'danger' },
    SKIPPED: { label: '跳过', type: 'info' },
  },
}

const mapping = computed(() => MAPS[props.kind]?.[props.value] || { label: props.value, type: 'info' })
const type = computed(() => mapping.value.type as 'info' | 'warning' | 'success' | 'danger')
const label = computed(() => mapping.value.label)
</script>
