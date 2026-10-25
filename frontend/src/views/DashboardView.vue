<template>
  <div class="dp-page">
    <h2 class="dp-page-title">仪表盘</h2>
    <p class="dp-page-sub">
      全部数字来自数据库实时统计 · 管理员可见全局，普通用户仅见本人数据 · 生成时间 {{ stats?.generatedAt || '-' }}
    </p>

    <el-row :gutter="16" v-if="stats">
      <el-col :xs="12" :sm="6" v-for="card in summaryCards" :key="card.label">
        <div class="dp-card dp-stat">
          <div class="dp-stat-num">{{ card.value }}</div>
          <div class="dp-stat-label">{{ card.label }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px" v-if="stats">
      <el-col :xs="24" :sm="12">
        <div class="dp-card">
          <h3 class="dp-block-title">仓库导入状态</h3>
          <StatusDistribute :map="stats.repositoryByStatus" :labels="importLabels" />
        </div>
      </el-col>
      <el-col :xs="24" :sm="12">
        <div class="dp-card">
          <h3 class="dp-block-title">缺陷状态分布</h3>
          <StatusDistribute :map="stats.defectByStatus" :labels="defectLabels" />
        </div>
      </el-col>
      <el-col :xs="24" :sm="12">
        <div class="dp-card">
          <h3 class="dp-block-title">索引任务</h3>
          <StatusDistribute :map="stats.indexTaskByStatus" :labels="taskLabels" />
        </div>
      </el-col>
      <el-col :xs="24" :sm="12">
        <div class="dp-card">
          <h3 class="dp-block-title">AI 分析</h3>
          <StatusDistribute :map="stats.analysisByType" :labels="analysisLabels" />
          <p style="color: var(--dp-text-secondary); font-size: 12px">
            模式分布：真实 {{ stats.analysisByMode['real'] || 0 }} · 演示 {{ stats.analysisByMode['mock'] || 0 }}
          </p>
        </div>
      </el-col>
    </el-row>

    <el-empty v-if="loaded && !stats" description="暂无统计数据" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { apiDashboard } from '../api'
import type { DashboardStats } from '../api/types'
import StatusDistribute from '../components/StatusDistribute.vue'

const stats = ref<DashboardStats | null>(null)
const loaded = ref(false)

const summaryCards = computed(() => [
  { label: '项目', value: stats.value?.projectCount ?? 0 },
  { label: '代码仓库', value: stats.value?.repositoryCount ?? 0 },
  { label: '缺陷', value: stats.value?.defectTotal ?? 0 },
  { label: 'AI 分析', value: stats.value?.analysisTotal ?? 0 },
])

const importLabels: Record<string, string> = { PENDING: '待导入', PROCESSING: '处理中', SUCCESS: '成功', FAILED: '失败' }
const taskLabels: Record<string, string> = { PENDING: '排队中', RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败' }
const defectLabels: Record<string, string> = { OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }
const analysisLabels: Record<string, string> = { QA: '代码问答', BUG_ANALYSIS: 'Bug 分析' }

onMounted(async () => {
  try {
    stats.value = (await apiDashboard()).data.data
  } finally {
    loaded.value = true
  }
})
</script>

<style scoped>
.dp-stat {
  text-align: center;
  padding: 18px;
}
.dp-stat-num {
  font-size: 28px;
  font-weight: 700;
  color: var(--dp-primary);
}
.dp-stat-label {
  color: var(--dp-text-secondary);
  margin-top: 4px;
}
.dp-block-title {
  margin: 0 0 12px;
  font-size: 14px;
}
</style>
