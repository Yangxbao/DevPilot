<template>
  <div class="dp-page">
    <div class="dp-toolbar">
      <el-button @click="$router.push({ name: 'projects' })">← 返回列表</el-button>
      <h2 class="dp-page-title" style="margin: 0">{{ project?.name || '...' }}</h2>
      <span class="dp-page-sub" style="margin: 0">{{ project?.description }}</span>
    </div>

    <el-tabs v-model="tab">
      <!-- ============ 仓库与索引 ============ -->
      <el-tab-pane label="代码仓库与索引" name="repos">
        <div class="dp-toolbar">
          <el-button type="primary" @click="uploadVisible = true">上传 zip 压缩包</el-button>
          <el-button @click="gitVisible = true">导入 Git 仓库</el-button>
        </div>
        <el-table :data="repos" v-loading="repoLoading">
          <el-table-column prop="id" label="ID" width="56" />
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column prop="sourceType" label="来源" width="100">
            <template #default="{ row }">{{ row.sourceType === 'UPLOAD' ? '压缩包' : 'Git' }}</template>
          </el-table-column>
          <el-table-column label="导入状态" width="120">
            <template #default="{ row }">
              <StatusTag :value="row.importStatus" kind="import" />
            </template>
          </el-table-column>
          <el-table-column label="文件/索引" width="120">
            <template #default="{ row }">{{ row.fileCount }} 个文件<span v-if="row.lastIndexedAt"> · {{ row.lastIndexedAt }} 已索引</span></template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="失败原因" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :loading="indexingId === row.id" @click="triggerIndex(row)">索引/重建索引</el-button>
              <el-button link type="primary" @click="browse(row)">浏览文件</el-button>
              <el-button link type="primary" @click="showTasks(row)">任务记录</el-button>
              <el-popconfirm title="删除仓库会同时清理索引数据，确认？" @confirm="removeRepo(row.id)">
                <template #reference><el-button link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="尚未导入代码。支持上传 zip 压缩包或导入公开 https Git 仓库" :image-size="90" />
          </template>
        </el-table>

        <!-- 活跃任务进度 -->
        <div v-if="activeTask" class="dp-card" style="margin-top: 12px">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
            <b>索引任务 {{ activeTask.taskNo }}</b>
            <StatusTag :value="activeTask.status" kind="task" />
          </div>
          <el-progress :percentage="progress" :status="activeTask.status === 'FAILED' ? 'exception' : undefined" />
          <p style="color: var(--dp-text-secondary); font-size: 12px; margin: 6px 0 0">
            文件 {{ activeTask.processedFiles || 0 }}/{{ activeTask.totalFiles || '?' }} ·
            分块 {{ activeTask.totalChunks || 0 }} ·
            <span v-if="activeTask.errorMessage" style="color: var(--dp-danger)">{{ activeTask.errorMessage }}</span>
          </p>
        </div>
      </el-tab-pane>

      <!-- ============ AI 分析记录 ============ -->
      <el-tab-pane label="AI 分析记录" name="analyses">
        <div class="dp-toolbar">
          <el-radio-group v-model="analysisType" @change="loadAnalyses">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="QA">代码问答</el-radio-button>
            <el-radio-button value="BUG_ANALYSIS">Bug 分析</el-radio-button>
          </el-radio-group>
        </div>
        <el-table :data="analyses" v-loading="analysisLoading" @row-click="openAnalysis">
          <el-table-column prop="id" label="ID" width="64" />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ row.type === 'QA' ? '代码问答' : 'Bug 分析' }}</template>
          </el-table-column>
          <el-table-column prop="question" label="问题/标题" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'" effect="plain">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="模式" width="130">
            <template #default="{ row }"><ModeBadge :mode="row.mode" /></template>
          </el-table-column>
          <el-table-column prop="evidenceCount" label="证据" width="70" />
          <el-table-column prop="createdAt" label="时间" width="170" />
          <template #empty><el-empty description="暂无分析记录，可前往「AI 问答」或「Bug 分析」发起" :image-size="90" /></template>
        </el-table>
      </el-tab-pane>

      <!-- ============ 缺陷 ============ -->
      <el-tab-pane label="缺陷任务" name="defects">
        <div class="dp-toolbar">
          <el-button type="primary" @click="goDefects">打开缺陷看板</el-button>
        </div>
        <el-table :data="defects" v-loading="defectLoading">
          <el-table-column prop="id" label="ID" width="64" />
          <el-table-column prop="title" label="标题" min-width="220" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><StatusTag :value="row.status" kind="defect" /></template>
          </el-table-column>
          <el-table-column prop="severity" label="严重程度" width="100" />
          <el-table-column prop="priority" label="优先级" width="80" />
          <el-table-column prop="assigneeName" label="负责人" width="110" />
          <template #empty><el-empty description="该项目还没有缺陷记录" :image-size="90" /></template>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传 zip 代码压缩包" width="460">
      <el-form label-width="80">
        <el-form-item label="仓库名称"><el-input v-model="uploadForm.name" placeholder="如 demo-task-app" /></el-form-item>
        <el-form-item label="压缩包">
          <input type="file" accept=".zip" @change="onFileChange" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" title="上限 100MB；依赖目录/二进制/敏感文件会被自动过滤并记录原因"
        style="margin-bottom: 12px" />
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="doUpload">上传并导入</el-button>
      </template>
    </el-dialog>

    <!-- Git 导入对话框 -->
    <el-dialog v-model="gitVisible" title="导入公开 Git 仓库" width="460">
      <el-form label-width="90">
        <el-form-item label="仓库名称"><el-input v-model="gitForm.name" /></el-form-item>
        <el-form-item label="Git 地址"><el-input v-model="gitForm.gitUrl" placeholder="https://github.com/user/repo.git" /></el-form-item>
        <el-form-item label="分支"><el-input v-model="gitForm.gitRef" placeholder="默认 main" /></el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" title="MVP 仅支持公开 https 仓库（深度 1 克隆，不托管凭据）" />
      <template #footer>
        <el-button @click="gitVisible = false">取消</el-button>
        <el-button type="primary" :loading="gitLoading" @click="doGit">导入</el-button>
      </template>
    </el-dialog>

    <!-- 任务记录 -->
    <el-dialog v-model="taskDialogVisible" :title="`索引任务记录 · 仓库 #${taskRepoId}`" width="720">
      <el-table :data="tasks" size="small">
        <el-table-column prop="taskNo" label="任务号" width="200" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" kind="task" /></template>
        </el-table-column>
        <el-table-column prop="totalFiles" label="文件" width="70" />
        <el-table-column prop="totalChunks" label="分块" width="70" />
        <el-table-column prop="errorMessage" label="失败原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
    </el-dialog>

    <!-- 文件浏览 -->
    <el-dialog v-model="filesDialogVisible" :title="`文件清单 · 仓库 #${filesRepoId}`" width="860">
      <el-table :data="files" size="small" max-height="480" @row-click="openPreview">
        <el-table-column prop="path" label="路径" min-width="320" />
        <el-table-column prop="language" label="语言" width="110" />
        <el-table-column prop="lineCount" label="行数" width="80" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag v-if="!row.skipReason" size="small" type="success" effect="plain">已入库</el-tag>
            <el-tooltip v-else :content="`被过滤：${row.skipReason}`">
              <el-tag size="small" type="warning" effect="plain">{{ row.skipReason }}</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <p style="color: var(--dp-text-secondary); font-size: 12px">点击行可预览文件内容</p>
    </el-dialog>

    <!-- 文件预览 -->
    <el-dialog v-model="previewVisible" :title="previewFile?.path" width="820">
      <div class="dp-code" style="max-height: 60vh">{{ previewFile?.content || '(无内容)' }}</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  apiAnalyses, apiCodeFile, apiDefects, apiImportGit, apiIndexTask, apiIndexTasks,
  apiProject, apiRepoFiles, apiRepositories, apiTriggerIndex, apiUploadRepo, apiDeleteRepository,
} from '../api'
import type { AnalysisVO, CodeFileVO, DefectVO, IndexTaskVO, ProjectVO, RepositoryVO } from '../api/types'
import { errorMessage } from '../api/http'
import StatusTag from '../components/StatusTag.vue'
import ModeBadge from '../components/ModeBadge.vue'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)

const project = ref<ProjectVO | null>(null)
const repos = ref<RepositoryVO[]>([])
const tab = ref('repos')

const uploadVisible = ref(false)
const gitVisible = ref(false)
const taskDialogVisible = ref(false)
const filesDialogVisible = ref(false)
const previewVisible = ref(false)

const uploadForm = reactive({ name: '', file: null as File | null })
const gitForm = reactive({ name: '', gitUrl: '', gitRef: '' })

const uploading = ref(false)
const gitLoading = ref(false)
const repoLoading = ref(false)
const indexingId = ref<number | null>(null)

const activeTask = ref<IndexTaskVO | null>(null)
let pollTimer: number | undefined

const tasks = ref<IndexTaskVO[]>([])
const taskRepoId = ref(0)
const files = ref<CodeFileVO[]>([])
const filesRepoId = ref(0)
const previewFile = ref<CodeFileVO | null>(null)

const analyses = ref<AnalysisVO[]>([])
const analysisType = ref('')
const analysisLoading = ref(false)

const defects = ref<DefectVO[]>([])
const defectLoading = ref(false)

const progress = computed(() => {
  const t = activeTask.value
  if (!t || !t.totalFiles) return t?.status === 'SUCCESS' ? 100 : 0
  return Math.round(((t.processedFiles || 0) / t.totalFiles) * 100)
})

async function loadAll() {
  project.value = (await apiProject(projectId)).data.data
  repoLoading.value = true
  try {
    repos.value = (await apiRepositories(projectId, 1, 50)).data.data.list
  } finally {
    repoLoading.value = false
  }
}

async function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  uploadForm.file = target.files?.[0] || null
  if (uploadForm.file && !uploadForm.name) {
    uploadForm.name = uploadForm.file.name.replace(/\.zip$/i, '')
  }
}

async function doUpload() {
  if (!uploadForm.file) {
    ElMessage.warning('请选择 zip 文件')
    return
  }
  uploading.value = true
  try {
    await apiUploadRepo(projectId, uploadForm.file, uploadForm.name)
    ElMessage.success('上传成功，可点击「索引/重建索引」建立检索索引')
    uploadVisible.value = false
    uploadForm.file = null
    uploadForm.name = ''
    await loadAll()
  } catch (err) {
    ElMessage.error(errorMessage(err))
  } finally {
    uploading.value = false
  }
}

async function doGit() {
  if (!gitForm.name || !gitForm.gitUrl) {
    ElMessage.warning('请填写名称与 Git 地址')
    return
  }
  gitLoading.value = true
  try {
    await apiImportGit(projectId, { ...gitForm })
    ElMessage.success('已创建导入任务')
    gitVisible.value = false
    await loadAll()
  } catch (err) {
    ElMessage.error(errorMessage(err))
  } finally {
    gitLoading.value = false
  }
}

async function triggerIndex(repo: RepositoryVO) {
  indexingId.value = repo.id
  try {
    const task = (await apiTriggerIndex(repo.id)).data.data
    activeTask.value = task
    startPolling()
  } catch (err) {
    ElMessage.error(errorMessage(err))
  } finally {
    indexingId.value = null
  }
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    if (!activeTask.value) return stopPolling()
    const t = activeTask.value
    if (t.status === 'SUCCESS' || t.status === 'FAILED') {
      stopPolling()
      if (t.status === 'SUCCESS') ElMessage.success('索引完成')
      loadAll()
      return
    }
    try {
      activeTask.value = (await apiIndexTask(t.id)).data.data
    } catch {
      stopPolling()
    }
  }, 1500)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

async function showTasks(repo: RepositoryVO) {
  taskRepoId.value = repo.id
  tasks.value = (await apiIndexTasks(repo.id, 1, 20)).data.data.list
  taskDialogVisible.value = true
}

async function browse(repo: RepositoryVO) {
  filesRepoId.value = repo.id
  files.value = (await apiRepoFiles(repo.id, 1, 100)).data.data.list
  filesDialogVisible.value = true
}

async function openPreview(row: CodeFileVO) {
  if (!row.id) return
  previewFile.value = (await apiCodeFile(row.id)).data.data
  previewVisible.value = true
}

async function removeRepo(id: number) {
  try {
    await apiDeleteRepository(id)
    ElMessage.success('已删除')
    loadAll()
  } catch (err) {
    ElMessage.error(errorMessage(err))
  }
}

async function loadAnalyses() {
  analysisLoading.value = true
  try {
    analyses.value = (await apiAnalyses(projectId, analysisType.value || undefined, 1, 20)).data.data.list
  } finally {
    analysisLoading.value = false
  }
}

async function loadDefects() {
  defectLoading.value = true
  try {
    defects.value = (await apiDefects(projectId, { page: 1, size: 20 })).data.data.list
  } finally {
    defectLoading.value = false
  }
}

function goDefects() {
  router.push({ name: 'defects', params: { projectId } })
}

function openAnalysis(row: AnalysisVO) {
  if (row.type === 'QA') router.push({ name: 'qa', query: { analysisId: row.id } })
  else router.push({ name: 'bug', query: { analysisId: row.id } })
}

onMounted(() => {
  loadAll()
  loadAnalyses()
  loadDefects()
})
onBeforeUnmount(stopPolling)
</script>
