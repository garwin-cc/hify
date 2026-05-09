import { ref, onMounted, onUnmounted } from 'vue'

const NARROW = 1200

/**
 * 追踪视口宽度，isNarrow = window.innerWidth < 1200。
 * 每次调用独立监听 resize，组件卸载时自动清理。
 */
export function useBreakpoint() {
  const isNarrow = ref(window.innerWidth < NARROW)

  function onResize() {
    isNarrow.value = window.innerWidth < NARROW
  }

  onMounted(() => window.addEventListener('resize', onResize))
  onUnmounted(() => window.removeEventListener('resize', onResize))

  return { isNarrow }
}
