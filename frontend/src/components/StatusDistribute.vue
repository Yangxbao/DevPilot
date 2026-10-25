<template>
  <div>
    <el-progress v-if="!entries.length" :percentage="0" :show-text="false" style="height: 0" />
    <div v-for="entry in entries" :key="entry.label" class="dp-dist-row">
      <span class="dp-dist-label">{{ entry.label }}</span>
      <el-progress :percentage="entry.percent" :color="entry.color" :stroke-width="12" />
      <span class="dp-dist-num">{{ entry.count }}</span>
    </div>
    <el-empty v-if="!entries.length" description="暂无数据" :image-size="50" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ map: Record<string, number>; labels?: Record<string, string> }>()

const COLORS: Record<string, string> = {
  SUCCESS: '#52c41a', FAILED: '#f5222d', RUNNING: '#faad14', PENDING: '#8c8c8c', PROCESSING: '#faad14',
  OPEN: '#2f54eb', IN_PROGRESS: '#faad14', RESOLVED: '#52c41a', CLOSED: '#8c8c8c',
  QA: '#2f54eb', BUG_ANALYSIS: '#722ed1', real: '#52c41a', mock: '#faad14',
}

const entries = computed(() => {
  const total = Object.values(props.map).reduce((a, b) => a + b, 0)
  return Object.entries(props.map).map(([key, count]) => ({
    label: props.labels?.[key] || key,
    count,
    percent: total ? Math.round((count / total) * 100) : 0,
    color: COLORS[key] || '#2f54eb',
  }))
})
</script>

<style scoped>
.dp-dist-row {
  display: grid;
  grid-template-columns: 72px 1fr 40px;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.dp-dist-label {
  font-size: 13px;
  color: var(--dp-text-secondary);
}
.dp-dist-num {
  text-align: right;
  font-weight: 600;
}
</style>
