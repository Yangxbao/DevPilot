import http from './http'
import type {
  AgentEvidence,
  AgentStep,
  AgentReport,
  AnalysisVO,
  CodeFileVO,
  DashboardStats,
  DefectCommentVO,
  DefectVO,
  IndexTaskVO,
  LoginResponse,
  PageResult,
  ProjectVO,
  RepositoryVO,
  SearchResponse,
  UserVO,
} from './types'

// ---------- auth ----------
export const apiLogin = (username: string, password: string) =>
  http.post<{ code: number; message: string; data: LoginResponse }>('/auth/login', { username, password })

export const apiRegister = (username: string, password: string, nickname: string) =>
  http.post('/auth/register', { username, password, nickname })

export const apiLogout = () => http.post('/auth/logout')

export const apiMe = () => http.get<{ data: UserVO }>('/users/me')

// ---------- admin ----------
export const apiUsers = (page: number, size: number, keyword?: string) =>
  http.get<{ data: PageResult<UserVO> }>('/admin/users', { params: { page, size, keyword } })

export const apiCreateUser = (body: { username: string; password: string; nickname: string; role: string }) =>
  http.post('/admin/users', body)

export const apiUpdateUserStatus = (id: number, status: number) =>
  http.patch(`/admin/users/${id}/status`, { status })

// ---------- project / repository ----------
export const apiProjects = (page = 1, size = 10, keyword?: string) =>
  http.get<{ data: PageResult<ProjectVO> }>('/projects', { params: { page, size, keyword } })

export const apiCreateProject = (body: { name: string; description: string }) =>
  http.post<{ data: ProjectVO }>('/projects', body)

export const apiProject = (id: number) => http.get<{ data: ProjectVO }>(`/projects/${id}`)

export const apiDeleteProject = (id: number) => http.delete(`/projects/${id}`)

export const apiUploadRepo = (projectId: number, file: File, name: string) => {
  const form = new FormData()
  form.append('file', file)
  form.append('name', name)
  return http.post<{ data: RepositoryVO }>(`/projects/${projectId}/repositories/upload`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export const apiImportGit = (projectId: number, body: { name: string; gitUrl: string; gitRef?: string }) =>
  http.post<{ data: RepositoryVO }>(`/projects/${projectId}/repositories/git`, body)

export const apiRepositories = (projectId: number, page = 1, size = 50) =>
  http.get<{ data: PageResult<RepositoryVO> }>(`/projects/${projectId}/repositories`, { params: { page, size } })

export const apiDeleteRepository = (id: number) => http.delete(`/repositories/${id}`)

export const apiRepoFiles = (repoId: number, page = 1, size = 20, path?: string) =>
  http.get<{ data: PageResult<CodeFileVO> }>(`/repositories/${repoId}/files`, { params: { page, size, path } })

export const apiCodeFile = (id: number) => http.get<{ data: CodeFileVO }>(`/code-files/${id}`)

// ---------- indexing ----------
export const apiTriggerIndex = (repoId: number) =>
  http.post<{ data: IndexTaskVO }>(`/repositories/${repoId}/index-tasks`)

export const apiIndexTasks = (repoId: number, page = 1, size = 10) =>
  http.get<{ data: PageResult<IndexTaskVO> }>(`/repositories/${repoId}/index-tasks`, { params: { page, size } })

export const apiIndexTask = (id: number) => http.get<{ data: IndexTaskVO }>(`/index-tasks/${id}`)

// ---------- search ----------
export const apiSearch = (params: {
  projectId?: number
  repositoryId?: number
  q: string
  mode: string
  page?: number
  size?: number
}) => http.get<{ data: SearchResponse }>('/search', { params })

// ---------- ai ----------
export const apiQa = (projectId: number, repositoryId: number, question: string) =>
  http.post<{ data: AnalysisVO }>(`/projects/${projectId}/ai/qa`, { repositoryId, question })

export const apiBugAnalysis = (
  projectId: number,
  body: { repositoryId: number; title: string; errorMessage: string; stackTrace?: string; reproSteps?: string },
) => http.post<{ data: AnalysisVO }>(`/projects/${projectId}/ai/bug-analysis`, body)

export const apiAnalyses = (projectId: number, type?: string, page = 1, size = 10) =>
  http.get<{ data: PageResult<AnalysisVO> }>(`/projects/${projectId}/ai/analyses`, { params: { type, page, size } })

export const apiAnalysis = (id: number) => http.get<{ data: AnalysisVO }>(`/ai/analyses/${id}`)

export const apiLinkDefect = (analysisId: number, defectId: number) =>
  http.post(`/ai/analyses/${analysisId}/link-defect`, { defectId })

// ---------- defect ----------
export const apiDefects = (
  projectId: number,
  params: { page?: number; size?: number; status?: string; severity?: string; keyword?: string },
) => http.get<{ data: PageResult<DefectVO> }>(`/projects/${projectId}/defects`, { params })

export const apiCreateDefect = (
  projectId: number,
  body: { title: string; description?: string; severity?: string; priority?: string; assigneeId?: number; analysisId?: number },
) => http.post<{ data: DefectVO }>(`/projects/${projectId}/defects`, body)

export const apiDefect = (id: number) => http.get<{ data: DefectVO }>(`/defects/${id}`)

export const apiUpdateDefect = (id: number, body: Record<string, unknown>) =>
  http.patch<{ data: DefectVO }>(`/defects/${id}`, body)

export const apiChangeDefectStatus = (id: number, status: string) =>
  http.post<{ data: { id: number; status: string; allowedNextStatuses: string[] } }>(`/defects/${id}/status`, { status })

export const apiDefectComments = (id: number) =>
  http.get<{ data: DefectCommentVO[] }>(`/defects/${id}/comments`)

export const apiAddComment = (id: number, content: string) =>
  http.post<{ data: DefectCommentVO }>(`/defects/${id}/comments`, { content })

// ---------- stats ----------
export const apiDashboard = () => http.get<{ data: DashboardStats }>('/stats/dashboard')

export type { AgentEvidence, AgentStep, AgentReport }
