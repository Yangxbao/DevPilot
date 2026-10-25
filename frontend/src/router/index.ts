import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    {
      path: '/',
      component: () => import('../layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '仪表盘' } },
        { path: 'projects', name: 'projects', component: () => import('../views/ProjectListView.vue'), meta: { title: '项目管理' } },
        { path: 'projects/:id', name: 'project-detail', component: () => import('../views/ProjectDetailView.vue'), meta: { title: '项目详情' } },
        { path: 'search', name: 'search', component: () => import('../views/SearchView.vue'), meta: { title: '代码检索' } },
        { path: 'qa', name: 'qa', component: () => import('../views/QaView.vue'), meta: { title: 'AI 问答' } },
        { path: 'bug', name: 'bug', component: () => import('../views/BugAnalysisView.vue'), meta: { title: 'Bug 分析' } },
        { path: 'defects/:projectId', name: 'defects', component: () => import('../views/DefectListView.vue'), meta: { title: '缺陷管理' } },
        { path: 'defect-detail/:id', name: 'defect-detail', component: () => import('../views/DefectDetailView.vue'), meta: { title: '缺陷详情' } },
        { path: 'admin', name: 'admin', component: () => import('../views/AdminView.vue'), meta: { title: '系统设置', adminOnly: true } },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('devpilot_token')
  if (to.name !== 'login' && !token) {
    return { name: 'login' }
  }
  if (to.name === 'login' && token) {
    return { name: 'dashboard' }
  }
  if (to.meta.adminOnly) {
    const user = JSON.parse(localStorage.getItem('devpilot_user') || 'null')
    if (user?.role !== 'ADMIN') {
      ElMessage.error('仅管理员可访问该页面')
      return { name: 'dashboard' }
    }
  }
  document.title = to.meta.title ? `${to.meta.title} · DevPilot` : 'DevPilot'
  return true
})

export default router
