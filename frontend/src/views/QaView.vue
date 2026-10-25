<template>
  <div class="dp-page">
    <el-row :gutter="16">
      <el-col :xs="24" :md="9">
        <div class="dp-card">
          <h3 class="dp-block-title">向代码库提问</h3>
          <el-form label-position="top">
            <el-form-item label="项目">
              <el-select v-model="projectId" style="width: 100%" @change="onProjectChange">
                <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="代码仓库">
              <el-select v-model="repositoryId" style="width: 100%">
                <el-option v-for="r in repos" :key="r.id" :label="r.name" :value="r.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="问题">
              <el-input v-model="question" type="textarea" :rows="5" maxlength="2000" show-word-limit
                placeholder="例如：完成任务的业务逻辑在哪个文件？有没有潜在空指针风险？" data-testid="qa-question" />
            </el-form-item>
            <el-button type="primary" style="width: 100%" :loading="asking" @click="ask" data-testid="qa-submit">
              {{ asking ? 'AI 分析中…（最长 90 秒）' : '提交问题' }}
            </el-button>
          </el-form>
          <el-alert style="margin-top: 12px" type="info" :closable="false"
            title="回答基于真实检索证据，并附可点击核对的文件路径与行号；证据不足时系统会如实说明。" />
        </div>
      </el-col>

      <el-col :xs="24" :md="15">
        <div class="dp-card" v-loading="asking">
          <template v-if="answer">
            <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 10px">
              <ModeBadge :mode="answer.mode" />
              <el-tag size="small" effect="plain">置信度 {{ answer.confidence }}</el-tag>
              <el-tag size="small" effect="plain">耗时 {{ answer.durationMs }}ms</el-tag>
              <el-tag size="small" effect="plain">{{ answer.provider }} / {{ answer.model }}</el-tag>
            </div>
            <el-alert v-for="w in answer.warnings" :key="w" type="warning" :title="w" :closable="false"
              style="margin-bottom: 8px" />
            <div class="dp-answer">{{ answer.answer }}</div>

            <h4 style="margin: 16px 0 8px">证据（可核对）</h4>
            <EvidenceList :evidence="answer.evidence" @view="openFile" />

            <h4 style="margin: 16px 0 8px">Agent 执行轨迹</h4>
            <ul class="dp-steps">
              <li v-for="s in answer.steps" :key="s.name">
                <el-tag size="small" :type="s.status === 'SUCCESS' ? 'success' : s.status === 'FAILED' ? 'danger' : 'info'" effect="plain">
                  {{ s.status }}
                </el-tag>
                <b style="margin: 0 6px">{{ s.name }}</b>
                <span style="color: var(--dp-text-secondary)">{{ s.detail }} · {{ s.durationMs }}ms</span>
              </li>
            </ul>
          </template>
          <el-empty v-else-if="!asking" description="提交问题后，这里会展示带证据引用的回答与执行轨迹" />
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="previewVisible" :title="preview?.path" width="820">
      <div class="dp-code" style="max-height: 60vh">{{ preview?.content || '(无内容)' }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiAnalysis, apiCodeFile, apiProjects, apiQa, apiRepositories } from '../api'
import type { AnalysisVO, CodeFileVO, ProjectVO, RepositoryVO } from '../api/types'
import ModeBadge from '../components/ModeBadge.vue'
import EvidenceList from '../components/EvidenceList.vue'

const route = useRoute()
const projects = ref<ProjectVO[]>([])
const repos = ref<RepositoryVO[]>([])
const projectId = ref<number>()
const repositoryId = ref<number>()
const question = ref('')
const asking = ref(false)
const answer = ref<AnalysisVO | null>(null)
const previewVisible = ref(false)
const preview = ref<CodeFileVO | null>(null)

async function onProjectChange() {
  repositoryId.value = undefined
  if (projectId.value) {
    repos.value = (await apiRepositories(projectId.value, 1, 50)).data.data.list
    if (repos.value.length) repositoryId.value = repos.value[0].id
  }
}

async function ask() {
  if (!projectId.value || !repositoryId.value || !question.value.trim()) {
    ElMessage.warning('请选择项目、仓库并输入问题')
    return
  }
  asking.value = true
  try {
    answer.value = (await apiQa(projectId.value, repositoryId.value, question.value.trim())).data.data
  } finally {
    asking.value = false
  }
}

async function openFile(ev?: { fileId?: number }) {
  if (!ev?.fileId) return
  preview.value = (await apiCodeFile(ev.fileId)).data.data
  previewVisible.value = true
}

onMounted(async () => {
  projects.value = (await apiProjects(1, 50)).data.data.list
  // 支持从项目详情的分析记录跳转回看
  const analysisId = route.query.analysisId
  if (analysisId) {
    answer.value = (await apiAnalysis(Number(analysisId))).data.data
    projectId.value = answer.value.projectId
    await onProjectChange()
    repositoryId.value = answer.value.repositoryId
  } else if (projects.value.length) {
    projectId.value = projects.value[0].id
    await onProjectChange()
  }
})
</script>

<style scoped>
.dp-answer {
  white-space: pre-wrap;
  line-height: 1.8;
  background: #fafbff;
  border: 1px solid var(--dp-border);
  border-radius: 8px;
  padding: 14px 16px;
}
.dp-block-title {
  margin: 0 0 12px;
}
</style>
