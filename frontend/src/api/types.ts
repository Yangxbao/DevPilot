/** 与后端 docs/API_CONTRACT.md 对应的类型定义 */

export interface UserVO {
  id: number
  username: string
  nickname: string
  role: 'ADMIN' | 'USER'
  status: number
  createdAt: string
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  user: UserVO
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface ProjectVO {
  id: number
  name: string
  description: string
  ownerId: number
  createdAt: string
  updatedAt: string
}

export interface RepositoryVO {
  id: number
  projectId: number
  name: string
  sourceType: 'GIT_URL' | 'UPLOAD'
  gitUrl?: string
  gitRef?: string
  fileName?: string
  importStatus: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'
  errorMessage?: string
  fileCount: number
  totalSize: number
  lastIndexedAt?: string
  createdAt: string
}

export interface IndexTaskVO {
  id: number
  repositoryId: number
  taskNo: string
  triggerType: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  totalFiles?: number
  processedFiles?: number
  totalChunks?: number
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  createdAt: string
}

export interface CodeFileVO {
  id: number
  repositoryId: number
  path: string
  language: string
  size: number
  lineCount: number
  content?: string
  skipReason?: string
  createdAt: string
}

export interface SearchHit {
  chunkId: number
  fileId: number
  repositoryId: number
  path: string
  language: string
  startLine: number
  endLine: number
  snippet: string
  score: number
  channel: string
}

export interface SearchResponse {
  results: SearchHit[]
  total: number
  page: number
  size: number
  vectorEnabled: boolean
}

export interface AgentStep {
  name: string
  status: string
  detail?: string
  durationMs?: number
}

export interface AgentEvidence {
  chunkId: string
  fileId: number
  path: string
  language: string
  startLine: number
  endLine: number
  snippet: string
  score: number
}

export interface AgentReport {
  summary: string
  possibleCauses: string[]
  impact: string
  verificationSteps: string[]
  fixDirections: string
  confidence: number
}

export interface AnalysisVO {
  id: number
  projectId: number
  repositoryId: number
  type: 'QA' | 'BUG_ANALYSIS'
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  question: string
  answer?: string
  mode?: 'real' | 'mock'
  provider?: string
  model?: string
  confidence?: number
  evidenceCount?: number
  durationMs?: number
  errorMessage?: string
  createdBy: number
  createdAt: string
  evidence?: AgentEvidence[]
  steps?: AgentStep[]
  warnings?: string[]
  report?: AgentReport
}

export interface DefectVO {
  id: number
  projectId: number
  title: string
  description?: string
  severity: 'CRITICAL' | 'MAJOR' | 'MEDIUM' | 'MINOR'
  priority: 'P0' | 'P1' | 'P2' | 'P3'
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
  assigneeId?: number
  assigneeName?: string
  reporterId: number
  reporterName?: string
  analysisId?: number
  createdAt: string
  updatedAt: string
}

export interface DefectCommentVO {
  id: number
  defectId: number
  authorId: number
  authorName?: string
  content: string
  createdAt: string
}

export interface DashboardStats {
  projectCount: number
  repositoryCount: number
  repositoryByStatus: Record<string, number>
  indexTaskTotal: number
  indexTaskByStatus: Record<string, number>
  defectTotal: number
  defectByStatus: Record<string, number>
  analysisTotal: number
  analysisByType: Record<string, number>
  analysisByMode: Record<string, number>
  generatedAt: string
}
