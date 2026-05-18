import { createApp } from 'vue'
import { pinia } from './stores'
import router from './router'
import App from './App.vue'
import { i18n } from './i18n'

// 1. Hify 设计系统 Token（CSS 自定义属性）
import './styles/tokens.css'

// 2. Element Plus 变量覆盖（依赖 tokens.css 中的变量）
import './styles/element.css'

// 3. 全局基础样式
import './assets/main.css'

createApp(App)
  .use(pinia)
  .use(router)
  .use(i18n)
  .mount('#app')
