import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

const instance = axios.create({
  baseURL: '/api',
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
})

instance.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const res: Result = response.data
    if (res.code === 200) {
      return res.data as any
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    const msg: string = error.response?.data?.message ?? error.message ?? '网络错误'
    if (error.response?.status === 401 || error.response?.data?.code === 401) {
      useAuthStore().clear()
      router.push('/login')
    }
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export const get = <T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> =>
  instance.get(url, { params, ...config })

export const post = <T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> =>
  instance.post(url, data, config)

export const put = <T>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> =>
  instance.put(url, data, config)

export const del = <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
  instance.delete(url, config)
