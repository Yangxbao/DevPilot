<template>
  <div class="dp-login">
    <el-card class="dp-login-card">
      <div class="dp-login-brand">
        <span style="font-size: 28px">🛠️</span>
        <h1>DevPilot</h1>
        <p>AI 研发协作与代码库分析平台</p>
      </div>
      <el-tabs v-model="tab">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-position="top" @keyup.enter="doLogin">
            <el-form-item label="用户名">
              <el-input v-model="loginForm.username" placeholder="用户名" data-testid="login-username" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" show-password placeholder="密码" data-testid="login-password" />
            </el-form-item>
            <el-button type="primary" style="width: 100%" :loading="loading" data-testid="login-submit" @click="doLogin">
              登 录
            </el-button>
          </el-form>
          <el-alert style="margin-top: 12px" type="info" :closable="false"
            title="本地演示账号：admin / admin123456（首次启动自动创建）" />
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" label-position="top">
            <el-form-item label="用户名">
              <el-input v-model="registerForm.username" placeholder="字母/数字/下划线，3-32 位" />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="registerForm.nickname" placeholder="显示名称" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="registerForm.password" type="password" show-password
                placeholder="至少 8 位，须包含字母和数字" />
            </el-form-item>
            <el-button type="primary" style="width: 100%" :loading="loading" @click="doRegister">注 册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { apiRegister } from '../api'
import { errorMessage } from '../api/http'

const auth = useAuthStore()
const router = useRouter()
const tab = ref('login')
const loading = ref(false)
const loginForm = reactive({ username: 'admin', password: '' })
const registerForm = reactive({ username: '', nickname: '', password: '' })

async function doLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(loginForm.username, loginForm.password)
    ElMessage.success('登录成功')
    router.push({ name: 'dashboard' })
  } catch (err) {
    ElMessage.error(errorMessage(err))
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  if (!registerForm.username || !registerForm.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await apiRegister(registerForm.username, registerForm.password, registerForm.nickname)
    ElMessage.success('注册成功，请登录')
    loginForm.username = registerForm.username
    tab.value = 'login'
  } catch (err) {
    ElMessage.error(errorMessage(err))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dp-login {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1c2b5e 0%, #2f54eb 60%, #4c6ef5 100%);
}
.dp-login-card {
  width: 400px;
  border-radius: 12px;
}
.dp-login-brand {
  text-align: center;
  margin-bottom: 8px;
}
.dp-login-brand h1 {
  margin: 4px 0;
  font-size: 24px;
}
.dp-login-brand p {
  margin: 0 0 12px;
  color: var(--dp-text-secondary);
  font-size: 13px;
}
</style>
