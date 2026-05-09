import axios from 'axios'
import { ElMessage } from 'element-plus'

export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

request.interceptors.response.use(
  (response) => {
    const res: Result = response.data
    if (res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res.data as any
  },
  (error) => {
    const msg = error.response?.data?.message ?? error.message ?? '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export default request
