<template>
  <div class="dp-page">
    <div class="dp-toolbar">
      <el-input v-model="keyword" placeholder="搜索项目名称" clearable style="width: 240px" @keyup.enter="load(1)" />
      <el-button type="primary" @click="load(1)">查询</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="dialogVisible = true">新建项目</el-button>
    </div>

    <div class="dp-card">
      <el-table :data="projects" v-loading="loading" @row-click="openDetail">
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="name" label="项目名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">进入</el-button>
            <el-popconfirm title="确认删除该项目？（软删除，历史可审计）" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" @click.stop>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有项目，点击右上角「新建项目」开始" :image-size="90" />
        </template>
      </el-table>
      <el-pagination style="margin-top: 12px; justify-content: flex-end" background layout="total, prev, pager, next"
        :total="total" :page-size="size" :current-page="page" @current-change="load" />
    </div>

    <el-dialog v-model="dialogVisible" title="新建项目" width="440">
      <el-form :model="form" label-width="70">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="100" placeholder="项目名称（2-100 字符）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiCreateProject, apiDeleteProject, apiProjects } from '../api'
import type { ProjectVO } from '../api/types'
import { errorMessage } from '../api/http'

const router = useRouter()
const projects = ref<ProjectVO[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const form = reactive({ name: '', description: '' })

async function load(p = page.value) {
  loading.value = true
  page.value = p
  try {
    const data = (await apiProjects(p, size, keyword.value || undefined)).data.data
    projects.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.name || form.name.trim().length < 2) {
    ElMessage.warning('项目名至少 2 个字符')
    return
  }
  saving.value = true
  try {
    await apiCreateProject({ name: form.name.trim(), description: form.description })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    form.name = ''
    form.description = ''
    load(1)
  } catch (err) {
    ElMessage.error(errorMessage(err))
  } finally {
    saving.value = false
  }
}

async function remove(id: number) {
  try {
    await apiDeleteProject(id)
    ElMessage.success('已删除')
    load()
  } catch (err) {
    ElMessage.error(errorMessage(err))
  }
}

function openDetail(row: ProjectVO) {
  router.push({ name: 'project-detail', params: { id: row.id } })
}

onMounted(() => load(1))
</script>

<style scoped>
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
