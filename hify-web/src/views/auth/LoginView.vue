<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="brand-mark">H</div>
      <h1>Hify</h1>
      <p>内部 AI Agent 平台</p>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" autofocus placeholder="admin" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="login-button" @click="handleLogin">
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

async function handleLogin() {
  if (!form.username.trim() || !form.password) {
    ElMessage.error('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    router.push((route.query.redirect as string) || '/conversation')
  } catch {
    // request interceptor has shown the error message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  background:
    linear-gradient(135deg, rgba(76, 110, 245, 0.10), rgba(6, 182, 212, 0.08)),
    var(--bg-app);
}

.login-panel {
  width: min(420px, calc(100vw - 32px));
  padding: 34px;
  background: var(--bg-surface);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  box-shadow: var(--shadow-lg);
}

.brand-mark {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, var(--color-primary-500), var(--color-cyan-500));
  border-radius: 8px;
}

h1 {
  margin: 18px 0 6px;
  color: var(--text-primary);
  font-size: 28px;
}

p {
  margin: 0 0 26px;
  color: var(--text-secondary);
}

.login-button {
  width: 100%;
}
</style>
