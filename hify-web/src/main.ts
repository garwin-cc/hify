import { createApp } from 'vue'
import { pinia } from './stores'
import router from './router'
import App from './App.vue'

// 1. Element Plus 基础样式（先加载，后续 CSS 可覆盖其变量）
import 'element-plus/dist/index.css'

// 2. Hify 设计系统 Token（CSS 自定义属性）
import './styles/tokens.css'

// 3. Element Plus 变量覆盖（依赖 tokens.css 中的变量）
import './styles/element.css'

// 4. 全局基础样式
import './assets/main.css'

createApp(App)
  .use(pinia)
  .use(router)
  .mount('#app')
