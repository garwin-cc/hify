import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getMe, login as loginApi, logout as logoutApi, type UserInfo, type UserRole } from '@/api/auth'

const TOKEN_KEY = 'hify.auth.token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<UserInfo | null>(null)
  const loaded = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value))
  const role = computed<UserRole | null>(() => user.value?.role ?? null)
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isEditor = computed(() => role.value === 'ADMIN' || role.value === 'EDITOR')

  function setToken(nextToken: string) {
    token.value = nextToken
    if (nextToken) {
      localStorage.setItem(TOKEN_KEY, nextToken)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  }

  async function login(username: string, password: string) {
    const resp = await loginApi(username, password)
    setToken(resp.token)
    user.value = resp.user
    loaded.value = true
  }

  async function loadMe() {
    if (!token.value) {
      loaded.value = true
      return null
    }
    user.value = await getMe()
    loaded.value = true
    return user.value
  }

  async function logout() {
    try {
      if (token.value) {
        await logoutApi()
      }
    } finally {
      user.value = null
      loaded.value = false
      setToken('')
    }
  }

  function clear() {
    user.value = null
    loaded.value = false
    setToken('')
  }

  return {
    token,
    user,
    loaded,
    isLoggedIn,
    role,
    isAdmin,
    isEditor,
    login,
    loadMe,
    logout,
    clear,
  }
})
