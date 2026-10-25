<template>
  <div class="dp-page">
    <div class="dp-toolbar">
      <el-button @click="$router.back()">← 返回</el-button>
      <h2 class="dp-page-title" style="margin: 0">#{{ defect?.id }} {{ defect?.title || '...' }}</h2>
      <StatusTag v-if="defect" :value="defect.status" kind="defect" />
      <div style="flex: 1"></div>
      <template v-if="defect && nextStatuses.length">
        <el-dropdown v-if="nextStatuses.length > 1" @command="changeStatus">
          <el-button type="primary">流转状态 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="s in nextStatuses" :key="s" :command="s">
                {{ statusLabel(s) }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button v-else type="primary" @click="changeStatus(nextStatuses[0])">
          流转到「{{ statusLabel(nextStatuses[0]) }}」
        </el-button>
      </template>
      <el-tag v-else-if="defect" type="info" effect="plain">已终态，无法继续流转</el-tag>
    </div>

    <el-row :gutter="16" v-if="defect">
      <el-col :xs="24" :md="16">
        <div class="dp-card">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="严重程度">{{ defect.severity }}</el-descriptions-item>
            <el-descriptions-item label="优先级">{{ defect.priority }}</el-descriptions-item>
            <el-descriptions-item label="报告人">{{ defect.reporterName || defect.reporterId }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ defect.assigneeName || '未指派' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ defect.createdAt }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ defect.updatedAt }}</el-descriptions-item>
          </el-descriptions>
          <h4>描述</h4>
          <div class="dp-code" style="max-height: 300px">{{ defect.description || '（无）' }}</div>
          <template v-if="defect.analysisId">
            <h4>关联的 AI 分析</h4>
            <el-button link type="primary" @click="viewAnalysis">查看分析报告 #{{ defect.analysisId }}</el-button>
          </template>
        </div>

        <div class="dp-card" style="margin-top: 16px">
          <h3 class="dp-block-title">处理记录</h3>
          <div v-for="c in comments" :key="c.id" class="dp-comment">
            <div class="dp-comment-meta">
              <b>{{ c.authorName || c.authorId }}</b> · {{ c.createdAt }}
            </div>
            <div>{{ c.content }}</div>
          </div>
          <el-empty v-if="!comments.length" description="暂无处理记录" :image-size="60" />
          <div style="display: flex; gap: 8px; margin-top: 10px">
            <el-input v-model="newComment" maxlength="2000" placeholder="添加处理记录…" @keyup.enter="addComment" />
            <el-button type="primary" :loading="commenting" @click="addComment">提交</el-button>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :md="8">
        <div class="dp-card">
          <h3 class="dp-block-title">编辑</h3>
          <el-form label-position="top">
            <el-form-item label="标题"><el-input v-model="editForm.title" /></el-form-item>
            <el-form-item label="严重程度">
              <el-select v-model="editForm.severity">
                <el-option v-for="s in ['CRITICAL', 'MAJOR', 'MEDIUM', 'MINOR']" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item label="优先级">
              <el-select v-model="editForm.priority">
                <el-option v-for="p in ['P0', 'P1', 'P2', 'P3']" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
            <el-form-item label="指派给（用户ID）">
              <el-input-number v-model="editForm.assigneeId" :min="1" style="width: 100%" />
            </el-form-item>
            <el-button type="primary" style="width: 100%" :loading="saving" @click="save">保存修改</el-button>
          </el-form>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiAddComment, apiAnalysis, apiChangeDefectStatus, apiDefect, apiDefectComments, apiUpdateDefect } from '../api'
import type { AnalysisVO, DefectCommentVO, DefectVO } from '../api/types'
import { errorMessage } from '../api/http'
import StatusTag from '../components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const defectId = Number(route.params.id)

const defect = ref<DefectVO | null>(null)
const comments = ref<DefectCommentVO[]>([])
const newComment = ref('')
const commenting = ref(false)
const saving = ref(false)
const editForm = reactive({ title: '', severity: 'MEDIUM', priority: 'P2', assigneeId: undefined as number | undefined })

const NEXT: Record<string, string[]> = {
  OPEN: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED', 'OPEN'],
  RESOLVED: ['CLOSED', 'OPEN'],
  CLOSED: [],
}

const nextStatuses = computed(() => NEXT[defect.value?.status || ''] || [])

function statusLabel(s: string): string {
  return { OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }[s] || s
}

async function load() {
  defect.value = (await apiDefect(defectId)).data.data
  comments.value = (await apiDefectComments(defectId)).data.data
  editForm.title = defect.value.title
  editForm.severity = defect.value.severity
  editForm.priority = defect.value.priority
  editForm.assigneeId = defect.value.assigneeId
}

async function changeStatus(target: string) {
  try {
    defect.value = (await apiChangeDefectStatus(defectId, target)).data.data as unknown as DefectVO
    await load()
    ElMessage.success(`已流转到「${statusLabel(target)}」`)
  } catch (err) {
    ElMessage.error(errorMessage(err))
  }
}

async function save() {
  saving.value = true
  try {
    await apiUpdateDefect(defectId, {
      title: editForm.title,
      severity: editForm.severity,
      priority: editForm.priority,
      assigneeId: editForm.assigneeId,
    })
    ElMessage.success('已保存')
    await load()
  } catch (err) {
    ElMessage.error(errorMessage(err))
  } finally {
    saving.value = false
  }
}

async function addComment() {
  if (!newComment.value.trim()) return
  commenting.value = true
  try {
    await apiAddComment(defectId, newComment.value.trim())
    newComment.value = ''
    comments.value = (await apiDefectComments(defectId)).data.data
  } catch (err) {
    ElMessage.error(errorMessage(err))
  } finally {
    commenting.value = false
  }
}

async function viewAnalysis() {
  const analysis: AnalysisVO = (await apiAnalysis(defect.value!.analysisId!)).data.data
  const target = analysis.type === 'QA' ? { name: 'qa' } : { name: 'bug' }
  router.push({ ...target, query: { analysisId: analysis.id } })
}

onMounted(load)
</script>

<style scoped>
.dp-comment {
  border-bottom: 1px dashed var(--dp-border);
  padding: 8px 0;
}
.dp-comment-meta {
  color: var(--dp-text-secondary);
  font-size: 12px;
  margin-bottom: 4px;
}
.dp-block-title {
  margin: 0 0 10px;
}
h4 {
  margin: 14px 0 8px;
}
</style>
