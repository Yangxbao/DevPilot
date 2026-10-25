import axios, { AxiosError, type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/** 统一响应结构 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 180000, // AI 分析同步调用最长 ~90s，留足余量
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('devpilot_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    const body = error.response?.data
    if (status === 401) {
      localStorage.removeItem('devpilot_token')
      localStorage.removeItem('devpilot_user')
      if (router.currentRoute.value.name !== 'login') {
        ElMessage.warning('登录已过期，请重新登录')
        router.push({ name: 'login' })
      }
    } else {
      const message =
        (body?.message as string) || error.message || '请求失败，请稍后重试'
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

/** 业务请求包装：code!=0 时抛出带 message 的错误 */
export async function request<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const resp = await promise
  if (resp.data.code !== 0) {
    ElMessage.error(resp.data.message || '操作失败')
    throw new Error(resp.data.message)
  }
  return resp.data.data
}

export function errorMessage(err: unknown): string {
  const axiosError = err as AxiosError<ApiResponse<unknown>>
  return axiosError?.response?.data?.message || (err as Error)?.message || '未知错误'
}

export default http
