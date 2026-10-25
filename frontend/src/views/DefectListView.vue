<template>
  <div class="dp-page">
    <div class="dp-toolbar">
      <el-button @click="$router.back()">← 返回</el-button>
      <h2 class="dp-page-title" style="margin: 0">缺陷管理</h2>
      <div style="flex: 1"></div>
      <el-select v-model="status" placeholder="状态" clearable style="width: 130px">
        <el-option label="待处理" value="OPEN" />
        <el-option label="处理中" value="IN_PROGRESS" />
        <el-option label="已解决" value="RESOLVED" />
        <el-option label="已关闭" value="CLOSED" />
      </el-select>
      <el-select v-model="severity" placeholder="严重程度" clearable style="width: 130px">
        <el-option v-for="s in ['CRITICAL', 'MAJOR', 'MEDIUM', 'MINOR']" :key="s" :label="s" :value="s" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索标题" clearable style="width: 200px" @keyup.enter="load(1)" />
      <el-button type="primary" @click="load(1)">筛选</el-button>
      <el-button type="primary" plain @click="createVisible = true">新建缺陷</el-button>
    </div>

    <div class="dp-card">
      <el-table :data="defects" v-loading="loading" @row-click="openDetail">
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="title" label="标题" min-width="240" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" kind="defect" /></template>
        </el-table-column>
        <el-table-column prop="severity" label="严重程度" width="110" />
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column prop="assigneeName" label="负责人" width="120" />
        <el-table-column prop="reporterName" label="报告人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <template #empty><el-empty description="暂无缺陷，点击右上角「新建缺陷」创建" :image-size="90" /></template>
      </el-table>
      <el-pagination style="margin-top: 12px; justify-content: flex-end" background layout="total, prev, pager, next"
        :total="total" :page-size="size" :current-page="page" @current-change="load" />
    </div>

    <el-dialog v-model="createVisible" title="新建缺陷" width="520">
      <el-form :model="form" label-width="80">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="10000" /></el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="form.severity">
            <el-option v-for="s in ['CRITICAL', 'MAJOR', 'MEDIUM', 'MINOR']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority">
            <el-option v-for="p in ['P0', 'P1', 'P2', 'P3']" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiCreateDefect, apiDefects } from '../api'
import type { DefectVO } from '../api/types'
import { errorMessage } from '../api/http'
import StatusTag from '../components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.projectId)

const defects = ref<DefectVO[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('')
const severity = ref('')
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)
const form = reactive({ title: '', description: '', severity: 'MEDIUM', priority: 'P2' })

async function load(p = page.value) {
  loading.value = true
  page.value = p
  try {
    const data = (
      await apiDefects(projectId, {
        page: p,
        size,
        status: status.value || undefined,
        severity: severity.value || undefined,
        keyword: keyword.value || undefined,
      })
    ).data.data
    defects.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  saving.value = true
  try {
    await apiCreateDefect(projectId, { ...form, title: form.title.trim() })
    ElMessage.success('创建成功')
    createVisible.value = false
    form.title = ''
    form.description = ''
    load(1)
  } catch (err) {
    ElMessage.error(errorMessage(err))
  } finally {
    saving.value = false
  }
}

function openDetail(row: DefectVO) {
  router.push({ name: 'defect-detail', params: { id: row.id } })
}

onMounted(() => load(1))
</script>

<style scoped>
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
