import { ref, type Ref } from 'vue'

export interface UseRequestReturn<T> {
  data: Ref<T | null>
  loading: Ref<boolean>
  error: Ref<Error | null>
  execute: (...args: any[]) => Promise<void>
}

/**
 * 请求状态管理。自动处理 loading / error / data 三态，避免每个页面重复写 try-catch-finally。
 *
 * @example
 * const { data: providers, loading, execute: loadProviders } = useRequest(listProviders)
 * onMounted(() => loadProviders())
 */
export function useRequest<T>(apiFn: (...args: any[]) => Promise<T>): UseRequestReturn<T> {
  const data    = ref<T | null>(null) as Ref<T | null>
  const loading = ref(false)
  const error   = ref<Error | null>(null)

  const execute = async (...args: any[]) => {
    loading.value = true
    error.value   = null
    try {
      data.value = await apiFn(...args)
    } catch (err) {
      error.value = err as Error
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, execute }
}
