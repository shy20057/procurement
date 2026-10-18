import axios, { type AxiosInstance, type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Add correlation ID
    if (!config.headers['X-Correlation-Id']) {
      config.headers['X-Correlation-Id'] = crypto.randomUUID()
    }
    // Add auth token if available
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// Response interceptor
request.interceptors.response.use(
  (response) => {
    return response
  },
  (error: AxiosError<{ code?: string; message?: string }>) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    const code = error.response?.data?.code || 'UNKNOWN_ERROR'

    ElMessage.error({
      message: `[${code}] ${message}`,
      duration: 4000,
    })

    return Promise.reject(error)
  }
)

export default request

/**
 * 解包列表接口返回数据。
 * 兼容纯数组、后端 Result 包装（{ code, data: [...] }）、
 * 分页对象（Spring Page 的 content / MyBatis-Plus 的 records / 自定义 list）。
 */
export function unwrapList<T = any>(data: any): T[] {
  if (Array.isArray(data)) return data
  const inner = data?.content ?? data?.records ?? data?.list ?? data?.data
  return Array.isArray(inner) ? inner : []
}
