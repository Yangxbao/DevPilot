<template>
  <div class="dp-page">
    <el-row :gutter="16">
      <el-col :xs="24" :md="9">
        <div class="dp-card">
          <h3 class="dp-block-title">提交 Bug 进行根因分析</h3>
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
            <el-form-item label="问题标题">
              <el-input v-model="form.title" maxlength="200" placeholder="如：完成任务时抛空指针" />
            </el-form-item>
            <el-form-item label="错误信息 *">
              <el-input v-model="form.errorMessage" type="textarea" :rows="3" maxlength="8000"
                placeholder="java.lang.NullPointerException: ..." />
            </el-form-item>
            <el-form-item label="异常堆栈">
              <el-input v-model="form.stackTrace" type="textarea" :rows="6" maxlength="8000"
                placeholder="at com.demo.TaskService.completeTask(TaskService.java:24)&#10;at ..." />
            </el-form-item>
            <el-form-item label="复现步骤">
              <el-input v-model="form.reproSteps" type="textarea" :rows="3" maxlength="2000" />
            </el-form-item>
            <el-button type="primary" style="width: 100%" :loading="analyzing" @click="analyze">
              {{ analyzing ? 'Agent 分析中…（最长 90 秒）' : '开始分析' }}
            </el-button>
          </el-form>
          <el-alert style="margin-top: 12px" type="info" :closable="false"
            title="系统只读分析、不会修改代码；结论区分证据与推测，并提供置信度。" />
        </div>
      </el-col>

      <el-col :xs="24" :md="15">
        <div class="dp-card" v-loading="analyzing">
          <template v-if="result">
            <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap">
              <ModeBadge :mode="result.mode" />
              <el-tag size="small" effect="plain">置信度 {{ result.confidence }}</el-tag>
              <el-tag size="small" effect="plain">耗时 {{ result.durationMs }}ms</el-tag>
              <div style="flex: 1"></div>
              <el-button type="primary" size="small" @click="convertToDefect" :loading="creatingDefect">
                转为缺陷任务
              </el-button>
            </div>
            <el-alert v-for="w in result.warnings" :key="w" type="warning" :title="w" :closable="false"
              style="margin-bottom: 8px" />

            <template v-if="result.report">
              <h4>问题摘要</h4>
              <div class="dp-answer">{{ result.report.summary }}</div>
              <h4>可能原因</h4>
              <ul class="dp-steps">
                <li v-for="(c, i) in result.report.possibleCauses" :key="i">{{ c }}</li>
              </ul>
              <h4>影响范围</h4>
              <p>{{ result.report.impact || '（需人工评估）' }}</p>
              <h4>建议验证方法</h4>
              <ul class="dp-steps">
                <li v-for="(v, i) in result.report.verificationSteps" :key="i">{{ v }}</li>
              </ul>
              <h4>修复方向</h4>
              <p>{{ result.report.fixDirections || '（见证据与可能原因）' }}</p>
            </template>
            <div v-else class="dp-answer">{{ result.answer }}</div>

            <h4 style="margin: 16px 0 8px">代码证据</h4>
            <EvidenceList :evidence="result.evidence" @view="openFile" />

            <h4 style="margin: 16px 0 8px">Agent 执行轨迹</h4>
            <ul class="dp-steps">
              <li v-for="s in result.steps" :key="s.name">
                <el-tag size="small" :type="s.status === 'SUCCESS' ? 'success' : s.status === 'FAILED' ? 'danger' : 'info'" effect="plain">
                  {{ s.status }}
                </el-tag>
                <b style="margin: 0 6px">{{ s.name }}</b>
                <span style="color: var(--dp-text-secondary)">{{ s.detail }} · {{ s.durationMs }}ms</span>
              </li>
            </ul>
          </template>
          <el-empty v-else-if="!analyzing" description="填写错误信息与堆栈后开始分析；报告将包含证据、验证方法与修复方向" />
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="previewVisible" :title="preview?.path" width="820">
      <div class="dp-code" style="max-height: 60vh">{{ preview?.content || '(无内容)' }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiAnalysis, apiBugAnalysis, apiCodeFile, apiCreateDefect, apiProjects, apiRepositories } from '../api'
import type { AnalysisVO, CodeFileVO, ProjectVO, RepositoryVO } from '../api/types'
import ModeBadge from '../components/ModeBadge.vue'
import EvidenceList from '../components/EvidenceList.vue'

const route = useRoute()
const router = useRouter()
const projects = ref<ProjectVO[]>([])
const repos = ref<RepositoryVO[]>([])
const projectId = ref<number>()
const repositoryId = ref<number>()
const analyzing = ref(false)
const creatingDefect = ref(false)
const result = ref<AnalysisVO | null>(null)
const previewVisible = ref(false)
const preview = ref<CodeFileVO | null>(null)

const form = reactive({ title: '', errorMessage: '', stackTrace: '', reproSteps: '' })

async function onProjectChange() {
  repositoryId.value = undefined
  if (projectId.value) {
    repos.value = (await apiRepositories(projectId.value, 1, 50)).data.data.list
    if (repos.value.length) repositoryId.value = repos.value[0].id
  }
}

async function analyze() {
  if (!projectId.value || !repositoryId.value || !form.title.trim() || !form.errorMessage.trim()) {
    ElMessage.warning('请选择项目与仓库，并填写标题和错误信息')
    return
  }
  analyzing.value = true
  try {
    result.value = (
      await apiBugAnalysis(projectId.value, {
        repositoryId: repositoryId.value,
        title: form.title.trim(),
        errorMessage: form.errorMessage,
        stackTrace: form.stackTrace || undefined,
        reproSteps: form.reproSteps || undefined,
      })
    ).data.data
  } finally {
    analyzing.value = false
  }
}

async function convertToDefect() {
  if (!result.value || !projectId.value) return
  creatingDefect.value = true
  try {
    const defect = (
      await apiCreateDefect(projectId.value, {
        title: form.title || 'Bug 分析结论',
        description: result.value.answer,
        severity: 'MAJOR',
        priority: 'P2',
        analysisId: result.value.id,
      })
    ).data.data
    ElMessage.success('已创建缺陷任务并关联本次分析')
    router.push({ name: 'defect-detail', params: { id: defect.id } })
  } finally {
    creatingDefect.value = false
  }
}

async function openFile(ev?: { fileId?: number }) {
  if (!ev?.fileId) return
  preview.value = (await apiCodeFile(ev.fileId)).data.data
  previewVisible.value = true
}

onMounted(async () => {
  projects.value = (await apiProjects(1, 50)).data.data.list
  const analysisId = route.query.analysisId
  if (analysisId) {
    result.value = (await apiAnalysis(Number(analysisId))).data.data
    projectId.value = result.value.projectId
    await onProjectChange()
    repositoryId.value = result.value.repositoryId
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
  padding: 12px 14px;
}
h4 {
  margin: 14px 0 6px;
}
.dp-block-title {
  margin: 0 0 12px;
}
</style>
