<template>
  <div class="dp-page">
    <div class="dp-toolbar">
      <el-input v-model="keyword" placeholder="搜索用户名/昵称" clearable style="width: 220px" @keyup.enter="load(1)" />
      <el-button type="primary" @click="load(1)">查询</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="createVisible = true">创建用户</el-button>
    </div>

    <div class="dp-card">
      <el-table :data="users" v-loading="loading">
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" effect="plain">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="danger" :disabled="row.id === auth.user?.id"
              @click="setStatus(row.id, 0)">禁用</el-button>
            <el-button v-else link type="success" @click="setStatus(row.id, 1)">启用</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无用户" /></template>
      </el-table>
      <el-pagination style="margin-top: 12px; justify-content: flex-end" background layout="total, prev, pager, next"
        :total="total" :page-size="size" :current-page="page" @current-change="load" />
    </div>

    <el-dialog v-model="createVisible" title="创建用户" width="460">
      <el-form :model="form" label-width="70">
        <el-form-item label="用户名"><el-input v-model="form.username" placeholder="3-32 位字母/数字/下划线" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password placeholder="8 位以上，含字母和数字" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role">
            <el-option label="USER 普通用户" value="USER" />
            <el-option label="ADMIN 管理员" value="ADMIN" />
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
import { ElMessage } from 'element-plus'
import { apiCreateUser, apiUpdateUserStatus, apiUsers } from '../api'
import type { UserVO } from '../api/types'
import { errorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const users = ref<UserVO[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const createVisible = ref(false)
const form = reactive({ username: '', nickname: '', password: '', role: 'USER' })

async function load(p = page.value) {
  loading.value = true
  page.value = p
  try {
    const data = (await apiUsers(p, size, keyword.value || undefined)).data.data
    users.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function create() {
  saving.value = true
  try {
    await apiCreateUser({ ...form })
    ElMessage.success('创建成功')
    createVisible.value = false
    form.username = ''
    form.nickname = ''
    form.password = ''
    load(1)
  } catch (err) {
    ElMessage.error(errorMessage(err))
  } finally {
    saving.value = false
  }
}

async function setStatus(id: number, status: number) {
  try {
    await apiUpdateUserStatus(id, status)
    ElMessage.success('已更新')
    load()
  } catch (err) {
    ElMessage.error(errorMessage(err))
  }
}

onMounted(() => load(1))
</script>
