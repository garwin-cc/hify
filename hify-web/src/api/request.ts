import axios from 'axios'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import { i18n } from '@/i18n'

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

async function showError(message: string) {
  const [{ ElMessage }] = await Promise.all([
    import('element-plus/es/components/message/index'),
    import('element-plus/es/components/message/style/css'),
  ])
  ElMessage.error(message)
}

request.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res: Result = response.data
    if (res.code !== 0) {
      void showError(res.message || i18n.global.t('common.requestFailed'))
      return Promise.reject(new Error(res.message))
    }
    return res.data as any
  },
  (error) => {
    const msg = error.response?.data?.message ?? error.message ?? i18n.global.t('common.networkError')
    if (error.response?.status === 401 || error.response?.data?.code === 401) {
      useAuthStore().clear()
      router.push('/login')
    }
    void showError(msg)
    return Promise.reject(error)
  },
)

export default request
